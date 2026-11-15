package com.navan.travel.sharding;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.transaction.support.TransactionTemplate;

import com.navan.travel.common.shard.Shard;
import com.navan.travel.common.shard.ShardScopedJdbcClient;
import com.navan.travel.common.shard.Shards;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
@EnableConfigurationProperties(ShardingProperties.class)
class ShardingConfig {

    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(3);

    @Bean
    Shards shards(ShardingProperties properties) {
        List<Shard> shards = new ArrayList<>();
        for (ShardingProperties.ShardConfig shardConfig : properties.shards()) {
            HikariDataSource dataSource = dataSource(properties, shardConfig);
            migrate(dataSource, properties.freshSchema());
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.setQueryTimeout((int) QUERY_TIMEOUT.toSeconds());
            jdbcTemplate.setExceptionTranslator(new SQLErrorCodeSQLExceptionTranslator(dataSource));
            shards.add(new Shard(shardConfig.id(), dataSource, JdbcClient.create(jdbcTemplate),
                    new TransactionTemplate(new DataSourceTransactionManager(dataSource))));
        }
        return new Shards(shards, properties.previousRing());
    }

    @Bean
    @Primary
    JdbcClient shardScopedJdbcClient(Shards shards) {
        return new ShardScopedJdbcClient(shards);
    }

    @Bean
    ShardPools shardPools(Shards shards) {
        return new ShardPools(shards);
    }

    @Bean
    HealthIndicator shardsHealthIndicator(Shards shards) {
        return () -> {
            Health.Builder health = Health.up();
            for (Shard shard : shards.all()) {
                try {
                    shard.jdbc().sql("SELECT 1").query(Integer.class).single();
                    health.withDetail(shard.toString(), "UP");
                } catch (RuntimeException e) {
                    health.down().withDetail(shard.toString(), "DOWN: " + e.getMessage());
                }
            }
            return health.build();
        };
    }

    /**
     * Shard {@code n} hands out ids n + 1, n + 1 + 64, ... so an id names its shard no matter how many are added.
     */
    private static HikariDataSource dataSource(ShardingProperties properties, ShardingProperties.ShardConfig shard) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("shard-" + shard.id());
        dataSource.setJdbcUrl(shard.url());
        dataSource.setUsername(properties.username());
        dataSource.setPassword(properties.password());
        dataSource.setMaximumPoolSize(properties.poolSizePerShard());
        dataSource.setConnectionTimeout(CONNECTION_TIMEOUT.toMillis());
        dataSource.setConnectionInitSql("SET SESSION auto_increment_increment = " + Shards.MAX_SHARDS
                + ", auto_increment_offset = " + (shard.id() + 1)
                + ", innodb_lock_wait_timeout = " + properties.lockWaitTimeoutSeconds()
                + ", time_zone = '+00:00'");
        return dataSource;
    }

    private static void migrate(HikariDataSource dataSource, boolean freshSchema) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(!freshSchema)
                .load();
        if (freshSchema) {
            flyway.clean();
        }
        flyway.migrate();
    }
}
