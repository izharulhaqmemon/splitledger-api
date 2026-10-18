package com.izhar.splitledger.logic;

import java.util.*;

/**
 * Settles a whole group with few payments.
 * Input: net balance per member in minor units (positive = is owed money, negative = owes money; sums to zero).
 * Greedy rule: repeatedly let the biggest debtor pay the biggest creditor. This needs at most (n - 1) payments.
 * (Finding the absolute minimum is NP-hard; this is the standard practical approach.)
 */
public final class DebtSimplifier {

    public record Transfer(Long fromMemberId, Long toMemberId, long amountMinor) {
    }

    private record Node(Long memberId, long amount) {
    }

    private DebtSimplifier() {
    }

    public static List<Transfer> simplify(Map<Long, Long> netBalances) {
        Comparator<Node> biggestFirst = Comparator.<Node>comparingLong(Node::amount).reversed()
                .thenComparing(Node::memberId);
        PriorityQueue<Node> creditors = new PriorityQueue<>(biggestFirst);
        PriorityQueue<Node> debtors = new PriorityQueue<>(biggestFirst);

        netBalances.forEach((id, balance) -> {
            if (balance > 0) creditors.add(new Node(id, balance));
            else if (balance < 0) debtors.add(new Node(id, -balance));
        });

        List<Transfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Node creditor = creditors.poll();
            Node debtor = debtors.poll();
            long amount = Math.min(creditor.amount(), debtor.amount());
            transfers.add(new Transfer(debtor.memberId(), creditor.memberId(), amount));
            if (creditor.amount() > amount) creditors.add(new Node(creditor.memberId(), creditor.amount() - amount));
            if (debtor.amount() > amount) debtors.add(new Node(debtor.memberId(), debtor.amount() - amount));
        }
        return transfers;
    }
}
