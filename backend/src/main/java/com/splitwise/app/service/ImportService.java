package com.splitwise.app.service;

import com.splitwise.app.dto.expense.CreateExpenseRequest;
import com.splitwise.app.dto.expense.ExpenseParticipantInput;
import com.splitwise.app.dto.group.CreateGroupRequest;
import com.splitwise.app.dto.importcsv.*;
import com.splitwise.app.entity.*;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Imports a parsed Splitwise CSV export. The CSV format gives, per row, each
 * member's NET value for that expense (positive = they effectively fronted
 * money, negative = they owe their share). By construction every row's values
 * sum to exactly zero, which lets us reconstruct the original payer and exact
 * per-person shares with no ambiguity: - payer = the member with the (single)
 * positive value - payer's own share = cost - theirValue - every other
 * participant's share = -theirValue "Payment" category rows are settlements
 * rather than expenses: the positive-value member paid the negative-value
 * member the row's cost.
 *
 * Frontend parses the CSV client-side (so we don't need multipart upload
 * handling) and sends the structured rows + a member-name-to-user-id mapping
 * here in one JSON request.
 */
@Service
@RequiredArgsConstructor
public class ImportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SettlementRepository settlementRepository;
    private final ImportHistoryRepository importHistoryRepository;
    private final ExpenseService expenseService;
    private final GroupService groupService;
    private final ActivityLogService activityLogService;

    @Transactional
    public ImportResultResponse execute(UUID actingUserId, ExecuteImportRequest request) {
        validateMapping(actingUserId, request.getMemberMapping());
        UUID groupId = resolveGroup(actingUserId, request);
        Map<String, UUID> mapping = request.getMemberMapping();

        List<ImportRowError> errors = new ArrayList<>();
        int imported = 0;

        List<ImportRowRequest> rows = request.getRows();
        for (int i = 0; i < rows.size(); i++) {
            ImportRowRequest row = rows.get(i);
            try {
                processRow(actingUserId, groupId, row, mapping);
                imported++;
            } catch (Exception ex) {
                errors.add(ImportRowError.builder()
                        .rowIndex(i)
                        .description(row.getDescription())
                        .reason(ex.getMessage() != null ? ex.getMessage() : "Unknown error")
                        .build());
            }
        }

        int failed = errors.size();
        String status = failed == 0 ? "SUCCESS" : (imported == 0 ? "FAILED" : "PARTIAL");

        ImportHistory history = ImportHistory.builder()
                .user(userRepository.getReferenceById(actingUserId))
                .source("SPLITWISE_CSV")
                .fileName(request.getFileName())
                .status(status)
                .totalRows(rows.size())
                .importedRows(imported)
                .failedRows(failed)
                .errorReport(new ArrayList<>(errors))
                .build();
        history = importHistoryRepository.save(history);

        activityLogService.log(groupId, actingUserId, ActivityLog.ActionType.IMPORT_COMPLETED, history.getId(),
                Map.of("totalRows", rows.size(), "importedRows", imported, "failedRows", failed));

        return ImportResultResponse.builder()
                .importId(history.getId())
                .groupId(groupId)
                .totalRows(rows.size())
                .importedRows(imported)
                .failedRows(failed)
                .errors(errors)
                .build();
    }

    // ---------------- row processing ----------------
    private void processRow(UUID actingUserId, UUID groupId, ImportRowRequest row, Map<String, UUID> mapping) {
        Map<String, BigDecimal> nonZeroValues = new LinkedHashMap<>();
        row.getMemberValues().forEach((name, value) -> {
            if (value != null && value.compareTo(ZERO) != 0) {
                nonZeroValues.put(name, value);
            }
        });

        if (nonZeroValues.isEmpty()) {
            throw new IllegalArgumentException("Row has no non-zero member values - nothing to import");
        }

        boolean isPayment = row.getCategory() != null && row.getCategory().equalsIgnoreCase("Payment");

        if (isPayment) {
            processSettlementRow(actingUserId, groupId, row, nonZeroValues, mapping);
        } else {
            processExpenseRow(actingUserId, groupId, row, nonZeroValues, mapping);
        }
    }

    private void processSettlementRow(UUID actingUserId, UUID groupId, ImportRowRequest row,
            Map<String, BigDecimal> values, Map<String, UUID> mapping) {
        String payerName = null, payeeName = null;
        for (var entry : values.entrySet()) {
            if (entry.getValue().compareTo(ZERO) > 0) {
                if (payerName != null) {
                    throw new IllegalArgumentException("More than one payer found for this settlement");
                }
                payerName = entry.getKey();
            } else {
                if (payeeName != null) {
                    throw new IllegalArgumentException("More than one payee found for this settlement");
                }
                payeeName = entry.getKey();
            }
        }
        if (payerName == null || payeeName == null) {
            throw new IllegalArgumentException("Could not determine payer and payee for this settlement");
        }

        UUID payerId = mustMapUser(mapping, payerName);
        UUID payeeId = mustMapUser(mapping, payeeName);

        Settlement settlement = Settlement.builder()
                .group(groupId != null ? groupRepository.getReferenceById(groupId) : null)
                .paidBy(userRepository.getReferenceById(payerId))
                .paidTo(userRepository.getReferenceById(payeeId))
                .amount(row.getCost().abs())
                .currency(row.getCurrency() != null ? row.getCurrency() : "INR")
                .note(row.getDescription() + " (imported)")
                .settledAt(row.getDate().atStartOfDay(ZoneOffset.UTC).toInstant())
                .createdBy(userRepository.getReferenceById(actingUserId))
                .build();
        settlementRepository.save(settlement);
    }

    private void processExpenseRow(UUID actingUserId, UUID groupId, ImportRowRequest row,
            Map<String, BigDecimal> values, Map<String, UUID> mapping) {
        String payerName = null;
        BigDecimal payerValue = null;
        for (var entry : values.entrySet()) {
            if (entry.getValue().compareTo(ZERO) > 0) {
                if (payerName != null) {
                    throw new IllegalArgumentException(
                            "This expense has more than one contributor - multi-payer expenses aren't supported by the importer");
                }
                payerName = entry.getKey();
                payerValue = entry.getValue();
            }
        }
        if (payerName == null) {
            throw new IllegalArgumentException("Could not determine who paid for this expense");
        }

        UUID payerId = mustMapUser(mapping, payerName);

        List<ExpenseParticipantInput> participants = new ArrayList<>();
        for (var entry : values.entrySet()) {
            UUID userId = mustMapUser(mapping, entry.getKey());
            BigDecimal shareAmount = entry.getKey().equals(payerName)
                    ? row.getCost().subtract(payerValue)
                    : entry.getValue().negate();
            ExpenseParticipantInput p = new ExpenseParticipantInput();
            p.setUserId(userId);
            p.setAmount(shareAmount);
            participants.add(p);
        }

        CreateExpenseRequest req = new CreateExpenseRequest();
        req.setGroupId(groupId);
        req.setTitle(row.getDescription());
        req.setAmount(row.getCost());
        req.setCurrency(row.getCurrency() != null ? row.getCurrency() : "INR");
        req.setCategoryId(null);
        req.setNotes(row.getCategory() != null ? "Imported from Splitwise CSV - category: " + row.getCategory() : "Imported from Splitwise CSV");
        req.setExpenseDate(row.getDate());
        req.setPaidBy(payerId);
        req.setSplitType(Expense.SplitType.EXACT);
        req.setParticipants(participants);

        expenseService.create(actingUserId, req, false);
    }

    // ---------------- validation / setup ----------------
    private void validateMapping(UUID actingUserId, Map<String, UUID> mapping) {
        if (mapping == null || mapping.isEmpty()) {
            throw ApiException.badRequest("You must map every CSV member to a Splenza user");
        }
        long selfCount = mapping.values().stream().filter(id -> id.equals(actingUserId)).count();
        if (selfCount != 1) {
            throw ApiException.badRequest("Map exactly one CSV column to yourself before importing");
        }
        Set<UUID> distinctTargets = new HashSet<>(mapping.values());
        if (distinctTargets.size() != mapping.values().size()) {
            throw ApiException.badRequest("Each CSV member must be mapped to a different person - the same person can't be mapped twice");
        }
        for (UUID userId : mapping.values()) {
            if (!userRepository.existsById(userId)) {
                throw ApiException.badRequest("One of the mapped users no longer exists");
            }
        }
    }

    private UUID resolveGroup(UUID actingUserId, ExecuteImportRequest request) {
        boolean hasGroupId = request.getGroupId() != null;
        boolean hasNewGroupName = request.getNewGroupName() != null && !request.getNewGroupName().isBlank();

        if (hasGroupId == hasNewGroupName) {
            throw ApiException.badRequest("Provide either an existing groupId or a newGroupName, not both or neither");
        }

        if (hasGroupId) {
            groupRepository.findById(request.getGroupId())
                    .filter(g -> !g.isDeleted())
                    .orElseThrow(() -> ApiException.badRequest("Invalid group"));
            if (!groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(request.getGroupId(), actingUserId)) {
                throw ApiException.forbidden("You are not a member of this group");
            }
            return request.getGroupId();
        }

        CreateGroupRequest createReq = new CreateGroupRequest();
        createReq.setName(request.getNewGroupName());
        List<UUID> otherMembers = request.getMemberMapping().values().stream()
                .filter(id -> !id.equals(actingUserId))
                .distinct()
                .toList();
        createReq.setMemberIds(otherMembers);

        return groupService.create(actingUserId, createReq).getId();
    }

    private UUID mustMapUser(Map<String, UUID> mapping, String csvMemberName) {
        UUID id = mapping.get(csvMemberName);
        if (id == null) {
            throw new IllegalArgumentException("No user mapped for CSV member \"" + csvMemberName + "\"");
        }
        return id;
    }
}
