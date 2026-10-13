package top.yuxs.springbootdev.modules.file.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * RustFS 专用客户端，支持切换上传类型后继续处理历史对象。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "file.rustfs", name = "enabled", havingValue = "true")
public class RustfsClientConfig {
    /**
     * 构造同步客户端并限制网络等待，应用关闭时释放 SDK 资源。
     */
    @Bean(destroyMethod = "close")
    public S3Client rustfsS3Client(FileProperties properties) {
        RustfsConfig config = properties.getRustfs();
        config.validate();
        return S3Client.builder()
                .endpointOverride(URI.create(config.getEndpoint()))
                .region(Region.of(config.getRegion()))
                .credentialsProvider(credentials(config))
                .forcePathStyle(config.isPathStyleAccess())
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(config.getConnectionTimeout())
                        .socketTimeout(config.getAttemptTimeout()))
                .overrideConfiguration(builder -> builder
                        .apiCallAttemptTimeout(config.getAttemptTimeout())
                        .apiCallTimeout(config.getCallTimeout())
                        .retryStrategy(StandardRetryStrategy.builder()
                                .maxAttempts(config.getMaxRetries() + 1).build()))
                .build();
    }

    /**
     * 使用浏览器实际访问的端点签名，保持区域及寻址方式一致。
     */
    @Bean(destroyMethod = "close")
    public S3Presigner rustfsPresigner(FileProperties properties) {
        RustfsConfig config = properties.getRustfs();
        config.validate();
        return S3Presigner.builder()
                .endpointOverride(URI.create(config.effectivePublicEndpoint()))
                .region(Region.of(config.getRegion()))
                .credentialsProvider(credentials(config))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.isPathStyleAccess()).build())
                .build();
    }

    /**
     * 仅使用显式应用凭据，禁止回退到主机上的 AWS 身份链。
     */
    private StaticCredentialsProvider credentials(RustfsConfig config) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey()));
    }
}
