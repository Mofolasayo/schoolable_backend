package com.schoolable.backend.kpi;

import com.schoolable.backend.storage.StorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Optional;

@Service
public class TeamReportDocumentService {

    private static final Logger log = LoggerFactory.getLogger(TeamReportDocumentService.class);
    private static final int MAX_TEXT_LENGTH = 6000;

    private final StorageService storageService;
    private final RestTemplate restTemplate;

    public TeamReportDocumentService(
            StorageService storageService,
            @Value("${report.document.timeout-ms:10000}") int timeoutMs) {
        this.storageService = storageService;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    public Optional<String> extractReportText(String reportUrl) {
        if (reportUrl == null || reportUrl.isBlank()) {
            return Optional.empty();
        }

        String resolvedUrl = storageService.ensurePublicDelivery(reportUrl);
        byte[] payload = fetchBytes(resolvedUrl);
        if (payload == null || payload.length == 0) {
            return Optional.empty();
        }

        String text = extractPdfText(payload);
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() > MAX_TEXT_LENGTH) {
            normalized = normalized.substring(0, MAX_TEXT_LENGTH) + "...";
        }

        return Optional.of(normalized);
    }

    private byte[] fetchBytes(String url) {
        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Failed to download report document: status {}", response.getStatusCode());
                if (response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403) {
                    return retryAfterUnblock(url);
                }
                return null;
            }
            return response.getBody();
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                return retryAfterUnblock(url);
            }
            log.warn("Failed to download report document: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Failed to download report document: {}", e.getMessage());
            return null;
        }
    }

    private byte[] retryAfterUnblock(String url) {
        boolean unblocked = storageService.unblockDelivery(url);
        if (!unblocked) {
            log.warn("Report delivery unblock attempt failed");
            return null;
        }

        try {
            ResponseEntity<byte[]> retryResponse = restTemplate.getForEntity(url, byte[].class);
            if (!retryResponse.getStatusCode().is2xxSuccessful()) {
                log.warn("Retry download failed: status {}", retryResponse.getStatusCode());
                return null;
            }
            return retryResponse.getBody();
        } catch (Exception e) {
            log.warn("Retry download failed: {}", e.getMessage());
            return null;
        }
    }

    private String extractPdfText(byte[] payload) {
        try (PDDocument document = PDDocument.load(payload)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            log.warn("Failed to extract PDF text: {}", e.getMessage());
            return null;
        }
    }
}
