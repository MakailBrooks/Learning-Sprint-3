package com.sensordata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SensorDataProcessorAppTest {

    @TempDir
    Path tempDir;

    @Test
    void main_runsWithoutThrowing_andCreatesOutputFile() {
        Path oldUserDir = Path.of(System.getProperty("user.dir"));
        System.setProperty("user.dir", tempDir.toString());
        try {
            assertDoesNotThrow(() -> SensorDataProcessorApp.main(new String[0]));

            Path output = tempDir.resolve("RacingStatsData.txt");
            assertTrue(Files.exists(output), "Expected RacingStatsData.txt to be created by the app");
        } finally {
            System.setProperty("user.dir", oldUserDir.toString());
        }
    }
}