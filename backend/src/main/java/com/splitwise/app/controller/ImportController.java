package com.splitwise.app.controller;

import com.splitwise.app.dto.importcsv.ExecuteImportRequest;
import com.splitwise.app.dto.importcsv.ImportResultResponse;
import com.splitwise.app.service.ImportService;
import com.splitwise.app.util.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
@Tag(name = "Import", description = "Splitwise CSV migration import")
public class ImportController {

    private final ImportService importService;

    @PostMapping("/execute")
    public ResponseEntity<ImportResultResponse> execute(@Valid @RequestBody ExecuteImportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(importService.execute(SecurityUtils.getCurrentUserId(), request));
    }
}
