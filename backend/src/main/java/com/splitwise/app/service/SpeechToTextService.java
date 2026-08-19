package com.splitwise.app.service;

/**
 * Turns audio into text - knows nothing about expenses, splits, or
 * participants. Keeping this narrow is what makes the STT provider swappable
 * later (e.g. to a cheaper/faster model) without touching any parsing logic
 * downstream (see ExpenseNlpEngine, which only ever sees the resulting
 * transcript string).
 */
public interface SpeechToTextService {

    /**
     * @param audioBytes the raw audio file bytes, as uploaded by the client
     * @param mimeType the client-reported content type (e.g. "audio/m4a",
     * "audio/wav", "audio/x-caf") - forwarded as-is to the provider, which
     * infers format from it
     * @return the transcript text
     */
    String transcribe(byte[] audioBytes, String mimeType);
}
