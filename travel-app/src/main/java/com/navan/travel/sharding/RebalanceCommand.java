package com.navan.travel.sharding;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import com.navan.travel.transaction.identity.IdentityRebalancer;

/** {@code --rebalance}: move external_id records after adding a shard, then exit (see README). */
@Component
class RebalanceCommand implements ApplicationRunner {

    private final IdentityRebalancer rebalancer;
    private final ConfigurableApplicationContext context;

    RebalanceCommand(IdentityRebalancer rebalancer, ConfigurableApplicationContext context) {
        this.rebalancer = rebalancer;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("rebalance")) {
            return;
        }
        rebalancer.rebalance();
        System.exit(SpringApplication.exit(context));
    }
}
