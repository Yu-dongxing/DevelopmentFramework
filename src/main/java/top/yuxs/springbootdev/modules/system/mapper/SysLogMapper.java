/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/05/28
 */

package top.yuxs.springbootdev.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.yuxs.springbootdev.modules.system.entity.SysLog;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统操作日志 Mapper 接口
 *
 * @author YuDongXing
 * @since 2026/05/28
 */
@Mapper
public interface SysLogMapper extends BaseMapper<SysLog> {

    /**
     * 分批查询早于指定时间的日志 ID。分页方言由 MyBatis-Plus 按当前数据库自动处理。
     *
     * @param page      分页参数
     * @param cleanTime 截止时间
     * @return 待清理日志 ID
     */
    @Select("SELECT id FROM sys_log WHERE create_time < #{cleanTime} ORDER BY id")
    List<Long> selectExpiredLogIds(IPage<?> page, @Param("cleanTime") LocalDateTime cleanTime);
}

