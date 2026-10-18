package com.izhar.splitledger.logic;

import com.izhar.splitledger.logic.DebtSimplifier.Transfer;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DebtSimplifierTest {

    @Test
    void twoDebtorsPayTheOneCreditor() {
        Map<Long, Long> balances = new LinkedHashMap<>();
        balances.put(1L, 200L);
        balances.put(2L, -100L);
        balances.put(3L, -100L);
        List<Transfer> transfers = DebtSimplifier.simplify(balances);
        assertEquals(2, transfers.size());
        assertTrue(transfers.stream().allMatch(t -> t.toMemberId() == 1L && t.amountMinor() == 100));
    }

    @Test
    void settledGroupNeedsNoTransfers() {
        assertTrue(DebtSimplifier.simplify(Map.of(1L, 0L, 2L, 0L)).isEmpty());
    }

    @Test
    void plansAlwaysSettleEveryoneWithAtMostNMinusOnePayments() {
        Random random = new Random(7);
        for (int run = 0; run < 2000; run++) {
            int n = 2 + random.nextInt(10);
            Map<Long, Long> balances = new LinkedHashMap<>();
            long sum = 0;
            for (int i = 0; i < n - 1; i++) {
                long v = random.nextInt(200_001) - 100_000;
                balances.put((long) i, v);
                sum += v;
            }
            balances.put((long) (n - 1), -sum);

            List<Transfer> transfers = DebtSimplifier.simplify(balances);
            assertTrue(transfers.size() <= n - 1);

            Map<Long, Long> after = new HashMap<>(balances);
            for (Transfer t : transfers) {
                assertTrue(t.amountMinor() > 0);
                after.merge(t.fromMemberId(), t.amountMinor(), Long::sum);
                after.merge(t.toMemberId(), -t.amountMinor(), Long::sum);
            }
            assertTrue(after.values().stream().allMatch(v -> v == 0L));
        }
    }
}
