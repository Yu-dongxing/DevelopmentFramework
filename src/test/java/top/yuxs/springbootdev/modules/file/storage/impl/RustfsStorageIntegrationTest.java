package top.yuxs.springbootdev.modules.file.storage.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import top.yuxs.springbootdev.modules.file.config.FileProperties;
import top.yuxs.springbootdev.modules.file.config.RustfsClientConfig;
import top.yuxs.springbootdev.modules.file.enums.StorageAccessMode;
import top.yuxs.springbootdev.modules.file.service.FileUploadCompensationService;
import top.yuxs.springbootdev.modules.file.service.SysFileService;
import top.yuxs.springbootdev.modules.file.storage.StorageUploadResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 显式指定隔离 RustFS 环境后执行真实 S3 验证；只创建和清理本用例的唯一测试桶。
 */
@EnabledIfEnvironmentVariable(named = "RUSTFS_TEST_ENDPOINT", matches = ".+")
class RustfsStorageIntegrationTest {
    /**
     * 验证校验和上传、匿名拒绝、签名下载及失效、公开 URL、历史桶删除和重复删除。
     */
    @Test
    void shouldCompleteRealRustfsLifecycle() throws Exception {
        FileProperties properties = new FileProperties();
        properties.getRustfs().setEndpoint(System.getenv("RUSTFS_TEST_ENDPOINT"));
        properties.getRustfs().setAccessKey(System.getenv("RUSTFS_TEST_ACCESS_KEY"));
        properties.getRustfs().setSecretKey(System.getenv("RUSTFS_TEST_SECRET_KEY"));
        String bucket = "codex-rustfs-test-" + UUID.randomUUID();
        properties.getRustfs().setBucketName(bucket);
        properties.getRustfs().setAutoCreateBucket(true);
        properties.getRustfs().setPresignedUrlExpireSeconds(2);
        RustfsClientConfig configuration = new RustfsClientConfig();
        try (S3Client client = configuration.rustfsS3Client(properties);
             S3Presigner signer = configuration.rustfsPresigner(properties);
             HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            RustfsStorageServiceImpl storage = new RustfsStorageServiceImpl();
            ReflectionTestUtils.setField(storage, "fileProperties", properties);
            ReflectionTestUtils.setField(storage, "client", client);
            ReflectionTestUtils.setField(storage, "presigner", signer);
            ReflectionTestUtils.setField(storage, "compensationService",
                    new FileUploadCompensationService(mock(SysFileService.class)));
            StorageUploadResult uploaded = null;
            try {
                byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
                uploaded = storage.upload(new MockMultipartFile("file", "你好.txt", "text/plain", data), "test");
                assertEquals("5d41402abc4b2a76b9719d911017c592", uploaded.md5());
                assertArrayEquals(data, client.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(bucket).key(uploaded.filePath()).build()).asByteArray());
                URI signed = URI.create(storage.buildUrl(uploaded.filePath(), bucket));
                assertArrayEquals(data, get(http, signed).body());
                URI anonymous = new URI(signed.getScheme(), signed.getAuthority(), signed.getPath(), null, null);
                assertEquals(403, get(http, anonymous).statusCode());
                Thread.sleep(3100);
                assertEquals(403, get(http, signed).statusCode());

                // 公开策略只作用于本用例创建的测试桶，生产适配器不会设置桶策略。
                client.putBucketPolicy(PutBucketPolicyRequest.builder().bucket(bucket).policy(
                        "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
                                + "\"Principal\":\"*\",\"Action\":\"s3:GetObject\","
                                + "\"Resource\":\"arn:aws:s3:::" + bucket + "/*\"}]}").build());
                properties.getRustfs().setAccessMode(StorageAccessMode.PUBLIC);
                URI publicUrl = URI.create(storage.buildUrl(uploaded.filePath(), bucket));
                assertEquals(200, get(http, publicUrl).statusCode());
                assertArrayEquals(data, get(http, publicUrl).body());

                properties.getRustfs().setBucketName("different-default-bucket");
                storage.delete(uploaded.filePath(), bucket);
                storage.delete(uploaded.filePath(), bucket);
                assertEquals(404, get(http, publicUrl).statusCode());
            } finally {
                if (uploaded != null) {
                    client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(uploaded.filePath()).build());
                }
                client.deleteBucket(DeleteBucketRequest.builder().bucket(bucket).build());
            }
        }
    }

    /**
     * 限制真实 HTTP 调用时限，避免测试环境不可用时无限等待。
     */
    private HttpResponse<byte[]> get(HttpClient client, URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }
}
