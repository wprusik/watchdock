package com.github.wprusik.docker;

import java.io.IOException;
import java.util.List;

public interface DockerClient {
    List<ContainerSnapshot> listContainers() throws IOException, InterruptedException;
}
