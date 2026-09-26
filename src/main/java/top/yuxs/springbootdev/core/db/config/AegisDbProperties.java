/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/05/10
 */

package top.yuxs.springbootdev.core.db.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据库初始化配置属性
 *
 * @author YuDongXing
 * @since 2026/05/10
 */
@Data
@Component
@ConfigurationProperties(prefix = "db.init")
public class AegisDbProperties {

    /**
     * 是否开启数据库初始化（自动建表/更新表）
     */
    private boolean enabled = true;

    /**
     * 自动建表/更新表时扫描实体的基础包名
     */
    private String basePackage = "top.yuxs.springbootdev.modules";

    /**
     * 是否初始化默认数据
     */
    private boolean initData = true;

    /**
     * 显式指定数据库方言类型（如 MYSQL, POSTGRESQL, ORACLE, H2）。若不配置，系统将根据 DataSource 自动识别。
     */
    private String dialect;
}
