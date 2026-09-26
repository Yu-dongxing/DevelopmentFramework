/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/05/28
 */

package top.yuxs.springbootdev.modules.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import top.yuxs.springbootdev.modules.system.entity.SysLog;
import top.yuxs.springbootdev.modules.system.mapper.SysLogMapper;
import top.yuxs.springbootdev.modules.system.service.SysLogService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统操作日志服务实现类
 *
 * @author YuDongXing
 * @since 2026/05/28
 */
@Service
public class SysLogServiceImpl extends ServiceImpl<SysLogMapper, SysLog> implements SysLogService {

    @Override
    public int cleanExpiredLogs(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }
        LocalDateTime cleanTime = LocalDateTime.now().minusDays(retentionDays);
        int totalDeleted = 0;
        int batchSize = 500; // 兼容 Oracle IN 最大 1000 项限制，并避免大事务锁表
        int currentBatchSelected;

        // 循环执行小范围物理删除，避免一次性删除大量数据导致的死锁、慢 SQL 以及主从延迟
        do {
            // 批处理只需要首批 ID，不查询总记录数，避免循环 COUNT 导致大表重复扫描。
            List<Long> ids = baseMapper.selectExpiredLogIds(new Page<>(1, batchSize, false), cleanTime);
            currentBatchSelected = ids.size();
            if (!ids.isEmpty()) {
                totalDeleted += baseMapper.deleteByIds(ids);
            }

            // 为了给予数据库 CPU 和磁盘 I/O 喘息时间，如果发生了实际删除，则微弱休眠 (如 50ms)
            if (currentBatchSelected > 0) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } while (currentBatchSelected == batchSize);

        return totalDeleted;
    }
}

