package top.yuxs.springbootdev.modules.file.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证配置绑定、客户端生命周期与历史存储的独立启用。
 */
class RustfsClientConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class, RustfsClientConfig.class);

    /**
     * 仅加载文件配置绑定，不连接数据库及 Redis。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FileProperties.class)
    static class PropertiesConfiguration {
    }

    /**
     * 默认不开启 RustFS，不创建网络客户端。
     */
    @Test
    void shouldSkipDisabledStorage() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertFalse(context.containsBean("rustfsS3Client"));
        });
    }

    /**
     * 即使当前上传使用本地存储，也应保留历史 RustFS 客户端。
     */
    @Test
    void shouldRetainHistoricalClientAndHideCredentials() {
        configured().withPropertyValues("file.active=LOCAL").run(context -> {
            assertNull(context.getStartupFailure());
            assertNotNull(context.getBean(S3Client.class));
            assertNotNull(context.getBean(S3Presigner.class));
            String configuration = context.getBean(FileProperties.class).toString();
            assertFalse(configuration.contains("test-rustfs-secret"));
            assertFalse(configuration.contains("test-rustfs-access"));
        });
    }

    /**
     * 当前选择 RustFS 但未启用时，启动即失败。
     */
    @Test
    void shouldRejectActiveButDisabledStorage() {
        runner.withPropertyValues("file.active=RUSTFS")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    /**
     * 缺少凭据、端点带路径、期限越界及超时矛盾均不得启动。
     */
    @Test
    void shouldRejectIncompleteOrInvalidConfiguration() {
        configured().withPropertyValues("file.rustfs.secret-key=")
                .run(context -> assertNotNull(context.getStartupFailure()));
        configured().withPropertyValues("file.rustfs.endpoint=https://storage.example/prefix")
                .run(context -> assertNotNull(context.getStartupFailure()));
        configured().withPropertyValues("file.rustfs.presigned-url-expire-seconds=604801")
                .run(context -> assertNotNull(context.getStartupFailure()));
        configured().withPropertyValues("file.rustfs.call-timeout=1s")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    /**
     * 配置本地构造 SDK 所需的虚拟凭据，构造过程不发送网络请求。
     */
    private ApplicationContextRunner configured() {
        return runner.withPropertyValues("file.rustfs.enabled=true",
                "file.rustfs.endpoint=http://localhost:19000",
                "file.rustfs.access-key=test-rustfs-access",
                "file.rustfs.secret-key=test-rustfs-secret");
    }
}
