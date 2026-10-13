/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/05/28
 */

package top.yuxs.springbootdev.modules.file.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.yuxs.springbootdev.core.common.BaseController;
import top.yuxs.springbootdev.core.annotation.AegisLog;
import top.yuxs.springbootdev.core.common.Result;
import top.yuxs.springbootdev.core.exception.BusinessException;
import top.yuxs.springbootdev.modules.file.entity.SysFile;
import top.yuxs.springbootdev.modules.file.service.FileContextService;
import top.yuxs.springbootdev.modules.file.service.SysFileService;

import java.util.List;

/**
 * 系统文件后台管理接口控制器
 * 元数据只允许上传流程创建；后台保留分页和经过生命周期服务的删除入口。
 *
 * @author YuDongXing
 * @since 2026/05/28
 */
@RestController
@RequestMapping("/sys/file")
public class SysFileController extends BaseController<SysFile, SysFileService> {

    @Autowired
    private FileContextService fileContextService;

    /**
     * 禁止伪造对象位置和上传者绕过私有文件授权。
     */
    @Override
    public Result<?> save(SysFile entity) {
        throw new BusinessException("文件元数据只能通过上传接口创建");
    }

    /**
     * 批量入口同样禁止客户端伪造文件记录。
     */
    @Override
    public Result<?> saveBatch(List<SysFile> list) {
        throw new BusinessException("文件元数据只能通过上传接口创建");
    }

    /**
     * 禁止通用更新篡改历史桶、对象路径、状态或文件归属。
     */
    @Override
    public Result<?> update(SysFile entity) {
        throw new BusinessException("文件元数据不支持通用更新");
    }

    /**
     * 批量更新不能绕过文件元数据保护。
     */
    @Override
    public Result<?> updateBatch(List<SysFile> list) {
        throw new BusinessException("文件元数据不支持通用更新");
    }

    /**
     * 后台删除复用归属校验及事务一致性处理。
     */
    @Override
    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id) {
        fileContextService.delete(id, false);
        return Result.success();
    }

    /**
     * 任意文件无权删除时整批回滚，避免部分成功。
     */
    @Override
    @DeleteMapping("/deleteBatch")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> deleteBatch(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.contains(null)) {
            throw new BusinessException("批量删除需提供 1 至 100 个有效文件 ID");
        }
        for (Long id : ids) {
            fileContextService.delete(id, false);
        }
        return Result.success();
    }

    /**
     * RustFS 记录只向上传者展示，查询后统一生成最新地址。
     */
    @Override
    @GetMapping("/page")
    @AegisLog(isSaveRequestData = false, isSaveResponseData = false)
    public Result<?> page(@RequestParam(defaultValue = "1") long current,
                          @RequestParam(defaultValue = "10") long size, SysFile queryEntity) {
        StpUtil.checkLogin();
        if (current < 1 || size < 1 || size > 100) {
            throw new BusinessException("分页参数不合法，页大小必须在 1 至 100 之间");
        }
        QueryWrapper<SysFile> wrapper = getQueryWrapper(queryEntity);
        wrapper.and(condition -> condition.ne("storage_type", "RUSTFS")
                .or().eq("user_id", StpUtil.getLoginIdAsLong()));
        IPage<SysFile> page = baseService.page(new Page<>(current, size), wrapper);
        page.setRecords(page.getRecords().stream().map(fileContextService::prepareResponse).toList());
        return Result.success(page);
    }

    /**
     * 重写查询钩子，定制更适合文件搜索的查询条件。
     * 支持 originalName 模糊查询，以及等值的 bizType / bizId 匹配。
     */
    @Override
    protected QueryWrapper<SysFile> getQueryWrapper(SysFile queryEntity) {
        QueryWrapper<SysFile> wrapper = new QueryWrapper<>();
        if (queryEntity == null) {
            return wrapper;
        }

        // 1. originalName 模糊查询
        if (queryEntity.getOriginalName() != null && !queryEntity.getOriginalName().trim().isEmpty()) {
            wrapper.like("original_name", queryEntity.getOriginalName().trim());
        }

        // 2. bizType 等值匹配
        if (queryEntity.getBizType() != null && !queryEntity.getBizType().trim().isEmpty()) {
            wrapper.eq("biz_type", queryEntity.getBizType().trim());
        }

        // 3. bizId 等值匹配
        if (queryEntity.getBizId() != null && !queryEntity.getBizId().trim().isEmpty()) {
            wrapper.eq("biz_id", queryEntity.getBizId().trim());
        }

        // 4. storageBucket 等值匹配
        if (queryEntity.getStorageBucket() != null && !queryEntity.getStorageBucket().trim().isEmpty()) {
            wrapper.eq("storage_bucket", queryEntity.getStorageBucket().trim());
        }

        return wrapper;
    }
}
