package com.splitwise.app.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.splitwise.app.entity.Expense;
import com.splitwise.app.entity.ExpenseParticipant;
import com.splitwise.app.entity.GroupMember;
import com.splitwise.app.entity.Settlement;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.ExpenseRepository;
import com.splitwise.app.repository.GroupMemberRepository;
import com.splitwise.app.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Produces CSV (Splitwise-format-compatible, for round-tripping) and PDF
 * reports for a group's ledger. CSV reconstruction is the exact inverse of
 * ImportService: for each expense, the payer's column value is (amount -
 * theirShare), everyone else's is (-theirShare); settlements get their own
 * signed pair. This keeps the two formats symmetric.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final com.splitwise.app.repository.GroupRepository groupRepository;

    private record ExportRow(String date, String description, String category, BigDecimal cost,
            String currency, Map<String, BigDecimal> memberValues) {

    }

    @Transactional(readOnly = true)
    public String buildGroupCsv(UUID actingUserId, UUID groupId) {
        assertMember(groupId, actingUserId);
        List<String> memberNames = activeMemberNames(groupId);
        List<ExportRow> rows = buildRows(groupId);

        StringBuilder sb = new StringBuilder();
        sb.append("Date,Description,Category,Cost,Currency");
        for (String name : memberNames) {
            sb.append(",").append(csvEscape(name));
        }
        sb.append("\n");

        for (ExportRow row : rows) {
            sb.append(row.date()).append(",")
                    .append(csvEscape(row.description())).append(",")
                    .append(csvEscape(row.category())).append(",")
                    .append(row.cost().toPlainString()).append(",")
                    .append(row.currency());
            for (String name : memberNames) {
                BigDecimal value = row.memberValues().getOrDefault(name, BigDecimal.ZERO);
                sb.append(",").append(value.toPlainString());
            }
            sb.append("\n");
        }

        log.info(
                "User {} exported CSV report for group {}.",
                actingUserId,
                groupId
        );

        return sb.toString();
    }

    @Transactional(readOnly = true)
    public byte[] buildGroupPdf(UUID actingUserId, UUID groupId) {
        assertMember(groupId, actingUserId);
        String groupName = groupRepository.findById(groupId).map(g -> g.getName()).orElse("Group");
        List<ExportRow> rows = buildRows(groupId);
        BigDecimal totalAmount = rows.stream()
                .filter(r -> !r.category().equalsIgnoreCase("Payment"))
                .map(ExportRow::cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        try {
            Document document = new Document(PageSize.A4, 36, 36, 54, 36);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(0x6C, 0x63, 0xFF));
            Font subFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.GRAY);
            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
            Font cellFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);

            Paragraph title = new Paragraph("Splenza — " + groupName, titleFont);
            document.add(title);
            document.add(new Paragraph("Expense report - " + rows.size() + " entries, total ₹" + totalAmount, subFont));
            document.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2f, 4f, 2.5f, 2f, 2f});

            for (String header : new String[]{"Date", "Description", "Category", "Amount", "Currency"}) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setBackgroundColor(new Color(0x6C, 0x63, 0xFF));
                cell.setPadding(6);
                table.addCell(cell);
            }

            for (ExportRow row : rows) {
                table.addCell(cellOf(row.date(), cellFont));
                table.addCell(cellOf(row.description(), cellFont));
                table.addCell(cellOf(row.category(), cellFont));
                table.addCell(cellOf(row.cost().toPlainString(), cellFont));
                table.addCell(cellOf(row.currency(), cellFont));
            }

            document.add(table);
            document.close();

            log.info(
                    "User {} exported PDF report for group {}.",
                    actingUserId,
                    groupId
            );

            return out.toByteArray();
        } catch (DocumentException e) {
            log.error(
                    "Failed to generate PDF report for group {}.",
                    groupId,
                    e
            );
            throw new IllegalStateException("Failed to generate PDF report", e);
        }
    }

    private PdfPCell cellOf(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        return cell;
    }

    private List<ExportRow> buildRows(UUID groupId) {
        List<ExportRow> rows = new ArrayList<>();

        for (Expense e : expenseRepository.findByGroupIdAndDeletedFalseOrderByExpenseDateDesc(groupId)) {
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            BigDecimal payerShare = e.getParticipants().stream()
                    .filter(p -> p.getUser().getId().equals(e.getPaidBy().getId()))
                    .map(ExpenseParticipant::getShareAmount)
                    .findFirst().orElse(BigDecimal.ZERO);

            values.merge(e.getPaidBy().getName(), e.getAmount().subtract(payerShare), BigDecimal::add);
            for (ExpenseParticipant p : e.getParticipants()) {
                if (p.getUser().getId().equals(e.getPaidBy().getId())) {
                    continue;
                }
                values.merge(p.getUser().getName(), p.getShareAmount().negate(), BigDecimal::add);
            }

            rows.add(new ExportRow(
                    e.getExpenseDate().toString(),
                    e.getTitle(),
                    e.getCategory() != null ? e.getCategory().getName() : "General",
                    e.getAmount(),
                    e.getCurrency(),
                    values
            ));
        }

        for (Settlement s : settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId)) {
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            values.put(s.getPaidBy().getName(), s.getAmount());
            values.put(s.getPaidTo().getName(), s.getAmount().negate());

            rows.add(new ExportRow(
                    s.getSettledAt().toString().substring(0, 10),
                    s.getNote() != null ? s.getNote() : (s.getPaidBy().getName() + " paid " + s.getPaidTo().getName()),
                    "Payment",
                    s.getAmount(),
                    s.getCurrency(),
                    values
            ));
        }

        rows.sort(Comparator.comparing(ExportRow::date));
        return rows;
    }

    private List<String> activeMemberNames(UUID groupId) {
        return groupMemberRepository.findByGroupIdAndLeftAtIsNull(groupId).stream()
                .map(GroupMember::getUser)
                .map(u -> u.getName())
                .distinct()
                .toList();
    }

    private void assertMember(UUID groupId, UUID userId) {
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId)) {
            throw ApiException.forbidden("You are not a member of this group");
        }
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
