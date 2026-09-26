/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/16
 */

package top.yuxs.springbootdev.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import top.yuxs.springbootdev.modules.file.entity.SysFile;

import java.util.List;

/**
 * 文件信息 Mapper
 *
 * @author YuDongXing
 * @since 2026/04/16
 */
@Mapper
public interface SysFileMapper extends BaseMapper<SysFile> {

    /**
     * 物理删除记录
     *
     * @param id ID
     * @return 影响行数
     */
    @Delete("DELETE FROM sys_file WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /**
     * 查询已提交物理删除请求、但尚未完成清理的文件。
     *
     * @param page 分页参数，分页方言由 MyBatis-Plus 按当前数据库自动处理
     * @return 待清理文件列表
     */
    @Select("SELECT * FROM sys_file WHERE is_deleted = 1 AND physical_delete_status = 1 ORDER BY update_time ASC")
    @ResultMap("mybatis-plus_SysFile")
    List<SysFile> selectPendingPhysicalDeletes(IPage<SysFile> page);

    /**
     * 标记文件需要在事务提交后物理删除。
     *
     * @param id 文件 ID
     * @return 影响行数
     */
    @Update("UPDATE sys_file SET is_deleted = 1, physical_delete_status = 1, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND (physical_delete_status IS NULL OR physical_delete_status = 0)")
    int markPhysicalDeletePending(@Param("id") Long id);

    /**
     * 仅供文件生命周期编排查询，包含已经逻辑删除的记录。
     */
    @Select("SELECT * FROM sys_file WHERE id = #{id}")
    @ResultMap("mybatis-plus_SysFile")
    SysFile selectIncludingDeleted(@Param("id") Long id);

    /**
     * 仅清理处于待物理删除状态的元数据，避免误删正常记录。
     */
    @Delete("DELETE FROM sys_file WHERE id = #{id} AND is_deleted = 1 AND physical_delete_status = 1")
    int deletePendingPhysicalDelete(@Param("id") Long id);

    /**
     * 失败任务移到重试队列末尾，避免首批失败文件长期阻塞后续任务。
     */
    @Update("UPDATE sys_file SET update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND is_deleted = 1 AND physical_delete_status = 1")
    int deferPhysicalDelete(@Param("id") Long id);
}
