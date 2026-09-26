package top.yuxs.springbootdev.core.db.enums;

/**
 * 当前容器的数据库初始化状态。
 */
public enum DatabaseInitializationStatus {
    /** 正在准备连接池或初始化数据库。 */
    INITIALIZING,
    /** 自动初始化成功，业务数据源可以发布。 */
    READY,
    /** 初始化失败，应用启动终止。 */
    FAILED,
    /** 已禁用自动初始化，由外部提供数据库结构。 */
    SKIPPED
}
