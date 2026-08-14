package com.splitwise.app.dto.rtdn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Decoded RTDN payload from Google Play - see
 * https://developer.android.com/google/play/billing/rtdn-reference.
 * subscriptionNotification is for subscriptions (not used by Splenza, which
 * only sells consumable managed products) - oneTimeProductNotification is the
 * one that matters here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeveloperNotification {

    public String version;
    public String packageName;
    public long eventTimeMillis;

    public OneTimeProductNotification oneTimeProductNotification;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OneTimeProductNotification {

        public String version;

        /**
         * 1 = ONE_TIME_PRODUCT_PURCHASED (not acted on here - purchases are
         * credited synchronously via /purchases/verify, not via RTDN). 2 =
         * ONE_TIME_PRODUCT_CANCELED - the purchase was refunded/voided.
         */
        public int notificationType;

        public String purchaseToken;
        public String sku;
    }
}
