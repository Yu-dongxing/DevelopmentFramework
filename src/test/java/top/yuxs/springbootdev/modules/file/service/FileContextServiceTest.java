package top.yuxs.springbootdev.modules.file.service;

import cn.dev33.satoken.context.SaTokenContextForThreadLocalStaff;
import cn.dev33.satoken.servlet.model.SaRequestForServlet;
import cn.dev33.satoken.servlet.model.SaResponseForServlet;
import cn.dev33.satoken.servlet.model.SaStorageForServlet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.junit.jupiter.api.AfterEach;
import top.yuxs.springbootdev.core.exception.BusinessException;
import top.yuxs.springbootdev.modules.file.entity.SysFile;
import top.yuxs.springbootdev.modules.file.enums.StorageType;
import top.yuxs.springbootdev.modules.file.event.FileUploadedEvent;
import top.yuxs.springbootdev.modules.file.storage.StorageFactory;
import top.yuxs.springbootdev.modules.file.storage.StorageService;
import top.yuxs.springbootdev.modules.file.storage.StorageUploadResult;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 在真实 H2 事务上验证上传提交、回滚补偿和文件删除状态流转。
 */
@SpringBootTest(properties = {
        "db.init.enabled=true", "db.init.init-data=false",
        "spring.datasource.url=jdbc:h2:mem:file_lifecycle;DB_CLOSE_DELAY=-1"
})
@RecordApplicationEvents
class FileContextServiceTest {

    @Autowired
    private FileContextService contextService;

    @MockitoSpyBean
    private SysFileService fileService;

    @MockitoBean
    private StorageFactory storageFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents events;

    private StorageService storage;
    private String filePath;
    private MockMultipartFile multipartFile;

