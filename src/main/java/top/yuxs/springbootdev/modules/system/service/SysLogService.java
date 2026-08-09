/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/05/28
 */

package top.yuxs.springbootdev.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.yuxs.springbootdev.modules.system.entity.SysLog;

/**
 * 系统操作日志服务类接口
 *
 * @author YuDongXing
 * @since 2026/05/28
 */
public interface SysLogService extends IService<SysLog> {

    /**
     * 物理清理指定天数前的过期日志 (分批、非阻塞、大事务拆分)
     *
     * @param retentionDays 日志保留天数
     * @return 实际清理的总日志行数
     */
    int cleanExpiredLogs(int retentionDays);
}

