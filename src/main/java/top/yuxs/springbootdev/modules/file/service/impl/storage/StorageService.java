/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/16
 */

package top.yuxs.springbootdev.modules.file.service.impl.storage;

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
    String upload(MultipartFile file, String path);

    /**
     * 删除文件
     *
     * @param filePath 相对路径/对象Key
     */
    void delete(String filePath);

    /**
     * 构建访问URL
     *
     * @param filePath 相对路径/对象Key
     * @return 完整访问URL
     */
    String buildUrl(String filePath);

    /**
     * 获取存储类型
     *
     * @return 存储类型
     */
    StorageType getType();
}
