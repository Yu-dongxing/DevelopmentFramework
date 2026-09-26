package top.yuxs.springbootdev.modules.file.config;

import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet;
import cn.dev33.satoken.filter.SaFirewallCheckFilterForJakartaServlet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import top.yuxs.springbootdev.modules.file.entity.SysFile;
import top.yuxs.springbootdev.modules.file.service.SysFileService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通过实际资源处理器验证逻辑删除后的 URL 访问控制。
 */
@SpringBootTest(properties = {
        "db.init.enabled=true", "db.init.init-data=false",
        "spring.datasource.url=jdbc:h2:mem:file_resource;DB_CLOSE_DELAY=-1",
        "file.local.upload-path=./target/file-resource-test/"
})
class FileWebMvcConfigTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SysFileService fileService;

    private MockMvc mockMvc;
    private Path diskFile;
    private String filename;
    private Long fileId;

    /**
     * 创建独立文件，避免影响用户已有上传目录。
     */
    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new SaTokenContextFilterForJakartaServlet(),
                        new SaFirewallCheckFilterForJakartaServlet(), context.getBean(SaServletFilter.class))
                .build();
        filename = UUID.randomUUID() + ".txt";
        diskFile = Path.of("target", "file-resource-test", filename).toAbsolutePath();
        Files.createDirectories(diskFile.getParent());
        Files.writeString(diskFile, "测试文件");
    }

    /**
     * 只移除本用例创建的文件和数据库记录。
     */
    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(diskFile);
        if (fileId != null) {
            fileService.physicalDeleteById(fileId);
        }
    }

    /**
     * 正常文件可以访问，逻辑删除后同一路径返回 404，物理文件仍保留。
     */
    @Test
    void shouldStopServingLogicallyDeletedFile() throws Exception {
        SysFile file = new SysFile();
        file.setStorageType("LOCAL");
        file.setStorageBucket("local");
        file.setFilePath(filename);
        file.setUploadStatus(1);
        fileService.save(file);
        fileId = file.getId();
        mockMvc.perform(get("/uploads/" + filename))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        fileService.removeById(fileId);
        mockMvc.perform(get("/uploads/" + filename)).andExpect(status().isNotFound());
        assertTrue(Files.exists(diskFile));
    }

    /**
     * 没有已提交元数据的孤立文件不能通过静态路径公开读取。
     */
    @Test
    void shouldRejectUntrackedFile() throws Exception {
        mockMvc.perform(get("/uploads/" + filename)).andExpect(status().isNotFound());
    }
}
