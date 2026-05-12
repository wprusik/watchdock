package com.github.wprusik.monitor;

import com.github.wprusik.config.WatchDockConfig;
import com.github.wprusik.docker.ContainerSnapshot;
import com.github.wprusik.docker.DockerClient;
import com.github.wprusik.notify.NotificationService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ContainerMonitor {

    private final WatchDockConfig config;
    private final DockerClient dockerClient;
    private final NotificationService notificationService;
    private final Clock clock;
    private final Object lock = new Object();
    private final Map<String, ContainerRuntimeState> states = new HashMap<>();
    private final Map<String, ContainerSnapshot> snapshots = new LinkedHashMap<>();
    private final List<Alert> alerts = new ArrayList<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "watchdock-monitor"));
    private Instant lastMonitorErrorAt;

    public ContainerMonitor(WatchDockConfig config, DockerClient dockerClient, NotificationService notificationService) {
        this(config, dockerClient, notificationService, Clock.systemDefaultZone());
    }

    public ContainerMonitor(WatchDockConfig config, DockerClient dockerClient, NotificationService notificationService, Clock clock) {
        this.config = config;
        this.dockerClient = dockerClient;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    public void start() {
        executor.scheduleWithFixedDelay(this::pollSafely, 0, config.pollInterval().toSeconds(), TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    public void pollOnce() {
        pollSafely();
    }

    public List<ContainerSnapshot> snapshots() {
        synchronized (lock) {
            return snapshots.values().stream()
                    .sorted(Comparator.comparing(ContainerSnapshot::name))
                    .toList();
        }
    }

    public List<Alert> alerts() {
        synchronized (lock) {
            return alerts.stream()
                    .sorted(Comparator.comparing(Alert::createdAt).reversed())
                    .limit(100)
                    .toList();
        }
    }

    private void pollSafely() {
        try {
            poll();
        } catch (Exception e) {
            Instant now = Instant.now(clock);
            synchronized (lock) {
                if (lastMonitorErrorAt != null && Duration.between(lastMonitorErrorAt, now).compareTo(config.alertCooldown()) < 0) {
                    return;
                }
                lastMonitorErrorAt = now;
                Alert alert = new Alert(UUID.randomUUID().toString(), AlertType.MONITOR_ERROR, "", "Docker", "Monitoring failed: " + e.getMessage(), now);
                alerts.add(alert);
                sendNotificationIfAllowed(alert, now);
            }
        }
    }

    private void poll() throws Exception {
        Instant now = Instant.now(clock);
        List<ContainerSnapshot> current = dockerClient.listContainers();

        synchronized (lock) {
            snapshots.clear();
            for (ContainerSnapshot snapshot : current) {
                snapshots.put(snapshot.id(), snapshot);
                evaluate(snapshot, now);
            }
            states.keySet().removeIf(containerId -> !snapshots.containsKey(containerId));
        }
    }

    private void evaluate(ContainerSnapshot snapshot, Instant now) {
        ContainerRuntimeState state = states.computeIfAbsent(snapshot.id(), _ -> new ContainerRuntimeState());

        if (snapshot.running()) {
            state.inactiveSince = null;
        } else {
            Instant inactiveSince = snapshot.finishedAt() != null ? snapshot.finishedAt() : now;
            if (state.inactiveSince == null || inactiveSince.isBefore(state.inactiveSince)) {
                state.inactiveSince = inactiveSince;
            }
            Duration inactiveFor = Duration.between(state.inactiveSince, now);
            if (!snapshot.restarting() && inactiveFor.compareTo(config.inactiveThreshold()) >= 0) {
                emitWithCooldown(state, AlertType.INACTIVE_TOO_LONG, snapshot,
                        "%s is inactive for %s (state: %s)".formatted(snapshot.name(), human(inactiveFor), snapshot.status()),
                        now);
            }
        }

        if (state.lastRestartCount < 0) {
            state.lastRestartCount = snapshot.restartCount();
        }
        if (snapshot.restartCount() > state.lastRestartCount) {
            long delta = snapshot.restartCount() - state.lastRestartCount;
            for (int i = 0; i < delta; i++) {
                state.restartEvents.addLast(now);
            }
            state.lastRestartCount = snapshot.restartCount();
        }
        pruneRestartEvents(state.restartEvents, now);

        if (snapshot.restarting() || state.restartEvents.size() >= config.restartThreshold()) {
            String reason = snapshot.restarting()
                    ? "container is currently restarting"
                    : "%d restarts within %s".formatted(state.restartEvents.size(), human(config.restartWindow()));
            emitWithCooldown(state, AlertType.RESTART_LOOP, snapshot,
                    "%s restart loop suspected: %s".formatted(snapshot.name(), reason),
                    now);
        }
    }

    private void pruneRestartEvents(Deque<Instant> events, Instant now) {
        Instant oldestAllowed = now.minus(config.restartWindow());
        while (!events.isEmpty() && events.peekFirst().isBefore(oldestAllowed)) {
            events.removeFirst();
        }
    }

    private void emitWithCooldown(ContainerRuntimeState state, AlertType type, ContainerSnapshot snapshot, String message, Instant now) {
        Instant last = state.lastAlertAt.get(type);
        if (last != null && Duration.between(last, now).compareTo(config.alertCooldown()) < 0) {
            return;
        }
        state.lastAlertAt.put(type, now);
        Alert alert = new Alert(UUID.randomUUID().toString(), type, snapshot.id(), snapshot.name(), message, now);
        alerts.add(alert);
        sendNotificationIfAllowed(alert, now);
    }

    private void sendNotificationIfAllowed(Alert alert, Instant now) {
        if (isNotificationWindow(now)) {
            notificationService.send(alert);
        }
    }

    private boolean isNotificationWindow(Instant now) {
        LocalTime current = ZonedDateTime.ofInstant(now, clock.getZone()).toLocalTime();
        LocalTime start = LocalTime.of(config.notificationStartHour(), 0);
        LocalTime end = LocalTime.of(config.notificationEndHour(), 0);
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !current.isBefore(start) && current.isBefore(end);
        }
        return !current.isBefore(start) || current.isBefore(end);
    }

    private String human(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private static class ContainerRuntimeState {
        private Instant inactiveSince;
        private long lastRestartCount = -1;
        private final Deque<Instant> restartEvents = new ArrayDeque<>();
        private final Map<AlertType, Instant> lastAlertAt = new HashMap<>();
    }
}
