package com.github.wprusik.web;

import com.github.wprusik.config.WatchDockConfig;
import com.github.wprusik.monitor.ContainerMonitor;
import com.github.wprusik.notify.NotificationService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class WebServer {

    private final WatchDockConfig config;
    private final ContainerMonitor monitor;
    private final NotificationService notificationService;
    private HttpServer server;

    public WebServer(WatchDockConfig config, ContainerMonitor monitor, NotificationService notificationService) {
        this.config = config;
        this.monitor = monitor;
        this.notificationService = notificationService;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/", this::index);
        server.createContext("/api/status", this::status);
        server.setExecutor(Executors.newSingleThreadScheduledExecutor());
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void index(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed");
            return;
        }
        send(exchange, 200, "text/html; charset=utf-8", HtmlPage.content());
    }

    private void status(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "application/json; charset=utf-8", "{\"error\":\"method_not_allowed\"}");
            return;
        }
        String body = Json.status(monitor.snapshots(), monitor.alerts(), config, notificationService.providerName());
        send(exchange, 200, "application/json; charset=utf-8", body);
    }

    private void send(HttpExchange exchange, int statusCode, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
