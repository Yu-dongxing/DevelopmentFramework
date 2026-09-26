package top.yuxs.springbootdev.core.config;

import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet;
import cn.dev33.satoken.filter.SaFirewallCheckFilterForJakartaServlet;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import top.yuxs.springbootdev.core.common.Result;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用实际 MVC 转换器验证 Long 精度，避免仅测试一个未被 Web 使用的 ObjectMapper。
 */
@SpringBootTest(properties = "jackson.long-to-string=true")
@Import(JacksonConfigTest.PrecisionTestConfiguration.class)
class JacksonConfigTest {

    private static final long LARGE_ID = 9007199254740993L;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    /**
     * 接入应用真实的拦截器和 JSON 转换器。
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new SaTokenContextFilterForJakartaServlet(),
                        new SaFirewallCheckFilterForJakartaServlet(), context.getBean(SaServletFilter.class))
                .build();
    }

    /**
     * 分页、集合及嵌套对象中的包装类型、基本类型和大整数均输出字符串。
     */
    @Test
    void shouldProtectNestedIdsInActualHttpResponse() throws Exception {
        String body = mockMvc.perform(get("/api/common/precision-test"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var json = JsonMapper.builder().build().readTree(body);
        var record = json.get("data").get("records").get(0);
        assertTrue(record.get("id").isString());
        assertEquals(Long.toString(LARGE_ID), record.get("id").asString());
        assertTrue(record.get("primitiveId").isString());
        assertTrue(record.get("largeNumber").isString());
        assertTrue(record.get("nested").get(0).isString());
        assertTrue(json.get("data").get("total").isString());
    }

    /**
     * 前端回传字符串 ID 时仍精确还原 Long，避免反序列化经过浮点数。
     */
    @Test
    void shouldReadStringIdWithoutPrecisionLoss() throws Exception {
        String body = mockMvc.perform(post("/api/common/precision-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"9007199254740993\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(Long.toString(LARGE_ID), JsonMapper.builder().build()
                .readTree(body).get("data").asString());
    }

    /**
     * 关闭配置后不注册精度模块，保留数字输出语义。
     */
    @Test
    void shouldRespectDisabledSwitch() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(JacksonConfig.class)
                .withPropertyValues("jackson.long-to-string=false")
                .run(application -> {
                    assertFalse(application.containsBean("jacksonModule"));
                    assertEquals(Long.toString(LARGE_ID), application.getBean(JsonMapper.class)
                            .writeValueAsString(LARGE_ID));
                });
    }

    /**
     * 仅供 MVC 回归测试使用的响应结构。
     */
    record PrecisionPayload(Long id, long primitiveId, BigInteger largeNumber, List<Long> nested) {
    }

    /**
     * 前端字符串 ID 的入参模型。
     */
    record IdRequest(Long id) {
    }

    /**
     * 测试控制器不进入生产组件扫描。
     */
    @TestConfiguration
    static class PrecisionTestConfiguration {
        /**
         * 注册测试专用接口。
         */
        @Bean
        PrecisionController precisionController() {
            return new PrecisionController();
        }
    }

    /**
     * 通过现有公共路径白名单验证实际 HTTP 编解码。
     */
    @RestController
    @RequestMapping("/api/common/precision-test")
    static class PrecisionController {
        /**
         * 返回包含大整数的分页结构。
         */
        @GetMapping
        public Result<Page<PrecisionPayload>> getPage() {
            Page<PrecisionPayload> page = new Page<>(1, 10, 1);
            page.setRecords(List.of(new PrecisionPayload(LARGE_ID, LARGE_ID,
                    BigInteger.valueOf(LARGE_ID), List.of(LARGE_ID))));
            return Result.success(page);
        }

        /**
         * 回显反序列化后的 Long ID。
         */
        @PostMapping
        public Result<Long> echo(@RequestBody IdRequest request) {
            return Result.success(request.id());
        }
    }
}
