package com.splitwise.app.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default SmsSender - logs the message instead of sending anything. Active
 * whenever `app.sms.provider` is unset or explicitly "noop", which is the
 * default in dev/local/test so the whole OTP flow can be built and tested
 * without any real SMS vendor wired up yet.
 *
 * IMPORTANT: this is the only place the raw OTP code is ever allowed to appear
 * in logs/console output - nowhere else in the OTP flow should log the
 * plaintext code (see OtpService).
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.sms", name = "provider", havingValue = "noop", matchIfMissing = true)
public class NoOpSmsSender implements SmsSender {

    @Override
    public void send(String e164Phone, String message) {
        log.info("[NoOpSmsSender] Would send SMS to {}: {}", e164Phone, message);
    }
}
