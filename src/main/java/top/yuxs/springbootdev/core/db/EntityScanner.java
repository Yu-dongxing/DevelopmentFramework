/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/05/10
 */

package top.yuxs.springbootdev.core.db;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import top.yuxs.springbootdev.core.db.config.AegisDbProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体扫描器：负责扫描指定包下带有 @TableName 注解的类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityScanner {

    private final AegisDbProperties properties;

    /**
     * 扫描实体类
     *
     * @return 实体类列表
     */
    public List<Class<?>> scanEntityClasses() {
        String basePackage = properties.getBasePackage();
        if (!StringUtils.hasText(basePackage)) {
            throw new IllegalStateException("数据库实体扫描包不能为空");
        }
        log.info("开始扫描实体类，基础包: {}", basePackage);
        List<Class<?>> entityClasses = new ArrayList<>();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));
        
        for (var beanDef : scanner.findCandidateComponents(basePackage)) {
            try {
                Class<?> entityClass = ClassUtils.forName(beanDef.getBeanClassName(), ClassUtils.getDefaultClassLoader());
                entityClasses.add(entityClass);
            } catch (ClassNotFoundException | LinkageError e) {
                throw new IllegalStateException("数据库实体加载失败: " + beanDef.getBeanClassName(), e);
            }
        }
        if (entityClasses.isEmpty() && !properties.isAllowEmptyScan()) {
            throw new IllegalStateException("数据库实体扫描结果为空，请检查 db.init.base-package: " + basePackage);
        }
        return entityClasses;
    }
}
