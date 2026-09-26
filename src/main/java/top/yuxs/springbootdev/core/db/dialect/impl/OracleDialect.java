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
import top.yuxs.springbootdev.core.db.enums.ForeignKeyAction;
import top.yuxs.springbootdev.core.db.enums.IndexType;
import top.yuxs.springbootdev.core.db.metadata.ColumnMetadata;
import top.yuxs.springbootdev.core.db.metadata.TableMetadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Oracle 及达梦 (DM) 数据库方言实现：优化标准大写标识符与 USER_TAB_COLUMNS 元数据关联
 */
@Component
public class OracleDialect implements DbDialect {

    @Override
    public String getDialectName() {
        return "ORACLE";
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
        // Oracle 与达梦无双引号时默认大写，强制加上双引号并大写确保规范
        return "\"" + trimmed.toUpperCase() + "\"";
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
            return "TIMESTAMP";
        }
        if (trimmed.startsWith("BIGINT")) {
            return "NUMBER(20)";
        }
        if (trimmed.startsWith("INT")) {
            return "NUMBER(10)";
        }
        if (trimmed.startsWith("TINYINT")) {
            return "NUMBER(3)";
        }
        if (trimmed.startsWith("VARCHAR2")) {
            return trimmed;
        }
        if (trimmed.startsWith("VARCHAR")) {
            return trimmed.replaceFirst("VARCHAR", "VARCHAR2");
        }
        if (trimmed.equals("TEXT")) {
            return "CLOB";
        }
        if (trimmed.equals("JSON")) {
            // Oracle 21c 前没有原生 JSON 类型，CLOB 可兼容 Oracle 与达梦。
            return "CLOB";
        }
        if (trimmed.equals("DOUBLE")) {
            return "BINARY_DOUBLE";
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
        return String.format("ALTER TABLE %s ADD (%s)", quoteIdentifier(tableName), buildColumnDefinition(column));
    }

    @Override
    public String generateModifyColumnSql(String tableName, ColumnMetadata column) {
        // Oracle 修改列类型时不能再次声明已有主键，仅保留列名与目标类型。
        return String.format("ALTER TABLE %s MODIFY (%s %s)", quoteIdentifier(tableName),
                quoteIdentifier(column.getName()), mapColumnType(column.getType()));
    }

    @Override
    public String generateModifyColumnDefaultSql(String tableName, ColumnMetadata column) {
        String defaultValue = column.getDefaultValue() == null ? "NULL" : column.getDefaultValue().trim();
        return String.format("ALTER TABLE %s MODIFY (%s DEFAULT %s)", quoteIdentifier(tableName),
                quoteIdentifier(column.getName()), defaultValue);
    }

    @Override
    public List<String> generateModifyColumnSqls(String tableName, ColumnMetadata column,
                                                  ColumnMetadata existingColumn) {
        String targetType = normalizeColumnType(mapColumnType(column.getType()));
        String existingType = normalizeColumnType(existingColumn.getType());
        if (!"clob".equals(targetType) || "clob".equals(existingType)) {
            return List.of(generateModifyColumnSql(tableName, column));
        }
        if (column.isPrimaryKey()) {
            throw new IllegalArgumentException("Oracle 不支持将主键列自动迁移为 CLOB: " + column.getName());
        }

        // Oracle 不允许直接把普通字符列 MODIFY 为 CLOB，使用临时列复制后再替换原列。
        String tempName = buildLobMigrationColumnName(column.getName());
        String safeTableName = quoteIdentifier(tableName);
        String safeColumnName = quoteIdentifier(column.getName());
        String safeTempName = quoteIdentifier(tempName);
        String conversionExpression = "json".equals(existingType)
                ? String.format("JSON_SERIALIZE(%s RETURNING CLOB)", safeColumnName)
                : String.format("TO_CLOB(%s)", safeColumnName);
        String targetDefinition = "CLOB";
        if (column.getDefaultValue() != null) {
            targetDefinition += " DEFAULT " + column.getDefaultValue().trim();
        }
        List<String> statements = new ArrayList<>();
        statements.add(String.format("ALTER TABLE %s ADD (%s %s)", safeTableName, safeTempName,
                targetDefinition));
        statements.add(String.format("UPDATE %s SET %s = %s WHERE %s IS NOT NULL",
                safeTableName, safeTempName, conversionExpression, safeColumnName));
        statements.add(String.format("ALTER TABLE %s DROP COLUMN %s", safeTableName, safeColumnName));
        statements.add(String.format("ALTER TABLE %s RENAME COLUMN %s TO %s",
                safeTableName, safeTempName, safeColumnName));
        return statements;
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
            throw new IllegalArgumentException("Oracle/达梦不支持通用 FULLTEXT 索引，请定义数据库专用全文索引");
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

        String onDelete = "";
        if (fk.onDelete() == ForeignKeyAction.CASCADE || fk.onDelete() == ForeignKeyAction.SET_NULL) {
            onDelete = " ON DELETE " + fk.onDelete().name().replace('_', ' ');
        }

        // Oracle 不支持 ON UPDATE 动作，禁止静默丢弃实体中声明的级联语义。
        if (fk.onUpdate() != ForeignKeyAction.RESTRICT && fk.onUpdate() != ForeignKeyAction.NO_ACTION) {
            throw new IllegalArgumentException("Oracle/达梦不支持外键 ON UPDATE " + fk.onUpdate());
        }

        return String.format("ALTER TABLE %s ADD CONSTRAINT %s FOREIGN KEY (%s) REFERENCES %s (%s)%s",
                safeTableName, safeFkName, String.join(", ", safeColumns), safeRefTableName, String.join(", ", safeRefColumns), onDelete);
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
        String comment = table.getTableComment() == null ? "" : table.getTableComment().replace("'", "''");
        return String.format("COMMENT ON TABLE %s IS '%s'", safeTableName, comment);
    }

