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
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import top.yuxs.springbootdev.core.common.Result;
import top.yuxs.springbootdev.core.annotation.AegisLog;
import top.yuxs.springbootdev.modules.file.config.FileProperties;
import top.yuxs.springbootdev.modules.file.entity.SysFile;
import top.yuxs.springbootdev.modules.file.service.FileContextService;
import top.yuxs.springbootdev.modules.file.service.SysFileService;

import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

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
    @AegisLog(isSaveResponseData = false)
    public Result<SysFile> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "bizId", required = false) String bizId,
                                 @RequestParam(value = "bizType", required = false) String bizType,
                                 @RequestParam(value = "path", defaultValue = "default") String path) {
        SysFile sysFile = fileContextService.upload(file, bizId, bizType, path);
        return Result.success(fileContextService.prepareUploadResponse(sysFile));
    }

    /**
     * 获取文件详情
     */
    @GetMapping("/{id}")
    @AegisLog(isSaveResponseData = false)
    public Result<SysFile> getInfo(@PathVariable("id") Long id) {
        SysFile sysFile = sysFileService.getById(id);
        return Result.success(fileContextService.prepareResponse(sysFile));
    }

    /**
     * 提供本地私有文件内容。云端私有文件在详情接口中返回即时签发的预签名 URL。
     */
    @GetMapping("/{id}/content")
    @AegisLog(isSaveResponseData = false)
    public ResponseEntity<Resource> getPrivateLocalContent(@PathVariable("id") Long id) throws IOException {
        SysFile sysFile = sysFileService.getById(id);
        Resource resource = fileContextService.getPrivateLocalResource(sysFile);
        MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
        if (sysFile.getContentType() != null && !sysFile.getContentType().isBlank()) {
            try {
                contentType = MediaType.parseMediaType(sysFile.getContentType());
            } catch (IllegalArgumentException ignored) {
                // 不信任客户端上报的 MIME 格式，无法解析时以安全的二进制类型返回。
            }
        }
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(resource.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(sysFile.getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .body(resource);
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
