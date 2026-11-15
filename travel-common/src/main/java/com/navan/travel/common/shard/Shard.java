package com.navan.travel.common.shard;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @param id stable for the life of the shard: it names the shard on the hash ring and is the id lane it issues
 */
public record Shard(int id, DataSource dataSource, JdbcClient jdbc, TransactionTemplate transactions) {

    @Override
    public String toString() {
        return "shard " + id;
    }
}
