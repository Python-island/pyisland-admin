package com.pyisland.server.upload.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 腾讯云 COS 文件服务。
 */
@Service
public class CosStorageService implements ObjectStorageClient {

    @Value("${tencent.cos.region:ap-guangzhou}")
    private String region;

    @Value("${tencent.cos.secret-id:}")
    private String secretId;

    @Value("${tencent.cos.secret-key:}")
    private String secretKey;

    @Value("${tencent.cos.bucket-name:}")
    private String bucketName;

    @Value("${tencent.cos.domain:}")
    private String domain;

    @Override
    public StorageProvider provider() {
        return StorageProvider.COS;
    }

    /**
     * 上传文件到 COS 并返回可访问地址。
     * @param file 待上传文件。
     * @param folder COS 目录。
     * @return 文件公网地址。
     * @throws IOException 文件读取失败时抛出。
     */
    public String upload(MultipartFile file, String folder) throws IOException {
        return uploadObject(file, folder).publicUrl();
    }

    @Override
    public StorageUploadResult uploadObject(MultipartFile file, String folder) throws IOException {
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("腾讯云 COS bucket-name 未配置");
        }
        if (secretId == null || secretId.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("腾讯云 COS 密钥未配置");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String objectKey = folder + "/" + UUID.randomUUID() + ext;

        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        COSClient cosClient = new COSClient(credentials, clientConfig);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            if (file.getContentType() != null) {
                metadata.setContentType(file.getContentType());
            }
            PutObjectRequest request = new PutObjectRequest(bucketName, objectKey, file.getInputStream(), metadata);
            cosClient.putObject(request);
        } finally {
            cosClient.shutdown();
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
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("腾讯云 COS bucket-name 未配置");
        }
        if (secretId == null || secretId.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("腾讯云 COS 密钥未配置");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }

        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        COSClient cosClient = new COSClient(credentials, clientConfig);
        try {
            byte[] safeContent = content == null ? new byte[0] : content;
            String safeContentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(safeContent.length);
            metadata.setContentType(safeContentType);
            PutObjectRequest request = new PutObjectRequest(
                    bucketName,
                    objectKey,
                    new java.io.ByteArrayInputStream(safeContent),
                    metadata
            );
            cosClient.putObject(request);
            return new StorageUploadResult(
                    provider(),
                    bucketName,
                    objectKey,
                    buildPublicUrl(objectKey),
                    safeContentType,
                    safeContent.length
            );
        } finally {
            cosClient.shutdown();
        }
    }

    private String buildPublicUrl(String objectKey) {
        String safeKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        if (domain != null && !domain.isBlank()) {
            String normalizedDomain = domain.startsWith("http") ? domain : "https://" + domain;
            if (normalizedDomain.endsWith("/")) {
                return normalizedDomain + safeKey;
            }
            return normalizedDomain + "/" + safeKey;
        }
        return "https://" + bucketName + ".cos." + region + ".myqcloud.com/" + safeKey;
    }
}
