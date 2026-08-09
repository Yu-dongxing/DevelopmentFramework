/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/16
 */

package top.yuxs.springbootdev.modules.file.enums;

import lombok.Getter;

/**
 * 存储类型枚举
 *
 * @author YuDongXing
 * @since 2026/04/16
 */
@Getter
public enum StorageType {
    /**
     * 本地存储
     */
    LOCAL,

    /**
     * 阿里云 OSS
     */
    ALIYUN_OSS,

    /**
     * MinIO
     */
    MINIO;
}
