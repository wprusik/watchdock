package com.github.wprusik.config;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;

public record WatchDockConfig(
        int port,
        Duration pollInterval,
        Duration inactiveThreshold,
        int restartThreshold,
        Duration restartWindow,
        Duration alertCooldown,
        String dockerCommand,
        String smsApiToken,
        String smsApiFrom,
        String smsApiTo,
        String smsApiUrl,
        int notificationStartHour,
        int notificationEndHour
) {
    public static WatchDockConfig load() {
        return load(System.getenv(), System.getProperties());
    }

    public static WatchDockConfig load(Map<String, String> env, Properties properties) {
        return new WatchDockConfig(
                intValue(env, properties, "WATCHDOCK_PORT", "watchdock.port", 8090),
                Duration.ofSeconds(longValue(env, properties, "WATCHDOCK_POLL_SECONDS", "watchdock.pollSeconds", 5)),
                Duration.ofMinutes(longValue(env, properties, "WATCHDOCK_INACTIVE_MINUTES", "watchdock.inactiveMinutes", 15)),
                intValue(env, properties, "WATCHDOCK_RESTART_THRESHOLD", "watchdock.restartThreshold", 3),
                Duration.ofSeconds(longValue(env, properties, "WATCHDOCK_RESTART_WINDOW_SECONDS", "watchdock.restartWindowSeconds", 120)),
                Duration.ofSeconds(longValue(env, properties, "WATCHDOCK_ALERT_COOLDOWN_SECONDS", "watchdock.alertCooldownSeconds", 900)),
                stringValue(env, properties, "WATCHDOCK_DOCKER_COMMAND", "watchdock.dockerCommand", "docker"),
                stringValue(env, properties, "SMSAPI_TOKEN", "smsapi.token", ""),
                stringValue(env, properties, "SMSAPI_FROM", "smsapi.from", "WatchDock"),
                stringValue(env, properties, "SMSAPI_TO", "smsapi.to", ""),
                stringValue(env, properties, "SMSAPI_URL", "smsapi.url", "https://api.smsapi.pl/sms.do"),
                hourValue(env, properties, "WATCHDOCK_NOTIFY_START_HOUR", "watchdock.notifyStartHour", 9),
                hourValue(env, properties, "WATCHDOCK_NOTIFY_END_HOUR", "watchdock.notifyEndHour", 21)
        );
    }

    private static String stringValue(Map<String, String> env, Properties properties, String envKey, String propKey, String defaultValue) {
        String property = properties.getProperty(propKey);
        if (property != null && !property.isBlank()) {
            return property.trim();
        }
        String value = env.get(envKey);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static int intValue(Map<String, String> env, Properties properties, String envKey, String propKey, int defaultValue) {
        return Math.toIntExact(longValue(env, properties, envKey, propKey, defaultValue));
    }

    private static int hourValue(Map<String, String> env, Properties properties, String envKey, String propKey, int defaultValue) {
        int hour = intValue(env, properties, envKey, propKey, defaultValue);
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("Invalid hour config value for " + envKey + "/" + propKey + ": " + hour);
        }
        return hour;
    }

    private static long longValue(Map<String, String> env, Properties properties, String envKey, String propKey, long defaultValue) {
        String value = stringValue(env, properties, envKey, propKey, Long.toString(defaultValue));
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric config value for " + envKey + "/" + propKey + ": " + value, e);
        }
    }
}
