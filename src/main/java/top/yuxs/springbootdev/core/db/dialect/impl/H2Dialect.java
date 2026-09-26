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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * H2 内存数据库方言实现
 */
@Component
public class H2Dialect implements DbDialect {

    @Override
    public String getDialectName() {
        return "H2";
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
        return "`" + trimmed.toUpperCase() + "`";
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
        if (trimmed.toUpperCase().startsWith("DATETIME")) {
            return trimmed.toUpperCase().replace("DATETIME", "TIMESTAMP");
        }
        if (trimmed.equalsIgnoreCase("TEXT")) {
            // H2 会将 TEXT 规范化为超长 VARCHAR，使用同一表示可避免重复 ALTER TABLE。
            return "VARCHAR(1000000000)";
        }
        return trimmed;
    }

    @Override
    public String generateCreateTableSql(TableMetadata table) {
        String tableName = quoteIdentifier(table.getTableName());
        StringBuilder sql = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");
        List<String> definitions = new ArrayList<>();

        for (ColumnMetadata column : table.getColumns()) {
            definitions.add(buildColumnDefinition(column));
        }

        sql.append(String.join(", ", definitions)).append(")");
        return sql.toString();
    }

    @Override
    public String generateAddColumnSql(String tableName, ColumnMetadata column) {
        return String.format("ALTER TABLE %s ADD COLUMN %s", quoteIdentifier(tableName), buildColumnDefinition(column));
    }

    @Override
    public String generateModifyColumnSql(String tableName, ColumnMetadata column) {
        // H2 修改列类型时不能重复声明 PRIMARY KEY，否则已有主键表会产生语法错误。
        return String.format("ALTER TABLE %s ALTER COLUMN %s %s",
                quoteIdentifier(tableName), quoteIdentifier(column.getName()), mapColumnType(column.getType()));
    }

    @Override
    public String generateModifyColumnDefaultSql(String tableName, ColumnMetadata column) {
        String prefix = String.format("ALTER TABLE %s ALTER COLUMN %s ", quoteIdentifier(tableName),
                quoteIdentifier(column.getName()));
        if (column.getDefaultValue() == null) {
            return prefix + "DROP DEFAULT";
        }
        return prefix + "SET DEFAULT " + column.getDefaultValue().trim();
    }

    @Override
    public String generateCreateIndexSql(String tableName, Index index) {
        String safeTableName = quoteIdentifier(tableName);
        String safeIndexName = quoteIdentifier(index.name());
        List<String> safeColumns = Arrays.stream(index.columns())
                .map(this::quoteIdentifier)
                .collect(Collectors.toList());

        if (index.type() == IndexType.FULLTEXT) {
            throw new IllegalArgumentException("H2 不支持通用 FULLTEXT 索引");
        }
        return String.format("CREATE %sINDEX %s ON %s (%s)", index.type() == IndexType.UNIQUE ? "UNIQUE " : "",
                safeIndexName, safeTableName, String.join(", ", safeColumns));
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

        String onDelete = "ON DELETE " + fk.onDelete().name().replace('_', ' ');
        String onUpdate = "ON UPDATE " + fk.onUpdate().name().replace('_', ' ');
        return String.format("ALTER TABLE %s ADD CONSTRAINT %s FOREIGN KEY (%s) REFERENCES %s (%s) %s %s",
                safeTableName, safeFkName, String.join(", ", safeColumns), safeRefTableName,
                String.join(", ", safeRefColumns), onDelete, onUpdate);
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
        List<String> comments = new ArrayList<>();
        comments.add(generateTableCommentSql(table));
        for (ColumnMetadata column : table.getColumns()) {
            String commentSql = generateColumnCommentSql(table.getTableName(), column);
            if (commentSql != null) {
                comments.add(commentSql);
            }
        }
        return comments;
    }

    @Override
    public String generateTableCommentSql(TableMetadata table) {
        String safeTableName = quoteIdentifier(table.getTableName());
        if (table.getTableComment() == null || table.getTableComment().isEmpty()) {
            return String.format("COMMENT ON TABLE %s IS NULL", safeTableName);
        }
        return String.format("COMMENT ON TABLE %s IS '%s'", safeTableName,
                table.getTableComment().replace("'", "''"));
    }

