package com.navan.travel.sharding;

import com.navan.travel.common.shard.Shard;
import com.navan.travel.common.shard.Shards;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Closes every shard's connection pool when the application stops.
 */
class ShardPools implements AutoCloseable {

    private final Shards shards;

    ShardPools(Shards shards) {
        this.shards = shards;
    }

    @Override
    public void close() {
        for (Shard shard : shards.all()) {
            if (shard.dataSource() instanceof HikariDataSource pool) {
                pool.close();
            }
        }
    }
}
