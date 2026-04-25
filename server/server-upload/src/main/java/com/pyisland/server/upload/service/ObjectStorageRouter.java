package com.pyisland.server.upload.service;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 统一对象存储客户端路由。
 */
@Component
public class ObjectStorageRouter {

    private final Map<StorageProvider, ObjectStorageClient> clients = new EnumMap<>(StorageProvider.class);

    public ObjectStorageRouter(List<ObjectStorageClient> objectStorageClients) {
        for (ObjectStorageClient client : objectStorageClients) {
            clients.put(client.provider(), client);
        }
    }

    /**
     * 根据存储提供商获取客户端。
     * @param provider 存储提供商。
     * @return 对应客户端。
     */
    public ObjectStorageClient get(StorageProvider provider) {
        ObjectStorageClient client = clients.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("Unsupported storage provider: " + provider);
        }
        return client;
    }
}
