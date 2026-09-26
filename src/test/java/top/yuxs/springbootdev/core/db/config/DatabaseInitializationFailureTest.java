package top.yuxs.springbootdev.core.db.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import top.yuxs.springbootdev.core.db.EntityScanner;
import top.yuxs.springbootdev.core.db.SqlGenerator;
import top.yuxs.springbootdev.core.db.TableMetadataParser;
import top.yuxs.springbootdev.core.db.dialect.DialectFactory;
import top.yuxs.springbootdev.core.db.dialect.impl.H2Dialect;
import top.yuxs.springbootdev.core.db.enums.DatabaseInitializationStatus;
import top.yuxs.springbootdev.core.db.metadata.TableMetadata;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * 在真实 H2 建表后验证默认数据成功与失败路径，以及异常资源释放。
 */
class DatabaseInitializationFailureTest {

    /**
     * 默认数据必须在数据源返回之前完成写入。
     */
    @Test
    void shouldInsertDefaultDataBeforeReturningDataSource() {
        AtomicReference<HikariDataSource> pool = new AtomicReference<>();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            DatabaseInitConfig config = new DatabaseInitConfig(context);
            try (HikariDataSource dataSource = initialize(config, new SqlGenerator(), pool)) {
                assertThat(config.getStatus()).isEqualTo(DatabaseInitializationStatus.READY);
                assertThat(new JdbcTemplate(dataSource).queryForObject(
                        "SELECT COUNT(*) FROM sys_file WHERE id = 123", Integer.class)).isEqualTo(1);
            }
        }
    }

    /**
     * 在连接池已经启动后制造默认数据 SQL 失败，确认失败状态且连接池关闭。
     */
    @Test
    void shouldCloseStartedPoolOnDefaultDataFailure() {
        AtomicReference<HikariDataSource> pool = new AtomicReference<>();
        SqlGenerator generator = spy(new SqlGenerator());
        doReturn("INSERT INTO missing_table(id) VALUES (?)")
                .when(generator).generateInsertDefaultDataSql(any(), eq("sys_file"), any());
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            DatabaseInitConfig config = new DatabaseInitConfig(context);
            assertThatThrownBy(() -> initialize(config, generator, pool))
                    .isInstanceOf(RuntimeException.class);
            assertThat(config.getStatus()).isEqualTo(DatabaseInitializationStatus.FAILED);
            assertThat(pool.get()).isNotNull();
            assertThat(pool.get().isClosed()).isTrue();
        }
    }

    /**
     * 注入测试默认数据并记录私有初始化通道的连接池，复用生产建表逻辑。
     */
    private HikariDataSource initialize(DatabaseInitConfig config, SqlGenerator generator,
                                       AtomicReference<HikariDataSource> pool) {
        DataSourceProperties connection = new DataSourceProperties();
        connection.setUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        connection.setDriverClassName("org.h2.Driver");
        connection.setUsername("sa");
        connection.setPassword("");
        AegisDbProperties properties = new AegisDbProperties();
        TableMetadataParser parser = new TableMetadataParser() {
            /** 在现有实体结构上提供默认数据，避免另建测试实体影响全局扫描。 */
            @Override
            public TableMetadata parse(Class<?> entityClass) {
                TableMetadata metadata = super.parse(entityClass);
                if ("sys_file".equals(metadata.getTableName())) {
                    metadata.setDefaultData(Map.of("id", 123L));
                }
                return metadata;
            }
        };
        DialectFactory factory = mock(DialectFactory.class);
        when(factory.getDialect(any())).thenAnswer(invocation -> {
            JdbcTemplate jdbc = invocation.getArgument(0);
            pool.set((HikariDataSource) jdbc.getDataSource());
            return new H2Dialect();
        });
        return config.dataSource(connection, new MockEnvironment(), properties,
                new EntityScanner(properties), parser, generator, factory,
                new StaticListableBeanFactory().getBeanProvider(JdbcConnectionDetails.class));
    }
}