    @Override
    public String generateColumnCommentSql(String tableName, ColumnMetadata column) {
        String safeTableName = quoteIdentifier(tableName);
        String safeColumnName = quoteIdentifier(column.getName());
        if (column.getComment() == null || column.getComment().isEmpty()) {
            return String.format("COMMENT ON COLUMN %s.%s IS NULL", safeTableName, safeColumnName);
        }
        return String.format("COMMENT ON COLUMN %s.%s IS '%s'", safeTableName, safeColumnName,
                column.getComment().replace("'", "''"));
    }

    @Override
    public String getTableExistsSql() {
        return "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = CURRENT_SCHEMA() AND LOWER(TABLE_NAME) = LOWER(?)";
    }

    @Override
    public String getTableCommentSql() {
        return "SELECT COALESCE(REMARKS, '') AS TABLE_COMMENT FROM information_schema.TABLES " +
               "WHERE TABLE_SCHEMA = CURRENT_SCHEMA() AND LOWER(TABLE_NAME) = LOWER(?)";
    }

    @Override
    public String getColumnsInfoSql() {
        return "SELECT c.COLUMN_NAME, CASE " +
               "WHEN UPPER(c.DATA_TYPE) = 'CHARACTER VARYING' THEN 'VARCHAR(' || c.CHARACTER_MAXIMUM_LENGTH || ')' " +
               "WHEN UPPER(c.DATA_TYPE) = 'NUMERIC' THEN 'DECIMAL(' || c.NUMERIC_PRECISION || ',' || c.NUMERIC_SCALE || ')' " +
               "ELSE c.DATA_TYPE END AS COLUMN_TYPE, c.REMARKS AS COLUMN_COMMENT, c.COLUMN_DEFAULT, " +
               "EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS tc " +
               "JOIN information_schema.KEY_COLUMN_USAGE ku ON tc.CONSTRAINT_CATALOG = ku.CONSTRAINT_CATALOG " +
               "AND tc.CONSTRAINT_SCHEMA = ku.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = ku.CONSTRAINT_NAME " +
               "WHERE tc.CONSTRAINT_TYPE = 'PRIMARY KEY' AND tc.TABLE_SCHEMA = c.TABLE_SCHEMA " +
               "AND tc.TABLE_NAME = c.TABLE_NAME AND ku.COLUMN_NAME = c.COLUMN_NAME) AS IS_PRIMARY_KEY, " +
               "(c.IS_IDENTITY = 'YES') AS IS_AUTO_INCREMENT FROM information_schema.COLUMNS c " +
               "WHERE c.TABLE_SCHEMA = CURRENT_SCHEMA() AND LOWER(c.TABLE_NAME) = LOWER(?)";
    }

    @Override
    public String getIndexNamesSql() {
        return "SELECT INDEX_NAME FROM information_schema.INDEXES WHERE TABLE_SCHEMA = CURRENT_SCHEMA() AND LOWER(TABLE_NAME) = LOWER(?) AND INDEX_NAME != 'PRIMARY'";
    }

    @Override
    public String getForeignKeyNamesSql() {
        return "SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = CURRENT_SCHEMA() AND CONSTRAINT_TYPE = 'FOREIGN KEY' AND LOWER(TABLE_NAME) = LOWER(?)";
    }

    private String buildColumnDefinition(ColumnMetadata column) {
        StringBuilder sb = new StringBuilder();
        sb.append(quoteIdentifier(column.getName())).append(" ");
        sb.append(mapColumnType(column.getType()));

        if (column.isAutoIncrement()) {
            sb.append(" GENERATED BY DEFAULT AS IDENTITY");
        }

        if (column.isPrimaryKey()) {
            sb.append(" PRIMARY KEY");
        }

        if (column.getDefaultValue() != null) {
            String defVal = column.getDefaultValue().trim();
            if (defVal.contains(";") || defVal.contains("--") || defVal.contains("/*")) {
                throw new IllegalArgumentException("默认值中含有不安全的 SQL 字符: " + defVal);
            }
            sb.append(" DEFAULT ").append(defVal);
        }

        return sb.toString();
    }
}
