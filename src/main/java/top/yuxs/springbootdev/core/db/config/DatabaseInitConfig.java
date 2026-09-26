/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/11
 */

package top.yuxs.springbootdev.core.db.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import top.yuxs.springbootdev.core.db.DatabaseInitService;

/**
 * 数据库初始化配置。
 * 在定时任务注册前完成数据库结构同步，避免启动阶段访问尚未创建的表。
 *
 * @author YuDongXing
 * @since 2026/04/11
 */
@Configuration
@ConditionalOnProperty(prefix = "db.init", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseInitConfig {

    private final DatabaseInitService databaseInitService;

    public DatabaseInitConfig(DatabaseInitService databaseInitService) {
        this.databaseInitService = databaseInitService;
    }

    /**
     * 初始化数据库结构和默认数据。
     */
    @PostConstruct
    public void initDatabase() {
        databaseInitService.initDatabase();
    }
}
