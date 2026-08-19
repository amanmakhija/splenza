package com.splitwise.app.enums;

/**
 * Status of a POST /api/v1/voice-expenses parse attempt. Never anything else -
 * the AI/deterministic pipeline either produced a usable draft (OK) or needs
 * the user to fill a gap (NEEDS_CLARIFICATION). There's no "FAILED" status: a
 * pipeline failure (STT error, AI error) is a thrown exception / non-2xx
 * response, not a draft status, since no draft or transcript exists to return
 * in that case.
 */
public enum VoiceExpenseStatus {
    OK,
    NEEDS_CLARIFICATION
}