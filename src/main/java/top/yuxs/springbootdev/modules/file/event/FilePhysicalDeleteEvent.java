package top.yuxs.springbootdev.modules.file.event;

import org.springframework.context.ApplicationEvent;
import top.yuxs.springbootdev.modules.file.entity.SysFile;

/**
 * 文件元数据事务提交后触发的物理删除事件。
 */
public class FilePhysicalDeleteEvent extends ApplicationEvent {

    private final SysFile sysFile;

    public FilePhysicalDeleteEvent(Object source, SysFile sysFile) {
        super(source);
        this.sysFile = sysFile;
    }

    public SysFile getSysFile() {
        return sysFile;
    }
}
