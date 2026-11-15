package com.navan.travel.common.shard;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Consistent hashing. Each shard owns {@value #VIRTUAL_NODES} points on a ring of 64-bit positions; a key belongs to
 * the first point at or after its own position. Adding a shard only takes keys away from the others, about 1/N of
 * them, and never moves a key between two shards that were already there.
 *
 * <p>Positions are the first 8 bytes of SHA-256: of {@code "shard-<id>#<n>"} for points, of the UTF-8 key for keys.
 */
public final class HashRing {

    static final int VIRTUAL_NODES = 256;

    private final NavigableMap<Long, Integer> pointToShard = new TreeMap<>();

    public HashRing(Collection<Integer> shardIds) {
        if (shardIds.isEmpty()) {
            throw new IllegalArgumentException("A ring needs at least one shard");
        }
        for (int shardId : shardIds) {
            for (int point = 0; point < VIRTUAL_NODES; point++) {
                pointToShard.put(position("shard-" + shardId + "#" + point), shardId);
            }
        }
    }

    public int ownerOf(String key) {
        Map.Entry<Long, Integer> point = pointToShard.ceilingEntry(position(key));
        return (point != null ? point : pointToShard.firstEntry()).getValue();
    }

    private static long position(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }
}
