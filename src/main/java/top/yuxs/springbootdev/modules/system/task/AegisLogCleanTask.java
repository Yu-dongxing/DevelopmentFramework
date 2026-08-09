/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/08/09
 */

package top.yuxs.springbootdev.modules.system.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.yuxs.springbootdev.core.config.AegisLogProperties;
import top.yuxs.springbootdev.modules.system.service.SysLogService;

/**
 * 接口系统操作日志滚动清理定时任务 (在虚拟线程中定时触发)
 *
 * @author YuDongXing
 * @since 2026/08/09
 */
@Slf4j
@Component
public class AegisLogCleanTask {

    private final SysLogService sysLogService;
    private final AegisLogProperties aegisLogProperties;

    public AegisLogCleanTask(SysLogService sysLogService, AegisLogProperties aegisLogProperties) {
        this.sysLogService = sysLogService;
        this.aegisLogProperties = aegisLogProperties;
    }

    /**
     * 每天凌晨 02:00:00 自动执行日志数据滚动物理清理
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanExpiredLogs() {
        int retentionDays = aegisLogProperties.getRetentionDays();
        if (retentionDays <= 0) {
            log.info("接口日志物理滚动清理：已关闭 (保留天数配置为 {})", retentionDays);
            return;
        }

        log.info("接口日志物理滚动清理：任务启动，当前配置保留天数: {} 天...", retentionDays);
        try {
            long startTime = System.currentTimeMillis();
            int deletedCount = sysLogService.cleanExpiredLogs(retentionDays);
            long cost = System.currentTimeMillis() - startTime;
            log.info("接口日志物理滚动清理：任务结束。成功清除过期的操作日志共计: {} 条，共耗时 {} ms", deletedCount, cost);
        } catch (Exception e) {
            log.error("接口日志物理滚动清理：出现异常错误！", e);
        }
    }
}
