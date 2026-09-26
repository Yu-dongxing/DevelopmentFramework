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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

    @Autowired
    private FileUploadCompensationService compensationService;

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

        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("文件上传必须在事务中执行");
        }
        String storagePath = path == null ? "default" : path;
        StorageService storageService = storageFactory.getActiveService();

        // 存储层在写入过程中计算摘要；返回结果记录本次实际使用的桶和路径。
        StorageUploadResult uploadResult = storageService.upload(file, storagePath);

        // 立即注册回滚补偿，覆盖 URL 构建、审计填充及事务提交阶段的异常。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 只在确认回滚时删除，事务结果未知时保留对象，避免误删已提交的数据。
             */
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    compensationService.compensate(storageService, uploadResult);
                } else if (status == STATUS_UNKNOWN) {
                    log.error("上传事务结果未知，暂不删除物理对象，需要核对数据库，桶: {}，路径: {}",
                            uploadResult.storageBucket(), uploadResult.filePath());
                }
            }
        });
        String fileUrl = storageService.buildUrl(uploadResult.filePath(), uploadResult.storageBucket());

        // 构造落库实体，任何异常均由上面的事务回调统一补偿。
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

        if (!sysFileService.save(sysFile)) {
            throw new BusinessException("文件上传保存记录失败");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 仅在实际提交后通知旁路业务，通知异常不得改变已经成功提交的上传结果。
             */
            @Override
            public void afterCommit() {
                try {
                    eventPublisher.publishEvent(new FileUploadedEvent(FileContextService.this, sysFile));
                } catch (Exception e) {
                    log.error("文件已上传并提交，但上传完成事件发布失败，文件ID: {}", sysFile.getId(), e);
                }
            }
        });

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
        SysFile sysFile = sysFileService.getIncludingDeleted(id);
        if (sysFile == null) {
            return;
        }

        if (physical) {
            if (!sysFileService.markPhysicalDeletePending(id)) {
                SysFile latest = sysFileService.getIncludingDeleted(id);
                if (latest == null || (Integer.valueOf(1).equals(latest.getIsDeleted())
                        && Integer.valueOf(1).equals(latest.getPhysicalDeleteStatus()))) {
                    return;
                }
                throw new BusinessException("文件删除状态更新失败");
            }
            sysFile.setIsDeleted(1);
            sysFile.setPhysicalDeleteStatus(1);
            eventPublisher.publishEvent(new FilePhysicalDeleteEvent(this, sysFile));
        } else if (!Integer.valueOf(1).equals(sysFile.getIsDeleted())) {
            // 逻辑删除 (MyBatis-Plus 配置 @TableLogic 后 removeById 即为逻辑删除)
            if (!sysFileService.removeById(id)) {
                SysFile latest = sysFileService.getIncludingDeleted(id);
                if (latest != null && !Integer.valueOf(1).equals(latest.getIsDeleted())) {
                    throw new BusinessException("文件逻辑删除失败");
                }
            }
        }
    }

    /**
     * 构建最新的访问地址 (防止域名变更)
     */
    public String buildUrl(SysFile sysFile) {
        if (sysFile == null) {
            return null;
        }
        StorageService storageService = storageFactory.getService(StorageType.valueOf(sysFile.getStorageType()));
        return storageService.buildUrl(sysFile.getFilePath(), sysFile.getStorageBucket());
    }

    /**
     * 提取原始文件名中的扩展名。
     */
    private String getFileExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf("."));
        }
        return "";
    }

    /**
     * 复用统一 IP 解析器填充上传审计。
     */
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
