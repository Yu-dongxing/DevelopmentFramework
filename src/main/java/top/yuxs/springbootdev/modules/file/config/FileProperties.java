/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/16
 */

package top.yuxs.springbootdev.modules.file.config;

import lombok.Data;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import top.yuxs.springbootdev.modules.file.enums.StorageAccessMode;
import top.yuxs.springbootdev.modules.file.enums.StorageType;

/**
 * 文件上传配置属性
 *
 * @author YuDongXing
 * @since 2026/04/16
 */
@Data
@Component
@ConfigurationProperties(prefix = "file")
public class FileProperties {

    /**
     * 当前启用的存储类型
     */
    private StorageType active = StorageType.LOCAL;

    /**
     * 本地存储配置
     */
    private LocalConfig local = new LocalConfig();

    /**
     * 阿里云存储配置
     */
    private AliyunConfig aliyun = new AliyunConfig();

    /**
     * MinIO 存储配置
     */
    private MinioConfig minio = new MinioConfig();

    /** RustFS 独立配置，不随当前上传类型切换而销毁历史客户端。 */
    private RustfsConfig rustfs = new RustfsConfig();

    /**
     * 校验当前选择与显式启用状态，完整配置在启用时校验。
     */
    @PostConstruct
    public void validateRustfs() {
        if (active == StorageType.RUSTFS && !rustfs.isEnabled()) {
            throw new IllegalArgumentException("选择 RustFS 存储时必须启用 file.rustfs.enabled");
        }
        if (rustfs.isEnabled()) {
            rustfs.validate();
        }
    }

    @Data
    public static class LocalConfig {
        /**
         * 访问域名
         */
        private String domain = "http://localhost:8080";
        /**
         * 物理存储路径
         */
        private String uploadPath = "./uploads/";
        /**
         * Web 访问路径
         */
        private String accessPath = "/uploads/**";
        /** 上传文件的默认访问方式。 */
        private StorageAccessMode accessMode = StorageAccessMode.PUBLIC;
    }

    @Data
    public static class AliyunConfig {
        /**
         * 访问域名
         */
        private String domain;
        /**
         * 端点
         */
        private String endpoint;
        /**
         * 存储桶名称
         */
        private String bucketName;
        /**
         * AccessKey
         */
        private String accessKey;
        /**
         * SecretKey
         */
        private String secretKey;
        /** 上传文件的默认访问方式。 */
        private StorageAccessMode accessMode = StorageAccessMode.PUBLIC;
        /** 私有预签名 URL 有效期，单位为秒。 */
        private int presignedUrlExpireSeconds = 900;
    }

    @Data
    public static class MinioConfig {
        /**
         * 访问域名
         */
        private String domain;
        /**
         * 端点
         */
        private String endpoint;
        /**
         * 存储桶名称
         */
        private String bucketName = "springbootdev";
        /**
         * AccessKey
         */
        private String accessKey;
        /**
         * SecretKey
         */
        private String secretKey;
        /** 上传文件的默认访问方式。 */
        private StorageAccessMode accessMode = StorageAccessMode.PUBLIC;
        /** 私有预签名 URL 有效期，单位为秒。 */
        private int presignedUrlExpireSeconds = 900;
    }
}
