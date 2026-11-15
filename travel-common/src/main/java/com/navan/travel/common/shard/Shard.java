package com.navan.travel.common.shard;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** @param id never changes: it is the shard's place on the ring and its id lane */
public record Shard(int id, DataSource dataSource, JdbcClient jdbc, TransactionTemplate transactions) {

    @Override
    public String toString() {
        return "shard " + id;
    }
}
