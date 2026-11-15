package com.navan.travel.transaction.identity;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.navan.travel.common.shard.Shard;
import com.navan.travel.common.shard.Shards;
import com.navan.travel.transaction.identity.ExternalIdentity.IdentityRecord;

/**
 * Run after adding a shard: moves each external_ids record to its new owner. Copy first, delete second, so a
 * create (which checks the old owner too meanwhile) always finds it. Safe to run again.
 */
@Component
public class IdentityRebalancer {

    private static final Logger log = LoggerFactory.getLogger(IdentityRebalancer.class);
    private static final int BATCH_SIZE = 500;

    private final Shards shards;
    private final ExternalIdentity identity;

    public IdentityRebalancer(Shards shards, ExternalIdentity identity) {
        this.shards = shards;
        this.identity = identity;
    }

    public int rebalance() {
        int moved = 0;
        for (Shard source : shards.all()) {
            String after = "";
            List<IdentityRecord> batch;
            while (!(batch = page(source, after)).isEmpty()) {
                moved += moveMisplaced(source, batch);
                after = batch.getLast().externalId();
            }
        }
        log.info("Moved {} external_id records to their owners on the current ring", moved);
        return moved;
    }

    private List<IdentityRecord> page(Shard source, String after) {
        return shards.read(source, () -> identity.page(after, BATCH_SIZE));
    }

    private int moveMisplaced(Shard source, List<IdentityRecord> batch) {
        Map<Shard, List<IdentityRecord>> misplacedByOwner = batch.stream()
                .filter(record -> !shards.forExternalId(record.externalId()).equals(source))
                .collect(Collectors.groupingBy(record -> shards.forExternalId(record.externalId())));
        misplacedByOwner.forEach((owner, records) -> {
            shards.inTransaction(owner, () -> identity.copy(records));
            shards.inTransaction(source, () -> identity.forget(records));
        });
        return misplacedByOwner.values().stream().mapToInt(List::size).sum();
    }
}
