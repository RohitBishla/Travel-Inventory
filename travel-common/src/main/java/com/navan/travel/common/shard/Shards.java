package com.navan.travel.common.shard;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.navan.travel.common.NotFoundException;

/**
 * Routing and shard-scoped execution.
 *
 * <ul>
 *   <li>Keys: an {@code external_id} is owned by one shard on a consistent-hash {@link HashRing}. New transactions
 *       are written there, and that shard holds the key's uniqueness record.</li>
 *   <li>Ids: shard {@code n} issues ids {@code n + 1, n + 1 + 64, ...} ({@code auto_increment_increment} =
 *       {@value #MAX_SHARDS}), so an id names the shard that holds its row, however many shards are added later.</li>
 *   <li>While shards are being added, {@code previousRing} keeps the old ring so a key whose owner changed is also
 *       looked up where it used to live.</li>
 * </ul>
 *
 * Repository code runs inside {@link #read} or {@link #inTransaction}, which select the shard that
 * {@link #currentJdbc()} talks to.
 */
public class Shards {

    public static final int MAX_SHARDS = 64;

    private final List<Shard> shards;
    private final Map<Integer, Shard> byId;
    private final HashRing ring;
    private final HashRing previousRing;
    private final ThreadLocal<Deque<Shard>> scope = ThreadLocal.withInitial(ArrayDeque::new);

    public Shards(List<Shard> shards, Collection<Integer> previousRingShardIds) {
        if (shards.isEmpty()) {
            throw new IllegalArgumentException("At least one shard is required");
        }
        this.shards = shards.stream().sorted(Comparator.comparingInt(Shard::id)).toList();
        this.byId = this.shards.stream().collect(Collectors.toMap(Shard::id, Function.identity()));
        for (int id : byId.keySet()) {
            if (id < 0 || id >= MAX_SHARDS) {
                throw new IllegalArgumentException("Shard ids must be between 0 and " + (MAX_SHARDS - 1) + ": " + id);
            }
        }
        if (!byId.keySet().containsAll(previousRingShardIds)) {
            throw new IllegalArgumentException("previous-ring names a shard that is not configured: " + previousRingShardIds);
        }
        this.ring = new HashRing(byId.keySet());
        this.previousRing = previousRingShardIds.isEmpty() ? null : new HashRing(previousRingShardIds);
    }

    public List<Shard> all() {
        return shards;
    }

    public Shard forExternalId(String externalId) {
        return byId.get(ring.ownerOf(externalId));
    }

    /**
     * Every shard that may hold the uniqueness record of {@code externalId}: its owner and, while shards are being
     * added, its owner on the previous ring.
     */
    public List<Shard> identityShards(String externalId) {
        Set<Shard> candidates = new LinkedHashSet<>();
        candidates.add(forExternalId(externalId));
        if (previousRing != null) {
            candidates.add(byId.get(previousRing.ownerOf(externalId)));
        }
        return List.copyOf(candidates);
    }

    public Optional<Shard> findForId(long id) {
        return Optional.ofNullable(byId.get((int) Math.floorMod(id - 1, (long) MAX_SHARDS)));
    }

    public Shard forId(long id) {
        return findForId(id).orElseThrow(() -> new NotFoundException("No shard holds id " + id));
    }

    public <T> T read(Shard shard, Supplier<T> work) {
        scope.get().push(shard);
        try {
            return work.get();
        } finally {
            scope.get().pop();
        }
    }

    public void read(Shard shard, Runnable work) {
        read(shard, () -> {
            work.run();
            return null;
        });
    }

    /**
     * Runs {@code work} in a database transaction on {@code shard}, joining one that is already open there.
     * Transactions on different shards can be open at once; the innermost commits first.
     */
    public <T> T inTransaction(Shard shard, Supplier<T> work) {
        return read(shard, () -> shard.transactions().execute(status -> work.get()));
    }

    public void inTransaction(Shard shard, Runnable work) {
        inTransaction(shard, () -> {
            work.run();
            return null;
        });
    }

    JdbcClient currentJdbc() {
        Shard current = scope.get().peek();
        if (current == null) {
            throw new IllegalStateException("Database access outside a shard scope; wrap it in Shards.read or inTransaction");
        }
        return current.jdbc();
    }
}
