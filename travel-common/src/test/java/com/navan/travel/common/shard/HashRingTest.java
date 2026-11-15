package com.navan.travel.common.shard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.navan.travel.common.NotFoundException;

class HashRingTest {

    private static final List<String> KEYS = IntStream.range(0, 20_000)
            .mapToObj(i -> "PNR" + Integer.toString(i * 7919, 36).toUpperCase() + "-2026-03-11")
            .toList();

    @Test
    void placesKeysExactlyAsTheDocumentedAlgorithmDoes() {
        HashRing twoShards = new HashRing(List.of(0, 1));
        HashRing threeShards = new HashRing(List.of(0, 1, 2));

        assertThat(twoShards.ownerOf("flight-TXL-LHR-2026-03-11")).isZero();
        assertThat(twoShards.ownerOf("navan-trip-fee-2026-03-11")).isEqualTo(1);
        assertThat(threeShards.ownerOf("flight-TXL-LHR-2026-03-11")).isEqualTo(2);
        assertThat(threeShards.ownerOf("hotel-LON-2026-03-11")).isZero();
    }

    @Test
    void spreadsKeysEvenly() {
        Map<Integer, Long> perShard = countByOwner(new HashRing(List.of(0, 1, 2)));

        assertThat(perShard.values()).allSatisfy(count -> assertThat(count).isBetween(6_000L, 7_400L));
    }

    @Test
    void addingAShardMovesAboutAThirdOfTheKeysAndOnlyToTheNewShard() {
        HashRing before = new HashRing(List.of(0, 1));
        HashRing after = new HashRing(List.of(0, 1, 2));

        List<String> moved = KEYS.stream().filter(key -> before.ownerOf(key) != after.ownerOf(key)).toList();

        assertThat(moved).allSatisfy(key -> assertThat(after.ownerOf(key)).isEqualTo(2));
        assertThat(moved.size()).isBetween(6_000, 7_400);
    }

    @Test
    void idsNameTheirShardWhateverTheShardCount() {
        Shards shards = new Shards(List.of(shard(0), shard(1), shard(5)), List.of());

        assertThat(shards.forId(1).id()).isZero();
        assertThat(shards.forId(2).id()).isEqualTo(1);
        assertThat(shards.forId(6).id()).isEqualTo(5);
        assertThat(shards.forId(1 + Shards.MAX_SHARDS).id()).isZero();
        assertThatThrownBy(() -> shards.forId(3)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void whileShardsAreAddedAKeyIsAlsoLookedUpWhereItUsedToLive() {
        Shards growing = new Shards(List.of(shard(0), shard(1), shard(2)), List.of(0, 1));
        Shards settled = new Shards(List.of(shard(0), shard(1), shard(2)), List.of());

        assertThat(growing.identityShards("flight-TXL-LHR-2026-03-11")).extracting(Shard::id).containsExactly(2, 0);
        assertThat(growing.identityShards("hotel-LON-2026-03-11")).extracting(Shard::id).containsExactly(0);
        assertThat(settled.identityShards("flight-TXL-LHR-2026-03-11")).extracting(Shard::id).containsExactly(2);
    }

    private static Map<Integer, Long> countByOwner(HashRing ring) {
        return KEYS.stream().collect(Collectors.groupingBy(ring::ownerOf, Collectors.counting()));
    }

    private static Shard shard(int id) {
        return new Shard(id, null, null, null);
    }
}
