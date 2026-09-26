/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/08/09
 */

package top.yuxs.springbootdev.modules.file.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端条件注入配置
 * 
 * @author YuDongXing
 * @since 2026/08/09
 */
@Configuration
@ConditionalOnProperty(prefix = "file.minio", name = {"endpoint", "access-key", "secret-key"})
public class MinioClientConfig {

    @Autowired
    private FileProperties fileProperties;

    /**
     * 按端点和凭据创建客户端，使非当前上传类型的历史文件仍可清理。
     */
    @Bean
    public MinioClient minioClient() {
        FileProperties.MinioConfig config = fileProperties.getMinio();
        return MinioClient.builder()
                .endpoint(config.getEndpoint())
                .credentials(config.getAccessKey(), config.getSecretKey())
                .build();
    }
}
