package com.navan.travel.common.shard;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The {@link JdbcClient} that repositories inject. Each statement goes to the shard selected by the
 * surrounding {@link Shards#read} or {@link Shards#inTransaction} call.
 */
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
