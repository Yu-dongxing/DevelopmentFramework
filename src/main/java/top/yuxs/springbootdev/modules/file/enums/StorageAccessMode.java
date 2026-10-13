package top.yuxs.springbootdev.modules.file.enums;

/**
 * 对象存储访问方式；公开策略由部署人员显式配置。
 */
public enum StorageAccessMode {
    /** 私有对象通过授权后的临时签名访问。 */
    PRIVATE,
    /** 公开对象通过固定地址访问。 */
    PUBLIC
}
