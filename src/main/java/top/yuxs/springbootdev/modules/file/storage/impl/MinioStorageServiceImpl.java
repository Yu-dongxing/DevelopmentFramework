/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/05/23
 */

package top.yuxs.springbootdev.modules.file.storage.impl;

import io.minio.*;
import io.minio.Http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.yuxs.springbootdev.modules.file.config.FileProperties;
import top.yuxs.springbootdev.modules.file.enums.StorageType;
import top.yuxs.springbootdev.core.exception.BusinessException;
import top.yuxs.springbootdev.modules.file.storage.StorageService;
import top.yuxs.springbootdev.modules.file.storage.StorageUploadResult;
import top.yuxs.springbootdev.modules.file.service.FileUploadCompensationService;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import top.yuxs.springbootdev.modules.file.enums.StorageAccessMode;
import top.yuxs.springbootdev.modules.file.storage.StorageFileSupport;

/**
 * MinIO 存储服务最新 SDK 完整实现
 *
 * @author YuDongXing
 * @since 2026/08/09
 */
@Slf4j
@Service
public class MinioStorageServiceImpl implements StorageService {

    @Autowired
    private FileProperties fileProperties;

    @Autowired(required = false)
    private MinioClient minioClient;

    @Autowired
    private FileUploadCompensationService compensationService;

    @Override
    public StorageUploadResult upload(MultipartFile file, String path) {
        checkClientInitialized();
        StorageUploadResult attemptedUpload = null;

        try {
            String objectKey = StorageFileSupport.objectKey(path, file.getOriginalFilename());

            String bucketName = fileProperties.getMinio().getBucketName();

            // 自动检查并创建存储桶，保证开箱即用
            ensureBucketExists(bucketName, getAccessMode());

            // 上传文件流到 MinIO
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            try (InputStream is = file.getInputStream();
                 DigestInputStream digestInputStream = new DigestInputStream(is, messageDigest)) {
                // SDK 报错时服务端仍可能已经接收对象，保留本次唯一位置用于补偿。
                attemptedUpload = new StorageUploadResult(objectKey, bucketName, null);
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectKey)
                                .stream(digestInputStream, file.getSize(), -1L)
                                .contentType(file.getContentType())
                                .build()
                );
            }

            log.info("MinIO 文件上传成功: {}/{}", bucketName, objectKey);
            return new StorageUploadResult(objectKey, bucketName, StorageFileSupport.digestHex(messageDigest.digest()));
        } catch (Exception e) {
            log.error("MinIO 文件上传失败", e);
            if (attemptedUpload != null) {
                compensationService.compensate(this, attemptedUpload);
            }
            throw new BusinessException("MinIO 文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public void delete(String filePath) {
        delete(filePath, fileProperties.getMinio().getBucketName());
    }

    /**
     * 按历史桶删除对象，禁止回退到新的默认桶误删同名文件。
     */
    @Override
    public void delete(String filePath, String storageBucket) {
        checkClientInitialized();
        if (storageBucket == null || storageBucket.isBlank()) {
            throw new BusinessException("文件缺少历史存储桶，拒绝物理删除");
        }

        try {
            String bucketName = storageBucket;
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filePath)
                            .build()
            );
            log.info("MinIO 物理文件删除成功: {}/{}", bucketName, filePath);
        } catch (Exception e) {
            log.error("MinIO 物理文件删除失败: {}", filePath, e);
            throw new BusinessException("MinIO 物理文件删除失败: " + e.getMessage());
        }
    }

    @Override
    public String buildUrl(String filePath) {
        return buildUrl(filePath, fileProperties.getMinio().getBucketName(), getAccessMode());
    }

    /**
     * 使用上传时记录的桶构建地址。
     */
    @Override
    public String buildUrl(String filePath, String storageBucket) {
        return buildUrl(filePath, storageBucket, getAccessMode());
    }

    /**
     * 私有 MinIO 对象按请求重新签发 URL，公开对象保留固定访问地址。
     */
    @Override
    public String buildUrl(String filePath, String storageBucket, StorageAccessMode accessMode) {
        if (storageBucket == null || storageBucket.isBlank()) {
            throw new BusinessException("文件缺少历史存储桶，无法构建访问地址");
        }
        if (accessMode == StorageAccessMode.PRIVATE) {
            checkClientInitialized();
            try {
                return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET).bucket(storageBucket).object(filePath)
                        .expiry(fileProperties.getMinio().getPresignedUrlExpireSeconds(), TimeUnit.SECONDS).build());
            } catch (Exception e) {
                log.error("MinIO 私有文件签名失败，桶: {}，路径: {}", storageBucket, filePath, e);
                throw new BusinessException("MinIO 私有文件访问地址生成失败");
            }
        }
        FileProperties.MinioConfig minioConfig = fileProperties.getMinio();
        String baseUrl = minioConfig.getDomain();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = minioConfig.getEndpoint();
        }

        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }

        // 默认公开访问 URL 拼装格式: http://ip:port/bucketName/objectKey
        return baseUrl + storageBucket + "/" + filePath;
    }

    @Override
    public StorageType getType() {
        return StorageType.MINIO;
    }

    /**
     * 返回新上传 MinIO 文件的访问方式。
     */
    @Override
    public StorageAccessMode getAccessMode() {
        return fileProperties.getMinio().getAccessMode();
    }

    private void checkClientInitialized() {
        if (minioClient == null) {
            throw new BusinessException("MinIO 客户端未成功初始化，请检查历史存储的端点和凭据配置！");
        }
    }

    /**
     * 保证存储桶存在；仅公开文件允许初始化匿名只读策略。
     */
    private void ensureBucketExists(String bucketName, StorageAccessMode accessMode) throws Exception {
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!found) {
            log.info("MinIO 存储桶 [{}] 不存在，正在自动创建...", bucketName);
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

            if (accessMode != StorageAccessMode.PUBLIC) {
                log.info("MinIO 存储桶 [{}] 创建成功，保持私有访问策略", bucketName);
                return;
            }
            // 仅 PUBLIC 配置允许匿名公开只读 (GetObject)
            String policy = "{\n" +
                    "  \"Version\": \"2012-10-17\",\n" +
                    "  \"Statement\": [\n" +
                    "    {\n" +
                    "      \"Effect\": \"Allow\",\n" +
                    "      \"Principal\": {\"AWS\": [\"*\"]},\n" +
                    "      \"Action\": [\"s3:GetObject\"],\n" +
                    "      \"Resource\": [\"arn:aws:s3:::" + bucketName + "/*\"]\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .config(policy)
                            .build()
            );
            log.info("MinIO 存储桶 [{}] 创建成功，并成功设置为匿名只读策略！", bucketName);
        }
    }

}
