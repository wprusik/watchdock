package com.github.wprusik;

import com.github.wprusik.config.WatchDockConfig;
import com.github.wprusik.docker.DockerCliClient;
import com.github.wprusik.monitor.ContainerMonitor;
import com.github.wprusik.notify.MockSmsApiNotificationService;
import com.github.wprusik.notify.NotificationService;
import com.github.wprusik.notify.smsapi.SmsApiNotificationService;
import com.github.wprusik.web.WebServer;

import java.util.concurrent.CountDownLatch;

public class App {

    static void main() throws Exception {
        WatchDockConfig config = WatchDockConfig.load();
        NotificationService notificationService = createNotificationService(config);
        DockerCliClient dockerClient = new DockerCliClient(config.dockerCommand());
        ContainerMonitor monitor = new ContainerMonitor(config, dockerClient, notificationService);
        WebServer webServer = new WebServer(config, monitor, notificationService);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            monitor.stop();
            webServer.stop();
        }, "watchdock-shutdown"));

        webServer.start();
        monitor.start();

        System.out.printf("WatchDock listening on http://localhost:%d%n", config.port());
        System.out.printf("Polling Docker every %s; inactive alert after %s%n", config.pollInterval(), config.inactiveThreshold());

        new CountDownLatch(1).await();
    }

    private static NotificationService createNotificationService(WatchDockConfig config) {
        if (isBlank(config.smsApiToken()) || isBlank(config.smsApiTo())) {
            return new MockSmsApiNotificationService(config);
        }
        return new SmsApiNotificationService(config);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
