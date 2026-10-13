package top.yuxs.springbootdev.modules.file.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.yuxs.springbootdev.core.common.Result;
import top.yuxs.springbootdev.core.enums.ResultCode;
import top.yuxs.springbootdev.core.exception.BusinessException;
import top.yuxs.springbootdev.modules.file.entity.SysFile;
import top.yuxs.springbootdev.modules.file.service.FileContextService;
import top.yuxs.springbootdev.modules.file.service.SysFileService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 通过实际 MVC 映射验证继承 CRUD 入口不会绕过文件生命周期保护。
 */
class SysFileControllerTest {
    private MockMvc mvc;
    private SysFileService files;
    private FileContextService context;

    /**
     * 构建轻量 MVC 环境，保留真实控制器映射及 JSON 绑定。
     */
    @BeforeEach
    void setUp() {
        files = mock(SysFileService.class);
        context = mock(FileContextService.class);
        SysFileController controller = new SysFileController();
        ReflectionTestUtils.setField(controller, "baseService", files);
        ReflectionTestUtils.setField(controller, "fileContextService", context);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new TestAdvice()).build();
    }

    /**
     * 测试只把业务异常转成统一响应，不依赖应用全局拦截器。
     */
    @RestControllerAdvice
    static class TestAdvice {
        /**
         * 返回可断言的拒绝结果。
         */
        @ExceptionHandler(BusinessException.class)
        public Result<?> handle(BusinessException exception) {
            return Result.error(ResultCode.FORBIDDEN, exception.getMessage());
        }
    }

    /**
     * 单条和批量写入口都不能修改桶、路径和归属，不调用底层通用服务。
     */
    @Test
    void shouldRejectForgedMetadataThroughInheritedRoutes() throws Exception {
        String forged = "{\"id\":\"1\",\"storageType\":\"RUSTFS\",\"storageBucket\":\"victim\",\"userId\":\"2\"}";
        mvc.perform(post("/sys/file/save").contentType(MediaType.APPLICATION_JSON).content(forged))
                .andExpect(jsonPath("$.message").value("文件元数据只能通过上传接口创建"));
        mvc.perform(post("/sys/file/saveBatch").contentType(MediaType.APPLICATION_JSON).content("[" + forged + "]"))
                .andExpect(jsonPath("$.message").value("文件元数据只能通过上传接口创建"));
        mvc.perform(put("/sys/file/update").contentType(MediaType.APPLICATION_JSON).content(forged))
                .andExpect(jsonPath("$.message").value("文件元数据不支持通用更新"));
        mvc.perform(put("/sys/file/updateBatch").contentType(MediaType.APPLICATION_JSON).content("[" + forged + "]"))
                .andExpect(jsonPath("$.message").value("文件元数据不支持通用更新"));
        verifyNoInteractions(files, context);
    }

    /**
     * 后台单条删除转入包含归属校验的业务服务。
     */
    @Test
    void shouldDeleteThroughLifecycleService() throws Exception {
        mvc.perform(delete("/sys/file/delete/1"));
        verify(context).delete(1L, false);
        verifyNoInteractions(files);
    }

    /**
     * RustFS 归属过滤必须在数据库分页前添加，避免越权计数和链接泄露。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldScopePageBeforeQuerying() throws Exception {
        IPage<SysFile> page = mock(IPage.class);
        when(page.getRecords()).thenReturn(List.of());
        when(files.page(any(), any(Wrapper.class))).thenReturn(page);
        try (MockedStatic<StpUtil> token = mockStatic(StpUtil.class)) {
            token.when(StpUtil::getLoginIdAsLong).thenReturn(22L);
            mvc.perform(get("/sys/file/page"));
            ArgumentCaptor<Wrapper<SysFile>> query = ArgumentCaptor.forClass(Wrapper.class);
            verify(files).page(any(), query.capture());
            String sql = query.getValue().getSqlSegment();
            assertTrue(sql.contains("storage_type <>"));
            assertTrue(sql.contains("user_id ="));
        }
    }
}