    @Override
    public String generateColumnCommentSql(String tableName, ColumnMetadata column) {
        String safeTableName = quoteIdentifier(tableName);
        String safeColumnName = quoteIdentifier(column.getName());
        if (column.getComment() == null || column.getComment().isEmpty()) {
            return String.format("COMMENT ON COLUMN %s.%s IS ''", safeTableName, safeColumnName);
        }
        return String.format("COMMENT ON COLUMN %s.%s IS '%s'", safeTableName, safeColumnName,
                column.getComment().replace("'", "''"));
    }

    /**
     * 统一 Oracle 数据字典与实体声明的同义类型，避免每次启动重复执行 ALTER TABLE。
     */
    @Override
    public String normalizeColumnType(String columnType) {
        String normalized = DbDialect.super.normalizeColumnType(columnType);
        return normalized
                .replace("varchar2", "varchar")
                .replace("number", "decimal")
                .replaceAll("timestamp\\(\\d+\\)", "timestamp");
    }

    @Override
    public String getTableExistsSql() {
        return "SELECT COUNT(*) FROM USER_TABLES WHERE UPPER(TABLE_NAME) = UPPER(?)";
    }

    @Override
    public String getTableCommentSql() {
        return "SELECT COALESCE(COMMENTS, '') AS TABLE_COMMENT FROM USER_TAB_COMMENTS " +
               "WHERE TABLE_TYPE = 'TABLE' AND UPPER(TABLE_NAME) = UPPER(?)";
    }

    @Override
    public String getColumnsInfoSql() {
        return "SELECT c.COLUMN_NAME AS COLUMN_NAME, " +
               "CASE WHEN c.DATA_TYPE LIKE 'VARCHAR%' THEN c.DATA_TYPE || '(' || " +
               "CASE WHEN c.CHAR_USED = 'C' THEN c.CHAR_LENGTH ELSE c.DATA_LENGTH END || ')' " +
               "WHEN c.DATA_TYPE = 'NUMBER' AND c.DATA_PRECISION IS NOT NULL THEN c.DATA_TYPE || '(' || c.DATA_PRECISION || CASE WHEN c.DATA_SCALE IS NOT NULL AND c.DATA_SCALE > 0 THEN ',' || c.DATA_SCALE ELSE '' END || ')' " +
               "ELSE c.DATA_TYPE END AS COLUMN_TYPE, " +
               "COALESCE(m.COMMENTS, '') AS COLUMN_COMMENT, c.DATA_DEFAULT AS COLUMN_DEFAULT, " +
               "CASE WHEN EXISTS (SELECT 1 FROM USER_CONSTRAINTS pk " +
               "JOIN USER_CONS_COLUMNS pcc ON pk.CONSTRAINT_NAME = pcc.CONSTRAINT_NAME " +
               "WHERE pk.CONSTRAINT_TYPE = 'P' AND pcc.TABLE_NAME = c.TABLE_NAME " +
               "AND pcc.COLUMN_NAME = c.COLUMN_NAME) THEN 1 ELSE 0 END AS IS_PRIMARY_KEY, " +
               "CASE WHEN c.IDENTITY_COLUMN = 'YES' THEN 1 ELSE 0 END AS IS_AUTO_INCREMENT " +
               "FROM USER_TAB_COLUMNS c " +
               "LEFT JOIN USER_COL_COMMENTS m ON c.TABLE_NAME = m.TABLE_NAME AND c.COLUMN_NAME = m.COLUMN_NAME " +
               "WHERE UPPER(c.TABLE_NAME) = UPPER(?)";
    }

    @Override
    public String getIndexNamesSql() {
        return "SELECT INDEX_NAME FROM USER_INDEXES WHERE UPPER(TABLE_NAME) = UPPER(?)";
    }

    @Override
    public String getForeignKeyNamesSql() {
        return "SELECT CONSTRAINT_NAME FROM USER_CONSTRAINTS WHERE CONSTRAINT_TYPE = 'R' AND UPPER(TABLE_NAME) = UPPER(?)";
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

    /**
     * 构造兼容 Oracle 12c 传统 30 字节限制的临时 LOB 列名。
     *
     * @param columnName 原列名
     * @return 临时列名
     */
    private String buildLobMigrationColumnName(String columnName) {
        String normalized = columnName.toUpperCase();
        String prefix = normalized.length() > 20 ? normalized.substring(0, 20) : normalized;
        return prefix + "_LOB_TMP";
    }
}
