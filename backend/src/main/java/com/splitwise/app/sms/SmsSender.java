package com.splitwise.app.sms;

/**
 * Abstraction over "send an SMS to a phone number". Every caller in the app
 * talks to this interface only, never to a specific SMS vendor's SDK/HTTP
 * client directly - mirrors the same pattern used for StorageService
 * (com.splitwise.app.storage) for the same reason: swapping providers later
 * should be a one-file, one-config-value change, not a rewrite.
 *
 * To add a real provider (MSG91, 2Factor.in, Twilio Verify, etc.) later: 1. Add
 * its SDK/HTTP client as a dependency. 2. Implement this interface, gated with
 * `@ConditionalOnProperty(prefix = "app.sms", name = "provider", havingValue =
 * "yourProviderName")`. 3. Set `app.sms.provider=yourProviderName` in config
 * for staging/prod.
 *
 * NoOpSmsSender (this package) is the only implementation right now and is
 * active by default - it just logs the code to the console, so the entire phone
 * OTP flow is fully testable without any SMS cost or vendor contract in place
 * yet.
 */
public interface SmsSender {

    void send(String e164Phone, String message);
}
