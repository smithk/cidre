package com.cidre.core;

public class Cidre {

    private static final long serialVersionUID = 1L;

    private final int lambdaRScaleFactor = 10;

    private final int lambdaRMinValue = 0;

    private final int lambdaRMaxValue = 9;

    private final double lambdaRDefaultValue = 6;

    private final int lambdaZScaleFactor = 10;

    private final int lambdaZMinValue = -2;

    private final int lambdaZMaxValue = 5;

    private final double lambdaZDefaultValue = 0.5;

    public Cidre() {};

    /*
     * Build CIDRE model
     */
    private void buildModel(
            Double zMin, Double zMax, Double lambdaVreg, Double lambdaZero)
    {
        Options options = new Options();
        options.folderSource = "";
        options.fileFilterSource = "";
        options.folderDestination = "";
        options.lambdaVreg = lambdaVreg;
        options.lambdaZero = lambdaZero;
        options.zLimits[0] = zMin;
        options.zLimits[1] = zMax;
    }

    public void run() {};

    public void applyCorrection() {};

}
