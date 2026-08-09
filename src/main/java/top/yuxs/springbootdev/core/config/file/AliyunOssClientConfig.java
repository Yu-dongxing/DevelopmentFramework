/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/08/09
 */

package top.yuxs.springbootdev.core.config.file;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 客户端条件注入配置
 *
 * @author YuDongXing
 * @since 2026/08/09
 */
@Configuration
@ConditionalOnProperty(prefix = "file", name = "active", havingValue = "ALIYUN_OSS")
public class AliyunOssClientConfig {

    @Autowired
    private FileProperties fileProperties;

    @Bean(destroyMethod = "shutdown")
    public OSS aliyunOssClient() {
        FileProperties.AliyunConfig config = fileProperties.getAliyun();
        return new OSSClientBuilder().build(
                config.getEndpoint(),
                config.getAccessKey(),
                config.getSecretKey()
        );
    }
}
