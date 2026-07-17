package com.splitwise.app.controller;

import com.splitwise.app.service.ExportService;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
@Tag(name = "Export", description = "CSV and PDF report exports")
public class ExportController {

    private final ExportService exportService;

    @GetMapping("/csv/group/{groupId}")
    public ResponseEntity<byte[]> exportGroupCsv(@PathVariable UUID groupId) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("CSV export requested by user {} for group {}.",
                userId, groupId);

        String csv = exportService.buildGroupCsv(userId, groupId);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        log.info("CSV exported successfully for group {} by user {}.",
                groupId, userId);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("splenza-export.csv")
                                .build()
                                .toString()
                )
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    @GetMapping("/pdf/group/{groupId}")
    public ResponseEntity<byte[]> exportGroupPdf(@PathVariable UUID groupId) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.debug("PDF export requested by user {} for group {}.",
                userId, groupId);

        byte[] pdf = exportService.buildGroupPdf(userId, groupId);

        log.info("PDF exported successfully for group {} by user {}.",
                groupId, userId);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("splenza-report.pdf")
                                .build()
                                .toString()
                )
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
