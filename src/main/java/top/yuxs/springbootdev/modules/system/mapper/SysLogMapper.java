/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/05/28
 */

package top.yuxs.springbootdev.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.yuxs.springbootdev.modules.system.entity.SysLog;

import java.time.LocalDateTime;

/**
 * 系统操作日志 Mapper 接口
 *
 * @author YuDongXing
 * @since 2026/05/28
 */
@Mapper
public interface SysLogMapper extends BaseMapper<SysLog> {

    /**
     * 物理删除早于指定时间的日志 (支持 limit 限制，防止大事务锁表)
     *
     * @param cleanTime 截止时间
     * @param limit 每次删除的最大条数
     * @return 实际清除的条数
     */
    @Delete("DELETE FROM sys_log WHERE create_time < #{cleanTime} LIMIT #{limit}")
    int deletePhysicalBefore(@Param("cleanTime") LocalDateTime cleanTime, @Param("limit") int limit);
}

