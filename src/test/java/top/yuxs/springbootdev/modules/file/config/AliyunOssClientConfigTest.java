package top.yuxs.springbootdev.modules.file.config;

import com.aliyun.oss.OSS;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 验证历史 OSS 客户端不会随当前上传类型切换而消失。
 */
class AliyunOssClientConfigTest {

    /**
     * 只构造 SDK 客户端，不执行网络请求；上下文关闭时自动释放客户端。
     */
    @Test
    void shouldRetainConfiguredHistoricalClient() {
        FileProperties properties = new FileProperties();
        properties.getAliyun().setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        properties.getAliyun().setAccessKey("test-access");
        properties.getAliyun().setSecretKey("test-secret");
        new ApplicationContextRunner().withUserConfiguration(AliyunOssClientConfig.class)
                .withBean(FileProperties.class, () -> properties)
                .withPropertyValues("file.active=LOCAL", "file.aliyun.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
                        "file.aliyun.access-key=test-access", "file.aliyun.secret-key=test-secret")
                .run(context -> assertNotNull(context.getBean(OSS.class)));
    }

    /**
     * 没有配置的云存储不影响本地存储启动。
     */
    @Test
    void shouldSkipUnconfiguredClient() {
        new ApplicationContextRunner().withUserConfiguration(AliyunOssClientConfig.class)
                .withBean(FileProperties.class)
                .run(context -> assertFalse(context.containsBean("aliyunOssClient")));
    }
}
