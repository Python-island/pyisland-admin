package com.pyisland.server.upload.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * OSS 文件服务。
 */
@Service
public class OssService implements ObjectStorageClient {

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${aliyun.oss.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;

    @Value("${aliyun.oss.domain}")
    private String domain;

    @Override
    public StorageProvider provider() {
        return StorageProvider.OSS;
    }

    /**
     * 上传文件到 OSS 并返回可访问地址。
     * @param file 待上传文件。
     * @param folder OSS 目录。
     * @return 文件公网地址。
     * @throws IOException 文件读取失败时抛出。
     */
    public String upload(MultipartFile file, String folder) throws IOException {
        return uploadObject(file, folder).publicUrl();
    }

    @Override
    public StorageUploadResult uploadObject(MultipartFile file, String folder) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String objectKey = folder + "/" + UUID.randomUUID() + ext;

        OSS ossClient = new OSSClientBuilder().build("https://" + endpoint, accessKeyId, accessKeySecret);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            String contentType = file.getContentType();
            metadata.setContentType(contentType);
            metadata.setContentLength(file.getSize());
            ossClient.putObject(bucketName, objectKey, file.getInputStream(), metadata);
        } finally {
            ossClient.shutdown();
        }

        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        return new StorageUploadResult(
                provider(),
                bucketName,
                objectKey,
                buildPublicUrl(objectKey),
                contentType,
                file.getSize()
        );
    }

    @Override
    public StorageUploadResult putObject(String objectKey, byte[] content, String contentType) throws IOException {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        OSS ossClient = new OSSClientBuilder().build("https://" + endpoint, accessKeyId, accessKeySecret);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            byte[] safeContent = content == null ? new byte[0] : content;
            String safeContentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
            metadata.setContentType(safeContentType);
            metadata.setContentLength(safeContent.length);
            ossClient.putObject(bucketName, objectKey, new java.io.ByteArrayInputStream(safeContent), metadata);
            return new StorageUploadResult(
                    provider(),
                    bucketName,
                    objectKey,
                    buildPublicUrl(objectKey),
                    safeContentType,
                    safeContent.length
            );
        } finally {
            ossClient.shutdown();
        }
    }

    private String buildPublicUrl(String objectKey) {
        String safeKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        String safeDomain = domain.startsWith("http") ? domain : "https://" + domain;
        if (safeDomain.endsWith("/")) {
            return safeDomain + safeKey;
        }
        return safeDomain + "/" + safeKey;
    }
}
