package com.navan.travel.sharding;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param shards       shard ids (0-63) never change once a shard holds data
 * @param previousRing the old shard ids, only while a new shard is joining
 * @param freshSchema  wipe and recreate the schema on start; tests only
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
