/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/16
 */

package top.yuxs.springbootdev.modules.file.storage;

import org.springframework.web.multipart.MultipartFile;
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
     * 获取存储类型
     *
     * @return 存储类型
     */
    StorageType getType();
}
