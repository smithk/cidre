package com.cidre.algorithms;

import java.util.List;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CidreMath {

    private static final Logger log =
            LoggerFactory.getLogger(CidreMath.class);

    public static double mean(double[] a) {
        int i;
        double sum = 0;
        for (i = 0; i < a.length; i++) {
            sum += a[i];
        }
        return sum / a.length;
    }

    public static double mean(double[][] a) {
        int i;
        int j;
        double sum = 0;
        double length = 0.0;
        for (i = 0; i < a.length; i++) {
            length += a[i].length;
            for (j = 0; j < a[i].length; j++) {
                sum += a[i][j];
            }
        }
        return sum / length;
    }

    public static double[][] min(List<double[][]> stack)
    {
        double[][] minImage = stack.get(0);
        double[][] currentImage;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (int i = 1; i < stack.size(); i++) {
            currentImage = stack.get(i);
            for (int x = 0; x < currentImage.length; x++) {
                for (int y = 0; y < currentImage[x].length; y++) {
                    if (currentImage[x][y] < minImage[x][y]) {
                        minImage[x][y] = currentImage[x][y];
                        if (currentImage[x][y] < min) {
                            min = currentImage[x][y];
                        }
                        if (currentImage[x][y] > max) {
                            max = currentImage[x][y];
                        }
                    } else {
                        if (minImage[x][y] < min) {
                            min = minImage[x][y];
                        }
                        if (minImage[x][y] > max) {
                            max = minImage[x][y];
                        }
                    }
                }
            }
        }
        log.info("Min: {}, Max: {}", min, max);
        return minImage;
    }

    public static double[][] min(double[][] image1, double[][] image2) {
        double[][] minImage = image1.clone();
        for (int x = 0; x < minImage.length; x++) {
            for (int y = 0; y < minImage[x].length; y++) {
                if (image2[x][y] < minImage[x][y]) {
                    minImage[x][y] = image2[x][y];
                }
            }
        }
        return minImage;
    }

    public static double min(double[][] a) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                if (a[i][j] < min) {
                    min = a[i][j];
                }
            }
        }
        return min;
    }

    public static double max(double[][] a) {
        double max = Double.MIN_VALUE;
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                if (a[i][j] > max) {
                    max = a[i][j];
                }
            }
        }
        return max;
    }

    public static double[] zLimitsFromPercentiles(double[] values) {
        double[] zLimits = new double[2];
        Percentile p = new Percentile();
        p.setData(values);
        zLimits[0] = p.evaluate(0.1);
        zLimits[1] = p.evaluate(99.9);
        log.info("zLimits {}, {}", zLimits[0], zLimits[1]);
        return zLimits;
    }

    public static double[] zLimitsFromPercentiles(double[][] values) {
        int height = values.length;
        int width = values[0].length;
        double[] valuesTemp = new double[width * height];
        for (int i = 0; i < values.length; i++) {
            for (int j = 0; j < values[j].length; j++) {
                valuesTemp[j + i * width] = values[i][j];
            }
        }
        return CidreMath.zLimitsFromPercentiles(valuesTemp);
    }

    public static double cubic(double x) {
        double absx = Math.abs(x);
        double absx2 = absx * absx;
        double absx3 = absx2 * absx;

        return (1.5 * absx3 - 2.5 * absx2 + 1.0) *
               (absx <= 1.0 ? 1.0 : 0.0) +
               (-0.5 * absx3 + 2.5 * absx2 - 4.0 * absx + 2.0) *
               ((1 < absx) && (absx <= 2) ? 1.0 : 0.0);
    }
}
