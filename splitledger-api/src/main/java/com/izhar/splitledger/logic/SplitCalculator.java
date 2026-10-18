package com.izhar.splitledger.logic;

import com.izhar.splitledger.domain.SplitType;
import com.izhar.splitledger.error.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Turns "who participates and how" into exact per-member amounts in minor units.
 * Guarantee: the returned shares ALWAYS add up to the total, to the last paisa.
 * Leftover paisa are handed out with the largest-remainder method (ties go to the earlier participant),
 * so results are deterministic.
 */
public final class SplitCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** value meaning depends on the split type: exact amount, percentage, or number of shares (ignored for EQUAL). */
    public record Entry(Long memberId, BigDecimal value) {
    }

    private SplitCalculator() {
    }

    public static Map<Long, Long> compute(long totalMinor, SplitType type, List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw ApiException.unprocessable("At least one participant is required in splits");
        }
        Set<Long> seen = new HashSet<>();
        for (Entry e : entries) {
            if (!seen.add(e.memberId())) {
                throw ApiException.unprocessable("Member " + e.memberId() + " appears more than once in splits");
            }
        }

        long[] result = switch (type) {
            case EQUAL -> allocate(totalMinor, Collections.nCopies(entries.size(), BigDecimal.ONE));
            case EXACT -> exact(totalMinor, entries);
            case PERCENTAGE -> percentage(totalMinor, entries);
            case SHARES -> allocate(totalMinor, positiveValues(entries, "shares"));
        };

        Map<Long, Long> shares = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            shares.put(entries.get(i).memberId(), result[i]);
        }
        return shares;
    }

    private static long[] exact(long total, List<Entry> entries) {
        long[] out = new long[entries.size()];
        long sum = 0;
        for (int i = 0; i < entries.size(); i++) {
            BigDecimal v = entries.get(i).value();
            if (v == null || v.signum() < 0) {
                throw ApiException.unprocessable("EXACT split needs a non-negative amount for every member");
            }
            out[i] = Money.toMinor(v);
            sum += out[i];
        }
        if (sum != total) {
            throw ApiException.unprocessable("Exact amounts must add up to the expense total "
                    + Money.fromMinor(total).toPlainString() + " but add up to " + Money.fromMinor(sum).toPlainString());
        }
        return out;
    }

    private static long[] percentage(long total, List<Entry> entries) {
        List<BigDecimal> weights = positiveValues(entries, "percentage");
        BigDecimal sum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(HUNDRED) != 0) {
            throw ApiException.unprocessable("Percentages must add up to 100 but add up to "
                    + sum.stripTrailingZeros().toPlainString());
        }
        return allocate(total, weights);
    }

    private static List<BigDecimal> positiveValues(List<Entry> entries, String label) {
        List<BigDecimal> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.value() == null || e.value().signum() <= 0) {
                throw ApiException.unprocessable(label.toUpperCase() + " split needs a positive " + label + " value for every member");
            }
            out.add(e.value());
        }
        return out;
    }

    /** Largest-remainder proportional allocation of {@code total} according to {@code weights}. */
    static long[] allocate(long total, List<BigDecimal> weights) {
        BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        int n = weights.size();
        long[] base = new long[n];
        BigDecimal[] remainder = new BigDecimal[n];
        long assigned = 0;

        for (int i = 0; i < n; i++) {
            BigDecimal raw = BigDecimal.valueOf(total).multiply(weights.get(i))
                    .divide(weightSum, 20, RoundingMode.DOWN);
            long floor = raw.setScale(0, RoundingMode.DOWN).longValue();
            base[i] = floor;
            remainder[i] = raw.subtract(BigDecimal.valueOf(floor));
            assigned += floor;
        }

        long leftover = total - assigned;
        Integer[] order = IntStream.range(0, n).boxed().toArray(Integer[]::new);
        Arrays.sort(order, (a, b) -> {
            int c = remainder[b].compareTo(remainder[a]);
            return c != 0 ? c : Integer.compare(a, b);
        });
        for (int k = 0; k < leftover; k++) {
            base[order[k]]++;
        }
        return base;
    }
}
