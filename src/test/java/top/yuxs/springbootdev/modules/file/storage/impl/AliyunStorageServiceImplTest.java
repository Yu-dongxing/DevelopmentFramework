package top.yuxs.springbootdev.modules.file.storage.impl;

import com.aliyun.oss.OSS;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.yuxs.springbootdev.core.exception.BusinessException;
import top.yuxs.springbootdev.modules.file.config.FileProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 验证 OSS 历史桶删除和域名构建。
 */
class AliyunStorageServiceImplTest {

    /**
     * 旧桶不能使用新桶绑定的 CDN 地址，也不能在新桶执行删除。
     */
    @Test
    void shouldUseHistoricalBucketAndAvoidCurrentCdn() {
        OSS client = mock(OSS.class);
        AliyunStorageServiceImpl service = createService(client);
        service.delete("file.txt", "old-bucket");
        verify(client).deleteObject("old-bucket", "file.txt");
        assertEquals("https://old-bucket.oss-cn-hangzhou.aliyuncs.com/file.txt",
                service.buildUrl("file.txt", "old-bucket"));
        assertEquals("https://new-cdn.example/file.txt", service.buildUrl("file.txt", "new-bucket"));
    }

    /**
     * 历史桶为空时拒绝删除，保留任务供人工修复元数据。
     */
    @Test
    void shouldRejectMissingBucket() {
        OSS client = mock(OSS.class);
        AliyunStorageServiceImpl service = createService(client);
        assertThrows(BusinessException.class, () -> service.delete("file.txt", ""));
        verifyNoInteractions(client);
    }

    /**
     * 构造绑定新桶 CDN 的存储服务。
     */
    private AliyunStorageServiceImpl createService(OSS client) {
        FileProperties properties = new FileProperties();
        properties.getAliyun().setBucketName("new-bucket");
        properties.getAliyun().setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        properties.getAliyun().setDomain("https://new-cdn.example");
        AliyunStorageServiceImpl service = new AliyunStorageServiceImpl();
        ReflectionTestUtils.setField(service, "fileProperties", properties);
        ReflectionTestUtils.setField(service, "ossClient", client);
        return service;
    }
}
