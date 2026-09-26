/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/08/09
 */

package top.yuxs.springbootdev.modules.file.config;

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
@ConditionalOnProperty(prefix = "file.aliyun", name = {"endpoint", "access-key", "secret-key"})
public class AliyunOssClientConfig {

    @Autowired
    private FileProperties fileProperties;

    /**
     * 配置仍存在时保留历史存储客户端，切换上传类型不影响旧文件清理。
     */
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
