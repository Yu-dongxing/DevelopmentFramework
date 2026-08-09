/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/16
 */

package top.yuxs.springbootdev.modules.file.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.yuxs.springbootdev.core.common.Result;
import top.yuxs.springbootdev.modules.file.config.FileProperties;
import top.yuxs.springbootdev.modules.file.entity.SysFile;
import top.yuxs.springbootdev.modules.file.service.FileContextService;
import top.yuxs.springbootdev.modules.file.service.SysFileService;

import java.util.HashMap;
import java.util.Map;

/**
 * 公共接口 -- 文件管理接口
 * 上传/查询（根据id）/获取上传配置详细
 *
 * @author YuDongXing
 * @since 2026/04/16
 */
@RestController
@RequestMapping("/api/common/file")
public class FileController {

    @Autowired
    private FileContextService fileContextService;

    @Autowired
    private SysFileService sysFileService;

    @Autowired
    private FileProperties fileProperties;

    /**
     * 上传文件
     *
     * @param file    文件对象
     * @param bizId   业务关联ID
     * @param bizType 业务类型
     * @param path    存储子路径
     * @return 文件信息
     */
    @PostMapping("/upload")
    public Result<SysFile> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "bizId", required = false) String bizId,
                                 @RequestParam(value = "bizType", required = false) String bizType,
                                 @RequestParam(value = "path", defaultValue = "default") String path) {
        SysFile sysFile = fileContextService.upload(file, bizId, bizType, path);
        return Result.success(sysFile);
    }

    /**
     * 获取文件详情
     */
    @GetMapping("/{id}")
    public Result<SysFile> getInfo(@PathVariable("id") Long id) {
        SysFile sysFile = sysFileService.getById(id);
        if (sysFile != null) {
            // 返回实时构建的 URL，确保环境迁移后依然可用
            sysFile.setFileUrl(fileContextService.buildUrl(sysFile));
        }
        return Result.success(sysFile);
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable("id") Long id,
                               @RequestParam(value = "physical", defaultValue = "false") boolean physical) {
        fileContextService.delete(id, physical);
        return Result.success();
    }

    /**
     * 获取配置信息
     */
    @GetMapping("/config")
    public Result<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("active", fileProperties.getActive());
        if (fileProperties.getActive() == top.yuxs.springbootdev.modules.file.enums.StorageType.LOCAL) {
            config.put("domain", fileProperties.getLocal().getDomain());
            config.put("accessPath", fileProperties.getLocal().getAccessPath());
        }
        return Result.success(config);
    }
}