    /**
     * 每个用例使用唯一对象位置，存储 SDK 不连接外部系统。
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM sys_file");
        storage = mock(StorageService.class);
        filePath = "test/" + UUID.randomUUID() + ".txt";
        multipartFile = new MockMultipartFile("file", "test.txt", "text/plain", new byte[]{1, 2, 3});
        when(storageFactory.getActiveService()).thenReturn(storage);
        when(storage.getType()).thenReturn(StorageType.MINIO);
        when(storage.upload(any(), anyString())).thenReturn(new StorageUploadResult(filePath, "old-bucket", "md5"));
        when(storage.buildUrl(filePath, "old-bucket")).thenReturn("https://storage/old-bucket/" + filePath);
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SaTokenContextForThreadLocalStaff.setModelBox(new SaRequestForServlet(request),
                new SaResponseForServlet(new MockHttpServletResponse()), new SaStorageForServlet(request));
    }

    /**
     * 清理当前线程的请求上下文，避免污染其他测试。
     */
    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SaTokenContextForThreadLocalStaff.clearModelBox();
    }

    /**
     * 只有最外层事务提交后才发出上传事件，成功对象不能被补偿删除。
     */
    @Test
    void shouldPublishOnlyAfterOuterCommit() {
        SysFile file = new TransactionTemplate(transactionManager).execute(status -> {
            SysFile uploaded = contextService.upload(multipartFile, null, null, null);
            assertEquals(0, events.stream(FileUploadedEvent.class).count());
            return uploaded;
        });
        assertNotNull(fileService.getById(file.getId()));
        assertEquals(1, events.stream(FileUploadedEvent.class).count());
        verify(storage, never()).delete(anyString(), anyString());
    }

    /**
     * 外层业务主动回滚时清理对象，并且不发布上传成功通知。
     */
    @Test
    void shouldCompensateOuterRollback() {
        SysFile file = new TransactionTemplate(transactionManager).execute(status -> {
            SysFile uploaded = contextService.upload(multipartFile, null, null, "test");
            status.setRollbackOnly();
            return uploaded;
        });
        assertNull(fileService.getIncludingDeleted(file.getId()));
        assertEquals(0, events.stream(FileUploadedEvent.class).count());
        verify(storage).delete(filePath, "old-bucket");
    }

    /**
     * 保存返回 false 也必须触发事务回滚和物理补偿。
     */
    @Test
    void shouldRejectFalseSaveResult() {
        doReturn(false).when(fileService).save(any(SysFile.class));
        assertThrows(BusinessException.class,
                () -> contextService.upload(multipartFile, null, null, "test"));
        verify(storage).delete(filePath, "old-bucket");
        assertEquals(0, events.stream(FileUploadedEvent.class).count());
    }

    /**
     * 在元数据构造前发生 URL 异常也必须被回滚补偿覆盖。
     */
    @Test
    void shouldCompensateMetadataFailure() {
        when(storage.buildUrl(filePath, "old-bucket")).thenThrow(new IllegalStateException("模拟地址构建失败"));
        assertThrows(IllegalStateException.class,
                () -> contextService.upload(multipartFile, null, null, "test"));
        verify(storage).delete(filePath, "old-bucket");
    }

    /**
     * 事务提交失败并确认回滚时，也会清理物理对象。
     */
    @Test
    void shouldCompensateCommitFailureWithConfirmedRollback() {
        DataSourceTransactionManager failingManager = new DataSourceTransactionManager(dataSource) {
            /**
             * 模拟实际提交前的数据库错误。
             */
            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                throw new TransactionSystemException("模拟数据库提交失败");
            }
        };
        failingManager.setRollbackOnCommitFailure(true);
        assertThrows(TransactionSystemException.class,
                () -> new TransactionTemplate(failingManager).execute(status ->
                        contextService.upload(multipartFile, null, null, "test")));
        verify(storage).delete(filePath, "old-bucket");
        assertEquals(0, events.stream(FileUploadedEvent.class).count());
        assertEquals(0, fileService.count());
    }

    /**
     * 回滚后物理删除失败，补偿记录必须独立提交并可被定时任务查询。
     */
    @Test
    void shouldPersistFailedCleanupOutsideRolledBackTransaction() {
        doThrow(new IllegalStateException("模拟存储不可用")).when(storage).delete(filePath, "old-bucket");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            contextService.upload(multipartFile, null, null, "test");
            status.setRollbackOnly();
        });
        var pending = fileService.listPendingPhysicalDeletes(10);
        assertEquals(1, pending.size());
        assertEquals(filePath, pending.getFirst().getFilePath());
        assertEquals("old-bucket", pending.getFirst().getStorageBucket());
        assertEquals(0, pending.getFirst().getUploadStatus());
        assertEquals(0, fileService.count());
    }

    /**
     * 已经逻辑删除的文件仍可以升级为物理清理，且重复请求保持幂等。
     */
    @Test
    void shouldUpgradeLogicalDeleteAndRemainIdempotent() {
        SysFile file = contextService.upload(multipartFile, null, null, "test");
        contextService.delete(file.getId(), false);
        assertNull(fileService.getById(file.getId()));
        assertEquals(0, fileService.getIncludingDeleted(file.getId()).getPhysicalDeleteStatus());
        // 在外层事务中观察状态后回滚，避免真正触发异步清理干扰断言。
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            contextService.delete(file.getId(), true);
            contextService.delete(file.getId(), true);
            assertEquals(1, fileService.getIncludingDeleted(file.getId()).getPhysicalDeleteStatus());
            status.setRollbackOnly();
        });
        assertEquals(0, fileService.getIncludingDeleted(file.getId()).getPhysicalDeleteStatus());
        verify(storage, never()).delete(anyString(), anyString());
    }

    /**
     * 逻辑删除失败不能返回成功。
     */
    @Test
    void shouldRejectFailedLogicalDelete() {
        SysFile file = contextService.upload(multipartFile, null, null, "test");
        doReturn(false).when(fileService).removeById(file.getId());
        assertThrows(BusinessException.class, () -> contextService.delete(file.getId(), false));
        assertNotNull(fileService.getById(file.getId()));
    }
}
