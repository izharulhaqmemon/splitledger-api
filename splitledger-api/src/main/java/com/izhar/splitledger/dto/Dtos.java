package com.izhar.splitledger.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.izhar.splitledger.domain.SplitType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Request and response shapes. Requests carry the syntactic validation rules (the first gate). */
public final class Dtos {

    private Dtos() {
    }

    // ---------- requests ----------

    public record CreateGroupRequest(
            @Schema(example = "Murree Trip") @NotBlank @Size(max = 80) String name,
            @Schema(example = "[\"Ali\", \"Sara\", \"Hamza\"]") @Size(max = 50) List<@NotBlank @Size(max = 60) String> members) {
    }

    public record AddMemberRequest(@Schema(example = "Ayesha") @NotBlank @Size(max = 60) String name) {
    }

    @Schema(description = "EQUAL: value ignored. EXACT: amount this member owes. PERCENTAGE: percent (all must total 100). SHARES: relative weight.")
    public record SplitInput(
            @NotNull Long memberId,
            @DecimalMin("0") @Digits(integer = 10, fraction = 4) BigDecimal value) {
    }

    public record CreateExpenseRequest(
            @Schema(example = "Hotel (3 nights)") @NotBlank @Size(max = 120) String description,
            @Schema(example = "24000.00") @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @Schema(example = "1") @NotNull Long paidBy,
            @NotNull SplitType splitType,
            @NotEmpty @Valid List<SplitInput> splits) {
    }

    public record CreateSettlementRequest(
            @Schema(example = "2") @NotNull Long fromMemberId,
            @Schema(example = "1") @NotNull Long toMemberId,
            @Schema(example = "1500.00") @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount) {
    }

    // ---------- responses ----------

    public record MemberResponse(Long id, String name) {
    }

    public record GroupResponse(Long id, String name, List<MemberResponse> members, Instant createdAt) {
    }

    public record ShareResponse(Long memberId, BigDecimal amount) {
    }

    public record ExpenseResponse(Long id, Long groupId, String description, BigDecimal amount, Long paidBy,
                                  SplitType splitType, List<ShareResponse> shares, Instant createdAt) {
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
    }

    public record BalanceResponse(Long memberId, String name, BigDecimal balance, String status) {
    }

    @Schema(description = "balance > 0: the member is owed money. balance < 0: the member owes money.")
    public record BalancesResponse(Long groupId, List<BalanceResponse> balances) {
    }

    public record TransferResponse(Long fromMemberId, String fromName, Long toMemberId, String toName, BigDecimal amount) {
    }

    public record SettlementPlanResponse(Long groupId, int transferCount, List<TransferResponse> transfers) {
    }

    public record SettlementResponse(Long id, Long groupId, Long fromMemberId, Long toMemberId,
                                     BigDecimal amount, Instant createdAt) {
    }

    public record LedgerEntryResponse(String type, Long id, String description, BigDecimal amount, Instant createdAt) {
    }

    public record FieldIssue(String field, String issue) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ErrorResponse(Instant timestamp, int status, String error, String message, String path,
                                List<FieldIssue> fieldErrors) {
    }
}
