/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/11
 */

package top.yuxs.springbootdev.core.db.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import top.yuxs.springbootdev.core.db.DatabaseInitService;
import top.yuxs.springbootdev.core.db.EntityScanner;
import top.yuxs.springbootdev.core.db.SchemaExecutor;
import top.yuxs.springbootdev.core.db.SqlGenerator;
import top.yuxs.springbootdev.core.db.TableMetadataParser;
import top.yuxs.springbootdev.core.db.dialect.DialectFactory;
import top.yuxs.springbootdev.core.db.enums.DatabaseInitializationStatus;
import top.yuxs.springbootdev.core.db.event.DatabaseInitializedEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在业务数据源发布前同步初始化数据库，避免任务与建表并发。
 *
 * @author YuDongXing
 * @since 2026/04/11
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataSourceProperties.class)
public class DatabaseInitConfig implements ApplicationListener<ContextRefreshedEvent> {

    private final ApplicationContext applicationContext;
    private final AtomicBoolean eventPublished = new AtomicBoolean();
    private volatile DatabaseInitializationStatus status = DatabaseInitializationStatus.INITIALIZING;

    /**
     * 保存所属容器，避免子容器刷新导致重复通知。
     */
    public DatabaseInitConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 先绑定连接池配置并完成初始化，再向业务组件发布数据源。
     * 初始化专用对象不注册为 Bean，避免依赖公共 JdbcTemplate 形成循环。
     */
    @Bean(destroyMethod = "close")
    @Lazy(false)
    public HikariDataSource dataSource(DataSourceProperties dataSourceProperties, Environment environment,
                                      AegisDbProperties properties, EntityScanner scanner,
                                      TableMetadataParser parser, SqlGenerator generator, DialectFactory dialectFactory,
                                      ObjectProvider<JdbcConnectionDetails> connectionDetailsProvider) {
        HikariDataSource dataSource = null;
        try {
            if (dataSourceProperties.getType() != null
                    && !HikariDataSource.class.equals(dataSourceProperties.getType())) {
                throw new IllegalStateException("数据库初始化通道目前仅支持 HikariDataSource");
            }
            dataSource = new HikariDataSource();
            if (StringUtils.hasText(dataSourceProperties.getName())) {
                dataSource.setPoolName(dataSourceProperties.getName());
            }
            Binder.get(environment).bind("spring.datasource.hikari", Bindable.ofInstance(dataSource));
            configureConnection(dataSource, dataSourceProperties, connectionDetailsProvider.getIfAvailable());
            if (properties.isEnabled()) {
                SchemaExecutor executor = new SchemaExecutor(new JdbcTemplate(dataSource), dialectFactory);
                DatabaseInitService initializer = new DatabaseInitService(scanner, parser, generator, executor, properties);
                initializer.initDatabase();
                status = DatabaseInitializationStatus.READY;
            } else {
                // 外部迁移或测试脚本提供结构时，不发布自动建表成功事件。
                status = DatabaseInitializationStatus.SKIPPED;
            }
            return dataSource;
        } catch (RuntimeException | Error failure) {
            status = DatabaseInitializationStatus.FAILED;
            if (dataSource != null) {
                try {
                    dataSource.close();
                } catch (RuntimeException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw failure;
        }
    }

    /**
     * 外部连接信息优先于配置文件；无外部信息时保留 Hikari 专属配置的覆盖能力。
     * 数据源类与驱动类互斥，必须在第一次获取连接前完成全部设置。
     */
    private void configureConnection(HikariDataSource dataSource, DataSourceProperties properties,
                                     JdbcConnectionDetails details) {
        boolean useDataSourceClass = StringUtils.hasText(dataSource.getDataSourceClassName());
        if (details != null) {
            dataSource.setJdbcUrl(details.getJdbcUrl());
            dataSource.setUsername(details.getUsername());
            dataSource.setPassword(details.getPassword());
            if (!useDataSourceClass) {
                dataSource.setDriverClassName(details.getDriverClassName());
            }
        } else {
            if (!useDataSourceClass && !StringUtils.hasText(dataSource.getJdbcUrl())) {
                dataSource.setJdbcUrl(properties.determineUrl());
            }
            if (!useDataSourceClass && !StringUtils.hasText(dataSource.getDriverClassName())) {
                dataSource.setDriverClassName(properties.determineDriverClassName());
            }
            if (dataSource.getUsername() == null) {
                dataSource.setUsername(properties.determineUsername());
            }
            if (dataSource.getPassword() == null) {
                dataSource.setPassword(properties.determinePassword());
            }
        }
        if (useDataSourceClass) {
            dataSource.setDriverClassName(null);
            if (details != null) {
                throw new IllegalStateException("外部 JDBC 连接信息不能与 Hikari 数据源类配置同时使用，请移除 data-source-class-name");
            }
        }
    }

    /**
     * 在监听器注册完成后通知扩展功能，事件不承担数据源解锁职责。
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext() == applicationContext
                && status == DatabaseInitializationStatus.READY
                && eventPublished.compareAndSet(false, true)) {
            applicationContext.publishEvent(new DatabaseInitializedEvent(applicationContext));
        }
    }

    /**
     * 返回当前容器的数据库初始化状态。
     */
    public DatabaseInitializationStatus getStatus() {
        return status;
    }
}
