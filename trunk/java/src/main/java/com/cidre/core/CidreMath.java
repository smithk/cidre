package com.cidre.core;

import java.util.List;

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
}
