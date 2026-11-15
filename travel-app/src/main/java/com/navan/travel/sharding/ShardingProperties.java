package com.navan.travel.sharding;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param shards       every shard, each with an id (0-63) that never changes once it holds data
 * @param previousRing while shards are being added: the shard ids that formed the ring before; empty otherwise
 * @param freshSchema  drop and recreate every shard's schema on start (tests only)
 */
@ConfigurationProperties("travel.sharding")
public record ShardingProperties(
        String username,
        String password,
        int poolSizePerShard,
        int lockWaitTimeoutSeconds,
        boolean freshSchema,
        List<ShardConfig> shards,
        List<Integer> previousRing) {

    public ShardingProperties {
        previousRing = previousRing == null ? List.of() : List.copyOf(previousRing);
    }

    public record ShardConfig(int id, String url) {
    }
}
