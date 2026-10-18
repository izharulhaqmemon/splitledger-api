package com.izhar.splitledger.service;

import com.izhar.splitledger.domain.*;
import com.izhar.splitledger.dto.Dtos.*;
import com.izhar.splitledger.error.ApiException;
import com.izhar.splitledger.logic.DebtSimplifier;
import com.izhar.splitledger.logic.Money;
import com.izhar.splitledger.logic.SplitCalculator;
import com.izhar.splitledger.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** All business rules live here (the second validation gate: is the request logically valid?). */
@Service
public class LedgerService {

    /** created = false means an idempotent replay of an earlier identical request. */
    public record Outcome<T>(T body, boolean created) {
    }

    private final GroupRepository groups;
    private final MemberRepository members;
    private final ExpenseRepository expenses;
    private final ExpenseShareRepository shares;
    private final SettlementRepository settlements;

    public LedgerService(GroupRepository groups, MemberRepository members, ExpenseRepository expenses,
                         ExpenseShareRepository shares, SettlementRepository settlements) {
        this.groups = groups;
        this.members = members;
        this.expenses = expenses;
        this.shares = shares;
        this.settlements = settlements;
    }

    // ================= groups & members =================

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest req) {
        ExpenseGroup group = groups.save(new ExpenseGroup(req.name().trim()));
        if (req.members() != null) {
            for (String name : req.members()) {
                addMemberInternal(group.getId(), name);
            }
        }
        return toGroup(group);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> listGroups() {
        return groups.findAll().stream().map(this::toGroup).toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroup(Long groupId) {
        return toGroup(requireGroup(groupId));
    }

    @Transactional
    public MemberResponse addMember(Long groupId, AddMemberRequest req) {
        requireGroup(groupId);
        Member m = addMemberInternal(groupId, req.name());
        return new MemberResponse(m.getId(), m.getName());
    }

    private Member addMemberInternal(Long groupId, String rawName) {
        String name = rawName.trim();
        boolean exists = members.findByGroupIdOrderByIdAsc(groupId).stream()
                .anyMatch(m -> m.getName().equalsIgnoreCase(name));
        if (exists) {
            throw ApiException.conflict("A member named '" + name + "' already exists in this group");
        }
        return members.save(new Member(groupId, name));
    }

    // ================= expenses =================

    @Transactional
    public Outcome<ExpenseResponse> addExpense(Long groupId, CreateExpenseRequest req, String rawKey) {
        requireGroup(groupId);
        String key = normalizeKey(rawKey);
        long total = Money.toMinor(req.amount());
        String description = req.description().trim();

        if (key != null) {
            Optional<Expense> existing = expenses.findByGroupIdAndIdempotencyKey(groupId, key);
            if (existing.isPresent()) {
                Expense e = existing.get();
                if (e.getAmountMinor() != total || !e.getPayerId().equals(req.paidBy())
                        || !e.getDescription().equals(description)) {
                    throw ApiException.conflict("Idempotency-Key was already used with a different request");
                }
                return new Outcome<>(toExpenses(List.of(e)).get(0), false);
            }
        }

        Set<Long> memberIds = memberIdsOf(groupId);
        if (!memberIds.contains(req.paidBy())) {
            throw ApiException.unprocessable("Payer " + req.paidBy() + " is not a member of this group");
        }
        for (SplitInput s : req.splits()) {
            if (!memberIds.contains(s.memberId())) {
                throw ApiException.unprocessable("Member " + s.memberId() + " in splits is not a member of this group");
            }
        }

        List<SplitCalculator.Entry> entries = req.splits().stream()
                .map(s -> new SplitCalculator.Entry(s.memberId(), s.value())).toList();
        Map<Long, Long> computed = SplitCalculator.compute(total, req.splitType(), entries);

        Expense expense = expenses.save(new Expense(groupId, description, total, req.paidBy(), req.splitType(), key));
        computed.forEach((memberId, amount) -> shares.save(new ExpenseShare(expense.getId(), memberId, amount)));
        return new Outcome<>(toExpenses(List.of(expense)).get(0), true);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getExpense(Long groupId, Long expenseId) {
        requireGroup(groupId);
        Expense e = expenses.findById(expenseId)
                .filter(x -> x.getGroupId().equals(groupId))
                .orElseThrow(() -> ApiException.notFound("Expense " + expenseId + " not found in group " + groupId));
        return toExpenses(List.of(e)).get(0);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> listExpenses(Long groupId, int page, int size, Long paidBy) {
        requireGroup(groupId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Expense> result = paidBy == null
                ? expenses.findAllByGroupId(groupId, pageable)
                : expenses.findAllByGroupIdAndPayerId(groupId, paidBy, pageable);
        return new PageResponse<>(toExpenses(result.getContent()), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    // ================= balances & settlements =================

    @Transactional(readOnly = true)
    public BalancesResponse balances(Long groupId) {
        requireGroup(groupId);
        List<Member> ms = members.findByGroupIdOrderByIdAsc(groupId);
        Map<Long, Long> net = netBalances(groupId, ms);
        List<BalanceResponse> rows = ms.stream().map(m -> {
            long b = net.get(m.getId());
            String status = b > 0 ? "OWED" : b < 0 ? "OWES" : "SETTLED";
            return new BalanceResponse(m.getId(), m.getName(), Money.fromMinor(b), status);
        }).toList();
        return new BalancesResponse(groupId, rows);
    }

    @Transactional(readOnly = true)
    public SettlementPlanResponse suggestedSettlements(Long groupId) {
        requireGroup(groupId);
        List<Member> ms = members.findByGroupIdOrderByIdAsc(groupId);
        Map<Long, String> names = ms.stream().collect(Collectors.toMap(Member::getId, Member::getName));
        List<TransferResponse> transfers = DebtSimplifier.simplify(netBalances(groupId, ms)).stream()
                .map(t -> new TransferResponse(t.fromMemberId(), names.get(t.fromMemberId()),
                        t.toMemberId(), names.get(t.toMemberId()), Money.fromMinor(t.amountMinor())))
                .toList();
        return new SettlementPlanResponse(groupId, transfers.size(), transfers);
    }

    @Transactional
    public Outcome<SettlementResponse> recordSettlement(Long groupId, CreateSettlementRequest req, String rawKey) {
        requireGroup(groupId);
        String key = normalizeKey(rawKey);
        long amount = Money.toMinor(req.amount());

        if (key != null) {
            Optional<Settlement> existing = settlements.findByGroupIdAndIdempotencyKey(groupId, key);
            if (existing.isPresent()) {
                Settlement s = existing.get();
                if (s.getAmountMinor() != amount || !s.getFromMemberId().equals(req.fromMemberId())
                        || !s.getToMemberId().equals(req.toMemberId())) {
                    throw ApiException.conflict("Idempotency-Key was already used with a different request");
                }
                return new Outcome<>(toSettlement(s), false);
            }
        }

        if (req.fromMemberId().equals(req.toMemberId())) {
            throw ApiException.unprocessable("A member cannot pay themselves");
        }
        List<Member> ms = members.findByGroupIdOrderByIdAsc(groupId);
        Map<Long, String> names = ms.stream().collect(Collectors.toMap(Member::getId, Member::getName));
        if (!names.containsKey(req.fromMemberId()) || !names.containsKey(req.toMemberId())) {
            throw ApiException.unprocessable("Both payer and receiver must be members of this group");
        }

        Map<Long, Long> net = netBalances(groupId, ms);
        long fromOwes = Math.max(0, -net.get(req.fromMemberId()));
        long toIsOwed = Math.max(0, net.get(req.toMemberId()));
        if (amount > fromOwes) {
            throw ApiException.unprocessable(names.get(req.fromMemberId()) + " only owes "
                    + Money.fromMinor(fromOwes).toPlainString() + ", cannot pay " + Money.fromMinor(amount).toPlainString());
        }
        if (amount > toIsOwed) {
            throw ApiException.unprocessable(names.get(req.toMemberId()) + " is only owed "
                    + Money.fromMinor(toIsOwed).toPlainString() + ", cannot receive " + Money.fromMinor(amount).toPlainString());
        }

        Settlement saved = settlements.save(new Settlement(groupId, req.fromMemberId(), req.toMemberId(), amount, key));
        return new Outcome<>(toSettlement(saved), true);
    }

    // ================= ledger =================

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> ledger(Long groupId) {
        requireGroup(groupId);
        Map<Long, String> names = members.findByGroupIdOrderByIdAsc(groupId).stream()
                .collect(Collectors.toMap(Member::getId, Member::getName));
        List<LedgerEntryResponse> entries = new ArrayList<>();
        for (Expense e : expenses.findByGroupId(groupId)) {
            entries.add(new LedgerEntryResponse("EXPENSE", e.getId(),
                    e.getDescription() + " (paid by " + names.get(e.getPayerId()) + ")",
                    Money.fromMinor(e.getAmountMinor()), e.getCreatedAt()));
        }
        for (Settlement s : settlements.findByGroupIdOrderByCreatedAtAsc(groupId)) {
            entries.add(new LedgerEntryResponse("SETTLEMENT", s.getId(),
                    names.get(s.getFromMemberId()) + " paid " + names.get(s.getToMemberId()),
                    Money.fromMinor(s.getAmountMinor()), s.getCreatedAt()));
        }
        entries.sort(Comparator.comparing(LedgerEntryResponse::createdAt)
                .thenComparing(LedgerEntryResponse::type).thenComparing(LedgerEntryResponse::id));
        return entries;
    }

    // ================= helpers =================

    private ExpenseGroup requireGroup(Long groupId) {
        return groups.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group " + groupId + " not found"));
    }

    private Set<Long> memberIdsOf(Long groupId) {
        return members.findByGroupIdOrderByIdAsc(groupId).stream().map(Member::getId).collect(Collectors.toSet());
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) return null;
        String k = key.trim();
        if (k.length() > 100) throw ApiException.badRequest("Idempotency-Key must be at most 100 characters");
        return k;
    }

    /** balance = (paid for others) - (own shares) + (settlements paid) - (settlements received). Sums to zero. */
    private Map<Long, Long> netBalances(Long groupId, List<Member> ms) {
        Map<Long, Long> net = new LinkedHashMap<>();
        ms.forEach(m -> net.put(m.getId(), 0L));

        List<Expense> exps = expenses.findByGroupId(groupId);
        exps.forEach(e -> net.merge(e.getPayerId(), e.getAmountMinor(), Long::sum));
        if (!exps.isEmpty()) {
            List<Long> ids = exps.stream().map(Expense::getId).toList();
            shares.findByExpenseIdIn(ids).forEach(s -> net.merge(s.getMemberId(), -s.getAmountMinor(), Long::sum));
        }
        settlements.findByGroupIdOrderByCreatedAtAsc(groupId).forEach(s -> {
            net.merge(s.getFromMemberId(), s.getAmountMinor(), Long::sum);
            net.merge(s.getToMemberId(), -s.getAmountMinor(), Long::sum);
        });
        return net;
    }

    private GroupResponse toGroup(ExpenseGroup g) {
        List<MemberResponse> ms = members.findByGroupIdOrderByIdAsc(g.getId()).stream()
                .map(m -> new MemberResponse(m.getId(), m.getName())).toList();
        return new GroupResponse(g.getId(), g.getName(), ms, g.getCreatedAt());
    }

    private List<ExpenseResponse> toExpenses(List<Expense> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, List<ExpenseShare>> byExpense = shares
                .findByExpenseIdIn(list.stream().map(Expense::getId).toList()).stream()
                .collect(Collectors.groupingBy(ExpenseShare::getExpenseId));
        return list.stream().map(e -> new ExpenseResponse(
                e.getId(), e.getGroupId(), e.getDescription(), Money.fromMinor(e.getAmountMinor()),
                e.getPayerId(), e.getSplitType(),
                byExpense.getOrDefault(e.getId(), List.of()).stream()
                        .sorted(Comparator.comparing(ExpenseShare::getId))
                        .map(s -> new ShareResponse(s.getMemberId(), Money.fromMinor(s.getAmountMinor()))).toList(),
                e.getCreatedAt())).toList();
    }

    private SettlementResponse toSettlement(Settlement s) {
        return new SettlementResponse(s.getId(), s.getGroupId(), s.getFromMemberId(), s.getToMemberId(),
                Money.fromMinor(s.getAmountMinor()), s.getCreatedAt());
    }
}
