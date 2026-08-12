/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/16
 */

package top.yuxs.springbootdev.modules.file.service;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import top.yuxs.springbootdev.modules.file.entity.SysFile;
import top.yuxs.springbootdev.modules.file.enums.StorageType;
import top.yuxs.springbootdev.core.exception.BusinessException;
import top.yuxs.springbootdev.modules.file.event.FileUploadedEvent;
import top.yuxs.springbootdev.modules.file.event.FilePhysicalDeleteEvent;
import top.yuxs.springbootdev.modules.file.storage.StorageFactory;
import top.yuxs.springbootdev.modules.file.storage.StorageService;
import top.yuxs.springbootdev.modules.file.storage.StorageUploadResult;
import top.yuxs.springbootdev.core.utils.IpUtils;


/**
 * 文件上下文服务（业务编排层）
 *
 * @author YuDongXing
 * @since 2026/04/16
 */
@Slf4j
@Service
public class FileContextService {

    @Autowired
    private StorageFactory storageFactory;

    @Autowired
    private SysFileService sysFileService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 上传文件
     *
     * @param file    文件
     * @param bizId   业务关联 ID
     * @param bizType 业务类型
     * @param path    存储子路径
     * @return 文件信息
     */
    @Transactional(rollbackFor = Exception.class)
    public SysFile upload(MultipartFile file, String bizId, String bizType, String path) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        // 校验 path 参数安全字符，防止路径越界和云端特殊 key 注入
        if (path != null && !path.matches("^[a-zA-Z0-9_\\-]+$")) {
            throw new BusinessException("非法子路径参数，仅支持字母、数字、下划线及中划线！");
        }

        StorageService storageService = storageFactory.getActiveService();

        // 1. 物理上传 (此时不计算 MD5，避免流消耗)
        StorageUploadResult uploadResult = storageService.upload(file, path);
        String fileUrl = storageService.buildUrl(uploadResult.filePath());

        // 3. 构造落库实体
        SysFile sysFile = new SysFile();
        sysFile.setBizId(bizId);
        sysFile.setBizType(bizType);
        sysFile.setOriginalName(file.getOriginalFilename());
        sysFile.setFileName(uploadResult.filePath().substring(uploadResult.filePath().lastIndexOf("/") + 1));
        sysFile.setFileExt(getFileExtension(file.getOriginalFilename()));
        sysFile.setFileSize(file.getSize());
        sysFile.setContentType(file.getContentType());
        sysFile.setMd5(uploadResult.md5());
        sysFile.setStorageType(storageService.getType().name());
        sysFile.setStorageBucket(uploadResult.storageBucket());
        sysFile.setFilePath(uploadResult.filePath());
        sysFile.setFileUrl(fileUrl);
        sysFile.setUploadStatus(1);
        sysFile.setUploadIp(getIpAddress());
        fillUploader(sysFile);

        try {
            // 4. 同步保存记录，保障事务强一致性
            sysFileService.save(sysFile);
            // 5. 发布上传完成事件，供旁路业务消费
            eventPublisher.publishEvent(new FileUploadedEvent(this, sysFile));
        } catch (Exception e) {
            // 6. 异常补偿：落库失败则删除已上传的物理文件
            log.error("文件记录落库/保存失败，执行补偿物理删除: {}", uploadResult.filePath(), e);
            storageService.delete(uploadResult.filePath());
            throw new BusinessException("文件上传保存记录失败");
        }

        return sysFile;
    }

    /**
     * 删除文件
     *
     * @param id       文件 ID
     * @param physical 是否物理删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, boolean physical) {
        SysFile sysFile = sysFileService.getById(id);
        if (sysFile == null) {
            return;
        }

        if (physical) {
            if (!sysFileService.markPhysicalDeletePending(id) || !sysFileService.removeById(id)) {
                throw new BusinessException("文件删除状态更新失败");
            }
            sysFile.setPhysicalDeleteStatus(1);
            eventPublisher.publishEvent(new FilePhysicalDeleteEvent(this, sysFile));
        } else {
            // 逻辑删除 (MyBatis-Plus 配置 @TableLogic 后 removeById 即为逻辑删除)
            sysFileService.removeById(id);
        }
    }

    /**
     * 构建最新的访问地址 (防止域名变更)
     */
    public String buildUrl(SysFile sysFile) {
        if (sysFile == null) return null;
        StorageService storageService = storageFactory.getService(StorageType.valueOf(sysFile.getStorageType()));
        return storageService.buildUrl(sysFile.getFilePath());
    }

    private String getFileExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf("."));
        }
        return "";
    }

    private String getIpAddress() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            return IpUtils.getClientIp(request);
        }
        return "unknown";
    }

    /**
     * 写入上传者审计信息；匿名上传统一登记为访客。
     */
    private void fillUploader(SysFile sysFile) {
        if (!StpUtil.isLogin()) {
            sysFile.setUsername("访客");
            return;
        }
        sysFile.setUserId(StpUtil.getLoginIdAsLong());
        Object username = StpUtil.getSession(false) == null ? null : StpUtil.getSession().get("username");
        sysFile.setUsername(username == null ? StpUtil.getLoginId().toString() : username.toString());
    }
}
