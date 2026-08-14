package com.splitwise.app.billing;

import com.splitwise.app.config.GooglePlayBillingProperties;
import com.splitwise.app.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the fail-closed behavior when Google Play billing isn't configured.
 * The actual verify-against-Google's-servers path requires a live
 * AndroidPublisher client (real network/service-account credentials) and is
 * intentionally not unit tested here - it should be exercised via a
 * manual/staging test against a real Play Console sandbox purchase once
 * GOOGLE_PLAY_SERVICE_ACCOUNT_CREDENTIALS is configured.
 */
class GooglePlayBillingServiceTest {

    @Test
    @DisplayName("Rejects verification when Google Play billing is not configured (fails closed)")
    void verifyAndAcknowledge_throwsWhenNotConfigured() {

        GooglePlayBillingProperties properties = new GooglePlayBillingProperties();
        // packageName / serviceAccountCredentials left blank -> not configured

        GooglePlayBillingService service = new GooglePlayBillingService(properties);
        service.init(); // @PostConstruct isn't triggered automatically in a plain unit test

        assertThatThrownBy(() -> service.verifyAndAcknowledge("ai_credits_30", "some-token"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("isConfigured() is false when either package name or credentials path is blank")
    void isConfigured_falseWhenEitherFieldMissing() {

        GooglePlayBillingProperties properties = new GooglePlayBillingProperties();
        assertThat(properties.isConfigured()).isFalse();

        properties.setPackageName("in.splenza.app");
        assertThat(properties.isConfigured()).isFalse(); // credentials still missing

        properties.setServiceAccountCredentials("/path/to/creds.json");
        assertThat(properties.isConfigured()).isTrue();
    }
}
