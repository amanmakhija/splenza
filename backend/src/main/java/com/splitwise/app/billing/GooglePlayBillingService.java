package com.splitwise.app.billing;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.api.services.androidpublisher.model.ProductPurchase;
import com.google.api.services.androidpublisher.model.ProductPurchasesAcknowledgeRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.splitwise.app.config.GooglePlayBillingProperties;
import com.splitwise.app.exception.ApiException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Verifies AI-credit purchase tokens server-to-server against the Google Play
 * Developer API (purchases.products.get) and acknowledges them
 * (purchases.products.acknowledge). Never trusts the client's own claim that a
 * purchase succeeded - see AiCreditController#verifyPurchase.
 *
 * Google auto-refunds any purchase that isn't acknowledged within 3 days -
 * that's separate from the client calling finishTransaction() on-device (which
 * only clears the local purchase cache, not Google's server-side
 * acknowledgement), so step 2 below matters even though the client also calls
 * finishTransaction() itself.
 */
@Slf4j
@Service
public class GooglePlayBillingService {

    private static final String APPLICATION_NAME = "Splenza";

    private final GooglePlayBillingProperties properties;
    private AndroidPublisher androidPublisher;

    public GooglePlayBillingService(GooglePlayBillingProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        if (!properties.isConfigured()) {
            // Fails closed - verifyPurchase() below rejects everything until
            // this is configured, rather than falling back to "no check".
            return;
        }

        try (InputStream credentialsStream = new FileInputStream(properties.getServiceAccountCredentials())) {

            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream)
                    .createScoped(AndroidPublisherScopes.ANDROIDPUBLISHER);

            HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();

            this.androidPublisher = new AndroidPublisher.Builder(
                    transport,
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            log.info("Google Play Android Publisher client initialized for package '{}'.",
                    properties.getPackageName());

        } catch (Exception ex) {
            log.error("Failed to initialize Google Play Android Publisher client. "
                    + "Purchase verification will reject all requests until this is fixed.", ex);
            this.androidPublisher = null;
        }
    }

    /**
     * Verifies {@code purchaseToken} for {@code productId} against Google's
     * servers. Throws ApiException.purchaseVerificationFailed() (400) if the
     * purchase can't be verified, is pending/cancelled, or doesn't match the
     * expected product - the caller must NOT credit the wallet in that case. On
     * success, also acknowledges the purchase so Google doesn't auto-refund it.
     */
    public ProductPurchase verifyAndAcknowledge(String productId, String purchaseToken) {

        if (androidPublisher == null) {
            log.error("Rejected purchase verification because Google Play billing is not configured.");
            throw ApiException.purchaseVerificationFailed(
                    "Purchase verification is temporarily unavailable. Please try again shortly.");
        }

        ProductPurchase purchase;

        try {
            purchase = androidPublisher.purchases()
                    .products()
                    .get(properties.getPackageName(), productId, purchaseToken)
                    .execute();
        } catch (IOException ex) {
            log.warn("Google Play purchase verification call failed for productId={}.", productId, ex);
            throw ApiException.purchaseVerificationFailed(
                    "Could not verify this purchase with Google Play. Please try again.");
        }

        // purchaseState: 0 = purchased, 1 = cancelled, 2 = pending.
        if (purchase.getPurchaseState() == null || purchase.getPurchaseState() != 0) {
            log.warn("Rejected purchase with non-purchased state: productId={}, purchaseState={}.",
                    productId, purchase.getPurchaseState());
            throw ApiException.purchaseVerificationFailed(
                    "This purchase has not completed successfully.");
        }

        // acknowledgementState: 0 = not yet acknowledged, 1 = acknowledged.
        if (purchase.getAcknowledgementState() != null && purchase.getAcknowledgementState() == 0) {
            try {
                androidPublisher.purchases()
                        .products()
                        .acknowledge(
                                properties.getPackageName(),
                                productId,
                                purchaseToken,
                                new ProductPurchasesAcknowledgeRequest())
                        .execute();
            } catch (IOException ex) {
                // Don't fail the whole verification over this - the credits
                // are still legitimately earned. Log loudly so it can be
                // acknowledged manually/retried; an un-acknowledged purchase
                // auto-refunds after 3 days if this keeps failing.
                log.error("Failed to acknowledge Google Play purchase for productId={}, token={}.",
                        productId, purchaseToken, ex);
            }
        }

        return purchase;
    }
}
