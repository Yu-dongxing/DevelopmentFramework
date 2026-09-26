/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/05/10
 */

package top.yuxs.springbootdev.core.db.dialect;

import top.yuxs.springbootdev.core.db.annotation.ForeignKey;
import top.yuxs.springbootdev.core.db.annotation.Index;
import top.yuxs.springbootdev.core.db.metadata.ColumnMetadata;
import top.yuxs.springbootdev.core.db.metadata.TableMetadata;

import java.util.List;
import java.util.Map;

/**
 * 数据库方言抽象接口：负责屏蔽不同数据库的 DDL 语法与元数据查询差异
 */
public interface DbDialect {

    /**
     * 获取数据库方言名称（例如 MySQL, PostgreSQL, Oracle, H2 等）
     *
     * @return 方言标识名称
     */
    String getDialectName();

    /**
     * 包装安全转义的 SQL 标识符（表名、列名、索引名等）
     *
     * @param identifier 原始标识符
     * @return 包含转义界定符的标识符（如 `table` 或 "table"）
     */
    String quoteIdentifier(String identifier);

    /**
     * 将通用/实体声明的列类型转换为当前数据库适配的类型
     *
     * @param originalType 原始列类型（如 DATETIME, TEXT, BIGINT 等）
     * @return 当前数据库对应的列类型
     */
    String mapColumnType(String originalType);

    /**
     * 生成创建表 SQL
     *
     * @param table 表元数据
     * @return 建表 SQL 语句
     */
    String generateCreateTableSql(TableMetadata table);

    /**
     * 生成新增列 SQL
     *
     * @param tableName 表名
     * @param column    列元数据
     * @return 新增列 SQL
     */
    String generateAddColumnSql(String tableName, ColumnMetadata column);

    /**
     * 生成修改列 SQL
     *
     * @param tableName 表名
     * @param column    列元数据
     * @return 修改列 SQL
     */
    String generateModifyColumnSql(String tableName, ColumnMetadata column);

    /**
     * 生成修改列类型所需的 SQL 列表。LOB 等不能通过单条 ALTER 完成的方言可覆盖此方法。
     *
     * @param tableName     表名
     * @param column        目标列元数据
     * @param existingColumn 数据库中的现有列元数据
     * @return 按顺序执行的 SQL 列表
     */
    default List<String> generateModifyColumnSqls(String tableName, ColumnMetadata column,
                                                  ColumnMetadata existingColumn) {
        return List.of(generateModifyColumnSql(tableName, column));
    }

    /**
     * 生成创建索引 SQL
     *
     * @param tableName 表名
     * @param index     索引定义
     * @return 创建索引 SQL
     */
    String generateCreateIndexSql(String tableName, Index index);

    /**
     * 生成添加外键 SQL
     *
     * @param tableName 表名
     * @param fk        外键定义
     * @return 添加外键 SQL
     */
    String generateAddForeignKeySql(String tableName, ForeignKey fk);

    /**
     * 生成插入默认数据 SQL
     *
     * @param tableName   表名
     * @param defaultData 默认数据映射
     * @return 插入 SQL
     */
    String generateInsertDefaultDataSql(String tableName, Map<String, Object> defaultData);

    /**
     * 获取对于不支持内联注释的数据库所需要的补充注释 SQL 语句列表（如 PostgreSQL / Oracle 的 COMMENT ON COLUMN/TABLE）
     *
     * @param table 表元数据
     * @return 注释 SQL 语句列表
     */
    List<String> generateCommentSqls(TableMetadata table);

    /**
     * 生成更新表注释的 SQL。
     *
     * @param table 表元数据
     * @return 表注释 SQL；不支持时返回 {@code null}
     */
    String generateTableCommentSql(TableMetadata table);

    /**
     * 生成更新单个列注释的 SQL。使用列定义内联注释的数据库应返回 {@code null}。
     *
     * @param tableName 表名
     * @param column    列元数据
     * @return 列注释 SQL；当前方言不支持独立注释时返回 {@code null}
     */
    String generateColumnCommentSql(String tableName, ColumnMetadata column);

    /**
     * 当前数据库是否支持持久化列注释。
     *
     * @return 支持时返回 {@code true}
     */
    default boolean supportsColumnComments() {
        return true;
    }

    /**
     * 当前数据库是否使用列定义内联方式更新注释。
     *
     * @return 使用 ALTER/MODIFY COLUMN 内联注释时返回 {@code true}
     */
    default boolean usesInlineColumnComment() {
        return false;
    }

    /**
     * 规范化列类型文本，用于与数据库元数据进行幂等比较。
     * 不同数据库会将 VARCHAR、DECIMAL、TIMESTAMP 等类型返回为不同同义词，
     * 因此不能直接比较原始字符串。
     *
     * @param columnType 原始类型文本
     * @return 可比较的规范化类型文本
     */
    default String normalizeColumnType(String columnType) {
        if (columnType == null) {
            return "";
        }
        return columnType.toLowerCase()
                .replaceAll("\\s+", "")
                .replace("charactervarying", "varchar")
                .replace("timestampwithouttimezone", "timestamp")
                .replace("numeric", "decimal")
                .replace("integer", "int");
    }

    /**
     * 规范化默认值表达式，用于比较实体声明和数据库元数据。
     *
     * @param defaultValue 默认值表达式
     * @return 可比较的默认值文本
     */
    default String normalizeDefaultValue(String defaultValue) {
        if (defaultValue == null) {
            return "";
        }
        String normalized = defaultValue.trim()
                .replaceAll("::[a-zA-Z0-9_ ]+(\\([0-9, ]+\\))?", "")
                .replaceAll("\\s+", " ")
                .toLowerCase()
                .replace("current_timestamp()", "current_timestamp")
                .replace("now()", "current_timestamp");
        return "null".equals(normalized) ? "" : normalized;
    }

    /**
     * 生成修改列默认值的 SQL。
     *
     * @param tableName 表名
     * @param column    目标列元数据
     * @return 修改默认值 SQL
     */
    String generateModifyColumnDefaultSql(String tableName, ColumnMetadata column);

    /**
     * 获取查询表是否存在的 SQL
     *
     * @return SQL 模板，使用 ? 绑定表名参数
     */
    String getTableExistsSql();

    /**
     * 获取查询表注释的 SQL。
     *
     * @return SQL 模板，使用 ? 绑定表名参数，并返回 TABLE_COMMENT 列
     */
    String getTableCommentSql();

    /**
     * 获取查询指定表所有列元数据的 SQL
     *
     * @return SQL 模板，使用 ? 绑定表名参数
     */
    String getColumnsInfoSql();

    /**
     * 获取查询指定表所有索引名称的 SQL
     *
     * @return SQL 模板，使用 ? 绑定表名参数
     */
    String getIndexNamesSql();

    /**
     * 获取查询指定表所有外键约束名称的 SQL
     *
     * @return SQL 模板，使用 ? 绑定表名参数
     */
    String getForeignKeyNamesSql();
}
