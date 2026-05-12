package com.github.wprusik.notify.smsapi;

import com.github.wprusik.config.WatchDockConfig;
import com.github.wprusik.monitor.Alert;
import com.github.wprusik.notify.NotificationService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsApiNotificationService implements NotificationService {

    private static final Pattern COUNT_PATTERN = Pattern.compile("\"count\"\\s*:\\s*(\\d+)");
    private static final Pattern ERROR_PATTERN = Pattern.compile("\"error\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"");

    private final WatchDockConfig config;
    private final HttpClient httpClient;

    public SmsApiNotificationService(WatchDockConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
    }

    SmsApiNotificationService(WatchDockConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    @Override
    public void send(Alert alert) {
        SendSmsRequest request = SendSmsRequest.builder()
                .to(config.smsApiTo())
                .message(alert.message())
                .from(config.smsApiFrom())
                .format("json")
                .encoding("utf-8")
                .build();
        try {
            int count = doSend(request);
            System.out.printf("[SMSAPI] sent=%d to=%s type=%s%n", count, request.to(), alert.type());
        } catch (SmsFailedException e) {
            System.err.printf("[SMSAPI] send failed to=%s type=%s reason=%s%n", request.to(), alert.type(), e.getMessage());
        }
    }

    @Override
    public String providerName() {
        return "SMSAPI.pl";
    }

    private int doSend(SendSmsRequest request) throws SmsFailedException {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(config.smsApiUrl()))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + config.smsApiToken())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(request.toFormBody()))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new SmsFailedException("Failed to call SMSAPI: " + e.getMessage(), e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SmsFailedException("SMSAPI returned HTTP " + response.statusCode() + ": " + response.body());
        }
        if (containsError(response.body())) {
            throw new SmsFailedException("SMSAPI error: " + extractErrorMessage(response.body()));
        }
        return extractCount(response.body());
    }

    private boolean containsError(String body) {
        return ERROR_PATTERN.matcher(body).find();
    }

    private String extractErrorMessage(String body) {
        Matcher messageMatcher = MESSAGE_PATTERN.matcher(body);
        if (messageMatcher.find()) {
            return messageMatcher.group(1);
        }
        Matcher errorMatcher = ERROR_PATTERN.matcher(body);
        if (errorMatcher.find()) {
            return errorMatcher.group(1);
        }
        return body;
    }

    private int extractCount(String body) {
        Matcher matcher = COUNT_PATTERN.matcher(body);
        if (!matcher.find()) {
            return 0;
        }
        return Integer.parseInt(matcher.group(1));
    }
}
