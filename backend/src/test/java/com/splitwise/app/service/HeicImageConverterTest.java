package com.splitwise.app.service;

import com.splitwise.app.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * heif-convert (from the libheif-tools Alpine package) isn't installed in most
 * local/CI dev environments, only in the production Docker image - so these
 * tests deliberately verify the failure path (garbage input can't possibly
 * convert successfully either way) rather than a real successful conversion. A
 * real conversion should be smoke-tested manually against the deployed
 * container with an actual HEIC photo before relying on it in production.
 */
class HeicImageConverterTest {

    private final HeicImageConverter converter = new HeicImageConverter();

    @Test
    @DisplayName("Throws a client-facing ApiException (never an unchecked exception) on invalid/corrupt input")
    void convertToJpeg_throwsApiException_onInvalidInput() {

        byte[] garbage = "not a real heic file".getBytes();

        assertThatThrownBy(() -> converter.convertToJpeg(garbage))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Throws a client-facing ApiException on empty input")
    void convertToJpeg_throwsApiException_onEmptyInput() {

        assertThatThrownBy(() -> converter.convertToJpeg(new byte[0]))
                .isInstanceOf(ApiException.class);
    }
}
