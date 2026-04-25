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
}
