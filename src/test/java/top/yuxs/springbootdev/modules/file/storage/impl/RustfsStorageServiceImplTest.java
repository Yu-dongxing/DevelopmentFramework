package top.yuxs.springbootdev.modules.file.storage.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import top.yuxs.springbootdev.core.exception.BusinessException;
import top.yuxs.springbootdev.modules.file.config.FileProperties;
import top.yuxs.springbootdev.modules.file.config.RustfsClientConfig;
import top.yuxs.springbootdev.modules.file.enums.StorageAccessMode;
import top.yuxs.springbootdev.modules.file.service.FileUploadCompensationService;
import top.yuxs.springbootdev.modules.file.storage.StorageUploadResult;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证可重读上传、失败补偿、历史桶删除以及真实 SDK 的 URL 签名。
 */
class RustfsStorageServiceImplTest {
    private FileProperties properties;
    private RustfsStorageServiceImpl storage;
    private S3Client client;
    private FileUploadCompensationService compensation;

    /**
     * 使用模拟传输层隔离网络，仅在签名用例中使用真实 SDK。
     */
    @BeforeEach
    void setUp() {
        properties = new FileProperties();
        properties.getRustfs().setEndpoint("http://localhost:19000");
        properties.getRustfs().setPublicEndpoint("https://files.example.com");
        properties.getRustfs().setAccessKey("test-access");
        properties.getRustfs().setSecretKey("test-secret");
        client = mock(S3Client.class);
        compensation = mock(FileUploadCompensationService.class);
        storage = new RustfsStorageServiceImpl();
        ReflectionTestUtils.setField(storage, "fileProperties", properties);
        ReflectionTestUtils.setField(storage, "client", client);
        ReflectionTestUtils.setField(storage, "compensationService", compensation);
    }

    /**
     * 同一请求体可以重复读取，摘要只包含一次完整内容。
     */
    @Test
    void shouldUploadRepeatableBodyWithCorrectDigest() {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            RequestBody body = invocation.getArgument(1);
            try (InputStream first = body.contentStreamProvider().newStream()) {
                assertArrayEquals(data, first.readAllBytes());
            }
            try (InputStream retry = body.contentStreamProvider().newStream()) {
                assertArrayEquals(data, retry.readAllBytes());
            }
            return PutObjectResponse.builder().build();
        });
        StorageUploadResult result = storage.upload(
                new MockMultipartFile("file", "test.txt", null, data), "test");
        assertEquals("5d41402abc4b2a76b9719d911017c592", result.md5());
        assertEquals("springbootdev", result.storageBucket());
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertEquals("application/octet-stream", request.getValue().contentType());
        assertEquals("XUFAKrxLKna5cZ2REBfFkg==", request.getValue().contentMD5());
        // 上传完成后临时文件已清理，不能继续读取。
        assertThrows(RuntimeException.class, () -> body.getValue().contentStreamProvider().newStream());
        verifyNoInteractions(compensation);
    }

    /**
     * 服务端可能写入后才断开连接，因此 SDK 报错也要执行补偿。
     */
    @Test
    void shouldCompensateUncertainUpload() {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new IllegalStateException("模拟网络中断"));
        assertThrows(BusinessException.class, () -> storage.upload(
                new MockMultipartFile("file", "test.txt", null, new byte[]{1}), "test"));
        ArgumentCaptor<StorageUploadResult> result = ArgumentCaptor.forClass(StorageUploadResult.class);
        verify(compensation).compensate(eq(storage), result.capture());
        assertEquals("springbootdev", result.getValue().storageBucket());
        assertTrue(result.getValue().filePath().startsWith("test/"));
    }

    /**
     * 上传上限和后缀注入在远端写入前被阻止。
     */
    @Test
    void shouldRejectOversizedAndUnsafeFiles() {
        properties.getRustfs().setMaxUploadBytes(1);
        assertThrows(BusinessException.class, () -> storage.upload(
                new MockMultipartFile("file", "test.txt", null, new byte[]{1, 2}), "test"));
        assertThrows(BusinessException.class, () -> storage.upload(
                new MockMultipartFile("file", "test.txt/escape", null, new byte[]{1}), "test"));
        verifyNoInteractions(client, compensation);
    }

    /**
     * 无权限访问桶时不能尝试建桶，更不能设置公开策略。
     */
    @Test
    void shouldNotCreateBucketAfterAccessDenied() {
        properties.getRustfs().setAutoCreateBucket(true);
        when(client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).build());
        assertThrows(BusinessException.class, () -> storage.upload(
                new MockMultipartFile("file", "test.txt", null, new byte[]{1}), "test"));
        verify(client, never()).createBucket(any(CreateBucketRequest.class));
        verifyNoInteractions(compensation);
    }

    /**
     * 切换默认桶后仍按历史桶删除，缺桶时拒绝回退。
     */
    @Test
    void shouldDeleteHistoricalBucketAndPropagateRealFailures() {
        properties.getRustfs().setBucketName("new-bucket");
        storage.delete("a.txt", "old-bucket");
        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(request.capture());
        assertEquals("old-bucket", request.getValue().bucket());
        assertThrows(BusinessException.class, () -> storage.delete("a.txt", null));
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404)
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchBucket").build()).build());
        assertThrows(S3Exception.class, () -> storage.delete("a.txt", "old-bucket"));
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404)
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchKey").build()).build());
        storage.delete("a.txt", "old-bucket");
    }

    /**
     * 使用真实 SDK 验证外部端点、历史桶、有效期及特殊字符编码，不发送网络请求。
     */
    @Test
    void shouldSignPublicEndpointAndEncodeObjectKey() {
        RustfsClientConfig configuration = new RustfsClientConfig();
        try (S3Client realClient = configuration.rustfsS3Client(properties);
             S3Presigner signer = configuration.rustfsPresigner(properties)) {
            ReflectionTestUtils.setField(storage, "client", realClient);
            ReflectionTestUtils.setField(storage, "presigner", signer);
            URI url = URI.create(storage.buildUrl("目录/a b+#.txt", "old-bucket"));
            assertEquals("files.example.com", url.getHost());
            assertEquals("/old-bucket/目录/a b+#.txt", url.getPath());
            assertTrue(url.getRawQuery().contains("X-Amz-Expires=900"));
            properties.getRustfs().setAccessMode(StorageAccessMode.PUBLIC);
            URI publicUrl = URI.create(storage.buildUrl("目录/a b+#.txt", "old-bucket"));
            assertEquals(url.getPath(), publicUrl.getPath());
            assertEquals(null, publicUrl.getQuery());
        }
    }
}
