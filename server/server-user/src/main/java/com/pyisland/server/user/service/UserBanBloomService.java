package com.pyisland.server.user.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * 用户封禁布隆过滤器服务。
 * 使用 Redis 位图实现 Bloom Filter，并用 Set 存储精确封禁名单避免误判。
 */
@Service
public class UserBanBloomService {

    private final StringRedisTemplate userBanRedisTemplate;
    private final String bloomKey;
    private final String exactSetKey;
    private final long bitSize;
    private final int hashCount;

    public UserBanBloomService(
            @Qualifier("userBanRedisTemplate") StringRedisTemplate userBanRedisTemplate,
            @Value("${user.ban.bloom.key:user:ban:bloom}") String bloomKey,
            @Value("${user.ban.set.key:user:ban:set}") String exactSetKey,
            @Value("${user.ban.bloom.bit-size:1000003}") long bitSize,
            @Value("${user.ban.bloom.hash-count:6}") int hashCount
    ) {
        this.userBanRedisTemplate = userBanRedisTemplate;
        this.bloomKey = bloomKey;
        this.exactSetKey = exactSetKey;
        this.bitSize = Math.max(1024, bitSize);
        this.hashCount = Math.max(2, hashCount);
    }

    /**
     * 判断用户是否被封禁。
     * @param username 用户名。
     * @return true 表示封禁。
     */
    public boolean isBanned(String username) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            return false;
        }
        if (!mightContain(normalized)) {
            return false;
        }
        Boolean exact = userBanRedisTemplate.opsForSet().isMember(exactSetKey, normalized);
        return Boolean.TRUE.equals(exact);
    }

    /**
     * 添加封禁。
     * @param username 用户名。
     */
    public void ban(String username) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            return;
        }
        userBanRedisTemplate.opsForSet().add(exactSetKey, normalized);
        long[] offsets = bloomOffsets(normalized);
        for (long offset : offsets) {
            userBanRedisTemplate.opsForValue().setBit(bloomKey, offset, true);
        }
    }

    /**
     * 解除封禁。
     * @param username 用户名。
     */
    public void unban(String username) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            return;
        }
        userBanRedisTemplate.opsForSet().remove(exactSetKey, normalized);
    }

    private boolean mightContain(String normalized) {
        long[] offsets = bloomOffsets(normalized);
        for (long offset : offsets) {
            Boolean bit = userBanRedisTemplate.opsForValue().getBit(bloomKey, offset);
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
        return Integer.toUnsignedLong((value + "#ban").hashCode());
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
