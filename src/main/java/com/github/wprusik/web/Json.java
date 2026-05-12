package com.github.wprusik.web;

import com.github.wprusik.config.WatchDockConfig;
import com.github.wprusik.docker.ContainerSnapshot;
import com.github.wprusik.monitor.Alert;

import java.time.Instant;
import java.util.List;

public final class Json {
    private Json() {
    }

    public static String status(List<ContainerSnapshot> containers, List<Alert> alerts, WatchDockConfig config, String notificationProvider) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        property(json, "pollIntervalSeconds", config.pollInterval().toSeconds()).append(',');
        property(json, "inactiveThresholdSeconds", config.inactiveThreshold().toSeconds()).append(',');
        property(json, "restartThreshold", config.restartThreshold()).append(',');
        property(json, "restartWindowSeconds", config.restartWindow().toSeconds()).append(',');
        property(json, "notificationStartHour", config.notificationStartHour()).append(',');
        property(json, "notificationEndHour", config.notificationEndHour()).append(',');
        property(json, "notificationProvider", notificationProvider).append(',');
        property(json, "containers", containers);
        json.append(',');
        property(json, "alerts", alerts);
        json.append('}');
        return json.toString();
    }

    private static void property(StringBuilder json, String name, List<?> values) {
        quoted(json, name).append(':').append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            Object value = values.get(i);
            if (value instanceof ContainerSnapshot snapshot) {
                container(json, snapshot);
            } else if (value instanceof Alert alert) {
                alert(json, alert);
            }
        }
        json.append(']');
    }

    private static void container(StringBuilder json, ContainerSnapshot snapshot) {
        json.append('{');
        property(json, "id", snapshot.id()).append(',');
        property(json, "shortId", snapshot.shortId()).append(',');
        property(json, "name", snapshot.name()).append(',');
        property(json, "status", snapshot.status()).append(',');
        property(json, "running", snapshot.running()).append(',');
        property(json, "restarting", snapshot.restarting()).append(',');
        property(json, "startedAt", snapshot.startedAt()).append(',');
        property(json, "finishedAt", snapshot.finishedAt()).append(',');
        property(json, "restartCount", snapshot.restartCount()).append(',');
        property(json, "observedAt", snapshot.observedAt());
        json.append('}');
    }

    private static void alert(StringBuilder json, Alert alert) {
        json.append('{');
        property(json, "id", alert.id()).append(',');
        property(json, "type", alert.type().name()).append(',');
        property(json, "containerId", alert.containerId()).append(',');
        property(json, "containerName", alert.containerName()).append(',');
        property(json, "message", alert.message()).append(',');
        property(json, "createdAt", alert.createdAt());
        json.append('}');
    }

    private static StringBuilder property(StringBuilder json, String name, String value) {
        quoted(json, name).append(':');
        return quoted(json, value);
    }

    private static StringBuilder property(StringBuilder json, String name, boolean value) {
        return quoted(json, name).append(':').append(value);
    }

    private static StringBuilder property(StringBuilder json, String name, long value) {
        return quoted(json, name).append(':').append(value);
    }

    private static StringBuilder property(StringBuilder json, String name, Instant value) {
        quoted(json, name).append(':');
        return value == null ? json.append("null") : quoted(json, value.toString());
    }

    private static StringBuilder quoted(StringBuilder json, String value) {
        json.append('"');
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> json.append("\\\"");
                    case '\\' -> json.append("\\\\");
                    case '\n' -> json.append("\\n");
                    case '\r' -> json.append("\\r");
                    case '\t' -> json.append("\\t");
                    default -> json.append(c);
                }
            }
        }
        return json.append('"');
    }
}
