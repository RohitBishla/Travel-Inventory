package com.navan.travel.common.shard;

import org.springframework.jdbc.core.simple.JdbcClient;

/** What repositories inject: each statement goes to the shard picked by the surrounding {@link Shards} call. */
public class ShardScopedJdbcClient implements JdbcClient {

    private final Shards shards;

    public ShardScopedJdbcClient(Shards shards) {
        this.shards = shards;
    }

    @Override
    public StatementSpec sql(String sql) {
        return shards.currentJdbc().sql(sql);
    }
}
