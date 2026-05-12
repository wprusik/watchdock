package com.github.wprusik.monitor;

import java.time.Instant;

public record Alert(
        String id,
        AlertType type,
        String containerId,
        String containerName,
        String message,
        Instant createdAt
) {
}
