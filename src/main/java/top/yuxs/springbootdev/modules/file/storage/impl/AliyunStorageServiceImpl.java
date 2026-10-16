/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/16
 */

package top.yuxs.springbootdev.modules.file.storage.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
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
import top.yuxs.springbootdev.modules.file.storage.StorageFileSupport;
import java.util.Objects;
import java.util.Date;
import top.yuxs.springbootdev.modules.file.enums.StorageAccessMode;

/**
 * 阿里云 OSS 存储服务完整实现
 *
 * @author YuDongXing
 * @since 2026/08/09
 */
@Slf4j
@Service
public class AliyunStorageServiceImpl implements StorageService {

    @Autowired
    private FileProperties fileProperties;

    @Autowired(required = false)
    private OSS ossClient;

    @Autowired
    private FileUploadCompensationService compensationService;

    @Override
    public StorageUploadResult upload(MultipartFile file, String path) {
        checkClientInitialized();
        StorageUploadResult attemptedUpload = null;

        try {
            String objectKey = StorageFileSupport.objectKey(path, file.getOriginalFilename());

            String bucketName = fileProperties.getAliyun().getBucketName();

            // 自动检查并创建存储桶，设置公共读 ACL
            ensureBucketExists(bucketName, getAccessMode());

            // 配置对象元数据，由于流无法回溯，显式设置 Content-Length 提升传输稳定性
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            // 上传文件流到 阿里云 OSS
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            try (InputStream is = file.getInputStream();
                 DigestInputStream digestInputStream = new DigestInputStream(is, messageDigest)) {
                // 超时不代表对象未落盘，失败时按本次生成的唯一 Key 补偿。
                attemptedUpload = new StorageUploadResult(objectKey, bucketName, null);
                ossClient.putObject(new PutObjectRequest(bucketName, objectKey, digestInputStream, metadata));
            }

            log.info("阿里云 OSS 文件上传成功: {}/{}", bucketName, objectKey);
            return new StorageUploadResult(objectKey, bucketName, StorageFileSupport.digestHex(messageDigest.digest()));
        } catch (Exception e) {
            log.error("阿里云 OSS 文件上传失败", e);
            if (attemptedUpload != null) {
                compensationService.compensate(this, attemptedUpload);
            }
            throw new BusinessException("阿里云 OSS 文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public void delete(String filePath) {
        delete(filePath, fileProperties.getAliyun().getBucketName());
    }

    /**
     * 使用历史桶删除对象；缺失桶信息时拒绝猜测删除位置。
     */
    @Override
    public void delete(String filePath, String storageBucket) {
        checkClientInitialized();
        if (storageBucket == null || storageBucket.isBlank()) {
            throw new BusinessException("文件缺少历史存储桶，拒绝物理删除");
        }

        try {
            String bucketName = storageBucket;
            ossClient.deleteObject(bucketName, filePath);
            log.info("阿里云 OSS 物理文件删除成功: {}/{}", bucketName, filePath);
        } catch (Exception e) {
            log.error("阿里云 OSS 物理文件删除失败: {}", filePath, e);
            throw new BusinessException("阿里云 OSS 物理文件删除失败: " + e.getMessage());
        }
    }

    @Override
    public String buildUrl(String filePath) {
        return buildUrl(filePath, fileProperties.getAliyun().getBucketName(), getAccessMode());
    }

    /**
     * 历史桶不复用当前桶绑定的 CDN 域名，回退到该桶的标准地址。
     */
    @Override
    public String buildUrl(String filePath, String storageBucket) {
        return buildUrl(filePath, storageBucket, getAccessMode());
    }

    /**
     * 私有 OSS 对象即时签发访问地址，固定公开 URL 仅用于公开对象。
     */
    @Override
    public String buildUrl(String filePath, String storageBucket, StorageAccessMode accessMode) {
        FileProperties.AliyunConfig aliyunConfig = fileProperties.getAliyun();
        if (storageBucket == null || storageBucket.isBlank()) {
            throw new BusinessException("文件缺少历史存储桶，无法构建访问地址");
        }
        if (accessMode == StorageAccessMode.PRIVATE) {
            checkClientInitialized();
            try {
                Date expiration = new Date(System.currentTimeMillis()
                        + aliyunConfig.getPresignedUrlExpireSeconds() * 1000L);
                return ossClient.generatePresignedUrl(storageBucket, filePath, expiration).toExternalForm();
            } catch (Exception e) {
                log.error("阿里云 OSS 私有文件签名失败，桶: {}，路径: {}", storageBucket, filePath, e);
                throw new BusinessException("阿里云 OSS 私有文件访问地址生成失败");
            }
        }
        String baseUrl = aliyunConfig.getDomain();
        if (baseUrl == null || baseUrl.isEmpty() || !Objects.equals(storageBucket, aliyunConfig.getBucketName())) {
            // 如果未配置专有加速或 CDN 域名，降级使用官方公网访问格式: https://bucketName.endpoint/objectKey
            String endpoint = aliyunConfig.getEndpoint().replace("http://", "").replace("https://", "");
            baseUrl = "https://" + storageBucket + "." + endpoint;
        }

        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }

        return baseUrl + filePath;
    }

    @Override
    public StorageType getType() {
        return StorageType.ALIYUN_OSS;
    }

    /**
     * 返回新上传 OSS 文件的访问方式。
     */
    @Override
    public StorageAccessMode getAccessMode() {
        return fileProperties.getAliyun().getAccessMode();
    }

    private void checkClientInitialized() {
        if (ossClient == null) {
            throw new BusinessException("阿里云 OSS 客户端未成功初始化，请检查历史存储的端点和凭据配置！");
        }
    }

    /**
     * 保证存储桶存在；仅公开文件允许初始化 PublicRead 权限。
     */
    private void ensureBucketExists(String bucketName, StorageAccessMode accessMode) {
        if (!ossClient.doesBucketExist(bucketName)) {
            log.info("阿里云 OSS 存储桶 [{}] 不存在，正在自动创建...", bucketName);
            ossClient.createBucket(bucketName);
            if (accessMode == StorageAccessMode.PUBLIC) {
                ossClient.setBucketAcl(bucketName, CannedAccessControlList.PublicRead);
                log.info("阿里云 OSS 存储桶 [{}] 创建并公共读初始化成功！", bucketName);
            } else {
                log.info("阿里云 OSS 存储桶 [{}] 创建成功，保持私有访问策略", bucketName);
            }
        }
    }

}
