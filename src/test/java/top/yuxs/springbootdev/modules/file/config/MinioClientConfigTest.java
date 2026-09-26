package top.yuxs.springbootdev.modules.file.config;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 验证切换当前上传类型后历史 MinIO 客户端仍可使用。
 */
class MinioClientConfigTest {

    /**
     * LOCAL 为当前类型时，仍按保留的 MinIO 配置创建客户端。
     */
    @Test
    void shouldRetainConfiguredHistoricalClient() {
        FileProperties properties = new FileProperties();
        properties.getMinio().setEndpoint("http://localhost:9000");
        properties.getMinio().setAccessKey("test-access");
        properties.getMinio().setSecretKey("test-secret");
        new ApplicationContextRunner().withUserConfiguration(MinioClientConfig.class)
                .withBean(FileProperties.class, () -> properties)
                .withPropertyValues("file.active=LOCAL", "file.minio.endpoint=http://localhost:9000",
                        "file.minio.access-key=test-access", "file.minio.secret-key=test-secret")
                .run(context -> assertNotNull(context.getBean(MinioClient.class)));
    }

    /**
     * 未配置历史存储时不强制创建客户端。
     */
    @Test
    void shouldSkipUnconfiguredClient() {
        new ApplicationContextRunner().withUserConfiguration(MinioClientConfig.class)
                .withBean(FileProperties.class)
                .run(context -> assertFalse(context.containsBean("minioClient")));
    }
}
