/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/11
 */

package top.yuxs.springbootdev.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.yuxs.springbootdev.modules.file.config.FileProperties;

import java.io.File;

/**
 * 实现 WebMvcConfigurer 接口，将请求路径（URL）映射到物理磁盘路径
 */
@Configuration
public class MyWebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private FileProperties fileProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        /*
          addResourceHandler: 浏览器访问的虚拟路径
          addResourceLocations: 磁盘实际的物理路径
          注意：物理路径必须以 "file:" 开头，且建议为绝对路径
         */
        String uploadPath = fileProperties.getLocal().getUploadPath();
        String accessPath = fileProperties.getLocal().getAccessPath();
        
        // 确保路径以 / 结尾
        if (!uploadPath.endsWith(File.separator) && !uploadPath.endsWith("/")) {
            uploadPath += File.separator;
        }

        // 获取绝对路径并转换为标准的 file 协议格式
        File file = new File(uploadPath);
        String absolutePath = file.getAbsolutePath();
        
        // 统一处理 Windows 和 Linux 路径格式，确保以 file:/// 开头
        String protocolPrefix = "file:";
        if (!absolutePath.startsWith("/")) {
            protocolPrefix = "file:/";
        }
        
        String resourceLocation = protocolPrefix + absolutePath.replace("\\", "/");
        if (!resourceLocation.endsWith("/")) {
            resourceLocation += "/";
        }

        registry.addResourceHandler(accessPath)
                .addResourceLocations(resourceLocation);
    }
}