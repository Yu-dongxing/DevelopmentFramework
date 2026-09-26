package top.yuxs.springbootdev.modules.file.storage.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import top.yuxs.springbootdev.modules.file.config.FileProperties;
import top.yuxs.springbootdev.modules.file.storage.StorageUploadResult;
import top.yuxs.springbootdev.modules.file.service.FileUploadCompensationService;
import top.yuxs.springbootdev.modules.file.service.SysFileService;
import top.yuxs.springbootdev.core.exception.BusinessException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 本地文件存储服务测试。
 */
class LocalStorageServiceImplTest {

    @TempDir
    Path tempDirectory;

    /**
     * 验证文件上传时保存的是实际内容的 MD5 摘要。
     */
    @Test
    void uploadShouldReturnContentMd5AndLocalBucket() throws Exception {
        FileProperties fileProperties = new FileProperties();
        fileProperties.getLocal().setUploadPath(tempDirectory.toString());

        LocalStorageServiceImpl storageService = new LocalStorageServiceImpl();
        ReflectionTestUtils.setField(storageService, "fileProperties", fileProperties);
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());

        StorageUploadResult result = storageService.upload(file, "test");

        assertEquals("local", result.storageBucket());
        assertEquals("5d41402abc4b2a76b9719d911017c592", result.md5());
        assertTrue(Files.exists(tempDirectory.resolve(result.filePath())));
    }

    /**
     * 文件写入中断时清理本次创建的残片，不留下可访问的孤立文件。
     */
    @Test
    void shouldRemovePartialFileWhenInputFails() throws Exception {
        FileProperties properties = new FileProperties();
        properties.getLocal().setUploadPath(tempDirectory.toString());
        SysFileService fileService = mock(SysFileService.class);
        LocalStorageServiceImpl storage = new LocalStorageServiceImpl();
        ReflectionTestUtils.setField(storage, "fileProperties", properties);
        ReflectionTestUtils.setField(storage, "compensationService", new FileUploadCompensationService(fileService));
        AtomicInteger openings = new AtomicInteger();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes()) {
            /**
             * 类型检测正常，正式复制流模拟中断。
             */
            @Override
            public InputStream getInputStream() throws IOException {
                if (openings.incrementAndGet() == 1) {
                    return super.getInputStream();
                }
                return new InputStream() {
                    /**
                     * 模拟读取上传数据失败。
                     */
                    @Override
                    public int read() throws IOException {
                        throw new IOException("模拟上传流中断");
                    }
                };
            }
        };
        assertThrows(BusinessException.class, () -> storage.upload(file, "test"));
        try (var paths = Files.walk(tempDirectory)) {
            assertEquals(0, paths.filter(Files::isRegularFile).count());
        }
        verifyNoInteractions(fileService);
    }

    /**
     * 补偿逻辑继续保留本地根目录边界，不能删除上传目录之外的文件。
     */
    @Test
    void shouldRejectDeletionOutsideRoot() {
        FileProperties properties = new FileProperties();
        properties.getLocal().setUploadPath(tempDirectory.toString());
        LocalStorageServiceImpl storage = new LocalStorageServiceImpl();
        ReflectionTestUtils.setField(storage, "fileProperties", properties);
        assertThrows(BusinessException.class, () -> storage.delete("../outside.txt", "local"));
    }
}
