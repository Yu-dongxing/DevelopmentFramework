/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/05/10
 */

package top.yuxs.springbootdev.modules.file.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import top.yuxs.springbootdev.modules.file.event.FileUploadedEvent;

/**
 * 文件上传事务提交后的旁路通知监听器。
 */
@Slf4j
@Component
public class FileUploadedListener {

    /**
     * 异步处理已提交的上传通知，文件元数据由主服务保存。
     */
    @Async("taskExecutor")
    @EventListener
    public void onFileUploaded(FileUploadedEvent event) {
        log.info("监听到文件上传完成事件，已由主服务同步落库完毕: {}", event.getSysFile().getFileName());
    }
}
