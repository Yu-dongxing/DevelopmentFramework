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
}
