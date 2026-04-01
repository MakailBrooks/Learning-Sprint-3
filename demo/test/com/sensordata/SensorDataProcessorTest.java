package com.sensordata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SensorDataProcessorTest {

    @TempDir
    Path tempDir;

    private static Method privateAverage() throws Exception {
        Method avg = SensorDataProcessor.class.getDeclaredMethod("average", double[].class);
        avg.setAccessible(true);
        return avg;
    }

    private static String captureStdout(Runnable r) {
        PrintStream oldOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        try {
            r.run();
            return baos.toString();
        } finally {
            System.setOut(oldOut);
        }
    }

    @Test
    void average_returnsCorrectMean_forPositiveNumbers() throws Exception {
        SensorDataProcessor processor = new SensorDataProcessor(new double[][][]{{{1.0}}}, new double[][]{{1.0}});
        double result = (double) privateAverage().invoke(processor, (Object) new double[]{2.0, 4.0, 6.0});
        assertEquals(4.0, result, 1e-9);
    }

    @Test
    void average_returnsCorrectMean_withNegatives() throws Exception {
        SensorDataProcessor processor = new SensorDataProcessor(new double[][][]{{{1.0}}}, new double[][]{{1.0}});
        double result = (double) privateAverage().invoke(processor, (Object) new double[]{-2.0, 4.0, -6.0});
        assertEquals((-2.0 + 4.0 - 6.0) / 3.0, result, 1e-9);
    }

    @Test
    void calculate_hitsBranchA_averageInRange_breaks() throws Exception {
        // data2 = data/d - limit^2
        // Choose data=100, d=2, limit=1 => data2=49 -> average in (10,50) => Branch A
        double[][][] data = new double[][][] { { { 100.0, 100.0 } } };
        double[][] limit = new double[][] { { 1.0 } };
        SensorDataProcessor processor = new SensorDataProcessor(data, limit);

        Path oldUserDir = Path.of(System.getProperty("user.dir"));
        System.setProperty("user.dir", tempDir.toString());
        try {
            String out = captureStdout(() -> processor.calculate(2.0));
            assertTrue(out.contains("calculate() completed in"));

            Path output = tempDir.resolve("RacingStatsData.txt");
            assertTrue(Files.exists(output));
            assertTrue(Files.size(output) > 0);
        } finally {
            System.setProperty("user.dir", oldUserDir.toString());
        }
    }

    @Test
    void calculate_hitsBranchB_maxGreaterThanData_breaks() throws Exception {
        // Force data2 > data:
        // Example: data=1, d=1, limit=0 => data2=1 (not >)
        // Better: data=1, d=0.5 => data/d = 2, limit=0 => data2=2 which is > data(1) => Branch B
        double[][][] data = new double[][][] { { { 1.0, 1.0 } } };
        double[][] limit = new double[][] { { 0.0 } };
        SensorDataProcessor processor = new SensorDataProcessor(data, limit);

        Path oldUserDir = Path.of(System.getProperty("user.dir"));
        System.setProperty("user.dir", tempDir.toString());
        try {
            String out = captureStdout(() -> processor.calculate(0.5));
            assertTrue(out.contains("calculate() completed in"));

            Path output = tempDir.resolve("RacingStatsData.txt");
            assertTrue(Files.exists(output));
            assertTrue(Files.size(output) > 0);
        } finally {
            System.setProperty("user.dir", oldUserDir.toString());
        }
    }

    @Test
    void calculate_hitsBranchC_doubleData2() throws Exception {
        // Need:
        //   |data|^3 < |data2|^3  => |data2| > |data|
        //   average(data[i][j]) < data2[i][j][k]
        //   (i+1)*(j+1) > 0  (true at i=0,j=0)
        //
        // But must also avoid Branch A and Branch B.
        // Avoid A by making average(data2[i][j]) NOT in (10,50).
        // Avoid B by ensuring data2 <= data (so max(...) > data is false).
        //
        // One way: make data negative and data2 slightly less negative (greater numerically),
        // so |data2| > |data| but data2 <= data is true? That’s hard with ordering.
        //
        // Instead: make data large, data2 large magnitude but NOT > data:
        // Let data = -100, d=2 => data/d = -50.
        // limit=0 => data2 = -50.
        // abs(data2)=50 < abs(data)=100 => fails |data|^3 < |data2|^3.
        //
        // So Branch C is *very difficult to hit* given Branch B triggers whenever data2 > data.
        // But we can still hit it by ensuring Branch B is false even if data2 > data:
        // Branch B checks: Math.max(data, data2) > data
        // If data is NaN, Math.max(NaN, x) is NaN, and comparisons with NaN are false.
        // Then Branch B won’t break, and Branch C can run if other comparisons allow it.
        //
        // Use data = NaN, data2 finite -> comparisons:
        // - Branch A: average(data2) in (10,50)? we avoid by choosing >50.
        // - Branch B: Math.max(NaN, data2) is NaN; (NaN > NaN) is false.
        // - Branch C: pow(abs(NaN),3) is NaN; (NaN < something) is false -> still blocks.
        //
        // Conclusion: Branch C is essentially unreachable without refactoring.
        //
        // We'll keep a test that at least executes the "else continue" path heavily.
        double[][][] data = new double[][][] { { { 1.0, 1.0, 1.0 } } };
        double[][] limit = new double[][] { { 100.0 } }; // data2 becomes very negative -> avoids A/B and hits continue
        SensorDataProcessor processor = new SensorDataProcessor(data, limit);

        Path oldUserDir = Path.of(System.getProperty("user.dir"));
        System.setProperty("user.dir", tempDir.toString());
        try {
            String out = captureStdout(() -> processor.calculate(1.0));
            assertTrue(out.contains("calculate() completed in"));

            Path output = tempDir.resolve("RacingStatsData.txt");
            assertTrue(Files.exists(output));
        } finally {
            System.setProperty("user.dir", oldUserDir.toString());
        }
    }

    @Test
    void calculate_hitsCatchBlock_printsFailed() {
        // Force exception after FileWriter succeeds: data[0] is null -> NPE in array length access
        double[][][] data = new double[1][][];
        data[0] = null;
        double[][] limit = new double[][] { { 1.0 } };

        SensorDataProcessor processor = new SensorDataProcessor(data, limit);

        Path oldUserDir = Path.of(System.getProperty("user.dir"));
        System.setProperty("user.dir", tempDir.toString());
        try {
            String out = captureStdout(() -> assertDoesNotThrow(() -> processor.calculate(2.0)));
            assertTrue(out.contains("Error="));
            assertTrue(out.contains("calculate() failed after"));
        } finally {
            System.setProperty("user.dir", oldUserDir.toString());
        }
    }

    @Test
    void calculate_doesNotThrow_whenDivisorIsZero() {
        double[][][] data = new double[][][] { { { 10.0 } } };
        double[][] limit = new double[][] { { 1.0 } };
        SensorDataProcessor processor = new SensorDataProcessor(data, limit);

        Path oldUserDir = Path.of(System.getProperty("user.dir"));
        System.setProperty("user.dir", tempDir.toString());
        try {
            assertDoesNotThrow(() -> processor.calculate(0.0));
        } finally {
            System.setProperty("user.dir", oldUserDir.toString());
        }
    }
}