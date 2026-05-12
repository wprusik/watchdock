package com.github.wprusik.docker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DockerCliClient implements DockerClient {
    private static final String INSPECT_FORMAT = "{{.Id}}\t{{.Name}}\t{{.State.Status}}\t{{.State.StartedAt}}\t{{.State.FinishedAt}}\t{{.RestartCount}}\t{{.State.Restarting}}";
    private final String dockerCommand;

    public DockerCliClient(String dockerCommand) {
        this.dockerCommand = dockerCommand;
    }

    @Override
    public List<ContainerSnapshot> listContainers() throws IOException, InterruptedException {
        List<String> ids = run(List.of(dockerCommand, "ps", "-aq"));
        if (ids.isEmpty()) {
            return List.of();
        }

        List<String> command = new ArrayList<>();
        command.add(dockerCommand);
        command.add("inspect");
        command.add("--format");
        command.add(INSPECT_FORMAT);
        command.addAll(ids);

        Instant observedAt = Instant.now();
        List<String> lines = run(command);
        List<ContainerSnapshot> snapshots = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.split("\t", -1);
            if (parts.length < 7) {
                continue;
            }
            String name = parts[1].startsWith("/") ? parts[1].substring(1) : parts[1];
            String state = parts[2];
            boolean restarting = Boolean.parseBoolean(parts[6]) || "restarting".equalsIgnoreCase(state);
            boolean running = "running".equalsIgnoreCase(state) && !restarting;
            snapshots.add(new ContainerSnapshot(
                    parts[0],
                    name,
                    state,
                    running,
                    restarting,
                    parseInstant(parts[3]),
                    parseInstant(parts[4]),
                    parseLong(parts[5]),
                    observedAt
            ));
        }
        return snapshots;
    }

    private List<String> run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    output.add(line.trim());
                }
            }
        }

        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Docker command timed out: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            throw new IOException("Docker command failed: " + String.join(" ", command) + " -> " + String.join("\n", output));
        }
        return output;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank() || value.startsWith("0001-")) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
