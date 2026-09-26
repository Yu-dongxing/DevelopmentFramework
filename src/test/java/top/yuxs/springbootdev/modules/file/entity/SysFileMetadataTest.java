/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/08/20
 */

package top.yuxs.springbootdev.modules.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.yuxs.springbootdev.core.db.TableMetadataParser;

/**
 * 文件实体多数据库元数据测试
 */
class SysFileMetadataTest {

    /**
     * 确保 JSON 文本字段能够在查询结果中使用类型处理器，并使用跨库文本类型。
     */
    @Test
    @DisplayName("测试文件扩展元数据使用跨库文本类型")
    void shouldUsePortableMetadataType() {
        TableName tableName = SysFile.class.getAnnotation(TableName.class);
        Assertions.assertTrue(tableName.autoResultMap());

        String metadataType = new TableMetadataParser().parse(SysFile.class).getColumns().stream()
                .filter(column -> "metadata".equals(column.getName()))
                .findFirst()
                .orElseThrow()
                .getType();
        Assertions.assertEquals("text", metadataType);
    }
}
