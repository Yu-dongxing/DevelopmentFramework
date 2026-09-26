/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/05/10
 */

package top.yuxs.springbootdev.core.db;

import org.springframework.stereotype.Component;
import top.yuxs.springbootdev.core.db.annotation.ForeignKey;
import top.yuxs.springbootdev.core.db.annotation.Index;
import top.yuxs.springbootdev.core.db.dialect.DbDialect;
import top.yuxs.springbootdev.core.db.metadata.ColumnMetadata;
import top.yuxs.springbootdev.core.db.metadata.TableMetadata;

import java.util.List;
import java.util.Map;

/**
 * SQL 生成器：根据数据方言委派生成对应数据库的 SQL 语句
 */
@Component
public class SqlGenerator {

    /**
     * 生成建表 SQL
     */
    public String generateCreateTableSql(DbDialect dialect, TableMetadata table) {
        return dialect.generateCreateTableSql(table);
    }

    /**
     * 生成新增列 SQL
     */
    public String generateAddColumnSql(DbDialect dialect, String tableName, ColumnMetadata column) {
        return dialect.generateAddColumnSql(tableName, column);
    }

    /**
     * 生成修改列 SQL
     */
    public String generateModifyColumnSql(DbDialect dialect, String tableName, ColumnMetadata column) {
        return dialect.generateModifyColumnSql(tableName, column);
    }

    /**
     * 生成新增索引 SQL
     */
    public String generateCreateIndexSql(DbDialect dialect, String tableName, Index index) {
        return dialect.generateCreateIndexSql(tableName, index);
    }

    /**
     * 生成新增外键 SQL
     */
    public String generateAddForeignKeySql(DbDialect dialect, String tableName, ForeignKey fk) {
        return dialect.generateAddForeignKeySql(tableName, fk);
    }

    /**
     * 生成插入默认数据 SQL
     */
    public String generateInsertDefaultDataSql(DbDialect dialect, String tableName, Map<String, Object> defaultData) {
        return dialect.generateInsertDefaultDataSql(tableName, defaultData);
    }

    /**
     * 生成独立注释 SQL 列表（用于 PostgreSQL / Oracle 等数据库）
     */
    public List<String> generateCommentSqls(DbDialect dialect, TableMetadata table) {
        return dialect.generateCommentSqls(table);
    }
}
