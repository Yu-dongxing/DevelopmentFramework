/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/16
 */

package top.yuxs.springbootdev.modules.file.service.impl.storage;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.yuxs.springbootdev.modules.file.config.FileProperties;
import top.yuxs.springbootdev.modules.file.enums.StorageType;
import top.yuxs.springbootdev.core.exception.BusinessException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 存储服务工厂
 *
 * @author YuDongXing
 * @since 2026/04/16
 */
@Component
public class StorageFactory {

    @Autowired
    private FileProperties fileProperties;

    public FileProperties getFileProperties() {
        return fileProperties;
    }

    @Autowired
    private List<StorageService> storageServices;

    private final Map<StorageType, StorageService> SERVICE_MAP = new HashMap<>();

    @PostConstruct
    public void init() {
        for (StorageService service : storageServices) {
            SERVICE_MAP.put(service.getType(), service);
        }
    }

    /**
     * 获取当前启用的存储服务
     */
    public StorageService getActiveService() {
        StorageType activeType = fileProperties.getActive();
        StorageService service = SERVICE_MAP.get(activeType);
        if (service == null) {
            throw new BusinessException("未找到对应的存储服务实现: " + activeType);
        }
        return service;
    }

    /**
     * 根据类型获取存储服务
     */
    public StorageService getService(StorageType type) {
        StorageService service = SERVICE_MAP.get(type);
        if (service == null) {
            throw new BusinessException("未找到对应的存储服务实现: " + type);
        }
        return service;
    }
}
