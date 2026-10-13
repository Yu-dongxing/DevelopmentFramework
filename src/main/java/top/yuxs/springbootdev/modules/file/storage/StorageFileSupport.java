package top.yuxs.springbootdev.modules.file.storage;

import top.yuxs.springbootdev.core.exception.BusinessException;

import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 文件存储公共命名与摘要格式，供各存储实现复用。
 */
public final class StorageFileSupport {
    /**
     * 工具类不允许实例化。
     */
    private StorageFileSupport() {
    }

    /**
     * 提取后缀；拒绝路径分隔符和控制字符，防止文件名参与路径拼接。
     */
    public static String extension(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "";
        }
        String extension = originalName.substring(originalName.lastIndexOf('.'));
        if (!extension.matches("\\.[a-zA-Z0-9_-]{0,32}")) {
            throw new BusinessException("文件后缀不合法");
        }
        return extension;
    }

    /**
     * 生成应用拥有的唯一对象位置，保持现有业务目录及年月布局。
     */
    public static String objectKey(String path, String originalName) {
        if (path == null || !path.matches("[a-zA-Z0-9_-]+")) {
            throw new BusinessException("非法上传子路径");
        }
        LocalDate now = LocalDate.now();
        return path + "/" + now.getYear() + "/" + String.format("%02d", now.getMonthValue())
                + "/" + UUID.randomUUID() + extension(originalName);
    }

    /**
     * 将文件摘要统一转换成小写十六进制。
     */
    public static String digestHex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
