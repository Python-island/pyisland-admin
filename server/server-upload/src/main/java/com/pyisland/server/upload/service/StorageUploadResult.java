package com.pyisland.server.upload.service;

/**
 * 对象存储上传结果。
 * @param provider 存储提供商。
 * @param bucketName 存储桶名称。
 * @param objectKey 对象 key。
 * @param publicUrl 公网访问 URL。
 * @param contentType 内容类型。
 * @param contentLength 内容长度。
 */
public record StorageUploadResult(StorageProvider provider,
                                  String bucketName,
                                  String objectKey,
                                  String publicUrl,
                                  String contentType,
                                  long contentLength) {
}
