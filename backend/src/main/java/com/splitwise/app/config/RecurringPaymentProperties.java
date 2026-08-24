package com.splitwise.app.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code recurring.*} config block driving both the recurring-payment
 * execution cron and the nightly recurrence-detection job.
 *
 * <p>The two feature flags ({@code detection.enabled}, {@code execution.enabled})
 * let either job be turned off in prod without a redeploy; {@code detection.dryRun}
 * runs the full detection pipeline but logs candidates instead of persisting them,
 * for safe validation of the algorithm against real data.</p>
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "recurring")
@Data
public class RecurringPaymentProperties {

    private Detection detection = new Detection();
    private Execution execution = new Execution();

    /** How far back (months) the detector looks at a user's own expenses. */
    private int windowMonths = 18;

    /** Minimum confidence [0,1] for a detected pattern to be surfaced. */
    private double minConfidence = 0.90;

    /** Days a dismissed suggestion stays suppressed before it may resurface. */
    private int dismissCooldownDays = 60;

    /** Lifetime cap on how many times one cluster may be re-suggested after dismissals. */
    private int maxSuggestionsPerCluster = 2;

    private Weights weights = new Weights();

    @Data
    public static class Detection {
        private boolean enabled = true;
        private boolean dryRun = false;
        /** Default: 03:30 daily. */
        private String cron = "0 30 3 * * *";
    }

    @Data
    public static class Execution {
        private boolean enabled = true;
        /** Default: 06:00 daily. */
        private String cron = "0 0 6 * * *";
    }

    /**
     * Confidence-formula weights (must conceptually sum to 1.0):
     * {@code 0.45*regularity + 0.30*repetition + 0.25*consistency}.
     */
    @Data
    public static class Weights {
        private double regularity = 0.45;
        private double repetition = 0.30;
        private double consistency = 0.25;
    }

    @PostConstruct
    void logConfiguration() {
        log.info("Loaded recurring config: detection(enabled={}, dryRun={}, cron={}), "
                        + "execution(enabled={}, cron={}), windowMonths={}, minConfidence={}, "
                        + "dismissCooldownDays={}, maxSuggestionsPerCluster={}",
                detection.enabled, detection.dryRun, detection.cron,
                execution.enabled, execution.cron, windowMonths, minConfidence,
                dismissCooldownDays, maxSuggestionsPerCluster);
    }
}
