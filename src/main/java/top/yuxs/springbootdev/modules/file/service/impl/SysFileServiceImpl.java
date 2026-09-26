/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/16
 */

package top.yuxs.springbootdev.modules.file.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.yuxs.springbootdev.core.exception.BusinessException;
import top.yuxs.springbootdev.modules.file.entity.SysFile;
import top.yuxs.springbootdev.modules.file.mapper.SysFileMapper;
import top.yuxs.springbootdev.modules.file.service.SysFileService;

import java.util.List;

/**
 * 文件信息服务实现
 *
 * @author YuDongXing
 * @since 2026/04/16
 */
@Service
public class SysFileServiceImpl extends ServiceImpl<SysFileMapper, SysFile> implements SysFileService {

    /**
     * 读取文件真实删除状态，供重复删除和逻辑删除升级使用。
     */
    @Override
    public SysFile getIncludingDeleted(Long id) {
        return baseMapper.selectIncludingDeleted(id);
    }

    /**
     * 物理对象清理完成后，仅移除对应的待清理记录。
     */
    @Override
    public boolean completePhysicalDelete(Long id) {
        return baseMapper.deletePendingPhysicalDelete(id) > 0;
    }

    /**
     * 延后重试失败的任务，保证其他任务有机会执行。
     */
    @Override
    public void deferPhysicalDelete(Long id) {
        baseMapper.deferPhysicalDelete(id);
    }

    /**
     * 在原上传事务之外持久化补偿记录，原事务回滚不会撤销该记录。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void saveUploadCleanup(SysFile file) {
        if (baseMapper.insert(file) != 1) {
            throw new BusinessException("保存上传补偿任务失败");
        }
    }

    @Override
    public boolean physicalDeleteById(Long id) {
        return baseMapper.physicalDeleteById(id) > 0;
    }

    @Override
    public boolean markPhysicalDeletePending(Long id) {
        return baseMapper.markPhysicalDeletePending(id) > 0;
    }

    @Override
    public List<SysFile> listPendingPhysicalDeletes(int limit) {
        int pageSize = Math.max(1, Math.min(limit, 500));
        // 补偿任务只读取首批数据，无需执行总数统计。
        return baseMapper.selectPendingPhysicalDeletes(new Page<>(1, pageSize, false));
    }
}
