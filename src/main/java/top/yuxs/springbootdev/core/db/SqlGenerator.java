/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/05/10
 */

package top.yuxs.springbootdev.core.db;

import com.baomidou.mybatisplus.annotation.TableName;
import org.springframework.stereotype.Component;
import top.yuxs.springbootdev.core.db.annotation.ForeignKey;
import top.yuxs.springbootdev.core.db.annotation.Index;
import top.yuxs.springbootdev.core.db.metadata.ColumnMetadata;
import top.yuxs.springbootdev.core.db.metadata.TableMetadata;
import top.yuxs.springbootdev.core.db.enums.IndexType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SQL 生成器：负责根据元数据生成 MySQL SQL 语句
 */
@Component
public class SqlGenerator {

    /**
     * 安全校验：强制 SQL 标识符只能包含字母、数字和下划线，防御 SQL 注入
     */
    public static String sanitizeIdentifier(String identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("标识符不能为空");
        }
        String trimmed = identifier.trim();
        if (!trimmed.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("非法的 SQL 标识符（仅允许字母、数字和下划线）: " + identifier);
        }
        return trimmed;
    }

    /**
     * 安全校验：限制列类型定义中只含有类型名字、长度和空格
     */
    public static String sanitizeColumnType(String type) {
        if (type == null) {
            throw new IllegalArgumentException("列类型不能为空");
        }
        String trimmed = type.trim();
        if (!trimmed.matches("^[a-zA-Z0-9_(),\\s]+$")) {
            throw new IllegalArgumentException("非法的列类型名称: " + type);
        }
        return trimmed;
    }

    /**
     * 生成建表 SQL
     */
    public String generateCreateTableSql(TableMetadata table) {
        String tableName = sanitizeIdentifier(table.getTableName());
        StringBuilder sql = new StringBuilder("CREATE TABLE `").append(tableName).append("` (");
        List<String> definitions = new ArrayList<>();

        for (ColumnMetadata column : table.getColumns()) {
            definitions.add(buildColumnDefinition(column));
        }

        for (Index index : table.getIndexes()) {
            definitions.add(buildIndexDefinition(index));
        }

        sql.append(String.join(", ", definitions)).append(")");
        if (table.getTableComment() != null && !table.getTableComment().isEmpty()) {
            // 修复表注释未转义单引号的问题
            String safeComment = table.getTableComment().replace("'", "''");
            sql.append(" COMMENT='").append(safeComment).append("'");
        }
        sql.append(";");
        return sql.toString();
    }

    /**
     * 生成新增列 SQL
     */
    public String generateAddColumnSql(String tableName, ColumnMetadata column) {
        return String.format("ALTER TABLE `%s` ADD COLUMN %s;", sanitizeIdentifier(tableName), buildColumnDefinition(column));
    }

    /**
     * 生成修改列 SQL
     */
    public String generateModifyColumnSql(String tableName, ColumnMetadata column) {
        return String.format("ALTER TABLE `%s` MODIFY COLUMN %s;", sanitizeIdentifier(tableName), buildColumnDefinition(column));
    }

    /**
     * 生成新增索引 SQL
     */
    public String generateCreateIndexSql(String tableName, Index index) {
        return "ALTER TABLE `" + sanitizeIdentifier(tableName) + "` ADD " + buildIndexDefinition(index) + ";";
    }

    /**
     * 生成新增外键 SQL
     */
    public String generateAddForeignKeySql(String tableName, ForeignKey fk) {
        TableName refAnn = fk.referenceEntity().getAnnotation(TableName.class);
        if (refAnn == null) {
            throw new IllegalArgumentException("外键引用实体 " + fk.referenceEntity().getSimpleName() + " 缺失 @TableName");
        }

        String safeTableName = sanitizeIdentifier(tableName);
        String safeFkName = sanitizeIdentifier(fk.name());
        String safeRefTableName = sanitizeIdentifier(refAnn.value());

        List<String> safeColumns = java.util.Arrays.stream(fk.columns())
                .map(SqlGenerator::sanitizeIdentifier)
                .collect(Collectors.toList());
        List<String> safeRefColumns = java.util.Arrays.stream(fk.referencedColumns())
                .map(SqlGenerator::sanitizeIdentifier)
                .collect(Collectors.toList());

        String columnsSql = "`" + String.join("`,`", safeColumns) + "`";
        String refColumnsSql = "`" + String.join("`,`", safeRefColumns) + "`";
        String onDelete = "ON DELETE " + fk.onDelete().name().replace('_', ' ');
        String onUpdate = "ON UPDATE " + fk.onUpdate().name().replace('_', ' ');

        return String.format("ALTER TABLE `%s` ADD CONSTRAINT `%s` FOREIGN KEY (%s) REFERENCES `%s` (%s) %s %s;",
                safeTableName, safeFkName, columnsSql, safeRefTableName, refColumnsSql, onDelete, onUpdate);
    }

    /**
     * 生成插入默认数据 SQL
     */
    public String generateInsertDefaultDataSql(String tableName, Map<String, Object> defaultData) {
        if (defaultData.isEmpty()) return null;

        StringBuilder sql = new StringBuilder("INSERT INTO `").append(sanitizeIdentifier(tableName)).append("` (");
        StringBuilder placeholders = new StringBuilder();

        List<String> columns = new ArrayList<>(defaultData.keySet());
        for (int i = 0; i < columns.size(); i++) {
            String colName = sanitizeIdentifier(columns.get(i));
            sql.append("`").append(colName).append("`").append(i == columns.size() - 1 ? "" : ", ");
            placeholders.append("?").append(i == columns.size() - 1 ? "" : ", ");
        }

        sql.append(") VALUES (").append(placeholders).append(");");
        return sql.toString();
    }

    private String buildColumnDefinition(ColumnMetadata column) {
        StringBuilder sb = new StringBuilder();
        sb.append("`").append(sanitizeIdentifier(column.getName())).append("` ");
        sb.append(sanitizeColumnType(column.getType()));

        if (column.isPrimaryKey()) {
            sb.append(" PRIMARY KEY");
            if (column.isAutoIncrement()) {
                sb.append(" AUTO_INCREMENT");
            }
        }

        if (column.getDefaultValue() != null) {
            String defVal = column.getDefaultValue().trim();
            // 简单防范注入，默认值禁止出现分号和注释段
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
        sb.append("`").append(sanitizeIdentifier(index.name())).append("` ");
        
        List<String> safeColumns = java.util.Arrays.stream(index.columns())
                .map(SqlGenerator::sanitizeIdentifier)
                .collect(Collectors.toList());
        sb.append("(`").append(String.join("`,`", safeColumns)).append("`) ");
        
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
