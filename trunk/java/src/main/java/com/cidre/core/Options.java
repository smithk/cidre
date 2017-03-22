package com.cidre.core;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

public class Options
{
    public enum CorrectionMode {
        ZERO_LIGHT_PRESERVED,
        DYNAMIC_RANGE_CORRECTED,
        DIRECT
    };

    public Double lambdaVreg = null;
    public Double lambdaZero = null;
    public Integer maxLbgfsIterations = null;
    public Double qPercent = null;
    public Double[] zLimits = new Double[2];
    public Dimension imageSize;
    public String folderSource;
    public String fileFilterSource;
    public String folderDestination;
    public List<String> fileNames = new ArrayList<String>();
    public int numImagesProvided;
    public Integer bitDepth = null;
    public CorrectionMode correctionMode = null;
    public int targetNumPixels = 9400;
    public Dimension workingSize;
    public int numberOfQuantiles = 200;

}