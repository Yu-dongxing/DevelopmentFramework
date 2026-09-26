/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/05/10
 */

package top.yuxs.springbootdev.core.db;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.yuxs.springbootdev.core.db.annotation.*;
import top.yuxs.springbootdev.core.db.metadata.ColumnMetadata;
import top.yuxs.springbootdev.core.db.metadata.TableMetadata;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 表元数据解析器：负责将实体类解析为 TableMetadata
 */
@Slf4j
@Component
public class TableMetadataParser {

    /**
     * 解析实体类
     *
     * @param entityClass 实体类
     * @return 表元数据
     */
    public TableMetadata parse(Class<?> entityClass) {
        TableName tableNameAnnotation = entityClass.getAnnotation(TableName.class);
        if (tableNameAnnotation == null) {
            return null;
        }

        String tableName = tableNameAnnotation.value();
        String tableComment = getTableComment(entityClass);
        List<ColumnMetadata> columns = parseColumns(entityClass);
        List<Index> indexes = Arrays.asList(entityClass.getAnnotationsByType(Index.class));
        List<ForeignKey> foreignKeys = Arrays.asList(entityClass.getAnnotationsByType(ForeignKey.class));
        Map<String, Object> defaultData = parseDefaultData(entityClass);

        return TableMetadata.builder()
                .tableName(tableName)
                .tableComment(tableComment)
                .columns(columns)
                .indexes(indexes)
                .foreignKeys(foreignKeys)
                .defaultData(defaultData)
                .build();
    }

    private List<ColumnMetadata> parseColumns(Class<?> entityClass) {
        List<ColumnMetadata> columns = new ArrayList<>();
        List<Field> fields = getAllFields(entityClass);
        boolean hasPrimaryKey = false;

        for (Field field : fields) {
            if (isIgnoreField(field)) continue;

            String columnName = getColumnName(field);
            if (columnName == null) continue;

            TableId tableId = field.getAnnotation(TableId.class);
            boolean isPrimaryKey = tableId != null;
            if (isPrimaryKey) hasPrimaryKey = true;

            ColumnMetadata column = ColumnMetadata.builder()
                    .name(columnName)
                    .type(getDataType(field))
                    .isPrimaryKey(isPrimaryKey)
                    .isAutoIncrement(isPrimaryKey && tableId.type() == IdType.AUTO)
                    .defaultValue(field.isAnnotationPresent(DefaultValue.class) 
                            ? formatDefaultValue(field, field.getAnnotation(DefaultValue.class).value()) 
                            : null)
                    .comment(field.isAnnotationPresent(ColumnComment.class) 
                            ? field.getAnnotation(ColumnComment.class).value() 
                            : null)
                    .build();
            columns.add(column);
        }

        // 如果没有显式指定主键，增加默认 ID
        if (!hasPrimaryKey) {
            columns.add(0, ColumnMetadata.builder()
                    .name("id")
                    .type("bigint")
                    .isPrimaryKey(true)
                    .isAutoIncrement(true)
                    .comment("主键ID")
                    .build());
        }

        return columns;
    }

    private Map<String, Object> parseDefaultData(Class<?> entityClass) {
        Map<String, Object> defaultData = new LinkedHashMap<>();
        List<Field> fields = getAllFields(entityClass);

        for (Field field : fields) {
            if (isIgnoreField(field)) continue;
            DefaultData annotation = field.getAnnotation(DefaultData.class);
            if (annotation != null) {
                String columnName = getColumnName(field);
                if (columnName != null) {
                    defaultData.put(columnName, annotation.value());
                }
            }
        }
        return defaultData;
    }

    private String getTableComment(Class<?> entityClass) {
        if (entityClass.isAnnotationPresent(TableComment.class)) {
            return entityClass.getAnnotation(TableComment.class).value();
        }
        return "";
    }

    private String getColumnName(Field field) {
        TableId tableId = field.getAnnotation(TableId.class);
        if (tableId != null && !tableId.value().isEmpty()) {
            return tableId.value();
        }
        TableField tableField = field.getAnnotation(TableField.class);
        if (tableField != null && !tableField.value().isEmpty()) {
            return tableField.value();
        }
        
        if (tableField != null && !tableField.exist()) {
            return null;
        }

        return cn.hutool.core.util.StrUtil.toUnderlineCase(field.getName());
    }

    private String getDataType(Field field) {
        if (field.isAnnotationPresent(ColumnType.class)) {
            return field.getAnnotation(ColumnType.class).value();
        }
        Class<?> fieldType = field.getType();
        if (fieldType.equals(String.class)) return "varchar(255)";
        if (fieldType.equals(LocalDateTime.class)) return "datetime";
        if (fieldType.equals(Long.class) || fieldType.equals(long.class)) return "bigint";
        if (fieldType.equals(Integer.class) || fieldType.equals(int.class)) return "int";
        if (fieldType.equals(Double.class) || fieldType.equals(double.class)) return "double";
        if (fieldType.equals(Boolean.class) || fieldType.equals(boolean.class)) return "tinyint(1)";
        if (fieldType.equals(BigDecimal.class)) return "decimal(19,2)";
        // 结构化字段统一保存为 JSON 文本，避免 PostgreSQL 原生 JSON 与通用 JDBC 类型处理器不兼容。
        if (Collection.class.isAssignableFrom(fieldType) || Map.class.isAssignableFrom(fieldType)) return "text";
        return "varchar(255)";
    }

    private String formatDefaultValue(Field field, String value) {
        if (value == null || value.equalsIgnoreCase("NULL")) return "NULL";

        if (value.toUpperCase().contains("(") || value.equalsIgnoreCase("CURRENT_TIMESTAMP")) {
            return value;
        }

        Class<?> type = field.getType();
        if (type == String.class || type == LocalDateTime.class) {
            if (!value.startsWith("'")) {
                return "'" + value + "'";
            }
        }
        return value;
    }

    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && !clazz.equals(Object.class)) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    private boolean isIgnoreField(Field field) {
        return Modifier.isStatic(field.getModifiers())
                || Modifier.isTransient(field.getModifiers())
                || "serialVersionUID".equals(field.getName());
    }
}
