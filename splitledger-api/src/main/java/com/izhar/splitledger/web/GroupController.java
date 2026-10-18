package com.izhar.splitledger.web;

import com.izhar.splitledger.dto.Dtos.*;
import com.izhar.splitledger.service.LedgerService;
import com.izhar.splitledger.service.LedgerService.Outcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Resources are nouns, methods are verbs. */
@RestController
@RequestMapping("/groups")
@Tag(name = "SplitLedger", description = "Groups, expenses, balances and settlements")
public class GroupController {

    private final LedgerService service;

    public GroupController(LedgerService service) {
        this.service = service;
    }

    // ---------- groups & members ----------

    @GetMapping
    @Operation(summary = "List all groups")
    public List<GroupResponse> listGroups() {
        return service.listGroups();
    }

    @PostMapping
    @Operation(summary = "Create a group (members optional)")
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        GroupResponse group = service.createGroup(request);
        return ResponseEntity.created(URI.create("/groups/" + group.id())).body(group);
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "Get a group and its members")
    public GroupResponse getGroup(@PathVariable Long groupId) {
        return service.getGroup(groupId);
    }

    @PostMapping("/{groupId}/members")
    @Operation(summary = "Add a member to a group")
    public ResponseEntity<MemberResponse> addMember(@PathVariable Long groupId,
                                                    @Valid @RequestBody AddMemberRequest request) {
        MemberResponse member = service.addMember(groupId, request);
        return ResponseEntity.created(URI.create("/groups/" + groupId)).body(member);
    }

    // ---------- expenses ----------

    @PostMapping("/{groupId}/expenses")
    @Operation(summary = "Add an expense (EQUAL, EXACT, PERCENTAGE or SHARES split)",
            description = "Send an Idempotency-Key header to make retries safe: repeating the same request "
                    + "returns the original expense (200, Idempotent-Replay: true) instead of creating a duplicate.")
    public ResponseEntity<ExpenseResponse> addExpense(@PathVariable Long groupId,
                                                      @Valid @RequestBody CreateExpenseRequest request,
                                                      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Outcome<ExpenseResponse> outcome = service.addExpense(groupId, request, idempotencyKey);
        if (outcome.created()) {
            return ResponseEntity.created(URI.create("/groups/" + groupId + "/expenses/" + outcome.body().id()))
                    .body(outcome.body());
        }
        return ResponseEntity.ok().header("Idempotent-Replay", "true").body(outcome.body());
    }

    @GetMapping("/{groupId}/expenses")
    @Operation(summary = "List expenses (newest first, paginated, optional paidBy filter)")
    public PageResponse<ExpenseResponse> listExpenses(@PathVariable Long groupId,
                                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                                      @RequestParam(required = false) Long paidBy) {
        return service.listExpenses(groupId, page, size, paidBy);
    }

    @GetMapping("/{groupId}/expenses/{expenseId}")
    @Operation(summary = "Get one expense with its per-member shares")
    public ExpenseResponse getExpense(@PathVariable Long groupId, @PathVariable Long expenseId) {
        return service.getExpense(groupId, expenseId);
    }

    // ---------- balances & settlements ----------

    @GetMapping("/{groupId}/balances")
    @Operation(summary = "Net balance of every member (positive = is owed, negative = owes)")
    public BalancesResponse balances(@PathVariable Long groupId) {
        return service.balances(groupId);
    }

    @GetMapping("/{groupId}/settlements/suggested")
    @Operation(summary = "Smart settle-up plan: the fewest practical payments that clear every debt")
    public SettlementPlanResponse suggestedSettlements(@PathVariable Long groupId) {
        return service.suggestedSettlements(groupId);
    }

    @PostMapping("/{groupId}/settlements")
    @Operation(summary = "Record a payment between two members",
            description = "Supports the Idempotency-Key header. A member cannot pay more than they owe.")
    public ResponseEntity<SettlementResponse> recordSettlement(@PathVariable Long groupId,
                                                               @Valid @RequestBody CreateSettlementRequest request,
                                                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Outcome<SettlementResponse> outcome = service.recordSettlement(groupId, request, idempotencyKey);
        if (outcome.created()) {
            return ResponseEntity.created(URI.create("/groups/" + groupId + "/ledger")).body(outcome.body());
        }
        return ResponseEntity.ok().header("Idempotent-Replay", "true").body(outcome.body());
    }

    // ---------- audit trail ----------

    @GetMapping("/{groupId}/ledger")
    @Operation(summary = "Append-only history of every expense and settlement, oldest first")
    public List<LedgerEntryResponse> ledger(@PathVariable Long groupId) {
        return service.ledger(groupId);
    }
}
