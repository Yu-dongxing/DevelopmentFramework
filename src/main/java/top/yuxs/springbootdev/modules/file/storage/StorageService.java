/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/16
 */

package top.yuxs.springbootdev.modules.file.storage;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import top.yuxs.springbootdev.core.exception.BusinessException;
import top.yuxs.springbootdev.modules.file.enums.StorageAccessMode;
import top.yuxs.springbootdev.modules.file.enums.StorageType;

/**
 * 存储服务接口
 *
 * @author YuDongXing
 * @since 2026/04/16
 */
public interface StorageService {

    /**
     * 上传文件
     *
     * @param file 文件
     * @param path 相对路径/子目录
     * @return 存储后的相对路径/对象Key
     */
    StorageUploadResult upload(MultipartFile file, String path);

    /**
     * 删除文件
     *
     * @param filePath 相对路径/对象Key
     */
    void delete(String filePath);

    /**
     * 按上传时记录的存储桶删除；本地存储不区分桶，复用原有路径校验。
     */
    default void delete(String filePath, String storageBucket) {
        delete(filePath);
    }

    /**
     * 构建访问URL
     *
     * @param filePath 相对路径/对象Key
     * @return 完整访问URL
     */
    String buildUrl(String filePath);

    /**
     * 按历史存储桶构建地址，避免切换默认桶后访问错误对象。
     */
    default String buildUrl(String filePath, String storageBucket) {
        return buildUrl(filePath);
    }

    /**
     * 根据上传时固化的访问方式构建地址，私有对象必须返回新的临时授权地址。
     */
    default String buildUrl(String filePath, String storageBucket, StorageAccessMode accessMode) {
        if (accessMode == StorageAccessMode.PRIVATE) {
            throw new BusinessException("当前存储不支持私有文件临时访问地址");
        }
        return buildUrl(filePath, storageBucket);
    }

    /**
     * 返回本次上传应持久化的访问方式；保留旧实现的临时地址语义以兼容存储适配器。
     */
    default StorageAccessMode getAccessMode() {
        return usesTemporaryUrl() ? StorageAccessMode.PRIVATE : StorageAccessMode.PUBLIC;
    }

    /**
     * 为本地私有文件提供受服务层授权保护的资源，云存储默认不支持该读取方式。
     */
    default Resource getPrivateResource(String filePath, String storageBucket) {
        throw new BusinessException("当前存储不支持应用内私有文件读取");
    }

    /**
     * 是否仅提供临时访问地址；临时地址不得作为持久化文件位置。
     */
    default boolean usesTemporaryUrl() {
        return false;
    }

    /**
     * 获取存储类型
     *
     * @return 存储类型
     */
    StorageType getType();
}
