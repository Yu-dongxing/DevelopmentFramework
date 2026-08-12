/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/16
 */

package top.yuxs.springbootdev.modules.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
     * @param limit 单次处理上限
     * @return 待清理文件列表
     */
    @Select("SELECT * FROM sys_file WHERE is_deleted = 1 AND physical_delete_status = 1 ORDER BY update_time ASC LIMIT #{limit}")
    List<SysFile> selectPendingPhysicalDeletes(@Param("limit") int limit);

    /**
     * 标记文件需要在事务提交后物理删除。
     *
     * @param id 文件 ID
     * @return 影响行数
     */
    @Update("UPDATE sys_file SET physical_delete_status = 1 WHERE id = #{id} AND is_deleted = 0")
    int markPhysicalDeletePending(@Param("id") Long id);
}
