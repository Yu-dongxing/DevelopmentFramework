package top.yuxs.springbootdev.modules.file.storage.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import top.yuxs.springbootdev.core.exception.BusinessException;
import top.yuxs.springbootdev.modules.file.config.FileProperties;
import top.yuxs.springbootdev.modules.file.config.RustfsConfig;
import top.yuxs.springbootdev.modules.file.enums.StorageAccessMode;
import top.yuxs.springbootdev.modules.file.enums.StorageType;
import top.yuxs.springbootdev.modules.file.service.FileUploadCompensationService;
import top.yuxs.springbootdev.modules.file.storage.StorageFileSupport;
import top.yuxs.springbootdev.modules.file.storage.StorageService;
import top.yuxs.springbootdev.modules.file.storage.StorageUploadResult;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;

/**
 * RustFS 标准 S3 适配；使用可重读临时文件保证 SDK 重试不破坏上传内容及摘要。
 */
@Slf4j
@Service
public class RustfsStorageServiceImpl implements StorageService {
    @Autowired
    private FileProperties fileProperties;

    @Autowired(required = false)
    @Qualifier("rustfsS3Client")
    private S3Client client;

    @Autowired(required = false)
    @Qualifier("rustfsPresigner")
    private S3Presigner presigner;

    @Autowired
    private FileUploadCompensationService compensationService;

    /**
     * 有界复制后上传，SDK 异常也按本次唯一位置执行补偿。
     */
    @Override
    public StorageUploadResult upload(MultipartFile file, String path) {
        checkClient();
        RustfsConfig config = fileProperties.getRustfs();
        if (file == null || file.isEmpty() || file.getSize() > config.getMaxUploadBytes()) {
            throw new BusinessException("RustFS 上传文件为空或超过配置上限");
        }
        Path temporary = null;
        StorageUploadResult attempted = null;
        try {
            String key = StorageFileSupport.objectKey(path, file.getOriginalFilename());
            temporary = Files.createTempFile("rustfs-upload-", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("MD5");
            long size = stage(file, temporary, digest, config.getMaxUploadBytes());
            byte[] hash = digest.digest();
            ensureBucket(config);
            attempted = new StorageUploadResult(key, config.getBucketName(), StorageFileSupport.digestHex(hash));
            String contentType = file.getContentType();
            client.putObject(PutObjectRequest.builder()
                            .bucket(attempted.storageBucket()).key(key).contentLength(size)
                            .contentType(contentType == null || contentType.isBlank()
                                    ? "application/octet-stream" : contentType)
                            .contentMD5(Base64.getEncoder().encodeToString(hash)).build(),
                    RequestBody.fromFile(temporary));
            log.info("RustFS 文件上传成功，桶: {}，路径: {}", attempted.storageBucket(), key);
            return attempted;
        } catch (Exception e) {
            log.error("RustFS 文件上传失败", e);
            if (attempted != null) {
                compensationService.compensate(this, attempted);
            }
            throw new BusinessException("RustFS 文件上传失败，请检查存储配置与服务状态");
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception e) {
                    log.error("RustFS 上传临时文件清理失败，需要清理本机临时目录", e);
                }
            }
        }
    }

    /**
     * 流式写入临时文件并计算一次摘要，不信任客户端声明的文件长度。
     */
    private long stage(MultipartFile file, Path target, MessageDigest digest, long limit) throws Exception {
        long total = 0;
        try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new BusinessException("RustFS 上传文件超过配置上限");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        if (total == 0 || total != file.getSize()) {
            throw new BusinessException("上传文件实际长度与声明长度不一致");
        }
        return total;
    }

    /**
     * 仅在显式开启时检查并创建桶；权限错误不能被误判为桶不存在。
     */
    private void ensureBucket(RustfsConfig config) {
        if (!config.isAutoCreateBucket()) {
            return;
        }
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(config.getBucketName()).build());
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            CreateBucketRequest.Builder request = CreateBucketRequest.builder().bucket(config.getBucketName());
            if (!"us-east-1".equals(config.getRegion())) {
                request.createBucketConfiguration(CreateBucketConfiguration.builder()
                        .locationConstraint(config.getRegion()).build());
            }
            try {
                client.createBucket(request.build());
            } catch (BucketAlreadyOwnedByYouException ignored) {
                // 多实例同时初始化同一自有桶时，复用已经创建的桶。
            }
        }
    }

    /**
     * 删除当前默认桶的对象，历史文件必须调用带桶参数的方法。
     */
    @Override
    public void delete(String filePath) {
        delete(filePath, fileProperties.getRustfs().getBucketName());
    }

    /**
     * 按历史位置幂等删除；只将明确的对象不存在视为成功。
     */
    @Override
    public void delete(String filePath, String storageBucket) {
        checkClient();
        requireLocation(filePath, storageBucket);
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(storageBucket).key(filePath).build());
        } catch (S3Exception e) {
            if (e.awsErrorDetails() == null || !"NoSuchKey".equals(e.awsErrorDetails().errorCode())) {
                throw e;
            }
        }
    }

    /**
     * 根据默认桶生成访问地址。
     */
    @Override
    public String buildUrl(String filePath) {
        return buildUrl(filePath, fileProperties.getRustfs().getBucketName());
    }

    /**
     * 按历史桶生成编码正确的访问地址；签名调用必须由业务层先完成授权。
     */
    @Override
    public String buildUrl(String filePath, String storageBucket) {
        checkClient();
        requireLocation(filePath, storageBucket);
        RustfsConfig config = fileProperties.getRustfs();
        if (!usesTemporaryUrl()) {
            return client.utilities().getUrl(GetUrlRequest.builder()
                    .endpoint(URI.create(config.effectivePublicEndpoint()))
                    .bucket(storageBucket).key(filePath).build()).toExternalForm();
        }
        if (presigner == null) {
            throw new BusinessException("RustFS 签名客户端未初始化");
        }
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(config.getPresignedUrlExpireSeconds()))
                .getObjectRequest(GetObjectRequest.builder().bucket(storageBucket).key(filePath).build())
                .build()).url().toExternalForm();
    }

    /**
     * 私有地址仅在响应时生成，不持久化到文件表。
     */
    @Override
    public boolean usesTemporaryUrl() {
        return fileProperties.getRustfs().getAccessMode() == StorageAccessMode.PRIVATE;
    }

    /**
     * 声明独立的 RustFS 存储标识。
     */
    @Override
    public StorageType getType() {
        return StorageType.RUSTFS;
    }

    /**
     * 缺少历史配置时明确失败，不切换到其他存储。
     */
    private void checkClient() {
        if (client == null) {
            throw new BusinessException("RustFS 客户端未初始化，请启用并配置历史存储");
        }
    }

    /**
     * 历史桶和对象路径必须完整，禁止回退当前桶。
     */
    private void requireLocation(String path, String bucket) {
        if (path == null || path.isBlank() || bucket == null || bucket.isBlank()) {
            throw new BusinessException("文件缺少历史存储桶或对象路径");
        }
    }
}
