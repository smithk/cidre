package com.cidre.core;

public class CidreMath {

    public static double mean(double[] a) {
        int i;
        double sum = 0;
        for (i = 0; i < a.length; i++) {
            sum += a[i];
        }
        return sum / a.length;
    }
}
