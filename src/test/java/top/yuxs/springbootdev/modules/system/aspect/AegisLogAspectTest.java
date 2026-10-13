package top.yuxs.springbootdev.modules.system.aspect;

import cn.dev33.satoken.stp.StpUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;
import top.yuxs.springbootdev.core.common.Result;
import top.yuxs.springbootdev.modules.file.controller.FileController;
import top.yuxs.springbootdev.modules.file.controller.SysFileController;
import top.yuxs.springbootdev.modules.file.entity.SysFile;
import top.yuxs.springbootdev.modules.system.event.AegisLogEvent;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证包含预签名链接的真实控制器方法仍审计调用，但不保存响应凭证。
 */
class AegisLogAspectTest {
    /**
     * 枚举上传、详情和分页三种签名响应入口。
     */
    static Stream<Method> signedResponseMethods() throws Exception {
        return Stream.of(
                FileController.class.getMethod("upload", MultipartFile.class, String.class, String.class, String.class),
                FileController.class.getMethod("getInfo", Long.class),
                SysFileController.class.getMethod("page", long.class, long.class, SysFile.class));
    }

    /**
     * 执行真实日志切面并检查发布事件，确保关闭正文记录不影响成功响应和审计状态。
     */
    @ParameterizedTest
    @MethodSource("signedResponseMethods")
    void shouldNotPersistSignedResponse(Method method) throws Throwable {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[0]);
        when(point.getArgs()).thenReturn(new Object[0]);
        when(point.getTarget()).thenReturn(method.getDeclaringClass().getDeclaredConstructor().newInstance());
        SysFile file = new SysFile();
        file.setFileUrl("https://files.example.com/bucket/a?X-Amz-Signature=secret");
        Result<SysFile> response = Result.success(file);
        when(point.proceed()).thenReturn(response);
        try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
            assertSame(response, new AegisLogAspect(publisher).around(point));
        }
        ArgumentCaptor<AegisLogEvent> event = ArgumentCaptor.forClass(AegisLogEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertNull(event.getValue().getSysLog().getResult());
        assertEquals(1, event.getValue().getSysLog().getStatus());
    }
}
