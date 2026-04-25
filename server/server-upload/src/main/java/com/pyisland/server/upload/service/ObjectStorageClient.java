package com.pyisland.server.upload.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 统一对象存储客户端接口。
 */
public interface ObjectStorageClient {

    /**
     * 当前客户端所属存储提供商。
     */
    StorageProvider provider();

    /**
     * 上传文件并返回结构化结果。
     * @param file 文件。
     * @param folder 目录。
     * @return 上传结果。
     * @throws IOException 上传失败时抛出。
     */
    StorageUploadResult uploadObject(MultipartFile file, String folder) throws IOException;

    /**
     * 按指定对象 key 写入二进制内容。
     * @param objectKey 对象 key。
     * @param content 文件内容。
     * @param contentType 内容类型。
     * @return 上传结果。
     * @throws IOException 上传失败时抛出。
     */
    StorageUploadResult putObject(String objectKey, byte[] content, String contentType) throws IOException;

    /**
     * 按指定对象 key 写入二进制内容（带业务上下文）。
     * @param objectKey 对象 key。
     * @param content 文件内容。
     * @param contentType 内容类型。
     * @param bizType 业务类型。
     * @param fieldName 业务字段。
     * @return 上传结果。
     * @throws IOException 上传失败时抛出。
     */
    default StorageUploadResult putObject(String objectKey,
                                          byte[] content,
                                          String contentType,
                                          String bizType,
                                          String fieldName) throws IOException {
        return putObject(objectKey, content, contentType);
    }
}
