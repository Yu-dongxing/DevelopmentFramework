package top.yuxs.springbootdev.modules.file.storage.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import top.yuxs.springbootdev.modules.file.config.FileProperties;
import top.yuxs.springbootdev.modules.file.storage.StorageUploadResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
