package top.yuxs.springbootdev.modules.file.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.yuxs.springbootdev.modules.file.entity.SysFile;
import top.yuxs.springbootdev.modules.file.enums.StorageType;
import top.yuxs.springbootdev.modules.file.event.FilePhysicalDeleteEvent;
import top.yuxs.springbootdev.modules.file.service.SysFileService;
import top.yuxs.springbootdev.modules.file.storage.StorageFactory;
import top.yuxs.springbootdev.modules.file.storage.StorageService;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证物理删除使用已提交状态和历史桶，失败后保留任务。
 */
class FilePhysicalDeleteListenerTest {

    private SysFileService fileService;
    private StorageFactory factory;
    private StorageService storage;
    private FilePhysicalDeleteListener listener;
    private SysFile pending;

    /**
     * 准备一个位于历史桶的已删除文件。
     */
    @BeforeEach
    void setUp() {
        fileService = mock(SysFileService.class);
        factory = mock(StorageFactory.class);
        storage = mock(StorageService.class);
        listener = new FilePhysicalDeleteListener(fileService, factory);
        pending = new SysFile();
        pending.setId(1L);
        pending.setIsDeleted(1);
        pending.setPhysicalDeleteStatus(1);
        pending.setStorageType("MINIO");
        pending.setStorageBucket("historical-bucket");
        pending.setFilePath("old.txt");
        when(fileService.getIncludingDeleted(1L)).thenReturn(pending);
        when(factory.getService(StorageType.MINIO)).thenReturn(storage);
    }

    /**
     * 先删除历史对象，再清理待删除元数据。
     */
    @Test
    void shouldDeleteHistoricalObject() {
        when(fileService.completePhysicalDelete(1L)).thenReturn(true);
        listener.onFilePhysicalDelete(new FilePhysicalDeleteEvent(this, pending));
        verify(storage).delete("old.txt", "historical-bucket");
        verify(fileService).completePhysicalDelete(1L);
    }

    /**
     * 删除失败时不移除元数据，后续轮询可以再次执行。
     */
    @Test
    void shouldKeepAndRetryFailedTask() {
        when(fileService.listPendingPhysicalDeletes(100)).thenReturn(List.of(pending));
        doThrow(new IllegalStateException("模拟存储删除失败"))
                .doNothing().when(storage).delete("old.txt", "historical-bucket");
        listener.retryPendingPhysicalDeletes();
        verify(fileService, never()).completePhysicalDelete(1L);
        verify(fileService).deferPhysicalDelete(1L);
        listener.retryPendingPhysicalDeletes();
        verify(fileService).completePhysicalDelete(1L);
    }

    /**
     * 正常文件收到错误通知时不得触发物理删除。
     */
    @Test
    void shouldRefuseActiveRecord() {
        pending.setIsDeleted(0);
        listener.onFilePhysicalDelete(new FilePhysicalDeleteEvent(this, pending));
        verifyNoInteractions(storage);
        verify(fileService, never()).completePhysicalDelete(1L);
    }

    /**
     * 已完成清理的重复事件直接忽略。
     */
    @Test
    void shouldIgnoreAlreadyCompletedTask() {
        when(fileService.getIncludingDeleted(1L)).thenReturn(null);
        listener.onFilePhysicalDelete(new FilePhysicalDeleteEvent(this, pending));
        verifyNoInteractions(storage);
    }
}
