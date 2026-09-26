package top.yuxs.springbootdev.core.db;

import org.junit.jupiter.api.Test;
import top.yuxs.springbootdev.core.db.config.AegisDbProperties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证实体加载失败不会被静默跳过。
 */
class EntityScannerTest {
    /**
     * 保留类路径资源扫描能力，仅模拟实体类加载失败。
     */
    @Test
    void shouldFailWhenDiscoveredEntityCannotBeLoaded() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader failing = new ClassLoader(original) {
            /** 模拟可发现元数据但无法加载实体的环境。 */
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.equals("top.yuxs.springbootdev.modules.file.entity.SysFile")) {
                    throw new ClassNotFoundException("测试实体加载失败");
                }
                return super.loadClass(name);
            }
        };
        AegisDbProperties properties = new AegisDbProperties();
        properties.setBasePackage("top.yuxs.springbootdev.modules.file.entity");
        try {
            Thread.currentThread().setContextClassLoader(failing);
            assertThatThrownBy(() -> new EntityScanner(properties).scanEntityClasses())
                    .isInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(ClassNotFoundException.class);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
}
