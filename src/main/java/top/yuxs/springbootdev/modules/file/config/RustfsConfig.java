package top.yuxs.springbootdev.modules.file.config;

import lombok.Data;
import lombok.ToString;
import top.yuxs.springbootdev.modules.file.enums.StorageAccessMode;

import java.net.URI;
import java.time.Duration;

/**
 * RustFS 连接与上传配置，凭据禁止进入配置对象的字符串输出。
 */
@Data
public class RustfsConfig {
    /** 是否保留 RustFS 客户端，也用于读取和清理历史文件。 */
    private boolean enabled;
    /** 后端使用的 S3 服务地址。 */
    private String endpoint;
    /** 浏览器使用的 S3 地址，必须在签名前确定。 */
    private String publicEndpoint;
    /** 与服务端一致的签名区域。 */
    private String region = "us-east-1";
    /** 新上传对象的默认桶。 */
    private String bucketName = "springbootdev";
    /** 应用专用访问密钥。 */
    @ToString.Exclude
    private String accessKey;
    /** 应用专用秘密密钥。 */
    @ToString.Exclude
    private String secretKey;
    /** 默认使用无需通配 DNS 的路径寻址。 */
    private boolean pathStyleAccess = true;
    /** 默认只生成短期授权地址。 */
    private StorageAccessMode accessMode = StorageAccessMode.PRIVATE;
    /** 签名有效期，单位为秒。 */
    private int presignedUrlExpireSeconds = 900;
    /** 开发环境可启用自动建桶，绝不自动设置公开策略。 */
    private boolean autoCreateBucket;
    /** 建立连接的最长等待时间。 */
    private Duration connectionTimeout = Duration.ofSeconds(5);
    /** 单次请求的最长执行时间。 */
    private Duration attemptTimeout = Duration.ofSeconds(60);
    /** 包含重试的调用总时限。 */
    private Duration callTimeout = Duration.ofSeconds(180);
    /** 首次请求以外的最大重试次数。 */
    private int maxRetries = 2;
    /** 单文件上限，默认 100 MiB；首版使用普通 PutObject。 */
    private long maxUploadBytes = 100L * 1024 * 1024;

    /**
     * 验证显式启用后的配置，启动时即暴露错误，避免上传时才失败。
     */
    public void validate() {
        validateEndpoint(endpoint);
        if (publicEndpoint != null && !publicEndpoint.isBlank()) {
            validateEndpoint(publicEndpoint);
        }
        if (region == null || region.isBlank() || accessKey == null || accessKey.isBlank()
                || secretKey == null || secretKey.isBlank() || accessMode == null) {
            throw new IllegalArgumentException("RustFS 区域、凭据和访问方式不能为空");
        }
        if (bucketName == null || !bucketName.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")) {
            throw new IllegalArgumentException("RustFS 存储桶名称不合法");
        }
        if (presignedUrlExpireSeconds < 1 || presignedUrlExpireSeconds > 604800
                || maxRetries < 0 || maxRetries > 5 || maxUploadBytes < 1
                || maxUploadBytes > 5L * 1024 * 1024 * 1024) {
            throw new IllegalArgumentException("RustFS 签名期限、重试次数或上传上限不合法");
        }
        if (!positive(connectionTimeout) || !positive(attemptTimeout) || !positive(callTimeout)
                || connectionTimeout.compareTo(attemptTimeout) > 0
                || attemptTimeout.compareTo(callTimeout) > 0) {
            throw new IllegalArgumentException("RustFS 超时必须为正，且连接时限不能超过单次时限、总时限");
        }
    }

    /**
     * 返回外部访问端点；不对已经签名的 URL 进行域名替换。
     */
    public String effectivePublicEndpoint() {
        return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
    }

    /**
     * 限制端点为纯 HTTP(S) 源站，禁止用户信息、查询参数和路径前缀。
     */
    private void validateEndpoint(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("RustFS 端点不能为空");
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("RustFS 端点格式不合法");
        }
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("RustFS 端点必须是无路径前缀的 HTTP(S) 服务地址");
        }
    }

    /**
     * 判断时限是否严格为正。
     */
    private boolean positive(Duration value) {
        return value != null && !value.isNegative() && !value.isZero();
    }
}
