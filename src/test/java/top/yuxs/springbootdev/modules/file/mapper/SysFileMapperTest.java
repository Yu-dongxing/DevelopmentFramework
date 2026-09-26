/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/08/20
 */

package top.yuxs.springbootdev.modules.file.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import top.yuxs.springbootdev.core.db.DatabaseInitService;
import top.yuxs.springbootdev.modules.file.entity.SysFile;

import java.util.Map;

/**
 * 文件 Mapper 多数据库结果映射测试。
 */
@SpringBootTest(properties = {
        "db.init.enabled=true",
        "db.init.init-data=false"
})
class SysFileMapperTest {

    @Autowired
    private SysFileMapper sysFileMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseInitService databaseInitService;

    /**
     * 确保自动 DDL 在已有表、注释、默认值和主键均已同步后可重复执行。
     */
    @Test
    @DisplayName("测试 H2 自动结构同步保持幂等")
    void shouldRunSchemaSynchronizationTwice() {
        Assertions.assertDoesNotThrow(databaseInitService::initDatabase);
    }

    /**
     * 确保自定义分页查询仍使用实体声明的 Jackson 类型处理器。
     */
    @Test
    @DisplayName("测试待物理删除文件查询可解析扩展元数据")
    void shouldMapMetadataInPendingDeleteQuery() {
        SysFile file = new SysFile();
        file.setOriginalName("metadata-test.txt");
        file.setFileName("metadata-test.txt");
        file.setStorageType("LOCAL");
        file.setFilePath("metadata-test.txt");
        file.setUploadStatus(1);
        file.setIsDeleted(0);
        file.setPhysicalDeleteStatus(1);
        file.setMetadata(Map.of("database", "h2"));
        sysFileMapper.insert(file);

        try {
            // 绕过逻辑删除拦截，仅构造补偿任务需要查询的数据库状态。
            jdbcTemplate.update("UPDATE sys_file SET is_deleted = 1 WHERE id = ?", file.getId());
            SysFile loaded = sysFileMapper.selectPendingPhysicalDeletes(new Page<>(1, 10, false)).stream()
                    .filter(item -> file.getId().equals(item.getId()))
                    .findFirst()
                    .orElseThrow();

            Assertions.assertEquals("h2", loaded.getMetadata().get("database"));
        } finally {
            sysFileMapper.physicalDeleteById(file.getId());
        }
    }
}
