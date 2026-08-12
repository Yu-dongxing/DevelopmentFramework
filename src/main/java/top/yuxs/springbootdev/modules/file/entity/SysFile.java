/*
 * Copyright © 2026 YuDongXing. All rights reserved.
 *
 * @author YuDongXing
 * @since 2026/04/16
 */

package top.yuxs.springbootdev.modules.file.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.yuxs.springbootdev.core.db.annotation.*;
import top.yuxs.springbootdev.core.common.BaseEntity;

import java.util.Map;

/**
 * 文件信息表
 *
 * @author YuDongXing
 * @since 2026/04/16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file")
@TableComment("文件信息表")
@Index(name = "idx_sys_file_biz_id", columns = {"biz_id"})
@Index(name = "idx_sys_file_md5", columns = {"md5"})
public class SysFile extends BaseEntity {

    /**
     * 业务关联ID
     */
    @TableField("biz_id")
    @ColumnComment("业务关联ID")
    private String bizId;

    /**
     * 业务类型
     */
    @TableField("biz_type")
    @ColumnComment("业务类型")
    private String bizType;

    /**
     * 原始文件名
     */
    @TableField("original_name")
    @ColumnComment("原始文件名")
    private String originalName;

    /**
     * 存储文件名
     */
    @TableField("file_name")
    @ColumnComment("存储文件名")
    private String fileName;

    /**
     * 后缀名
     */
    @TableField("file_ext")
    @ColumnComment("后缀名")
    private String fileExt;

    /**
     * 文件大小(Byte)
     */
    @TableField("file_size")
    @ColumnComment("文件大小(Byte)")
    private Long fileSize;

    /**
     * MIME类型
     */
    @TableField("content_type")
    @ColumnComment("MIME类型")
    private String contentType;

    /**
     * 文件MD5
     */
    @TableField("md5")
    @ColumnComment("文件MD5")
    private String md5;

    /**
     * 存储厂商
     */
    @TableField("storage_type")
    @ColumnComment("存储厂商")
    private String storageType;

    /**
     * 存储桶名
     */
    @TableField("storage_bucket")
    @ColumnComment("存储桶名")
    private String storageBucket;

    /**
     * 对象Key/相对路径
     */
    @TableField("file_path")
    @ColumnComment("对象Key/相对路径")
    private String filePath;

    /**
     * 上传时快照地址
     */
    @TableField("file_url")
    @ColumnType("varchar(500)")
    @ColumnComment("上传时快照地址")
    private String fileUrl;

    /**
     * 状态(0:失败, 1:成功)
     */
    @TableField("upload_status")
    @DefaultValue("1")
    @ColumnComment("状态(0:失败, 1:成功)")
    private Integer uploadStatus;

    /**
     * 逻辑删除(0:未删除, 1:已删除)
     */
    @TableLogic
    @TableField("is_deleted")
    @DefaultValue("0")
    @ColumnComment("逻辑删除(0:未删除, 1:已删除)")
    private Integer isDeleted;

    /**
     * 物理删除状态（0：无需删除，1：待删除）。
     */
    @TableField("physical_delete_status")
    @DefaultValue("0")
    @ColumnComment("物理删除状态(0:无需删除, 1:待删除)")
    private Integer physicalDeleteStatus;

    /**
     * 上传者ID
     */
    @TableField("user_id")
    @ColumnComment("上传者ID")
    private Long userId;

    /**
     * 上传者姓名
     */
    @TableField("username")
    @ColumnComment("上传者姓名")
    private String username;

    /**
     * 上传IP
     */
    @TableField("upload_ip")
    @ColumnComment("上传IP")
    private String uploadIp;

    /**
     * 扩展元数据
     */
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    @ColumnType("json")
    @ColumnComment("扩展元数据")
    private Map<String, Object> metadata;
}
