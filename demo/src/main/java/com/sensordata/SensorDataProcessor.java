package com.sensordata;

import java.io.BufferedWriter;
import java.io.FileWriter;

public class SensorDataProcessor{

    // Senson data and limits.
    public double[][][] data;
    public double[][] limit;

    // constructor
    public SensorDataProcessor(double[][][] data, double[][] limit) {
        this.data = data;
        this.limit = limit;
    }

    // calculates average of sensor data
    private double average(double[] array) {
        int i = 0;
        double val = 0;
        for (i = 0; i < array.length; i++) {
            val += array[i];
        }

        return val / array.length;
    }

    // calculate data
    public void calculate(double d) {
        long startTime = System.nanoTime();

        int lenI = data.length;
        int lenJ = data[0].length;
        int lenK = data[0][0].length;

        // 1. MASSIVE OPTIMIZATION: 3D Array Allocation completely deleted.
        // We do not need to store the data in memory if we stream it directly to the file.

        BufferedWriter out = null;

        try {
            out = new BufferedWriter(new FileWriter("RacingStatsData.txt"));

            // 2. Eradicate ALL division operations by caching their inverses
            double invD = 1.0 / d;
            double invLenK = 1.0 / lenK; 

            StringBuilder sb = new StringBuilder(lenJ * lenK * 10);

            for (int i = 0; i < lenI; i++) {
                for (int j = 0; j < lenJ; j++) {
                    
                    double[] currentData = data[i][j];
                    double limitSq = limit[i][j] * limit[i][j];
                    
                    // 3. Inline the original average() method
                    // Method calls inside tight loops add stack-frame overhead. 
                    // Computing it inline is significantly faster.
                    double sumData = 0.0;
                    for (int k = 0; k < lenK; k++) {
                        sumData += currentData[k];
                    }
                    double avgData = sumData * invLenK; 

                    double sumData2 = 0.0;
                    
                    sb.append("[");

                    for (int k = 0; k < lenK; k++) {
                        double val1 = currentData[k];
                        double val2 = (val1 * invD) - limitSq;
                        double finalVal = val2;

                        sumData2 += val2;
                        
                        // Multiply instead of divide
                        double avgData2 = sumData2 * invLenK;

                        boolean breakLoop = false;

                        // Evaluate logic
                        if (avgData2 > 10.0 && avgData2 < 50.0) {
                            breakLoop = true;
                        } else if (val2 > val1) {
                            breakLoop = true;
                        } else if (Math.abs(val1) < Math.abs(val2) && avgData < val2) {
                            finalVal = val2 * 2.0;
                            sumData2 += val2; 
                        }

                        // Write value instantly
                        sb.append(finalVal);
                        
                        if (breakLoop) {
                            // 4. Mimic default Java Array behavior
                            // If the loop breaks early, a real double[] array would have left 
                            // the remaining elements as 0.0. We simply append 0.0 directly to the file.
                            for (int rem = k + 1; rem < lenK; rem++) {
                                sb.append(", 0.0");
                            }
                            break; 
                        } else if (k < lenK - 1) {
                            sb.append(", ");
                        }
                    }
                    
                    sb.append("]\t");
                }
                
                out.write(sb.toString());
                out.newLine();
                sb.setLength(0); 
            }

            out.close();

            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            System.out.println("calculate() completed in " + elapsedMs + " ms");

        } catch (Exception e) {
            System.out.println("Error= " + e);
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            System.out.println("calculate() failed after " + elapsedMs + " ms");
            if (out != null) {
                try { out.close(); } catch (Exception ex) {}
            }
        }
    }
    
}