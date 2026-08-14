package com.splitwise.app.service;

import com.splitwise.app.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Converts HEIC/HEIF images (the default format modern Android/iOS cameras can
 * hand over) to JPEG server-side, since the Anthropic vision API doesn't accept
 * image/heic directly and the mobile client deliberately does no format
 * handling of its own.
 *
 * Shells out to the `heif-convert` CLI (from the `libheif-tools` Alpine package
 * - see Dockerfile) rather than pulling in a JVM HEIC-decoding library: there's
 * no well-maintained pure-Java HEIC decoder, and libheif via a native CLI is
 * the standard, well-tested way to do this conversion.
 */
@Slf4j
@Component
public class HeicImageConverter {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    /**
     * @return the converted image as JPEG bytes
     * @throws ApiException (400) if conversion fails - a corrupt file or a HEIF
     * variant heif-convert can't handle, which is a legitimate reason to reject
     * the upload rather than silently passing bad bytes downstream.
     */
    public byte[] convertToJpeg(byte[] heicBytes) {

        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("receipt-heic-");
        } catch (IOException ex) {
            log.error("Failed to create temp directory for HEIC conversion.", ex);
            throw ApiException.badRequest("Could not process this receipt image. Please try again.");
        }

        Path inputFile = tempDir.resolve("input.heic");
        Path outputFile = tempDir.resolve("output.jpg");

        try {
            Files.write(inputFile, heicBytes);

            Process process = new ProcessBuilder(
                    "heif-convert",
                    inputFile.toAbsolutePath().toString(),
                    outputFile.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.warn("heif-convert timed out after {}.", TIMEOUT);
                throw ApiException.badRequest(
                        "This receipt image took too long to process. Please try a different photo.");
            }

            if (process.exitValue() != 0 || !Files.exists(outputFile)) {
                log.warn("heif-convert failed (exit code {}) - likely a corrupt file or unsupported "
                        + "HEIF variant.", process.exitValue());
                throw ApiException.badRequest(
                        "Couldn't process this receipt image - it may be corrupted or an unsupported "
                        + "format. Try a different photo.");
            }

            return Files.readAllBytes(outputFile);

        } catch (IOException ex) {
            log.error("I/O error during HEIC conversion.", ex);
            throw ApiException.badRequest("Could not process this receipt image. Please try again.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("HEIC conversion was interrupted.", ex);
            throw ApiException.badRequest("Could not process this receipt image. Please try again.");
        } finally {
            deleteQuietly(inputFile);
            deleteQuietly(outputFile);
            deleteQuietly(tempDir);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.debug("Failed to delete temp file {} after HEIC conversion.", path, ex);
        }
    }
}
