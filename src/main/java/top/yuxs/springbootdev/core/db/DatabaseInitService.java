/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/11
 */

package top.yuxs.springbootdev.core.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.yuxs.springbootdev.core.db.annotation.ForeignKey;
import top.yuxs.springbootdev.core.db.annotation.Index;
import top.yuxs.springbootdev.core.db.config.AegisDbProperties;
import top.yuxs.springbootdev.core.db.dialect.DbDialect;
import top.yuxs.springbootdev.core.db.metadata.ColumnMetadata;
import top.yuxs.springbootdev.core.db.metadata.TableMetadata;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据库初始化服务：负责数据库结构的自动同步与数据初始化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseInitService {

    private final EntityScanner entityScanner;
    private final TableMetadataParser metadataParser;
    private final SqlGenerator sqlGenerator;
    private final SchemaExecutor schemaExecutor;
    private final AegisDbProperties properties;

    /**
     * 初始化入口
     */
    public void initDatabase() {
        if (!properties.isEnabled()) {
            log.info("数据库初始化已禁用");
            return;
        }

        DbDialect dialect = schemaExecutor.getDialect();
        log.info("开始数据库初始化，当前使用方言: {}，扫描包: {}", dialect.getDialectName(), properties.getBasePackage());

        List<Class<?>> entityClasses = entityScanner.scanEntityClasses();
        List<TableMetadata> tables = entityClasses.stream()
                .map(metadataParser::parse)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 第一阶段：创建或更新表结构、索引（不含外键）
        for (TableMetadata table : tables) {
            syncTableStructure(dialect, table);
        }

        // 第二阶段：统一处理外键约束（解决循环依赖）
        for (TableMetadata table : tables) {
            syncForeignKeys(dialect, table);
        }

        // 第三阶段：初始化默认数据
        if (properties.isInitData()) {
            initDefaultData(dialect, tables);
        }

        log.info("数据库初始化完成");
    }

    /**
     * 第一阶段：同步表结构（列新增/修改，索引同步）
     */
    private void syncTableStructure(DbDialect dialect, TableMetadata table) {
        String tableName = table.getTableName();
        try {
            if (!schemaExecutor.tableExists(tableName)) {
                log.info("表 {} 不存在，准备创建", tableName);
                schemaExecutor.execute(sqlGenerator.generateCreateTableSql(dialect, table));

                // 非 MySQL 方言不会将索引放入 CREATE TABLE，首次初始化也必须同步索引。
                syncIndexes(dialect, table);

                // 对于不支持内联注释的数据库（如 PostgreSQL / Oracle），补充执行注释语句
                List<String> commentSqls = sqlGenerator.generateCommentSqls(dialect, table);
                for (String commentSql : commentSqls) {
                    schemaExecutor.execute(commentSql);
                }
            } else {
                updateTableColumnsAndIndexes(dialect, table);
            }
        } catch (Exception e) {
            log.error("同步表 {} 结构失败: {}", tableName, e.getMessage(), e);
            throw new RuntimeException("数据库建表或结构同步失败，表名: " + tableName, e);
        }
    }

    private void updateTableColumnsAndIndexes(DbDialect dialect, TableMetadata table) {
        String tableName = table.getTableName();
        Map<String, ColumnMetadata> existingColumnsMap = schemaExecutor.getExistingColumnsInfo(tableName);

        syncTableComment(dialect, table);

        for (ColumnMetadata column : table.getColumns()) {
            String columnName = column.getName();
            String targetTypeMapped = dialect.normalizeColumnType(dialect.mapColumnType(column.getType()));

            if (!existingColumnsMap.containsKey(columnName.toLowerCase())) {
                log.info("表 {} 新增列: {}", tableName, columnName);
                schemaExecutor.execute(sqlGenerator.generateAddColumnSql(dialect, tableName, column));
                String commentSql = dialect.generateColumnCommentSql(tableName, column);
                if (commentSql != null) {
                    schemaExecutor.execute(commentSql);
                }
            } else {
                ColumnMetadata existingCol = existingColumnsMap.get(columnName.toLowerCase());
                validateColumnConstraints(tableName, column, existingCol);
                String existingType = dialect.normalizeColumnType(existingCol.getType());
                String existingComment = existingCol.getComment() != null ? existingCol.getComment().trim() : "";
                String targetComment = column.getComment() != null ? column.getComment().trim() : "";
                String existingDefault = dialect.normalizeDefaultValue(existingCol.getDefaultValue());
                String targetDefault = dialect.normalizeDefaultValue(column.getDefaultValue());

                // 仅在数据库能够返回类型时比较，避免空元数据触发无意义的 ALTER TABLE。
                boolean typeChanged = !existingType.isEmpty() && !targetTypeMapped.equals(existingType);
                boolean commentChanged = dialect.supportsColumnComments()
                        && !targetComment.equals(existingComment);
                boolean defaultChanged = !(column.isAutoIncrement() && existingCol.isAutoIncrement())
                        && !targetDefault.equals(existingDefault);

                if (typeChanged) {
                    log.info("表 {} 变更列类型: {} -> {}", tableName, existingType, targetTypeMapped);
                    List<String> modifySqls = dialect.generateModifyColumnSqls(tableName, column, existingCol);
                    for (String modifySql : modifySqls) {
                        schemaExecutor.execute(modifySql);
                    }
                }

                if (defaultChanged && !(typeChanged && dialect.usesInlineColumnComment())) {
                    log.info("表 {} 变更列默认值: {} -> {}", tableName, existingDefault, targetDefault);
                    schemaExecutor.execute(dialect.generateModifyColumnDefaultSql(tableName, column));
                }

                if (commentChanged) {
                    log.info("表 {} 变更列注释: '{}' -> '{}'", tableName, existingComment, targetComment);
                    String commentSql = dialect.generateColumnCommentSql(tableName, column);
                    if (commentSql != null) {
                        schemaExecutor.execute(commentSql);
                    } else if (!typeChanged && !defaultChanged && dialect.usesInlineColumnComment()) {
                        // MySQL 的列注释包含在 MODIFY COLUMN 中，只有注释变更时需补执行一次。
                        schemaExecutor.execute(sqlGenerator.generateModifyColumnSql(dialect, tableName, column));
                    }
                }
            }
        }

        syncIndexes(dialect, table);
    }

    /**
     * 校验不能安全在线自动改造的主键和自增属性，避免结构漂移被静默忽略。
     */
    private void validateColumnConstraints(String tableName, ColumnMetadata target, ColumnMetadata existing) {
        if (target.isPrimaryKey() != existing.isPrimaryKey()) {
            throw new IllegalStateException(String.format(
                    "表 %s 列 %s 的主键属性不一致，自动修改主键可能破坏约束，请执行人工迁移",
                    tableName, target.getName()));
        }
        if (target.isAutoIncrement() != existing.isAutoIncrement()) {
            throw new IllegalStateException(String.format(
                    "表 %s 列 %s 的自增属性不一致，请执行数据库专用迁移后再启动",
                    tableName, target.getName()));
        }
    }

    /**
     * 同步已有表的表注释，避免修改 {@code @TableComment} 后数据库元数据长期漂移。
     */
    private void syncTableComment(DbDialect dialect, TableMetadata table) {
        String existingComment = schemaExecutor.getExistingTableComment(table.getTableName());
        String targetComment = table.getTableComment() == null ? "" : table.getTableComment().trim();
        if (targetComment.equals(existingComment)) {
            return;
        }

        String commentSql = dialect.generateTableCommentSql(table);
        if (commentSql != null) {
            log.info("表 {} 变更表注释: '{}' -> '{}'", table.getTableName(), existingComment, targetComment);
            schemaExecutor.execute(commentSql);
        }
    }

    /**
     * 同步索引。该步骤覆盖新建表和已存在表，确保首次启动即可创建索引。
     */
    private void syncIndexes(DbDialect dialect, TableMetadata table) {
        String tableName = table.getTableName();
        Set<String> existingIndexes = schemaExecutor.getExistingIndexNames(tableName);
        for (Index index : table.getIndexes()) {
            if (!existingIndexes.contains(index.name().toLowerCase())) {
                log.info("表 {} 新增索引: {}", tableName, index.name());
                schemaExecutor.execute(sqlGenerator.generateCreateIndexSql(dialect, tableName, index));
            }
        }
    }

    /**
     * 第二阶段：同步外键
     */
    private void syncForeignKeys(DbDialect dialect, TableMetadata table) {
        String tableName = table.getTableName();
        try {
            Set<String> existingFks = schemaExecutor.getExistingForeignKeyNames(tableName);
            for (ForeignKey fk : table.getForeignKeys()) {
                if (!existingFks.contains(fk.name().toLowerCase())) {
                    log.info("表 {} 新增外键: {}", tableName, fk.name());
                    schemaExecutor.execute(sqlGenerator.generateAddForeignKeySql(dialect, tableName, fk));
                }
            }
        } catch (Exception e) {
            log.error("同步表 {} 外键失败: {}", tableName, e.getMessage(), e);
            throw new RuntimeException("同步表外键失败，表名: " + tableName, e);
        }
    }

    /**
     * 初始化默认数据
     */
    private void initDefaultData(DbDialect dialect, List<TableMetadata> tables) {
        log.info("开始检查并初始化默认数据...");
        for (TableMetadata table : tables) {
            String tableName = table.getTableName();
            if (schemaExecutor.isTableEmpty(tableName)) {
                insertDefaultData(dialect, table);
            }
        }
    }

    private void insertDefaultData(DbDialect dialect, TableMetadata table) {
        Map<String, Object> defaultData = table.getDefaultData();
        if (defaultData == null || defaultData.isEmpty()) return;

        String tableName = table.getTableName();
        String sql = sqlGenerator.generateInsertDefaultDataSql(dialect, tableName, defaultData);
        if (sql == null) return;

        try {
            log.info("表 {} 插入默认数据: {}", tableName, defaultData);
            schemaExecutor.executeWithParams(sql, defaultData.values().toArray());
        } catch (Exception e) {
            log.error("表 {} 插入默认数据失败: {}", tableName, e.getMessage(), e);
            throw new RuntimeException("插入表 " + tableName + " 默认数据失败", e);
        }
    }
}
