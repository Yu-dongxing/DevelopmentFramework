package top.yuxs.springbootdev.modules.file.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.resource.PathResourceResolver;
import top.yuxs.springbootdev.modules.file.entity.SysFile;
import top.yuxs.springbootdev.modules.file.enums.StorageType;
import top.yuxs.springbootdev.modules.file.service.SysFileService;

import java.io.IOException;

/**
 * 本地文件访问必须对应已提交且未删除的元数据，逻辑删除后立即停止提供资源。
 */
@Component
@RequiredArgsConstructor
public class LocalFileResourceResolver extends PathResourceResolver {

    private final SysFileService sysFileService;

    /**
     * 先复用 Spring 的路径边界检查，再检查文件状态，不缓存数据库授权结果。
     */
    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
        Resource resource = super.getResource(resourcePath, location);
        if (resource == null) {
            return null;
        }
        boolean available = sysFileService.exists(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getStorageType, StorageType.LOCAL.name())
                .eq(SysFile::getFilePath, resourcePath)
                .eq(SysFile::getUploadStatus, 1));
        return available ? resource : null;
    }
}
