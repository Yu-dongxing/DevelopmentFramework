package top.yuxs.springbootdev.modules.file.service;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import top.yuxs.springbootdev.core.exception.BusinessException;
import top.yuxs.springbootdev.modules.file.config.FileProperties;
import top.yuxs.springbootdev.modules.file.entity.SysFile;
import top.yuxs.springbootdev.modules.file.enums.StorageAccessMode;
import top.yuxs.springbootdev.modules.file.enums.StorageType;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

/**
 * 验证私有读写归属及公开文件删除边界。
 */
class FileAccessServiceTest {
    /**
     * 私有文件允许本人读取和删除，其他登录用户以及无归属记录均被拒绝。
     */
    @Test
    void shouldRequireOwnerForPrivateFiles() {
        FileAccessService access = new FileAccessService(new FileProperties());
        SysFile file = new SysFile();
        file.setStorageType("RUSTFS");
        file.setUserId(10L);
        try (MockedStatic<StpUtil> token = mockStatic(StpUtil.class)) {
            token.when(StpUtil::getLoginIdAsLong).thenReturn(10L);
            access.checkRead(file);
            access.checkDelete(file);
            token.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            assertThrows(BusinessException.class, () -> access.checkRead(file));
            assertThrows(BusinessException.class, () -> access.checkDelete(file));
            file.setUserId(null);
            assertThrows(BusinessException.class, () -> access.checkRead(file));
        }
    }

    /**
     * 匿名身份不能上传或读取私有文件，公开可读也不允许匿名删除。
     */
    @Test
    void shouldRejectAnonymousPrivateAccessAndPublicDeletion() {
        FileProperties properties = new FileProperties();
        FileAccessService access = new FileAccessService(properties);
        SysFile file = new SysFile();
        file.setStorageType("RUSTFS");
        try (MockedStatic<StpUtil> token = mockStatic(StpUtil.class)) {
            token.when(StpUtil::checkLogin).thenThrow(new BusinessException("未登录"));
            assertThrows(BusinessException.class, () -> access.checkUpload(StorageType.RUSTFS));
            assertThrows(BusinessException.class, () -> access.checkRead(file));
            properties.getRustfs().setAccessMode(StorageAccessMode.PUBLIC);
            access.checkRead(file);
            access.checkUpload(StorageType.RUSTFS);
            assertThrows(BusinessException.class, () -> access.checkDelete(file));
            file.setStorageType("MINIO");
            access.checkRead(file);
        }
    }
}
