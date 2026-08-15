package com.splitwise.app.service;

import com.splitwise.app.dto.receipt.ReceiptExtractionRaw;
import com.splitwise.app.dto.receipt.ReceiptScanResult;
import com.splitwise.app.entity.Category;
import com.splitwise.app.enums.AiFeature;
import com.splitwise.app.enums.CreditSource;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.CategoryRepository;
import com.splitwise.app.storage.ImageContentSniffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything specific to the RECEIPT_SCAN AI feature: validating the upload,
 * spending/refunding its credit via AiCreditService, calling the vision model,
 * and resolving a category. When VOICE_EXPENSE ships, it gets its own sibling
 * service following the exact same consume-before-work / refund-on-failure
 * pattern - see AiCreditService's class javadoc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptScanService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final String FALLBACK_CATEGORY_NAME = "Others";

    private final AiCreditService aiCreditService;
    private final ReceiptVisionClient visionClient;
    private final ReceiptCategoryMatcher categoryMatcher;
    private final CategoryRepository categoryRepository;
    private final HeicImageConverter heicImageConverter;

    public ReceiptScanResult scan(UUID userId, MultipartFile file) {

        byte[] rawBytes = validateAndRead(file);
        String contentType = ImageContentSniffer.detect(rawBytes);

        if (contentType == null) {
            throw ApiException.badRequest("File must be a valid JPEG, PNG, WEBP, or HEIC image");
        }

        // Normalize HEIC/HEIF to JPEG before it ever reaches the vision API,
        // which doesn't accept image/heic directly. The client deliberately
        // does no format handling of its own, so whatever the device camera
        // hands over is normalized here. A conversion failure (corrupt file,
        // unsupported HEIF variant) is a legitimate 400 - it happens before
        // any credit is spent, same as any other invalid upload.
        byte[] imageBytes = rawBytes;
        if (contentType.equals("image/heic")) {
            imageBytes = heicImageConverter.convertToJpeg(rawBytes);
            contentType = "image/jpeg";
        }

        // Give the model the app's actual category names so it can pick one
        // directly (see ReceiptVisionPrompt) - fetched fresh on every call so
        // admin-managed category renames/additions are reflected immediately,
        // no cache to invalidate.
        List<String> categoryNames = categoryRepository.findAll().stream()
                .map(Category::getName)
                .toList();

        // Consume-before-work: reserve the credit first, refund it if the AI
        // call itself fails - so a failed scan never costs the user a credit,
        // but a user also can't race the AI call to get a free extra attempt.
        AiCreditService.ConsumptionResult consumption = aiCreditService.consume(userId, AiFeature.RECEIPT_SCAN);
        CreditSource source = consumption.source();

        try {
            ReceiptExtractionRaw raw = visionClient.extract(imageBytes, contentType, categoryNames);
            return toResult(userId, raw);
        } catch (RuntimeException ex) {
            aiCreditService.refund(userId, AiFeature.RECEIPT_SCAN, source);
            throw ex;
        }
    }

    private byte[] validateAndRead(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No receipt image was provided");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw ApiException.badRequest("Receipt image exceeds the maximum allowed size of 10MB");
        }

        try {
            return file.getBytes();
        } catch (IOException ex) {
            log.warn("Failed to read uploaded receipt image bytes.", ex);
            throw ApiException.badRequest("Could not read the uploaded image");
        }
    }

    private ReceiptScanResult toResult(UUID userId, ReceiptExtractionRaw raw) {

        Optional<Category> category = resolveCategory(raw);

        List<ReceiptScanResult.ReceiptLineItem> lineItems = raw.lineItems == null
                ? List.of()
                : raw.lineItems.stream()
                        .map(li -> ReceiptScanResult.ReceiptLineItem.builder()
                        .description(li.description)
                        .amount(li.amount)
                        .build())
                        .toList();

        var balance = aiCreditService.getBalance(userId, AiFeature.RECEIPT_SCAN);

        return ReceiptScanResult.builder()
                .merchantName(raw.merchantName)
                .totalAmount(raw.totalAmount)
                .currency(raw.currency)
                .purchaseDate(parseDate(raw.purchaseDate))
                .categoryId(category.map(Category::getId).orElse(null))
                .categoryName(category.map(Category::getName).orElse(null))
                .lineItems(lineItems)
                .creditsRemaining(balance.getTotalAvailable())
                .build();
    }

    /**
     * The model was given the exact list of category names and told to copy one
     * verbatim, so this should be an exact match - but never trust model output
     * blindly. Falls back to the merchant-name keyword matcher (see
     * ReceiptCategoryMatcher) if the model returned null or something that
     * doesn't correspond to a real category, and if even that comes up empty,
     * falls back to a category named "Others" (if the app has one) rather than
     * leaving the expense uncategorized.
     */
    private Optional<Category> resolveCategory(ReceiptExtractionRaw raw) {

        if (raw.category != null && !raw.category.isBlank()) {
            Optional<Category> exactMatch = categoryRepository.findByNameIgnoreCase(raw.category.trim());
            if (exactMatch.isPresent()) {
                return exactMatch;
            }
            log.debug("AI returned category '{}' which doesn't match any configured category - "
                    + "falling back to keyword matching.", raw.category);
        }

        Optional<Category> keywordMatch = categoryMatcher.match(raw.merchantName);
        if (keywordMatch.isPresent()) {
            return keywordMatch;
        }

        return categoryRepository.findByNameIgnoreCase(FALLBACK_CATEGORY_NAME);
    }

    private LocalDate parseDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(isoDate);
        } catch (DateTimeParseException ex) {
            log.debug("AI returned an unparseable purchaseDate '{}'.", isoDate);
            return null;
        }
    }
}
