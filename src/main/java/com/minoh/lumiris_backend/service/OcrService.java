package com.minoh.lumiris_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Best-effort text extraction from uploaded identity documents, via a self-hosted Tesseract OCR
 * binary (no external API, no API key, no cost). This is a sanity-check hint for admins during
 * KYB review, not a certified identity verification — any failure (binary missing, unsupported
 * format, timeout) is swallowed and simply yields no text rather than blocking the upload.
 *
 * Requires `tesseract` on PATH (e.g. `brew install tesseract tesseract-lang` locally, or the
 * equivalent apt/apk package in the deploy image). Only image uploads are processed — PDF
 * rasterization isn't wired up, so PDF documents are skipped rather than attempted unreliably.
 */
@Slf4j
@Service
public class OcrService {

    private static final long TIMEOUT_SECONDS = 20;

    public Optional<String> extractText(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Optional.empty();
        }

        Path tempInput = null;
        try {
            tempInput = Files.createTempFile("kyb-ocr-", extensionOf(file.getOriginalFilename()));
            file.transferTo(tempInput);
            return runTesseract(tempInput);
        } catch (IOException e) {
            log.warn("OCR: failed to stage uploaded file for extraction: {}", e.getMessage());
            return Optional.empty();
        } finally {
            if (tempInput != null) {
                try {
                    Files.deleteIfExists(tempInput);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
    }

    private Optional<String> runTesseract(Path imageFile) {
        Path outputBase = null;
        try {
            outputBase = Files.createTempFile("kyb-ocr-out-", "");
            Files.deleteIfExists(outputBase); // tesseract writes <outputBase>.txt itself

            ProcessBuilder pb = new ProcessBuilder("tesseract", imageFile.toString(), outputBase.toString(), "-l", "fra+eng");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("OCR: tesseract timed out after {}s", TIMEOUT_SECONDS);
                return Optional.empty();
            }

            Path outputFile = Path.of(outputBase + ".txt");
            if (!Files.exists(outputFile)) {
                log.warn("OCR: tesseract exited {} without producing output (binary missing?)", process.exitValue());
                return Optional.empty();
            }
            String text = Files.readString(outputFile);
            Files.deleteIfExists(outputFile);
            return Optional.of(text);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("OCR: extraction failed: {}", e.getMessage());
            return Optional.empty();
        } finally {
            if (outputBase != null) {
                try {
                    Files.deleteIfExists(outputBase);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.'));
    }
}
