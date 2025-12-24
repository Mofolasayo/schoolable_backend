package com.schoolable.backend.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
public class ResendEmailService {
    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);

    private final String apiKey;
    private final String from;
    private final HttpClient client = HttpClient.newHttpClient();

    public ResendEmailService(
            @Value("${resend.apiKey:}") String apiKey,
            @Value("${resend.from:}") String from
    ) {
        this.apiKey = apiKey;
        this.from = from;
        log.info("ResendEmailService initialized with from: {}, apiKey present: {}", from, apiKey != null && !apiKey.isBlank());
    }

    public void sendVerificationEmail(String to, String code) {
        if (apiKey == null || apiKey.isBlank() || from == null || from.isBlank()) {
            log.warn("Resend not configured (missing apiKey/from). Skipping email to {}", to);
            return;
        }
        try {
            String html = buildVerificationEmailHtml(code);
            String body = """
                    {
                      "from": "%s",
                      "to": ["%s"],
                      "subject": "Your Schoolable verification code",
                      "html": "%s"
                    }
                    """.formatted(escapeJson(from), escapeJson(to), html);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Sent verification email to {}", to);
            } else {
                log.warn("Failed to send verification email to {}. Status: {} Body: {}", to, response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.error("Error sending verification email to {}", to, ex);
        }
    }

    private String buildVerificationEmailHtml(String code) {
        // Keep it simple to avoid formatter issues with '%' characters
        String html = "<p>Welcome to Schoolable!</p>"
                + "<p>Your verification code is:</p>"
                + "<p style=\\\"font-size:22px; font-weight:bold; letter-spacing:4px;\\\">" + escapeJson(code) + "</p>"
                + "<p>Enter this code in the app to verify your email.</p>";
        return html;
    }

    private String escapeJson(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
