/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/16
 */

package top.yuxs.springbootdev.modules.file.service.impl.storage.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.yuxs.springbootdev.core.config.file.FileProperties;
import top.yuxs.springbootdev.core.enums.db.StorageType;
import top.yuxs.springbootdev.core.exception.BusinessException;
import top.yuxs.springbootdev.modules.file.service.impl.storage.StorageService;

import java.io.InputStream;
import java.util.UUID;

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

    @Override
    public String upload(MultipartFile file, String path) {
        checkClientInitialized();

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String fileName = UUID.randomUUID().toString() + extension;

            // 构造上传的 Object Key (例: default/2026/08/xxx.jpg)
            java.time.LocalDate now = java.time.LocalDate.now();
            String year = String.valueOf(now.getYear());
            String month = String.format("%02d", now.getMonthValue());
            String objectKey = (path + "/" + year + "/" + month + "/" + fileName).replace("\\", "/").replaceAll("/+", "/");

            String bucketName = fileProperties.getAliyun().getBucketName();

            // 自动检查并创建存储桶，设置公共读 ACL
            ensureBucketExists(bucketName);

            // 配置对象元数据，由于流无法回溯，显式设置 Content-Length 提升传输稳定性
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            // 上传文件流到 阿里云 OSS
            try (InputStream is = file.getInputStream()) {
                ossClient.putObject(new PutObjectRequest(bucketName, objectKey, is, metadata));
            }

            log.info("阿里云 OSS 文件上传成功: {}/{}", bucketName, objectKey);
            return objectKey;
        } catch (Exception e) {
            log.error("阿里云 OSS 文件上传失败", e);
            throw new BusinessException("阿里云 OSS 文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public void delete(String filePath) {
        checkClientInitialized();

        try {
            String bucketName = fileProperties.getAliyun().getBucketName();
            ossClient.deleteObject(bucketName, filePath);
            log.info("阿里云 OSS 物理文件删除成功: {}/{}", bucketName, filePath);
        } catch (Exception e) {
            log.error("阿里云 OSS 物理文件删除失败: {}", filePath, e);
            throw new BusinessException("阿里云 OSS 物理文件删除失败: " + e.getMessage());
        }
    }

    @Override
    public String buildUrl(String filePath) {
        FileProperties.AliyunConfig aliyunConfig = fileProperties.getAliyun();
        String baseUrl = aliyunConfig.getDomain();
        if (baseUrl == null || baseUrl.isEmpty()) {
            // 如果未配置专有加速或 CDN 域名，降级使用官方公网访问格式: https://bucketName.endpoint/objectKey
            String endpoint = aliyunConfig.getEndpoint().replace("http://", "").replace("https://", "");
            baseUrl = "https://" + aliyunConfig.getBucketName() + "." + endpoint;
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

    private void checkClientInitialized() {
        if (ossClient == null) {
            throw new BusinessException("阿里云 OSS 客户端未成功初始化，请检查配置文件中的 file.active 是否为 ALIYUN_OSS 以及相关参数是否配置正确！");
        }
    }

    /**
     * 保证存储桶存在，并设置为公共只读（PublicRead）权限
     */
    private void ensureBucketExists(String bucketName) {
        if (!ossClient.doesBucketExist(bucketName)) {
            log.info("阿里云 OSS 存储桶 [{}] 不存在，正在自动创建并开启公共读权限...", bucketName);
            ossClient.createBucket(bucketName);
            // 自动配置 Bucket 访问控制为：公共读
            ossClient.setBucketAcl(bucketName, CannedAccessControlList.PublicRead);
            log.info("阿里云 OSS 存储桶 [{}] 创建并公共读初始化成功！", bucketName);
        }
    }
}
