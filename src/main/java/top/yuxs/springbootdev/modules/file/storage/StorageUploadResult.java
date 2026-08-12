package top.yuxs.springbootdev.modules.file.storage;

/**
 * 文件上传后的存储结果。
 *
 * @param filePath 文件相对路径或对象 Key
 * @param storageBucket 文件所在存储桶或本地存储标识
 * @param md5 文件内容的 MD5 摘要
 */
public record StorageUploadResult(String filePath, String storageBucket, String md5) {
}
