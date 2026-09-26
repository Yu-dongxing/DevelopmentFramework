/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/05/10
 */

package top.yuxs.springbootdev.core.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import top.yuxs.springbootdev.core.db.dialect.DbDialect;
import top.yuxs.springbootdev.core.db.dialect.DialectFactory;
import top.yuxs.springbootdev.core.db.metadata.ColumnMetadata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 结构执行器：负责基于数据库方言执行 SQL 并查询数据库元数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaExecutor {

    private final JdbcTemplate jdbcTemplate;
    private final DialectFactory dialectFactory;

    public DbDialect getDialect() {
        return dialectFactory.getDialect(jdbcTemplate);
    }

    public boolean tableExists(String tableName) {
        DbDialect dialect = getDialect();
        String sql = dialect.getTableExistsSql();
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
        return count != null && count > 0;
    }

    /**
     * 查询数据库中已有的表注释。
     *
     * @param tableName 表名
     * @return 表注释；数据库返回空值时返回空字符串
     */
    public String getExistingTableComment(String tableName) {
        DbDialect dialect = getDialect();
        String comment = jdbcTemplate.queryForObject(dialect.getTableCommentSql(), String.class, tableName);
        return comment == null ? "" : comment.trim();
    }

    public Map<String, ColumnMetadata> getExistingColumnsInfo(String tableName) {
        DbDialect dialect = getDialect();
        String sql = dialect.getColumnsInfoSql();
        Map<String, ColumnMetadata> map = new HashMap<>();
        jdbcTemplate.query(sql, (rs) -> {
            String colName = rs.getString("COLUMN_NAME");
            String colType = rs.getString("COLUMN_TYPE");
            String colComment = rs.getString("COLUMN_COMMENT");
            String colDefault = rs.getString("COLUMN_DEFAULT");
            boolean primaryKey = rs.getBoolean("IS_PRIMARY_KEY");
            boolean autoIncrement = rs.getBoolean("IS_AUTO_INCREMENT");

            ColumnMetadata metadata = ColumnMetadata.builder()
                    .name(colName)
                    .type(colType)
                    .comment(colComment)
                    .defaultValue(colDefault)
                    .isPrimaryKey(primaryKey)
                    .isAutoIncrement(autoIncrement)
                    .build();
            map.put(colName.toLowerCase(), metadata);
        }, tableName);
        return map;
    }

    public Set<String> getExistingIndexNames(String tableName) {
        DbDialect dialect = getDialect();
        String sql = dialect.getIndexNamesSql();
        List<String> list = jdbcTemplate.queryForList(sql, String.class, tableName);
        return list.stream().map(String::toLowerCase).collect(Collectors.toSet());
    }

    public Set<String> getExistingForeignKeyNames(String tableName) {
        DbDialect dialect = getDialect();
        String sql = dialect.getForeignKeyNamesSql();
        List<String> list = jdbcTemplate.queryForList(sql, String.class, tableName);
        return list.stream().map(String::toLowerCase).collect(Collectors.toSet());
    }

    public boolean isTableEmpty(String tableName) {
        try {
            DbDialect dialect = getDialect();
            String quotedTable = dialect.quoteIdentifier(tableName);
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + quotedTable, Integer.class);
            return count != null && count == 0;
        } catch (Exception e) {
            log.warn("检查表 {} 是否为空时出错: {}", tableName, e.getMessage());
            return false;
        }
    }

    public void execute(String sql) {
        log.info("执行 SQL: {}", sql);
        jdbcTemplate.execute(sql);
    }

    public void executeWithParams(String sql, Object[] params) {
        log.info("执行 SQL (带参数): {}, 参数: {}", sql, params);
        jdbcTemplate.update(sql, params);
    }
}
