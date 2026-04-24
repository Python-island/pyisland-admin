package com.pyisland.server.user.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.zip.CRC32;

/**
 * 壁纸详情 ID 布隆过滤器服务。
 * 使用 Redis 位图 + 精确集合，提前拦截不存在的详情 ID，减少随机 ID 对数据库的穿透压力。
 */
@Service
public class WallpaperDetailBloomService {

    private final StringRedisTemplate wallpaperDetailBloomRedisTemplate;
    private final String bloomKey;
    private final String exactSetKey;
    private final long bitSize;
    private final int hashCount;

    public WallpaperDetailBloomService(
            @Qualifier("wallpaperDetailBloomRedisTemplate") StringRedisTemplate wallpaperDetailBloomRedisTemplate,
            @Value("${wallpaper.detail.bloom.key:wallpaper:detail:bloom}") String bloomKey,
            @Value("${wallpaper.detail.set.key:wallpaper:detail:set}") String exactSetKey,
            @Value("${wallpaper.detail.bloom.bit-size:2000003}") long bitSize,
            @Value("${wallpaper.detail.bloom.hash-count:6}") int hashCount
    ) {
        this.wallpaperDetailBloomRedisTemplate = wallpaperDetailBloomRedisTemplate;
        this.bloomKey = bloomKey;
        this.exactSetKey = exactSetKey;
        this.bitSize = Math.max(1024, bitSize);
        this.hashCount = Math.max(2, hashCount);
    }

    public boolean mightContain(Long id) {
        String normalized = normalizeId(id);
        if (normalized == null) {
            return false;
        }
        try {
            if (!mightContainBloom(normalized)) {
                return false;
            }
            Boolean exact = wallpaperDetailBloomRedisTemplate.opsForSet().isMember(exactSetKey, normalized);
            return Boolean.TRUE.equals(exact);
        } catch (Exception ignored) {
            return true;
        }
    }

    public void add(Long id) {
        String normalized = normalizeId(id);
        if (normalized == null) {
            return;
        }
        try {
            wallpaperDetailBloomRedisTemplate.opsForSet().add(exactSetKey, normalized);
            long[] offsets = bloomOffsets(normalized);
            for (long offset : offsets) {
                wallpaperDetailBloomRedisTemplate.opsForValue().setBit(bloomKey, offset, true);
            }
        } catch (Exception ignored) {
        }
    }

    public void remove(Long id) {
        String normalized = normalizeId(id);
        if (normalized == null) {
            return;
        }
        try {
            wallpaperDetailBloomRedisTemplate.opsForSet().remove(exactSetKey, normalized);
        } catch (Exception ignored) {
        }
    }

    public void rebuildFromIds(Collection<Long> ids) {
        try {
            wallpaperDetailBloomRedisTemplate.delete(exactSetKey);
            wallpaperDetailBloomRedisTemplate.delete(bloomKey);
            if (ids == null || ids.isEmpty()) {
                return;
            }
            for (Long id : ids) {
                add(id);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean mightContainBloom(String normalized) {
        long[] offsets = bloomOffsets(normalized);
        for (long offset : offsets) {
            Boolean bit = wallpaperDetailBloomRedisTemplate.opsForValue().getBit(bloomKey, offset);
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
        return Integer.toUnsignedLong((value + "#wallpaper-detail").hashCode());
    }

    private String normalizeId(Long id) {
        if (id == null || id <= 0) {
            return null;
        }
        return String.valueOf(id);
    }
}
