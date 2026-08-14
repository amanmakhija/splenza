package com.splitwise.app.controller;

import com.splitwise.app.dto.receipt.ReceiptScanResult;
import com.splitwise.app.service.ReceiptScanService;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/receipt-scans")
@RequiredArgsConstructor
@Tag(name = "Receipt Scanning", description = "AI-powered receipt photo -> structured expense data")
public class ReceiptScanController {

    private final ReceiptScanService receiptScanService;

    @Operation(summary = "Scan a receipt photo and extract structured expense data",
            description = "Spends one RECEIPT_SCAN AI credit (free daily allowance first, then the shared "
            + "purchased wallet). Returns 402 if no credit is available.")
    @PostMapping(consumes = "multipart/form-data")
    public ReceiptScanResult scan(@RequestParam("file") MultipartFile file) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.info("Receipt scan requested by user {}.", userId);

        return receiptScanService.scan(userId, file);
    }
}
