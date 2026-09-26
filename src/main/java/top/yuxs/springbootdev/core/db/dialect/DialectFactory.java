/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/05/10
 */

package top.yuxs.springbootdev.core.db.dialect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import top.yuxs.springbootdev.core.db.config.AegisDbProperties;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库方言工厂类：负责解析与提供当前运行环境对应的 DbDialect
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DialectFactory {

    private final List<DbDialect> dialects;
    private final AegisDbProperties properties;
    private final Map<String, DbDialect> dialectCache = new ConcurrentHashMap<>();

    /**
     * 获取当前活动的数据库方言策略
     *
     * @param jdbcTemplate JdbcTemplate 实例，用于感知 Connection 的 DatabaseMetaData
     * @return 匹配的 DbDialect 实例
     */
    public DbDialect getDialect(JdbcTemplate jdbcTemplate) {
        String configuredDialect = properties.getDialect();
        if (configuredDialect != null && !configuredDialect.trim().isEmpty()) {
            return findDialectByName(configuredDialect.trim());
        }

        return dialectCache.computeIfAbsent("AUTO_DETECTED", k -> resolveDialectFromJdbc(jdbcTemplate));
    }

    private DbDialect resolveDialectFromJdbc(JdbcTemplate jdbcTemplate) {
        String databaseProductName;
        try {
            databaseProductName = jdbcTemplate.execute((Connection conn) -> {
                DatabaseMetaData metaData = conn.getMetaData();
                return metaData.getDatabaseProductName();
            });
        } catch (Exception e) {
            throw new IllegalStateException("无法通过 JDBC 感知数据库类型，请通过 db.init.dialect 显式指定受支持方言", e);
        }

        log.info("感知到的数据库产品名称: {}", databaseProductName);
        if (databaseProductName != null) {
            String nameUpper = databaseProductName.toUpperCase();
            if (nameUpper.contains("MYSQL")) {
                return findDialectByName("MYSQL");
            }
            if (nameUpper.contains("POSTGRESQL")) {
                return findDialectByName("POSTGRESQL");
            }
            if (nameUpper.contains("ORACLE")) {
                return findDialectByName("ORACLE");
            }
            if (nameUpper.contains("DAMENG") || nameUpper.matches(".*\\bDM\\b.*")) {
                throw new IllegalStateException("检测到达梦数据库，请在确认 Oracle 兼容模式后显式设置 db.init.dialect=ORACLE");
            }
            if (nameUpper.contains("H2")) {
                return findDialectByName("H2");
            }
        }

        throw new IllegalStateException("未识别的数据库产品: " + databaseProductName + "，请通过 db.init.dialect 显式指定受支持方言");
    }

    private DbDialect findDialectByName(String dialectName) {
        for (DbDialect dialect : dialects) {
            if (dialect.getDialectName().equalsIgnoreCase(dialectName)) {
                return dialect;
            }
        }
        throw new IllegalArgumentException("不支持的数据库方言: " + dialectName + "，当前支持 MYSQL、POSTGRESQL、ORACLE、H2");
    }
}
