/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/16
 */

package top.yuxs.springbootdev.modules.file.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.yuxs.springbootdev.modules.file.entity.SysFile;

import java.util.List;

/**
 * 文件信息服务
 *
 * @author YuDongXing
 * @since 2026/04/16
 */
public interface SysFileService extends IService<SysFile> {

    /**
     * 物理删除记录
     *
     * @param id ID
     * @return 是否成功
     */
    boolean physicalDeleteById(Long id);

    /**
     * 标记文件在当前事务提交后进行物理删除。
     *
     * @param id 文件 ID
     * @return 是否标记成功
     */
    boolean markPhysicalDeletePending(Long id);

    /**
     * 查询待重试的物理删除文件。
     *
     * @param limit 单次查询上限
     * @return 待删除文件
     */
    List<SysFile> listPendingPhysicalDeletes(int limit);

    /**
     * 查询包含逻辑删除状态的记录，仅用于生命周期编排。
     */
    SysFile getIncludingDeleted(Long id);

    /**
     * 清理待删除元数据，正常文件不受影响。
     */
    boolean completePhysicalDelete(Long id);

    /**
     * 延后失败任务的重试顺序。
     */
    void deferPhysicalDelete(Long id);

    /**
     * 独立事务保存上传回滚后的清理任务，复用现有物理删除补偿队列。
     */
    void saveUploadCleanup(SysFile file);
}
