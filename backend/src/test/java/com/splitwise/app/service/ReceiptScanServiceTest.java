package com.splitwise.app.service;

import com.splitwise.app.dto.aicredit.AiFeatureCreditsResponse;
import com.splitwise.app.dto.receipt.ReceiptExtractionRaw;
import com.splitwise.app.dto.receipt.ReceiptScanResult;
import com.splitwise.app.entity.Category;
import com.splitwise.app.enums.AiFeature;
import com.splitwise.app.enums.CreditSource;
import com.splitwise.app.exception.ApiException;
import com.splitwise.app.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptScanServiceTest {

    @Mock
    private AiCreditService aiCreditService;
    @Mock
    private ReceiptVisionClient visionClient;
    @Mock
    private ReceiptCategoryMatcher categoryMatcher;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private HeicImageConverter heicImageConverter;

    @InjectMocks
    private ReceiptScanService receiptScanService;

    private UUID userId;

    // Minimal valid JPEG magic bytes (FF D8 FF ...) so ImageContentSniffer
    // recognizes it, padded to be > 12 bytes as the sniffer requires.
    private static final byte[] JPEG_BYTES = new byte[]{
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };

    // Minimal HEIC "ftyp" box magic bytes.
    private static final byte[] HEIC_BYTES = buildHeicMagicBytes();

    private static byte[] buildHeicMagicBytes() {
        byte[] bytes = new byte[16];
        // bytes[4..8) = "ftyp"
        System.arraycopy("ftyp".getBytes(), 0, bytes, 4, 4);
        // bytes[8..12) = "heic" brand
        System.arraycopy("heic".getBytes(), 0, bytes, 8, 4);
        return bytes;
    }

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("scan() consumes a credit, calls the vision API, and returns a mapped result")
    void scan_happyPath() {

        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", JPEG_BYTES);

        when(aiCreditService.consume(userId, AiFeature.RECEIPT_SCAN))
                .thenReturn(new AiCreditService.ConsumptionResult(CreditSource.FREE));

        ReceiptExtractionRaw raw = new ReceiptExtractionRaw();
        raw.merchantName = "Cafe Coffee Day";
        raw.totalAmount = new BigDecimal("250.00");
        raw.currency = "INR";
        raw.purchaseDate = "2026-08-01";
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(visionClient.extract(eq(JPEG_BYTES), eq("image/jpeg"), any())).thenReturn(raw);

        Category category = Category.builder().id(UUID.randomUUID()).name("Food & Drink").build();
        when(categoryMatcher.match("Cafe Coffee Day")).thenReturn(Optional.of(category));

        when(aiCreditService.getBalance(userId, AiFeature.RECEIPT_SCAN))
                .thenReturn(AiFeatureCreditsResponse.builder().totalAvailable(1).build());

        ReceiptScanResult result = receiptScanService.scan(userId, file);

        assertThat(result.getMerchantName()).isEqualTo("Cafe Coffee Day");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("250.00");
        assertThat(result.getCategoryId()).isEqualTo(category.getId());
        assertThat(result.getCreditsRemaining()).isEqualTo(1);

        verify(aiCreditService, never()).refund(any(), any(), any());
        verify(heicImageConverter, never()).convertToJpeg(any());
    }

    @Test
    @DisplayName("scan() passes the app's category names to the vision client and uses its exact match")
    void scan_usesAiReturnedCategory_whenItMatchesARealCategory() {

        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", JPEG_BYTES);

        Category foodCategory = Category.builder().id(UUID.randomUUID()).name("Food & Drink").build();
        Category travelCategory = Category.builder().id(UUID.randomUUID()).name("Transportation").build();
        when(categoryRepository.findAll()).thenReturn(List.of(foodCategory, travelCategory));

        when(aiCreditService.consume(userId, AiFeature.RECEIPT_SCAN))
                .thenReturn(new AiCreditService.ConsumptionResult(CreditSource.FREE));

        ReceiptExtractionRaw raw = new ReceiptExtractionRaw();
        raw.merchantName = "Random Merchant Co";
        raw.category = "Food & Drink";
        when(visionClient.extract(eq(JPEG_BYTES), eq("image/jpeg"),
                eq(List.of("Food & Drink", "Transportation")))).thenReturn(raw);

        when(categoryRepository.findByNameIgnoreCase("Food & Drink")).thenReturn(Optional.of(foodCategory));
        when(aiCreditService.getBalance(userId, AiFeature.RECEIPT_SCAN))
                .thenReturn(AiFeatureCreditsResponse.builder().totalAvailable(1).build());

        ReceiptScanResult result = receiptScanService.scan(userId, file);

        assertThat(result.getCategoryId()).isEqualTo(foodCategory.getId());
        assertThat(result.getCategoryName()).isEqualTo("Food & Drink");
        // The AI's own category choice was used directly - no need to fall
        // back to guessing from the merchant name.
        verify(categoryMatcher, never()).match(any());
    }

    @Test
    @DisplayName("scan() falls back to keyword matching when the AI's category doesn't match a real category")
    void scan_fallsBackToKeywordMatching_whenAiCategoryDoesNotMatch() {

        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", JPEG_BYTES);

        when(categoryRepository.findAll()).thenReturn(List.of());

        when(aiCreditService.consume(userId, AiFeature.RECEIPT_SCAN))
                .thenReturn(new AiCreditService.ConsumptionResult(CreditSource.FREE));

        ReceiptExtractionRaw raw = new ReceiptExtractionRaw();
        raw.merchantName = "Cafe Coffee Day";
        raw.category = "Made Up Category That Does Not Exist";
        when(visionClient.extract(eq(JPEG_BYTES), eq("image/jpeg"), any())).thenReturn(raw);

        when(categoryRepository.findByNameIgnoreCase("Made Up Category That Does Not Exist"))
                .thenReturn(Optional.empty());

        Category fallbackCategory = Category.builder().id(UUID.randomUUID()).name("Food & Drink").build();
        when(categoryMatcher.match("Cafe Coffee Day")).thenReturn(Optional.of(fallbackCategory));
        when(aiCreditService.getBalance(userId, AiFeature.RECEIPT_SCAN))
                .thenReturn(AiFeatureCreditsResponse.builder().totalAvailable(1).build());

        ReceiptScanResult result = receiptScanService.scan(userId, file);

        assertThat(result.getCategoryId()).isEqualTo(fallbackCategory.getId());
    }

    @Test
    @DisplayName("scan() refunds the credit if the vision call fails, and rethrows")
    void scan_refundsCredit_onVisionFailure() {

        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", JPEG_BYTES);

        when(aiCreditService.consume(userId, AiFeature.RECEIPT_SCAN))
                .thenReturn(new AiCreditService.ConsumptionResult(CreditSource.PURCHASED));
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(visionClient.extract(any(), anyString(), any()))
                .thenThrow(ApiException.badRequest("AI call failed"));

        assertThatThrownBy(() -> receiptScanService.scan(userId, file))
                .isInstanceOf(ApiException.class);

        verify(aiCreditService).refund(userId, AiFeature.RECEIPT_SCAN, CreditSource.PURCHASED);
    }

    @Test
    @DisplayName("scan() converts HEIC to JPEG before the vision call and before spending a credit")
    void scan_convertsHeicBeforeVisionCallAndCreditConsumption() {

        MockMultipartFile file = new MockMultipartFile("file", "receipt.heic", "image/heic", HEIC_BYTES);

        byte[] convertedJpegBytes = JPEG_BYTES;
        when(heicImageConverter.convertToJpeg(HEIC_BYTES)).thenReturn(convertedJpegBytes);

        when(aiCreditService.consume(userId, AiFeature.RECEIPT_SCAN))
                .thenReturn(new AiCreditService.ConsumptionResult(CreditSource.FREE));

        ReceiptExtractionRaw raw = new ReceiptExtractionRaw();
        raw.merchantName = null;
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(visionClient.extract(eq(convertedJpegBytes), eq("image/jpeg"), any())).thenReturn(raw);
        when(categoryMatcher.match(null)).thenReturn(Optional.empty());
        when(aiCreditService.getBalance(userId, AiFeature.RECEIPT_SCAN))
                .thenReturn(AiFeatureCreditsResponse.builder().totalAvailable(0).build());

        receiptScanService.scan(userId, file);

        verify(heicImageConverter).convertToJpeg(HEIC_BYTES);
        verify(visionClient).extract(eq(convertedJpegBytes), eq("image/jpeg"), any());
    }

    @Test
    @DisplayName("scan() rejects HEIC conversion failures without ever consuming a credit")
    void scan_heicConversionFailure_neverConsumesCredit() {

        MockMultipartFile file = new MockMultipartFile("file", "receipt.heic", "image/heic", HEIC_BYTES);

        when(heicImageConverter.convertToJpeg(HEIC_BYTES))
                .thenThrow(ApiException.badRequest("Couldn't process this receipt image"));

        assertThatThrownBy(() -> receiptScanService.scan(userId, file))
                .isInstanceOf(ApiException.class);

        verify(aiCreditService, never()).consume(any(), any());
    }

    @Test
    @DisplayName("scan() rejects an empty file without consuming a credit")
    void scan_rejectsEmptyFile() {

        MockMultipartFile emptyFile = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> receiptScanService.scan(userId, emptyFile))
                .isInstanceOf(ApiException.class);

        verify(aiCreditService, never()).consume(any(), any());
    }

    @Test
    @DisplayName("scan() rejects a file that isn't a recognizable image format")
    void scan_rejectsUnrecognizedFileFormat() {

        byte[] notAnImage = "hello world this is definitely text".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "receipt.txt", "text/plain", notAnImage);

        assertThatThrownBy(() -> receiptScanService.scan(userId, file))
                .isInstanceOf(ApiException.class);

        verify(aiCreditService, never()).consume(any(), any());
    }

    @Test
    @DisplayName("scan() rejects files over the 10MB size limit without consuming a credit")
    void scan_rejectsOversizedFile() {

        byte[] oversized = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", oversized);

        assertThatThrownBy(() -> receiptScanService.scan(userId, file))
                .isInstanceOf(ApiException.class);

        verify(aiCreditService, never()).consume(any(), any());
    }
}
