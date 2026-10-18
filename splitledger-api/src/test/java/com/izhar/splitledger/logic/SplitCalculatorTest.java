package com.izhar.splitledger.logic;

import com.izhar.splitledger.domain.SplitType;
import com.izhar.splitledger.error.ApiException;
import com.izhar.splitledger.logic.SplitCalculator.Entry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SplitCalculatorTest {

    private static Entry entry(long id, String value) {
        return new Entry(id, value == null ? null : new BigDecimal(value));
    }

    @Test
    void equalSplitGivesLeftoverPaisaToEarlierMembers() {
        Map<Long, Long> r = SplitCalculator.compute(10000, SplitType.EQUAL,
                List.of(entry(1, null), entry(2, null), entry(3, null)));
        assertEquals(List.of(3334L, 3333L, 3333L), List.copyOf(r.values()));
    }

    @Test
    void percentageSplit() {
        Map<Long, Long> r = SplitCalculator.compute(640000, SplitType.PERCENTAGE,
                List.of(entry(1, "40"), entry(2, "30"), entry(3, "30")));
        assertEquals(List.of(256000L, 192000L, 192000L), List.copyOf(r.values()));
    }

    @Test
    void sharesSplit() {
        Map<Long, Long> r = SplitCalculator.compute(10000, SplitType.SHARES,
                List.of(entry(1, "2"), entry(2, "1"), entry(3, "1")));
        assertEquals(List.of(5000L, 2500L, 2500L), List.copyOf(r.values()));
    }

    @Test
    void exactSplitMustMatchTotal() {
        Map<Long, Long> ok = SplitCalculator.compute(180050, SplitType.EXACT,
                List.of(entry(1, "600.50"), entry(2, "600"), entry(3, "600")));
        assertEquals(List.of(60050L, 60000L, 60000L), List.copyOf(ok.values()));

        ApiException ex = assertThrows(ApiException.class, () -> SplitCalculator.compute(10000, SplitType.EXACT,
                List.of(entry(1, "60"), entry(2, "30"))));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
    }

    @Test
    void percentagesMustTotalHundred() {
        assertThrows(ApiException.class, () -> SplitCalculator.compute(10000, SplitType.PERCENTAGE,
                List.of(entry(1, "60"), entry(2, "30"))));
    }

    @Test
    void rejectsDuplicateMembersAndTooManyDecimals() {
        assertThrows(ApiException.class, () -> SplitCalculator.compute(10000, SplitType.EQUAL,
                List.of(entry(1, null), entry(1, null))));
        assertThrows(ApiException.class, () -> SplitCalculator.compute(10000, SplitType.EXACT,
                List.of(entry(1, "33.333"), entry(2, "66.667"))));
    }

    @Test
    void sharesAlwaysAddUpToTheTotal() {
        Random random = new Random(42);
        for (int run = 0; run < 2000; run++) {
            long total = 1 + random.nextInt(5_000_000);
            int n = 1 + random.nextInt(9);
            List<Entry> equal = new ArrayList<>();
            List<Entry> weighted = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                equal.add(entry(i, null));
                weighted.add(entry(i, String.valueOf(1 + random.nextInt(7))));
            }
            assertEquals(total, SplitCalculator.compute(total, SplitType.EQUAL, equal)
                    .values().stream().mapToLong(Long::longValue).sum());
            assertEquals(total, SplitCalculator.compute(total, SplitType.SHARES, weighted)
                    .values().stream().mapToLong(Long::longValue).sum());
        }
    }
}
