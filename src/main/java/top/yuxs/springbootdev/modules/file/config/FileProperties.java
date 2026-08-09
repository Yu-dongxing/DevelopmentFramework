/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/16
 */

package top.yuxs.springbootdev.modules.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
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
    }
}
