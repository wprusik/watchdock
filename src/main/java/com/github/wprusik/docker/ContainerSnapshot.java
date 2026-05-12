package com.github.wprusik.docker;

import java.time.Instant;

public record ContainerSnapshot(
        String id,
        String name,
        String status,
        boolean running,
        boolean restarting,
        Instant startedAt,
        Instant finishedAt,
        long restartCount,
        Instant observedAt
) {
    public String shortId() {
        return id.length() <= 12 ? id : id.substring(0, 12);
    }
}
