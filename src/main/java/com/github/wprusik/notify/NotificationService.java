package com.github.wprusik.notify;

import com.github.wprusik.monitor.Alert;

@FunctionalInterface
public interface NotificationService {
    void send(Alert alert);

    default String providerName() {
        return getClass().getSimpleName();
    }
}
