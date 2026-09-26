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
 * PostgreSQL 数据库方言实现：使用 pg_catalog 精确查询完整列类型与注释
 */
@Component
public class PostgresqlDialect implements DbDialect {

    @Override
    public String getDialectName() {
        return "POSTGRESQL";
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
        return "\"" + trimmed.toLowerCase() + "\"";
    }

    @Override
    public String mapColumnType(String originalType) {
        if (originalType == null) {
            throw new IllegalArgumentException("列类型不能为空");
        }
        String trimmed = originalType.trim().toUpperCase();
        if (!trimmed.matches("^[a-zA-Z0-9_(),\\s]+$")) {
            throw new IllegalArgumentException("非法的列类型名称: " + originalType);
        }

        if (trimmed.startsWith("DATETIME")) {
            return trimmed.replace("DATETIME", "TIMESTAMP");
        }
        if (trimmed.startsWith("INT(") || trimmed.equals("INT")) {
            return "INTEGER";
        }
        if (trimmed.startsWith("TINYINT")) {
            return "SMALLINT";
        }
        if (trimmed.equals("DOUBLE")) {
            return "DOUBLE PRECISION";
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
        String safeTableName = quoteIdentifier(tableName);
        String safeColName = quoteIdentifier(column.getName());
        String mappedType = mapColumnType(column.getType());

        return String.format("ALTER TABLE %s ALTER COLUMN %s TYPE %s USING %s::%s",
                safeTableName, safeColName, mappedType, safeColName, mappedType);
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
        boolean isUnique = index.type() == IndexType.UNIQUE;

        List<String> safeColumns = Arrays.stream(index.columns())
                .map(this::quoteIdentifier)
                .collect(Collectors.toList());

        if (index.type() == IndexType.FULLTEXT) {
            throw new IllegalArgumentException("PostgreSQL 不支持通用 FULLTEXT 索引，请为该字段定义 PostgreSQL 专用全文检索方案");
        }
        return String.format("CREATE %sINDEX %s ON %s (%s)",
                isUnique ? "UNIQUE " : "",
                safeIndexName,
                safeTableName,
                String.join(", ", safeColumns));
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
                safeTableName, safeFkName, String.join(", ", safeColumns), safeRefTableName, String.join(", ", safeRefColumns), onDelete, onUpdate);
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
        String safeTableName = quoteIdentifier(table.getTableName());

        if (table.getTableComment() != null && !table.getTableComment().isEmpty()) {
            String safeComment = table.getTableComment().replace("'", "''");
            comments.add(String.format("COMMENT ON TABLE %s IS '%s'", safeTableName, safeComment));
        }

        for (ColumnMetadata column : table.getColumns()) {
            if (column.getComment() != null && !column.getComment().isEmpty()) {
                String safeComment = column.getComment().replace("'", "''");
                String safeColName = quoteIdentifier(column.getName());
                comments.add(String.format("COMMENT ON COLUMN %s.%s IS '%s'", safeTableName, safeColName, safeComment));
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
        return "SELECT COUNT(*) FROM information_schema.TABLES WHERE table_schema = current_schema() AND LOWER(table_name) = LOWER(?)";
    }

    @Override
    public String getTableCommentSql() {
        return "SELECT COALESCE(obj_description(c.oid, 'pg_class'), '') AS \"TABLE_COMMENT\" " +
               "FROM pg_class c JOIN pg_namespace n ON c.relnamespace = n.oid " +
               "WHERE n.nspname = current_schema() AND c.relkind IN ('r', 'p') AND LOWER(c.relname) = LOWER(?)";
    }

    @Override
    public String getColumnsInfoSql() {
        return "SELECT a.attname AS \"COLUMN_NAME\", " +
               "format_type(a.atttypid, a.atttypmod) AS \"COLUMN_TYPE\", " +
               "COALESCE(col_description(a.attrelid, a.attnum), '') AS \"COLUMN_COMMENT\", " +
               "pg_get_expr(d.adbin, d.adrelid) AS \"COLUMN_DEFAULT\", " +
               "EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid = a.attrelid " +
               "AND i.indisprimary AND a.attnum = ANY(i.indkey)) AS \"IS_PRIMARY_KEY\", " +
               "(a.attidentity <> '' OR COALESCE(pg_get_expr(d.adbin, d.adrelid), '') LIKE 'nextval(%') " +
               "AS \"IS_AUTO_INCREMENT\" " +
               "FROM pg_attribute a " +
               "JOIN pg_class c ON a.attrelid = c.oid " +
               "JOIN pg_namespace n ON c.relnamespace = n.oid " +
               "LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum " +
               "WHERE n.nspname = current_schema() " +
               "  AND LOWER(c.relname) = LOWER(?) " +
               "  AND a.attnum > 0 " +
               "  AND NOT a.attisdropped";
    }

    @Override
    public String getIndexNamesSql() {
        return "SELECT indexname FROM pg_indexes WHERE schemaname = current_schema() AND LOWER(tablename) = LOWER(?)";
    }

    @Override
    public String getForeignKeyNamesSql() {
        return "SELECT constraint_name FROM information_schema.table_constraints WHERE constraint_schema = current_schema() AND constraint_type = 'FOREIGN KEY' AND LOWER(table_name) = LOWER(?)";
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
