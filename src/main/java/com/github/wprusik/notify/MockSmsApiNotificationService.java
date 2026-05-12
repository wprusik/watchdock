package com.github.wprusik.notify;

import com.github.wprusik.config.WatchDockConfig;
import com.github.wprusik.monitor.Alert;

public class MockSmsApiNotificationService implements NotificationService {
    private final WatchDockConfig config;

    public MockSmsApiNotificationService(WatchDockConfig config) {
        this.config = config;
    }

    @Override
    public void send(Alert alert) {
        System.out.printf("[SMSAPI MOCK] to=%s from=%s tokenConfigured=%s type=%s message=\"%s\"%n",
                isBlank(config.smsApiTo()) ? "<missing>" : config.smsApiTo(),
                config.smsApiFrom(),
                !isBlank(config.smsApiToken()),
                alert.type(),
                alert.message());
    }

    @Override
    public String providerName() {
        return "Mock SMSAPI";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
