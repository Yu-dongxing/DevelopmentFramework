package top.yuxs.springbootdev.core.db.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import top.yuxs.springbootdev.core.db.EntityScanner;
import top.yuxs.springbootdev.core.db.SchemaExecutor;
import top.yuxs.springbootdev.core.db.SqlGenerator;
import top.yuxs.springbootdev.core.db.TableMetadataParser;
import top.yuxs.springbootdev.core.db.dialect.DialectFactory;
import top.yuxs.springbootdev.core.db.dialect.impl.H2Dialect;
import top.yuxs.springbootdev.core.db.enums.DatabaseInitializationStatus;
import top.yuxs.springbootdev.core.db.event.DatabaseInitializedEvent;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 使用真正的空库验证数据源发布顺序、完成通知和失败行为。
 */
class DatabaseInitConfigTest {

    /**
     * 每次创建独立空库，不依赖测试环境的预建表脚本。
     */
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(Infrastructure.class)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa",
                        "spring.datasource.hikari.maximum-pool-size=2",
                        "spring.datasource.hikari.minimum-idle=0",
                        "db.init.init-data=false");
    }

    /**
     * 外部连接信息必须覆盖属性和 Hikari 专属配置，避免在错误数据库执行 DDL。
     */
    @Test
    void shouldPreferConnectionDetails() {
        String expectedUrl = "jdbc:h2:mem:details_" + UUID.randomUUID();
        runner().withPropertyValues("spring.datasource.hikari.jdbc-url=jdbc:h2:mem:wrong_database")
                .withBean(JdbcConnectionDetails.class, () -> new JdbcConnectionDetails() {
                    /** 返回外部指定的数据库地址。 */
                    @Override
                    public String getJdbcUrl() {
                        return expectedUrl;
                    }

                    /** 返回外部指定的账号。 */
                    @Override
                    public String getUsername() {
                        return "sa";
                    }

                    /** 返回测试库的空密码。 */
                    @Override
                    public String getPassword() {
                        return "";
                    }
                }).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(HikariDataSource.class).getJdbcUrl()).isEqualTo(expectedUrl);
                });
    }

    /**
     * 全局懒加载不能延后建表或漏发事件。
     */
    @Test
    void shouldInitializeEagerlyWithGlobalLazyInitialization() {
        AtomicInteger events = new AtomicInteger();
        runner().withInitializer(context -> {
            context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
            context.addApplicationListener(event -> {
                if (event instanceof DatabaseInitializedEvent) {
                    events.incrementAndGet();
                }
            });
        }).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DatabaseInitConfig.class).getStatus()).isEqualTo(DatabaseInitializationStatus.READY);
            assertThat(events.get()).isEqualTo(1);
        });
    }

    /**
     * 数据源类模式不应同时设置驱动类，并使用其专属连接参数。
     */
    @Test
    void shouldSupportHikariDataSourceClass() {
        runner().withPropertyValues(
                "spring.datasource.hikari.data-source-class-name=org.h2.jdbcx.JdbcDataSource",
                "spring.datasource.hikari.data-source-properties.URL=jdbc:h2:mem:class_" + UUID.randomUUID())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(HikariDataSource.class).getDriverClassName()).isNull();
                    assertThat(context.getBean(DatabaseInitConfig.class).getStatus()).isEqualTo(DatabaseInitializationStatus.READY);
                });
    }

    /**
     * 错误包名和空白包名必须阻止启动，只有显式允许空扫描才能跳过。
     */
    @Test
    void shouldRejectEmptyScanUnlessExplicitlyAllowed() {
        runner().withPropertyValues("db.init.base-package=invalid.no.entities").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "数据库实体扫描结果为空，请检查 db.init.base-package: invalid.no.entities");
        });
        runner().withPropertyValues("db.init.base-package=").run(context -> {
            assertThat(context).hasFailed();
        });
        runner().withPropertyValues("db.init.base-package=invalid.no.entities", "db.init.allow-empty-scan=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                });
    }

    /**
     * 早期初始化回调、零延迟定时任务和事件监听器都应直接读取已建好的表。
     */
    @Test
    void shouldInitializeBeforeConsumersAndNotifyOnce() {
        runner().withUserConfiguration(Consumers.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DatabaseInitConfig.class).getStatus())
                    .isEqualTo(DatabaseInitializationStatus.READY);
            Probe probe = context.getBean(Probe.class);
            assertThat(probe.initialCount).isZero();
            assertThat(probe.scheduled.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(probe.events.get()).isEqualTo(1);
            assertThat(context.getBean(AsyncProbe.class).getCompleted().await(5, TimeUnit.SECONDS)).isTrue();
            context.publishEvent(new ContextRefreshedEvent(context.getSourceApplicationContext()));
            assertThat(probe.events.get()).isEqualTo(1);
            assertThat(context.getBean(HikariDataSource.class).getMaximumPoolSize()).isEqualTo(2);
        });
    }

    /**
     * 关闭初始化时允许访问数据库，但不创建业务表且不发出成功事件。
     */
    @Test
    void shouldSkipInitializationWithoutSuccessEvent() {
        AtomicInteger events = new AtomicInteger();
        runner().withPropertyValues("db.init.enabled=false")
                .withInitializer(context -> context.addApplicationListener(event -> {
                    if (event instanceof DatabaseInitializedEvent) {
                        events.incrementAndGet();
                    }
                })).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(DatabaseInitConfig.class).getStatus())
                            .isEqualTo(DatabaseInitializationStatus.SKIPPED);
                    JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
                    assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
                    assertThatThrownBy(() -> jdbc.queryForObject("SELECT COUNT(*) FROM sys_file", Integer.class))
                            .isInstanceOf(DataAccessException.class);
                    assertThat(events.get()).isZero();
                });
    }

    /**
     * 初始化失败必须阻止业务 Bean 创建，不能发送成功通知。
     */
    @Test
    void shouldFailStartupWhenInitializationFails() {
        AtomicInteger events = new AtomicInteger();
        runner().withUserConfiguration(Consumers.class)
                .withPropertyValues("db.init.dialect=INVALID")
                .withInitializer(context -> context.addApplicationListener(event -> {
                    if (event instanceof DatabaseInitializedEvent) {
                        events.incrementAndGet();
                    }
                })).run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                    assertThat(events.get()).isZero();
                });
    }

    /**
     * 检查默认数据时发生数据库异常，必须传播异常而不是报告表非空。
     */
    @Test
    void shouldPropagateEmptyTableCheckFailureAndClosePool() {
        HikariDataSource[] pool = new HikariDataSource[1];
        runner().run(context -> {
            pool[0] = context.getBean(HikariDataSource.class);
            SchemaExecutor executor = new SchemaExecutor(new JdbcTemplate(pool[0]), context.getBean(DialectFactory.class));
            assertThatThrownBy(() -> executor.isTableEmpty("missing_table"))
                    .isInstanceOf(DataAccessException.class);
        });
        assertThat(pool[0].isClosed()).isTrue();
    }

    /**
     * 仅加载建表依赖，避免业务应用配置或预建表脚本掩盖时序问题。
     */
    @Configuration(proxyBeanMethods = false)
    @Import({DatabaseInitConfig.class, AegisDbProperties.class, EntityScanner.class,
            TableMetadataParser.class, SqlGenerator.class, DialectFactory.class, H2Dialect.class})
    static class Infrastructure {
    }

    /**
     * 模拟无需修改的既有任务和初始化时访问数据库的业务对象。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @EnableAsync
    static class Consumers {
        /** 提供可关闭的虚拟线程执行器，模拟业务异步监听环境。 */
        @Bean(destroyMethod = "close")
        SimpleAsyncTaskExecutor taskExecutor() {
            SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("初始化测试-");
            executor.setVirtualThreads(true);
            return executor;
        }

        /** 创建异步数据库访问监听器。 */
        @Bean
        AsyncProbe asyncProbe(DataSource dataSource) {
            return new AsyncProbe(new JdbcTemplate(dataSource));
        }

        /**
         * 通过标准数据源注入建立启动依赖。
         */
        @Bean
        Probe probe(DataSource dataSource) {
            return new Probe(new JdbcTemplate(dataSource));
        }
    }

    /** 验证完成通知中的异步数据库操作不发生循环等待。 */
    static class AsyncProbe {
        private final JdbcTemplate jdbc;
        private final CountDownLatch completed = new CountDownLatch(1);

        /** 保存测试数据库访问入口。 */
        AsyncProbe(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        /** 在虚拟线程中读取已完成初始化的表。 */
        @Async("taskExecutor")
        @EventListener
        public void initialized(DatabaseInitializedEvent event) {
            jdbc.queryForObject("SELECT COUNT(*) FROM sys_file", Integer.class);
            completed.countDown();
        }

        /** 通过代理方法访问目标对象的完成信号。 */
        public CountDownLatch getCompleted() {
            return completed;
        }
    }

    /**
     * 从多个生命周期入口查询数据库，任一入口过早执行都会导致测试失败。
     */
    static class Probe {
        private final JdbcTemplate jdbc;
        private final CountDownLatch scheduled = new CountDownLatch(1);
        private final AtomicInteger events = new AtomicInteger();
        private int initialCount;

        /**
         * 保存标准业务 JDBC 访问入口。
         */
        Probe(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        /**
         * 在容器刷新之前验证业务表已经存在。
         */
        @PostConstruct
        void initialize() {
            initialCount = jdbc.queryForObject("SELECT COUNT(*) FROM sys_file", Integer.class);
        }

        /**
         * 首次调度无需延迟，也无需主动等待初始化事件。
         */
        @Scheduled(fixedDelay = 60000)
        void scheduledQuery() {
            jdbc.queryForObject("SELECT COUNT(*) FROM sys_file", Integer.class);
            scheduled.countDown();
        }

        /**
         * 完成通知到达时可以直接进行扩展数据库操作。
         */
        @EventListener
        void initialized(DatabaseInitializedEvent event) {
            jdbc.queryForObject("SELECT COUNT(*) FROM sys_file", Integer.class);
            events.incrementAndGet();
        }
    }
}
