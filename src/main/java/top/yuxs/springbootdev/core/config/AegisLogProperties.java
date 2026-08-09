/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/08/09
 */

package top.yuxs.springbootdev.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 操作日志自定义配置类
 *
 * @author YuDongXing
 * @since 2026/08/09
 */
@Data
@Component
@ConfigurationProperties(prefix = "aegis.log")
public class AegisLogProperties {

    /**
     * 接口日志保留时间 (天)。
     * -1 或 0 代表不进行自动物理清理。默认 30 天。
     */
    private int retentionDays = 30;
}
