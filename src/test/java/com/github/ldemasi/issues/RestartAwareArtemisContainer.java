package com.github.ldemasi.issues;

import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.images.builder.Transferable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

public class RestartAwareArtemisContainer extends ArtemisContainer {

    public RestartAwareArtemisContainer(String image) throws IOException {
        super(image);
        setPortBindings(Arrays.asList("61616:61616", "8161:8161"));
        String brokerXmlQueue = loadFileContentFromResources("broker.xml");
        withCopyToContainer(Transferable.of(brokerXmlQueue,0777), "/var/lib/artemis-instance/etc-override/broker.xml");
        setPortBindings(Arrays.asList("61616:61616", "8161:8161"));
    }

    public void restart() {
        String tag = this.getContainerId();
        String snapshotId = dockerClient.commitCmd(this.getContainerId())
                .withRepository("tempimg")
                .withTag(tag).exec();
        this.stop();
        this.setDockerImageName("tempimg:" + tag);
        this.start();
    }

    private static String loadFileContentFromResources(String filePath) throws IOException {
        ClassLoader classLoader = RestartAwareArtemisContainer.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(filePath)) {
            // the stream holding the file content
            if (inputStream == null) {
                throw new IllegalArgumentException("file not found! " + filePath);
            } else {
                return new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));
            }
        }
    }
}
