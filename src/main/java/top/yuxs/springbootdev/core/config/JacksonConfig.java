/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/11
 */

package top.yuxs.springbootdev.core.config;

import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;

/**
 * Jackson 全局配置
 * 解决 前端 JavaScript 处理 Long 型数据精度丢失（截断）的问题
 * 仅在配置 jackson.long-to-string=true 时生效
 */
@Configuration
@ConditionalOnProperty(prefix = "jackson", name = "long-to-string", havingValue = "true", matchIfMissing = false)
public class JacksonConfig {

    /**
     * 注册到 Spring Boot 4 实际使用的 Jackson 3，统一保护响应中的大整数精度。
     */
    @Bean
    public JacksonModule jacksonModule() {
        SimpleModule module = new SimpleModule();
        // 将 Long 和 BigInteger 类型在序列化时自动转为 String 类型
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        module.addSerializer(BigInteger.class, ToStringSerializer.instance);
        return module;
    }
}
