package top.yuxs.springbootdev.modules.file.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.yuxs.springbootdev.modules.file.entity.SysFile;
import top.yuxs.springbootdev.modules.file.storage.StorageService;
import top.yuxs.springbootdev.modules.file.storage.StorageUploadResult;

/**
 * 上传事务回滚后的物理清理；失败时复用已持久化的文件删除补偿机制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadCompensationService {

    private final SysFileService sysFileService;

    /**
     * 清理已经成功上传但未提交的对象，使用上传结果中的桶而非当前配置。
     */
    public void compensate(StorageService storageService, StorageUploadResult uploadResult) {
        try {
            storageService.delete(uploadResult.filePath(), uploadResult.storageBucket());
        } catch (Exception cleanupFailure) {
            SysFile pendingFile = new SysFile();
            pendingFile.setStorageType(storageService.getType().name());
            pendingFile.setStorageBucket(uploadResult.storageBucket());
            pendingFile.setFilePath(uploadResult.filePath());
            pendingFile.setUploadStatus(0);
            pendingFile.setIsDeleted(1);
            pendingFile.setPhysicalDeleteStatus(1);
            try {
                // 新记录使用独立雪花 ID，不能复用已回滚的业务实体或污染其引用。
                sysFileService.saveUploadCleanup(pendingFile);
                log.warn("上传回滚后的物理清理失败，已持久化重试任务，文件ID: {}", pendingFile.getId(), cleanupFailure);
            } catch (Exception persistenceFailure) {
                log.error("上传补偿清理及任务持久化均失败，需要人工核对存储对象，类型: {}，桶: {}，路径: {}",
                        storageService.getType(), uploadResult.storageBucket(), uploadResult.filePath(), persistenceFailure);
            }
        }
    }
}
