package com.github.wprusik.monitor;

import com.github.wprusik.config.WatchDockConfig;
import com.github.wprusik.docker.ContainerSnapshot;
import com.github.wprusik.docker.DockerClient;
import com.github.wprusik.notify.NotificationService;
import junit.framework.TestCase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class ContainerMonitorTest extends TestCase {
    public void testInactiveContainerCreatesAlert() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-11T16:00:00Z"));
        FakeDockerClient dockerClient = new FakeDockerClient();
        RecordingNotifications notifications = new RecordingNotifications();
        ContainerMonitor monitor = new ContainerMonitor(config(), dockerClient, notifications, clock);

        dockerClient.snapshots = List.of(new ContainerSnapshot(
                "abcdef1234567890",
                "api",
                "exited",
                false,
                false,
                Instant.parse("2026-05-11T15:00:00Z"),
                Instant.parse("2026-05-11T15:44:00Z"),
                0,
                clock.instant()
        ));

        monitor.pollOnce();

        assertEquals(1, monitor.alerts().size());
        assertEquals(AlertType.INACTIVE_TOO_LONG, monitor.alerts().getFirst().type());
        assertEquals(1, notifications.sent.size());
    }

    public void testRestartBurstCreatesAlert() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-11T16:00:00Z"));
        FakeDockerClient dockerClient = new FakeDockerClient();
        RecordingNotifications notifications = new RecordingNotifications();
        ContainerMonitor monitor = new ContainerMonitor(config(), dockerClient, notifications, clock);

        dockerClient.snapshots = List.of(running(0, clock.instant()));
        monitor.pollOnce();

        clock.advance(Duration.ofSeconds(30));
        dockerClient.snapshots = List.of(running(3, clock.instant()));
        monitor.pollOnce();

        assertEquals(1, monitor.alerts().size());
        assertEquals(AlertType.RESTART_LOOP, monitor.alerts().getFirst().type());
        assertEquals(1, notifications.sent.size());
    }

    public void testAlertOutsideNotificationWindowDoesNotSendNotification() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-11T22:00:00Z"));
        FakeDockerClient dockerClient = new FakeDockerClient();
        RecordingNotifications notifications = new RecordingNotifications();
        ContainerMonitor monitor = new ContainerMonitor(config(), dockerClient, notifications, clock);

        dockerClient.snapshots = List.of(new ContainerSnapshot(
                "abcdef1234567890",
                "api",
                "exited",
                false,
                false,
                Instant.parse("2026-05-11T21:00:00Z"),
                Instant.parse("2026-05-11T21:44:00Z"),
                0,
                clock.instant()
        ));

        monitor.pollOnce();

        assertEquals(1, monitor.alerts().size());
        assertEquals(0, notifications.sent.size());
    }

    private WatchDockConfig config() {
        return new WatchDockConfig(
                8080,
                Duration.ofSeconds(5),
                Duration.ofMinutes(15),
                3,
                Duration.ofMinutes(2),
                Duration.ofMinutes(15),
                "docker",
                "",
                "WatchDock",
                "+48123123123",
                "https://api.smsapi.pl/sms.do",
                9,
                21
        );
    }

    private ContainerSnapshot running(long restartCount, Instant now) {
        return new ContainerSnapshot(
                "abcdef1234567890",
                "worker",
                "running",
                true,
                false,
                now.minusSeconds(60),
                null,
                restartCount,
                now
        );
    }

    private static class FakeDockerClient implements DockerClient {
        private List<ContainerSnapshot> snapshots = List.of();

        @Override
        public List<ContainerSnapshot> listContainers() {
            return snapshots;
        }
    }

    private static class RecordingNotifications implements NotificationService {
        private final List<Alert> sent = new ArrayList<>();

        @Override
        public void send(Alert alert) {
            sent.add(alert);
        }
    }

    private static class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
