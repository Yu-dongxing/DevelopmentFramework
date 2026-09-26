package top.yuxs.springbootdev.core.db.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import top.yuxs.springbootdev.SpringbootDevApplication;
import top.yuxs.springbootdev.core.db.enums.DatabaseInitializationStatus;
import top.yuxs.springbootdev.core.db.event.DatabaseInitializedEvent;
import top.yuxs.springbootdev.modules.file.listener.FilePhysicalDeleteListener;
import top.yuxs.springbootdev.modules.file.mapper.SysFileMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 显式启用的本地 MySQL 与 Redis 集成测试，凭据仅通过环境变量提供。
 */
@EnabledIfSystemProperty(named = "db.integration.enabled", matches = "true")
class LocalDatabaseIntegrationTest {
    /**
     * 新建独立临时库，验证首次建表、再次启动、Mapper 与 Redis 连通性，最后清理测试库。
     */
    @Test
    void shouldInitializeAndRestartAgainstLocalServices() throws Exception {
        String database = "codex_init_test_" + UUID.randomUUID().toString().replace("-", "");
        String serverUrl = "jdbc:mysql://127.0.0.1:3306/";
        String options = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        String username = System.getenv("LOCAL_TEST_DB_USERNAME");
        String password = System.getenv("LOCAL_TEST_DB_PASSWORD");
        try (Connection admin = DriverManager.getConnection(serverUrl + options, username, password);
             Statement statement = admin.createStatement()) {
            // 名称由固定前缀和 UUID 生成，清理操作仅作用于本测试创建的库。
            statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4");
            try {
                for (int attempt = 0; attempt < 2; attempt++) {
                    AtomicInteger events = new AtomicInteger();
                    SpringApplication application = new SpringApplication(SpringbootDevApplication.class);
                    application.addListeners(event -> {
                        if (event instanceof DatabaseInitializedEvent) {
                            events.incrementAndGet();
                        }
                    });
                    try (ConfigurableApplicationContext context = application.run(
                            "--server.port=0",
                            "--spring.datasource.url=" + serverUrl + database + options,
                            "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                            "--spring.datasource.username=${LOCAL_TEST_DB_USERNAME}",
                            "--spring.datasource.password=${LOCAL_TEST_DB_PASSWORD}",
                            "--spring.datasource.hikari.maximum-pool-size=3",
                            "--spring.datasource.hikari.minimum-idle=0",
                            "--spring.data.redis.host=127.0.0.1",
                            "--spring.data.redis.port=6379",
                            "--spring.data.redis.username=",
                            "--spring.data.redis.password=",
                            "--db.init.enabled=true",
                            "--db.init.init-data=true",
                            "--db.init.dialect=MYSQL")) {
                        assertThat(context.getBean(DatabaseInitConfig.class).getStatus())
                                .isEqualTo(DatabaseInitializationStatus.READY);
                        assertThat(events.get()).isEqualTo(1);
                        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
                        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_file", Integer.class))
                                .isEqualTo(attempt);
                        if (attempt == 0) {
                            jdbc.update("INSERT INTO sys_file(id, original_name) VALUES (?, ?)", 123L, "启动验证");
                        }
                        assertThat(context.getBean(SysFileMapper.class).selectById(123L)).isNotNull();
                        context.getBean(FilePhysicalDeleteListener.class).retryPendingPhysicalDeletes();
                        try (RedisConnection redis = context.getBean(RedisConnectionFactory.class).getConnection()) {
                            assertThat(redis.ping()).isEqualTo("PONG");
                        }
                    }
                }
            } finally {
                statement.execute("DROP DATABASE `" + database + "`");
            }
        }
    }
}
