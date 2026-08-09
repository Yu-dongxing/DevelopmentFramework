/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/05/28
 */

package top.yuxs.springbootdev.modules.system.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import top.yuxs.springbootdev.modules.system.entity.SysLog;
import top.yuxs.springbootdev.modules.system.service.SysLogService;
import top.yuxs.springbootdev.modules.system.event.AegisLogEvent;

/**
 * 操作日志事件监听处理器 (在 Java 21 虚拟线程中非阻塞执行)
 *
 * @author YuDongXing
 * @since 2026/05/28
 */
@Slf4j
@Component
public class AegisLogListener {

    private final SysLogService sysLogService;

    public AegisLogListener(SysLogService sysLogService) {
        this.sysLogService = sysLogService;
    }

    /**
     * 监听日志记录事件
     * 绑定 taskExecutor (Virtual Threads 虚拟线程执行器) 异步、高吞吐入库
     */
    @Async("taskExecutor")
    @EventListener
    public void onAegisLogEvent(AegisLogEvent event) {
        SysLog sysLog = event.getSysLog();
        try {
            sysLogService.save(sysLog);
        } catch (Exception e) {
            log.error("异步落库系统操作日志失败！异常: {}, 日志数据: {}", e.getMessage(), sysLog, e);
        }
    }
}
