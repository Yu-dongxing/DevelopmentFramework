/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/05/10
 */

package top.yuxs.springbootdev.core.db.dialect.impl;

import com.baomidou.mybatisplus.annotation.TableName;
import org.springframework.stereotype.Component;
import top.yuxs.springbootdev.core.db.annotation.ForeignKey;
import top.yuxs.springbootdev.core.db.annotation.Index;
import top.yuxs.springbootdev.core.db.dialect.DbDialect;
import top.yuxs.springbootdev.core.db.enums.IndexType;
import top.yuxs.springbootdev.core.db.metadata.ColumnMetadata;
import top.yuxs.springbootdev.core.db.metadata.TableMetadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MySQL 数据库方言实现
 */
@Component
public class MysqlDialect implements DbDialect {

    @Override
    public String getDialectName() {
        return "MYSQL";
    }

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("标识符不能为空");
        }
        String trimmed = identifier.trim();
        if (!trimmed.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("非法的 SQL 标识符: " + identifier);
        }
        return "`" + trimmed + "`";
    }

    @Override
    public String mapColumnType(String originalType) {
        if (originalType == null) {
            throw new IllegalArgumentException("列类型不能为空");
        }
        String trimmed = originalType.trim();
        if (!trimmed.matches("^[a-zA-Z0-9_(),\\s]+$")) {
            throw new IllegalArgumentException("非法的列类型名称: " + originalType);
        }
        return trimmed;
    }

    @Override
    public String generateCreateTableSql(TableMetadata table) {
        String tableName = quoteIdentifier(table.getTableName());
        StringBuilder sql = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");
        List<String> definitions = new ArrayList<>();

        for (ColumnMetadata column : table.getColumns()) {
            definitions.add(buildColumnDefinition(column, true));
        }

        for (Index index : table.getIndexes()) {
            definitions.add(buildIndexDefinition(index));
        }

        sql.append(String.join(", ", definitions)).append(")");
        if (table.getTableComment() != null && !table.getTableComment().isEmpty()) {
            String safeComment = table.getTableComment().replace("'", "''");
            sql.append(" COMMENT='").append(safeComment).append("'");
        }
        return sql.toString();
    }

    @Override
    public String generateAddColumnSql(String tableName, ColumnMetadata column) {
        return String.format("ALTER TABLE %s ADD COLUMN %s", quoteIdentifier(tableName),
                buildColumnDefinition(column, true));
    }

    @Override
    public String generateModifyColumnSql(String tableName, ColumnMetadata column) {
        return String.format("ALTER TABLE %s MODIFY COLUMN %s", quoteIdentifier(tableName),
                buildColumnDefinition(column, false));
    }

    @Override
    public String generateModifyColumnDefaultSql(String tableName, ColumnMetadata column) {
        return generateModifyColumnSql(tableName, column);
    }

    @Override
    public String generateCreateIndexSql(String tableName, Index index) {
        return "ALTER TABLE " + quoteIdentifier(tableName) + " ADD " + buildIndexDefinition(index);
    }

    @Override
    public String generateAddForeignKeySql(String tableName, ForeignKey fk) {
        TableName refAnn = fk.referenceEntity().getAnnotation(TableName.class);
        if (refAnn == null) {
            throw new IllegalArgumentException("外键引用实体 " + fk.referenceEntity().getSimpleName() + " 缺失 @TableName");
        }

        String safeTableName = quoteIdentifier(tableName);
        String safeFkName = quoteIdentifier(fk.name());
        String safeRefTableName = quoteIdentifier(refAnn.value());

        List<String> safeColumns = Arrays.stream(fk.columns())
                .map(this::quoteIdentifier)
                .collect(Collectors.toList());
        List<String> safeRefColumns = Arrays.stream(fk.referencedColumns())
                .map(this::quoteIdentifier)
                .collect(Collectors.toList());

        String columnsSql = String.join(", ", safeColumns);
        String refColumnsSql = String.join(", ", safeRefColumns);
        String onDelete = "ON DELETE " + fk.onDelete().name().replace('_', ' ');
        String onUpdate = "ON UPDATE " + fk.onUpdate().name().replace('_', ' ');

        return String.format("ALTER TABLE %s ADD CONSTRAINT %s FOREIGN KEY (%s) REFERENCES %s (%s) %s %s",
                safeTableName, safeFkName, columnsSql, safeRefTableName, refColumnsSql, onDelete, onUpdate);
    }

    @Override
    public String generateInsertDefaultDataSql(String tableName, Map<String, Object> defaultData) {
        if (defaultData == null || defaultData.isEmpty()) {
            return null;
        }

        StringBuilder sql = new StringBuilder("INSERT INTO ").append(quoteIdentifier(tableName)).append(" (");
        StringBuilder placeholders = new StringBuilder();

        List<String> columns = new ArrayList<>(defaultData.keySet());
        for (int i = 0; i < columns.size(); i++) {
            String colName = quoteIdentifier(columns.get(i));
            sql.append(colName).append(i == columns.size() - 1 ? "" : ", ");
            placeholders.append("?").append(i == columns.size() - 1 ? "" : ", ");
        }

        sql.append(") VALUES (").append(placeholders).append(")");
        return sql.toString();
    }

    @Override
    public List<String> generateCommentSqls(TableMetadata table) {
        // MySQL 支持表注释和列注释内联定义，无需额外的 COMMENT ON 语句
        return Collections.emptyList();
    }

    @Override
    public String generateTableCommentSql(TableMetadata table) {
        String comment = table.getTableComment() == null ? "" : table.getTableComment().replace("'", "''");
        return String.format("ALTER TABLE %s COMMENT='%s'", quoteIdentifier(table.getTableName()), comment);
    }

    @Override
    public String generateColumnCommentSql(String tableName, ColumnMetadata column) {
        // MySQL 使用 MODIFY COLUMN 的内联 COMMENT 语法，不生成独立注释语句。
        return null;
    }

    @Override
    public boolean usesInlineColumnComment() {
        return true;
    }

    @Override
    public String getTableExistsSql() {
        return "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
    }

    @Override
    public String getTableCommentSql() {
        return "SELECT TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
    }

    @Override
    public String getColumnsInfoSql() {
        return "SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT, COLUMN_DEFAULT, " +
               "(COLUMN_KEY = 'PRI') AS IS_PRIMARY_KEY, (EXTRA LIKE '%auto_increment%') AS IS_AUTO_INCREMENT " +
               "FROM information_schema.COLUMNS " +
               "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
    }

    @Override
    public String getIndexNamesSql() {
        return "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME != 'PRIMARY'";
    }

    @Override
    public String getForeignKeyNamesSql() {
        return "SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_TYPE = 'FOREIGN KEY'";
    }

    private String buildColumnDefinition(ColumnMetadata column, boolean includePrimaryKey) {
        StringBuilder sb = new StringBuilder();
        sb.append(quoteIdentifier(column.getName())).append(" ");
        sb.append(mapColumnType(column.getType()));

        if (includePrimaryKey && column.isPrimaryKey()) {
            sb.append(" PRIMARY KEY");
        }
        if (column.isAutoIncrement()) {
            sb.append(" AUTO_INCREMENT");
        }

        if (column.getDefaultValue() != null) {
            String defVal = column.getDefaultValue().trim();
            if (defVal.contains(";") || defVal.contains("--") || defVal.contains("/*")) {
                throw new IllegalArgumentException("默认值中含有不安全的 SQL 字符: " + defVal);
            }
            sb.append(" DEFAULT ").append(defVal);
        }

        if (column.getComment() != null && !column.getComment().isEmpty()) {
            String safeComment = column.getComment().replace("'", "''");
            sb.append(" COMMENT '").append(safeComment).append("'");
        }

        return sb.toString();
    }

    private String buildIndexDefinition(Index index) {
        StringBuilder sb = new StringBuilder();
        switch (index.type()) {
            case UNIQUE -> sb.append("UNIQUE KEY ");
            case FULLTEXT -> sb.append("FULLTEXT KEY ");
            default -> sb.append("KEY ");
        }
        sb.append(quoteIdentifier(index.name())).append(" ");

        List<String> safeColumns = Arrays.stream(index.columns())
                .map(this::quoteIdentifier)
                .collect(Collectors.toList());
        sb.append("(").append(String.join(", ", safeColumns)).append(") ");

        if (index.type() != IndexType.FULLTEXT) {
            sb.append("USING BTREE");
        }
        if (!index.comment().isEmpty()) {
            String safeComment = index.comment().replace("'", "''");
            sb.append(" COMMENT '").append(safeComment).append("'");
        }
        return sb.toString();
    }
}
