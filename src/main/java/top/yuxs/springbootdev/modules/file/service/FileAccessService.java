package top.yuxs.springbootdev.modules.file.service;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.yuxs.springbootdev.core.exception.BusinessException;
import top.yuxs.springbootdev.modules.file.config.FileProperties;
import top.yuxs.springbootdev.modules.file.entity.SysFile;
import top.yuxs.springbootdev.modules.file.enums.StorageAccessMode;
import top.yuxs.springbootdev.modules.file.enums.StorageType;

import java.util.Objects;

/**
 * 文件访问边界；私有文件只允许上传者在服务端授权后获得访问地址。
 */
@Service
@RequiredArgsConstructor
public class FileAccessService {
    private final FileProperties properties;

    /**
     * 兼容既有调用：非 RustFS 默认按公开文件处理，RustFS 读取当前默认配置。
     */
    public void checkUpload(StorageType type) {
        StorageAccessMode accessMode = type == StorageType.RUSTFS
                ? properties.getRustfs().getAccessMode() : StorageAccessMode.PUBLIC;
        checkUpload(type, accessMode);
    }

    /**
     * 私有上传必须有明确归属，禁止产生无法授权读取的匿名私有文件。
     */
    public void checkUpload(StorageType type, StorageAccessMode accessMode) {
        if (accessMode == StorageAccessMode.PRIVATE) {
            StpUtil.checkLogin();
        }
    }

    /**
     * 私有文件在返回详情和签发 URL 前校验归属。
     */
    public void checkRead(SysFile file) {
        if (getAccessMode(file) == StorageAccessMode.PRIVATE) {
            checkOwner(file);
        }
    }

    /**
     * RustFS 公开可读不代表允许匿名删除，删除一律要求上传者身份。
     */
    public void checkDelete(SysFile file) {
        if (isRustfs(file) || getAccessMode(file) == StorageAccessMode.PRIVATE) {
            checkOwner(file);
        }
    }

    /**
     * 判断文件是否由 RustFS 托管。
     */
    private boolean isRustfs(SysFile file) {
        return StorageType.RUSTFS.name().equals(file.getStorageType());
    }

    /**
     * 读取文件记录中的访问方式；仅为旧 RustFS 记录兼容历史默认配置。
     */
    private StorageAccessMode getAccessMode(SysFile file) {
        if (file.getAccessMode() == null || file.getAccessMode().isBlank()) {
            return isRustfs(file) ? properties.getRustfs().getAccessMode() : StorageAccessMode.PUBLIC;
        }
        try {
            return StorageAccessMode.valueOf(file.getAccessMode());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("文件访问方式不合法");
        }
    }

    /**
     * 只信任数据库归属，管理员身份不自动获得私有对象访问权。
     */
    private void checkOwner(SysFile file) {
        StpUtil.checkLogin();
        if (file.getUserId() == null || !Objects.equals(file.getUserId(), StpUtil.getLoginIdAsLong())) {
            throw new BusinessException("无权访问或删除该文件");
        }
    }
}
