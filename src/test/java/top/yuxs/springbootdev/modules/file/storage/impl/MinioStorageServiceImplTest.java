package top.yuxs.springbootdev.modules.file.storage.impl;

import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import top.yuxs.springbootdev.core.exception.BusinessException;
import top.yuxs.springbootdev.modules.file.config.FileProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 验证 MinIO 操作不会在切桶后指向同名的新对象。
 */
class MinioStorageServiceImplTest {

    /**
     * 删除和 URL 使用历史桶，不受当前默认桶影响。
     */
    @Test
    void shouldUseHistoricalBucket() throws Exception {
        MinioClient client = mock(MinioClient.class);
        MinioStorageServiceImpl service = createService(client);
        service.delete("file.txt", "old-bucket");
        ArgumentCaptor<RemoveObjectArgs> args = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(client).removeObject(args.capture());
        assertEquals("old-bucket", args.getValue().bucket());
        assertEquals("file.txt", args.getValue().object());
        assertEquals("https://storage.example/old-bucket/file.txt", service.buildUrl("file.txt", "old-bucket"));
    }

    /**
     * 缺失历史桶时不能猜测删除位置。
     */
    @Test
    void shouldRejectMissingBucket() {
        MinioClient client = mock(MinioClient.class);
        MinioStorageServiceImpl service = createService(client);
        assertThrows(BusinessException.class, () -> service.delete("file.txt", null));
        verifyNoInteractions(client);
    }

    /**
     * 构造当前桶已切换的存储服务。
     */
    private MinioStorageServiceImpl createService(MinioClient client) {
        FileProperties properties = new FileProperties();
        properties.getMinio().setBucketName("new-bucket");
        properties.getMinio().setDomain("https://storage.example");
        MinioStorageServiceImpl service = new MinioStorageServiceImpl();
        ReflectionTestUtils.setField(service, "fileProperties", properties);
        ReflectionTestUtils.setField(service, "minioClient", client);
        return service;
    }
}
