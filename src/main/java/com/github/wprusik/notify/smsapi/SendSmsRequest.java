package com.github.wprusik.notify.smsapi;

import lombok.Builder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Builder(builderClassName = "Builder")
public record SendSmsRequest(
        String to,
        String message,
        String from,
        String format,
        String encoding
) {

    public SendSmsRequest {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("SMS recipient number is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("SMS message is required");
        }
        to = normalizePhoneNumber(to);
        format = format == null || format.isBlank() ? "json" : format;
        encoding = encoding == null || encoding.isBlank() ? "utf-8" : encoding;
    }

    public String toFormBody() {
        List<String> parts = new ArrayList<>();
        parts.add(encoded("to", to));
        parts.add(encoded("message", message));
        if (from != null && !from.isBlank()) {
            parts.add(encoded("from", from));
        }
        parts.add(encoded("format", format));
        parts.add(encoded("encoding", encoding));
        return String.join("&", parts);
    }

    private static String normalizePhoneNumber(String value) {
        String digitsOnly = value.replaceAll("\\D", "");
        if (digitsOnly.length() == 9) {
            return "48" + digitsOnly;
        }
        if (digitsOnly.isEmpty()) {
            throw new IllegalArgumentException("SMS recipient number does not contain digits");
        }
        return digitsOnly;
    }

    private static String encoded(String name, String value) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
