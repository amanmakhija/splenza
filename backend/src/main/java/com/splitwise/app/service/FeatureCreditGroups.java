package com.splitwise.app.service;

import java.util.Map;

import com.splitwise.app.enums.AiFeature;

/**
 * Which features share a free daily credit pool with each other. A hardcoded
 * code-level mapping rather than a config value - which features are grouped
 * together is a product/code decision (does a new feature belong in the
 * existing pool, or does it deserve its own?), not an operational tuning knob
 * the way the pool's actual daily limit is (see
 * AiCreditProperties.freeDailyLimits, keyed by group name).
 *
 * A feature not listed here defaults to its own name as its group - i.e.
 * "ungrouped" behaves exactly like today, with its own independent allowance.
 * This is what keeps the door open for a future feature (e.g. a premium AI
 * feature) to NOT share this pool: simply don't add it here.
 */
public final class FeatureCreditGroups {

    /**
     * The shared pool RECEIPT_SCAN and VOICE_EXPENSE both draw from - see class
     * javadoc. Add a new feature here (not a new group name) when it should
     * share this same pool; a feature needing its own separate allowance should
     * simply be left out entirely.
     */
    public static final String AI_ASSIST = "AI_ASSIST";

    private static final Map<AiFeature, String> GROUPS_BY_FEATURE = Map.of(
            AiFeature.RECEIPT_SCAN, AI_ASSIST,
            AiFeature.VOICE_EXPENSE, AI_ASSIST
    );

    private FeatureCreditGroups() {
    }

    /**
     * @return the credit group this feature's free daily usage is tracked under
     * - defaults to the feature's own name (a "group of one") if it isn't
     * explicitly grouped with anything else
     */
    public static String groupFor(AiFeature feature) {
        return GROUPS_BY_FEATURE.getOrDefault(feature, feature.name());
    }
}
