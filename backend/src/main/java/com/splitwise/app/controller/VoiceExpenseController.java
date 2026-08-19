package com.splitwise.app.controller;

import com.splitwise.app.dto.voiceexpense.VoiceExpenseResult;
import com.splitwise.app.service.VoiceExpenseService;
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
@RequestMapping("/api/v1/voice-expenses")
@RequiredArgsConstructor
@Tag(name = "Voice Expenses", description = "AI-powered voice recording -> expense draft")
public class VoiceExpenseController {

    private final VoiceExpenseService voiceExpenseService;

    @Operation(summary = "Parse a voice recording into an expense draft",
            description = "Spends one VOICE_EXPENSE AI credit (free daily allowance first, then the "
            + "shared purchased wallet). Never saves anything - always returns a draft for the "
            + "existing expense-creation UI to prefill. Returns 402 if no credit is available.")
    @PostMapping(consumes = "multipart/form-data")
    public VoiceExpenseResult transcribeAndParse(
            @RequestParam("file") MultipartFile file,
            @RequestParam("groupId") UUID groupId) {

        UUID userId = SecurityUtils.getCurrentUserId();

        log.info("Voice expense requested by user {} for group {}.", userId, groupId);

        return voiceExpenseService.process(userId, groupId, file);
    }
}
