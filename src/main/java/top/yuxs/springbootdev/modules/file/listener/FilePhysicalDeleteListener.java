package top.yuxs.springbootdev.modules.file.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import top.yuxs.springbootdev.modules.file.entity.SysFile;
import top.yuxs.springbootdev.modules.file.enums.StorageType;
import top.yuxs.springbootdev.modules.file.event.FilePhysicalDeleteEvent;
import top.yuxs.springbootdev.modules.file.service.SysFileService;
import top.yuxs.springbootdev.modules.file.storage.StorageFactory;
import top.yuxs.springbootdev.modules.file.storage.StorageService;

/**
 * 在数据库事务提交后删除物理文件，并定时重试失败任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FilePhysicalDeleteListener {

    private static final int RETRY_BATCH_SIZE = 100;

    private final SysFileService sysFileService;
    private final StorageFactory storageFactory;

    /**
     * 仅在逻辑删除事务实际提交后执行，避免事务回滚造成物理文件误删。
     */
    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFilePhysicalDelete(FilePhysicalDeleteEvent event) {
        deletePhysicalFile(event.getSysFile());
    }

    /**
     * 定时补偿未完成的物理删除任务。
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void retryPendingPhysicalDeletes() {
        for (SysFile sysFile : sysFileService.listPendingPhysicalDeletes(RETRY_BATCH_SIZE)) {
            deletePhysicalFile(sysFile);
        }
    }

    /**
     * 再次读取已提交状态，只处理待清理文件，重复通知和正常记录均不会触发误删。
     */
    private void deletePhysicalFile(SysFile sysFile) {
        try {
            SysFile pending = sysFileService.getIncludingDeleted(sysFile.getId());
            if (pending == null || !Integer.valueOf(1).equals(pending.getIsDeleted())
                    || !Integer.valueOf(1).equals(pending.getPhysicalDeleteStatus())) {
                return;
            }
            StorageService storageService = storageFactory.getService(StorageType.valueOf(pending.getStorageType()));
            storageService.delete(pending.getFilePath(), pending.getStorageBucket());
            if (!sysFileService.completePhysicalDelete(pending.getId())) {
                log.warn("物理文件已删除，但文件元数据不存在或已被其他任务清理，文件ID: {}", sysFile.getId());
            }
        } catch (Exception e) {
            log.error("物理文件删除失败，将在下次任务中重试，文件ID: {}，路径: {}", sysFile.getId(), sysFile.getFilePath(), e);
            try {
                sysFileService.deferPhysicalDelete(sysFile.getId());
            } catch (Exception deferFailure) {
                log.error("更新文件重试顺序失败，文件ID: {}", sysFile.getId(), deferFailure);
            }
        }
    }
}
