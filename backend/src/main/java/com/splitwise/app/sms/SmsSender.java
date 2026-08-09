package com.splitwise.app.sms;

/**
 * Abstraction over "send an SMS to a phone number". Every caller in the app
 * talks to this interface only, never to a specific SMS vendor's SDK/HTTP
 * client directly - mirrors the same pattern used for StorageService
 * (com.splitwise.app.storage) for the same reason: swapping providers later
 * should be a one-file, one-config-value change, not a rewrite.
 *
 * To add a new provider: 1. Add its SDK/HTTP client as a dependency. 2.
 * Implement this interface, gated with `@ConditionalOnProperty(prefix =
 * "app.sms", name = "provider", havingValue = "yourProvider")`. 3. Set
 * `app.sms.provider=yourProvider` in config for staging/prod.
 *
 * NoOpSmsSender (this package) is the default - logs the message instead of
 * sending anything, active whenever `app.sms.provider` is unset or "noop".
 * TwoFactorSmsSender is the current real implementation
 * (`app.sms.provider=twofactor`) - use it as the template for adding the next
 * provider when 2Factor's pricing/limits stop making sense: same interface,
 * same OtpService call sites, zero changes needed outside the sms package.
 */
public interface SmsSender {

    void send(String e164Phone, String message);
}
