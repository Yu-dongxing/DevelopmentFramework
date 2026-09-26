package top.yuxs.springbootdev.core.db.event;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;

/**
 * 自动数据库初始化成功且所属容器刷新完成后的通知。
 * 扩展监听器可直接访问业务数据源；关键启动步骤应放在初始化流程中。
 */
public class DatabaseInitializedEvent extends ApplicationEvent {

    /**
     * 使用所属容器作为事件来源，便于多容器场景识别。
     */
    public DatabaseInitializedEvent(ApplicationContext source) {
        super(source);
    }
}
