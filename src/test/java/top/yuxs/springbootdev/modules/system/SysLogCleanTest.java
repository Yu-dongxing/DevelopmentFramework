/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/08/09
 */

package top.yuxs.springbootdev.modules.system;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.yuxs.springbootdev.modules.system.entity.SysLog;
import top.yuxs.springbootdev.modules.system.service.SysLogService;

import java.time.LocalDateTime;

/**
 * 接口日志物理清理与分批删除功能单元测试
 *
 * @author YuDongXing
 * @since 2026/08/09
 */
@SpringBootTest
class SysLogCleanTest {

    @Autowired
    private SysLogService sysLogService;

    @Test
    void testCleanExpiredLogs() {
        // 1. 模拟构造测试日志数据
        LocalDateTime now = LocalDateTime.now();
        
        // 今天的正常日志（应保留）
        SysLog logNormal = new SysLog();
        logNormal.setUsername("TestUserNormal");
        logNormal.setIp("127.0.0.1");
        logNormal.setUrl("/api/test/normal");
        logNormal.setMethod("GET");
        logNormal.setClassName("TestController");
        logNormal.setMethodName("normalMethod");
        logNormal.setTitle("正常测试日志");
        logNormal.setStatus(1);
        logNormal.setRequestTime(now);
        // 这里手动设置它的 createTime
        logNormal.setCreateTime(now);

        // 35 天前的过期日志（应物理清除）
        LocalDateTime expiredTime1 = now.minusDays(35);
        SysLog logExpired1 = new SysLog();
        logExpired1.setUsername("TestUserExpired1");
        logExpired1.setIp("127.0.0.1");
        logExpired1.setUrl("/api/test/expired1");
        logExpired1.setMethod("POST");
        logExpired1.setClassName("TestController");
        logExpired1.setMethodName("expiredMethod1");
        logExpired1.setTitle("过期测试日志1");
        logExpired1.setStatus(1);
        logExpired1.setRequestTime(expiredTime1);
        logExpired1.setCreateTime(expiredTime1);

        // 40 天前的过期日志（应物理清除）
        LocalDateTime expiredTime2 = now.minusDays(40);
        SysLog logExpired2 = new SysLog();
        logExpired2.setUsername("TestUserExpired2");
        logExpired2.setIp("127.0.0.1");
        logExpired2.setUrl("/api/test/expired2");
        logExpired2.setMethod("POST");
        logExpired2.setClassName("TestController");
        logExpired2.setMethodName("expiredMethod2");
        logExpired2.setTitle("过期测试日志2");
        logExpired2.setStatus(0);
        logExpired2.setRequestTime(expiredTime2);
        logExpired2.setCreateTime(expiredTime2);

        // 2. 插入测试数据
        sysLogService.save(logNormal);
        sysLogService.save(logExpired1);
        sysLogService.save(logExpired2);

        // 记录生成的主键，以便稍后断言验证
        Long normalId = logNormal.getId();
        Long expiredId1 = logExpired1.getId();
        Long expiredId2 = logExpired2.getId();

        Assertions.assertNotNull(normalId, "正常日志主键不应为空");
        Assertions.assertNotNull(expiredId1, "过期日志1主键不应为空");
        Assertions.assertNotNull(expiredId2, "过期日志2主键不应为空");

        try {
            // 3. 执行物理清理动作：设置保留天数为 30 天
            int deletedCount = sysLogService.cleanExpiredLogs(30);
            
            // 因为我们至少插入了两条早于 30 天的过期日志，所以删除数量至少为 2
            Assertions.assertTrue(deletedCount >= 2, "删除日志数量不应小于 2，实际删除了: " + deletedCount);

            // 4. 验证结果
            SysLog normalLogAfterClean = sysLogService.getById(normalId);
            SysLog expiredLog1AfterClean = sysLogService.getById(expiredId1);
            SysLog expiredLog2AfterClean = sysLogService.getById(expiredId2);

            // 正常日志必须依然存在
            Assertions.assertNotNull(normalLogAfterClean, "正常日志 (30天内) 不应该被清理");
            Assertions.assertEquals("TestUserNormal", normalLogAfterClean.getUsername());

            // 过期日志必须已被物理删除
            Assertions.assertNull(expiredLog1AfterClean, "过期日志1 (35天前) 应该被物理清理");
            Assertions.assertNull(expiredLog2AfterClean, "过期日志2 (40天前) 应该被物理清理");

        } finally {
            // 5. 资源清理兜底：最后删除我们添加的测试正常日志，保持数据库干净
            sysLogService.removeById(normalId);
        }
    }
}
