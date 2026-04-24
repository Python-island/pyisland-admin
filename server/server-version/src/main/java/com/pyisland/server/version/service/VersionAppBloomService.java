package com.pyisland.server.version.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.zip.CRC32;

/**
 * 版本应用名布隆过滤器服务。
 * 使用 Redis 位图 + 精确集合，快速拦截不存在的 appName，降低缓存穿透导致的数据库压力。
 */
@Service
public class VersionAppBloomService {

    private final StringRedisTemplate versionBloomRedisTemplate;
    private final String bloomKey;
    private final String exactSetKey;
    private final long bitSize;
    private final int hashCount;

    public VersionAppBloomService(
            @Qualifier("versionBloomRedisTemplate") StringRedisTemplate versionBloomRedisTemplate,
            @Value("${version.bloom.key:version:app:bloom}") String bloomKey,
            @Value("${version.app.set.key:version:app:set}") String exactSetKey,
            @Value("${version.bloom.bit-size:1000003}") long bitSize,
            @Value("${version.bloom.hash-count:6}") int hashCount
    ) {
        this.versionBloomRedisTemplate = versionBloomRedisTemplate;
        this.bloomKey = bloomKey;
        this.exactSetKey = exactSetKey;
        this.bitSize = Math.max(1024, bitSize);
        this.hashCount = Math.max(2, hashCount);
    }

    /**
     * 判断 appName 是否可能存在。
     * Redis 不可用时失败放行，避免把可用性问题升级为功能故障。
     * @param appName 应用名。
     * @return true 表示可能存在（需继续查缓存/DB）；false 表示确定不存在。
     */
    public boolean mightContain(String appName) {
        String normalized = normalizeAppName(appName);
        if (normalized == null) {
            return false;
        }
        try {
            if (!mightContainBloom(normalized)) {
                return false;
            }
            Boolean exact = versionBloomRedisTemplate.opsForSet().isMember(exactSetKey, normalized);
            return Boolean.TRUE.equals(exact);
        } catch (Exception ignored) {
            return true;
        }
    }

    /**
     * 新增一个 appName 到布隆过滤器与精确集合。
     * @param appName 应用名。
     */
    public void add(String appName) {
        String normalized = normalizeAppName(appName);
        if (normalized == null) {
            return;
        }
        try {
            versionBloomRedisTemplate.opsForSet().add(exactSetKey, normalized);
            long[] offsets = bloomOffsets(normalized);
            for (long offset : offsets) {
                versionBloomRedisTemplate.opsForValue().setBit(bloomKey, offset, true);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 从精确集合移除 appName。
     * 位图不做回收，允许保留假阳性并由精确集合二次判定。
     * @param appName 应用名。
     */
    public void remove(String appName) {
        String normalized = normalizeAppName(appName);
        if (normalized == null) {
            return;
        }
        try {
            versionBloomRedisTemplate.opsForSet().remove(exactSetKey, normalized);
        } catch (Exception ignored) {
        }
    }

    /**
     * 用数据库快照重建版本布隆过滤器。
     * @param appNames 应用名集合。
     */
    public void rebuildFromAppNames(Collection<String> appNames) {
        try {
            versionBloomRedisTemplate.delete(exactSetKey);
            versionBloomRedisTemplate.delete(bloomKey);
            if (appNames == null || appNames.isEmpty()) {
                return;
            }
            for (String appName : appNames) {
                add(appName);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean mightContainBloom(String normalized) {
        long[] offsets = bloomOffsets(normalized);
        for (long offset : offsets) {
            Boolean bit = versionBloomRedisTemplate.opsForValue().getBit(bloomKey, offset);
            if (!Boolean.TRUE.equals(bit)) {
                return false;
            }
        }
        return true;
    }

    private long[] bloomOffsets(String normalized) {
        long hash1 = hashCrc32(normalized);
        long hash2 = hashJava(normalized);
        long[] offsets = new long[hashCount];
        for (int i = 0; i < hashCount; i++) {
            long combined = hash1 + i * hash2 + (long) i * i;
            long positive = combined & Long.MAX_VALUE;
            offsets[i] = positive % bitSize;
        }
        return offsets;
    }

    private long hashCrc32(String value) {
        CRC32 crc32 = new CRC32();
        crc32.update(value.getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }

    private long hashJava(String value) {
        return Integer.toUnsignedLong((value + "#version").hashCode());
    }

    private String normalizeAppName(String appName) {
        if (appName == null) {
            return null;
        }
        String normalized = appName.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
