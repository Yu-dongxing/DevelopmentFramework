/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/11
 */

package top.yuxs.springbootdev.core.db.enums;

/**
 * 外键级联操作类型
 */
public enum ForeignKeyAction {
    /**
     * 级联：主表记录删除/更新时，从表的相关记录也随之删除/更新
     */
    CASCADE,
    /**
     * 置空：主表记录删除/更新时，从表的相关记录外键字段设置为NULL (要求该字段允许为NULL)
     */
    SET_NULL,
    /**
     * 限制：如果从表存在相关记录，则不允许删除/更新主表记录 (默认行为)
     */
    RESTRICT,
    /**
     * 无操作：同 RESTRICT
     */
    NO_ACTION
}
