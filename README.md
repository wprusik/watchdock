# WatchDock

Watchdog monitor for Docker containers, raises alerts for restart loops and long inactivity, and exposes a small web panel.

## Requirements

- Java 25 (for local run)
- Docker CLI available in PATH (`docker`) or custom command in `WATCHDOCK_DOCKER_COMMAND`
- Access to Docker daemon (local socket or remote context)

## Run locally

```bash
mvn clean package
java -jar target/watchdock-1.0-SNAPSHOT.jar
```

Panel: `http://localhost:8090` (or custom `WATCHDOCK_PORT`).

## Run in Docker

Build image:

```bash
docker build -t watchdock:latest .
```

Run container (Linux/macOS Docker Engine):

```bash
docker run --name watchdock \
  -p 8090:8090 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e WATCHDOCK_PORT=8090 \
  watchdock:latest
```

Optional SMSAPI.pl envs:

```bash
-e SMSAPI_TOKEN=... \
-e SMSAPI_TO=48123123123 \
-e SMSAPI_FROM=WatchDock
```

If `SMSAPI_TOKEN` or `SMSAPI_TO` is missing, WatchDock uses mock notifications.

## Environment variables

All variables can also be passed as Java system properties (`-D...`) with the property names from the table.

| Environment variable | Java property | Default | Description |
|---|---|---|---|
| `WATCHDOCK_PORT` | `watchdock.port` | `8090` | HTTP port for web panel/API. |
| `WATCHDOCK_POLL_SECONDS` | `watchdock.pollSeconds` | `5` | Poll interval for Docker state checks. |
| `WATCHDOCK_INACTIVE_MINUTES` | `watchdock.inactiveMinutes` | `15` | Inactivity threshold for `INACTIVE_TOO_LONG` alert. |
| `WATCHDOCK_RESTART_THRESHOLD` | `watchdock.restartThreshold` | `3` | Restart events threshold for restart-loop alert. |
| `WATCHDOCK_RESTART_WINDOW_SECONDS` | `watchdock.restartWindowSeconds` | `120` | Time window for counting restarts. |
| `WATCHDOCK_ALERT_COOLDOWN_SECONDS` | `watchdock.alertCooldownSeconds` | `900` | Cooldown per alert type/container. |
| `WATCHDOCK_DOCKER_COMMAND` | `watchdock.dockerCommand` | `docker` | Docker CLI command used by the app. |
| `SMSAPI_TOKEN` | `smsapi.token` | empty | SMSAPI.pl bearer token. |
| `SMSAPI_FROM` | `smsapi.from` | `WatchDock` | Sender name for SMSAPI.pl. |
| `SMSAPI_TO` | `smsapi.to` | empty | Target phone number (e.g. `48123123123`). |
| `SMSAPI_URL` | `smsapi.url` | `https://api.smsapi.pl/sms.do` | SMSAPI.pl endpoint URL. |
| `WATCHDOCK_NOTIFY_START_HOUR` | `watchdock.notifyStartHour` | `9` | Notification window start hour (`0-23`, local timezone). |
| `WATCHDOCK_NOTIFY_END_HOUR` | `watchdock.notifyEndHour` | `21` | Notification window end hour (`0-23`, local timezone). |

## Provider shown in web panel

The web panel shows an active notification provider:

- `SMSAPI.pl` when real SMS integration is enabled
- `Mock SMSAPI` when SMS config is incomplete
