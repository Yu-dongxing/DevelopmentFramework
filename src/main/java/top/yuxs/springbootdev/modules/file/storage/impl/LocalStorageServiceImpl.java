/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/16
 */

package top.yuxs.springbootdev.modules.file.storage.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.yuxs.springbootdev.modules.file.service.FileUploadCompensationService;
import top.yuxs.springbootdev.modules.file.config.FileProperties;
import top.yuxs.springbootdev.modules.file.enums.StorageType;
import top.yuxs.springbootdev.core.exception.BusinessException;
import top.yuxs.springbootdev.modules.file.storage.StorageService;
import top.yuxs.springbootdev.modules.file.storage.StorageUploadResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.security.DigestInputStream;
import java.security.MessageDigest;

/**
 * 本地存储服务实现
 *
 * @author YuDongXing
 * @since 2026/04/16
 */
@Slf4j
@Service
public class LocalStorageServiceImpl implements StorageService {

    @Autowired
    private FileProperties fileProperties;

    @Autowired
    private FileUploadCompensationService compensationService;

    @Override
    public StorageUploadResult upload(MultipartFile file, String path) {
        String createdFilePath = null;
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            // 后缀黑名单校验 (仅在配置为本地存储时校验，防范 RCE 和 存储型 XSS)
            String cleanExt = extension.startsWith(".") ? extension.substring(1) : extension;
            if (isBlacklistedExtension(cleanExt)) {
                throw new BusinessException("本地存储安全策略拦截：禁止上传含有敏感后缀的文件！");
            }

            // 深度防伪校验 (魔数 Magic Number 校验，防范恶意脚本/网页伪装成图片上传)
            try (java.io.InputStream is = file.getInputStream()) {
                String magicType = cn.hutool.core.io.FileTypeUtil.getType(is);
                if (magicType != null && isBlacklistedExtension(magicType)) {
                    log.warn("检测到恶意文件伪装！原始文件名: {}, 真实物理文件类型(魔数): {}", originalFilename, magicType);
                    throw new BusinessException("本地存储安全策略拦截：上传文件的真实物理类型已被系统禁止！");
                }
            }

            String fileName = UUID.randomUUID().toString() + extension;
            
            // 获取当前年月
            java.time.LocalDate now = java.time.LocalDate.now();
            String year = String.valueOf(now.getYear());
            String month = String.format("%02d", now.getMonthValue());
            
            // 构造完整的存储相对路径: path/yyyy/MM
            String relativeDir = Paths.get(path, year, month).toString().replace("\\", "/");
            
            // 构造物理路径
            String uploadPath = fileProperties.getLocal().getUploadPath();
            Path rootPath = Paths.get(uploadPath).toAbsolutePath().normalize();
            Path directory = Paths.get(uploadPath, relativeDir).toAbsolutePath().normalize();
            
            // 校验根路径，防范路径穿越越界
            if (!directory.startsWith(rootPath)) {
                throw new BusinessException("非法上传路径，禁止越界！");
            }
            
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }
            
            Path targetPath = directory.resolve(fileName).toAbsolutePath().normalize();
            if (!targetPath.startsWith(rootPath)) {
                throw new BusinessException("非法上传路径，禁止越界！");
            }
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            try (InputStream inputStream = file.getInputStream();
                 DigestInputStream digestInputStream = new DigestInputStream(inputStream, messageDigest);
                 OutputStream outputStream = Files.newOutputStream(targetPath,
                         StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                // 仅清理本次成功创建的文件，路径碰撞时不能删除已有文件。
                createdFilePath = Paths.get(relativeDir, fileName).toString().replace("\\", "/");
                digestInputStream.transferTo(outputStream);
            }
            String filePath = Paths.get(relativeDir, fileName).toString().replace("\\", "/");
            return new StorageUploadResult(filePath, "local", toHex(messageDigest.digest()));
        } catch (Exception e) {
            log.error("本地文件上传失败", e);
            if (createdFilePath != null) {
                compensationService.compensate(this, new StorageUploadResult(createdFilePath, "local", null));
            }
            throw new BusinessException("文件上传失败");
        }
    }

    @Override
    public void delete(String filePath) {
        String uploadPath = fileProperties.getLocal().getUploadPath();
        Path rootPath = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path targetPath = rootPath.resolve(filePath).toAbsolutePath().normalize();
        
        // 校验根路径，防范越界物理删除
        if (!targetPath.startsWith(rootPath)) {
            log.warn("非法删除路径拦截，目标路径不在上传根目录内: {}", filePath);
            throw new BusinessException("非法删除路径拦截，禁止物理删除根路径之外的文件");
        }
        
        try {
            if (Files.deleteIfExists(targetPath)) {
                log.info("本地文件删除成功: {}", targetPath);
                // 递归清理空文件夹
                cleanEmptyParentDirectories(targetPath.getParent(), rootPath);
            }
        } catch (IOException e) {
            log.error("本地文件物理删除失败: {}", filePath, e);
            throw new BusinessException("物理文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 递归清理空文件夹，直到根目录
     */
    private void cleanEmptyParentDirectories(Path directory, Path rootPath) {
        try {
            // 确保不删根目录，且路径在根目录之内
            while (directory != null && !directory.equals(rootPath) && directory.startsWith(rootPath)) {
                if (Files.exists(directory) && Files.isDirectory(directory)) {
                    try (java.util.stream.Stream<Path> stream = Files.list(directory)) {
                        if (!stream.findAny().isPresent()) {
                            Files.delete(directory);
                            log.info("清理空文件夹: {}", directory);
                            directory = directory.getParent();
                        } else {
                            // 文件夹不为空，停止递归
                            break;
                        }
                    }
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            log.error("清理空文件夹时出错", e);
        }
    }

    @Override
    public String buildUrl(String filePath) {
        String domain = fileProperties.getLocal().getDomain();
        String accessPath = fileProperties.getLocal().getAccessPath();
        // 去掉末尾的 **
        String prefix = accessPath.replace("/**", "");
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        
        String cleanFilePath = filePath;
        if (cleanFilePath.startsWith("/")) {
            cleanFilePath = cleanFilePath.substring(1);
        }
        
        return domain + prefix + cleanFilePath;
    }

    @Override
    public StorageType getType() {
        return StorageType.LOCAL;
    }

    private boolean isBlacklistedExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        // 包含常见的动态脚本 (防 RCE) 及静态超文本 (防 Stored XSS)
        java.util.Set<String> blacklist = java.util.Set.of(
                "jsp", "jspx", "properties", "yml", "yaml", "asp", "aspx", "sh", "exe", "bat", "cmd", "php", "html", "htm"
        );
        return blacklist.contains(extension.toLowerCase());
    }

    /**
     * 将摘要字节转换为小写十六进制字符串。
     */
    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
