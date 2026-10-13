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
 * RustFS 文件访问边界；首版只允许上传者访问私有文件和删除对象。
 */
@Service
@RequiredArgsConstructor
public class FileAccessService {
    private final FileProperties properties;

    /**
     * 私有上传必须有明确归属，禁止产生无法授权读取的匿名私有文件。
     */
    public void checkUpload(StorageType type) {
        if (type == StorageType.RUSTFS && isPrivate()) {
            StpUtil.checkLogin();
        }
    }

    /**
     * 私有文件在返回详情和签发 URL 前校验归属。
     */
    public void checkRead(SysFile file) {
        if (isRustfs(file) && isPrivate()) {
            checkOwner(file);
        }
    }

    /**
     * RustFS 公开可读不代表允许匿名删除，删除一律要求上传者身份。
     */
    public void checkDelete(SysFile file) {
        if (isRustfs(file)) {
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
     * 判断当前 RustFS 是否采用私有访问模式。
     */
    private boolean isPrivate() {
        return properties.getRustfs().getAccessMode() == StorageAccessMode.PRIVATE;
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
