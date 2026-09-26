/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/08/20
 */

package top.yuxs.springbootdev.core.db.dialect;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import top.yuxs.springbootdev.core.db.config.AegisDbProperties;
import top.yuxs.springbootdev.core.db.dialect.impl.H2Dialect;
import top.yuxs.springbootdev.core.db.dialect.impl.MysqlDialect;
import top.yuxs.springbootdev.core.db.dialect.impl.OracleDialect;
import top.yuxs.springbootdev.core.db.dialect.impl.PostgresqlDialect;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 数据库方言自动识别测试
 */
class DialectFactoryTest {

    /**
     * 确保默认 MySQL 数据源能够自动匹配 MySQL 方言。
     */
    @Test
    @DisplayName("测试自动识别 MySQL 方言")
    void shouldDetectMysqlDialect() throws Exception {
        JdbcTemplate jdbcTemplate = createJdbcTemplate("MySQL");

        AegisDbProperties properties = new AegisDbProperties();
        properties.setDialect("");
        DialectFactory factory = createFactory(properties);

        Assertions.assertEquals("MYSQL", factory.getDialect(jdbcTemplate).getDialectName());
    }

    /**
     * 达梦必须由部署方确认 Oracle 兼容模式，不能仅根据产品名称静默套用 Oracle 方言。
     */
    @Test
    @DisplayName("测试达梦数据库要求显式指定兼容方言")
    void shouldRequireExplicitDialectForDameng() throws Exception {
        JdbcTemplate jdbcTemplate = createJdbcTemplate("DM DBMS");
        AegisDbProperties properties = new AegisDbProperties();
        properties.setDialect("");
        DialectFactory factory = createFactory(properties);

        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
                () -> factory.getDialect(jdbcTemplate));
        Assertions.assertTrue(exception.getMessage().contains("显式设置"));
    }

    /**
     * 创建返回指定数据库产品名称的 JdbcTemplate 测试替身。
     *
     * @param databaseProductName 数据库产品名称
     * @return JdbcTemplate 测试替身
     */
    private JdbcTemplate createJdbcTemplate(String databaseProductName) throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn(databaseProductName);
        when(jdbcTemplate.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Object>>any())).thenAnswer(invocation -> {
            ConnectionCallback<?> callback = invocation.getArgument(0);
            return callback.doInConnection(connection);
        });
        return jdbcTemplate;
    }

    /**
     * 创建包含全部内置方言的工厂。
     *
     * @param properties 初始化配置
     * @return 方言工厂
     */
    private DialectFactory createFactory(AegisDbProperties properties) {
        return new DialectFactory(List.of(
                new MysqlDialect(), new PostgresqlDialect(), new OracleDialect(), new H2Dialect()), properties);
    }
}
