package com.cidre.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.algorithms.CidreMath;
import com.cidre.io.ImageLoader;


public class ImageCorrection {

    private static final Logger log =
        LoggerFactory.getLogger(ImageCorrection.class);

    private ModelDescriptor descriptor;

    private Options.CorrectionMode correctionMode;

    private String outputDirectory;

    private ImageLoader imageLoader;

    private double mean_v;

    private double mean_z;

    private boolean modelInitialised;

    public ImageCorrection(
            ModelDescriptor descriptor, Options.CorrectionMode correctionMode,
            String outputDirectory, ImageLoader imageLoader)
    {
        this.descriptor = descriptor;
        this.correctionMode = correctionMode;
        this.outputDirectory = outputDirectory;
        this.imageLoader = imageLoader;
        this.modelInitialised = false;
    }

    private void initializeModel() {
        this.mean_v = CidreMath.mean(descriptor.v);
        this.mean_z = CidreMath.mean(descriptor.z);
        this.modelInitialised = true;
    }

    public float[][] correctPlane(double[][] pixelData) {
        if (!this.modelInitialised) {
            this.initializeModel();
        }
        return this.correctPlane(
            pixelData, this.descriptor.imageSize.width,
            this.descriptor.imageSize.height);
    }

    private float[][] correctPlane(double[][] pixelData, int width, int height)
    {
        double minImageMean = CidreMath.mean(descriptor.minImage);
        double enumerator, denominator;
        log.info("{}, {}", width, height);
        log.info("{}, {} ,{}", mean_v, mean_z, minImageMean);
        float[][] floatArray = new float[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // enumerator = pixelData[x][y] - model.z[x * height + y];
                enumerator =
                    pixelData[x][y] - (
                    descriptor.minImage[x * height + y]
                    - minImageMean + mean_z);
                denominator = descriptor.v[x * height + y];
                switch(this.correctionMode)
                {
                    case ZERO_LIGHT_PRESERVED:
                        floatArray[x][y] = (float) (
                            ((enumerator / denominator) * mean_v) + mean_z);
                        break;
                    case DYNAMIC_RANGE_CORRECTED:
                        floatArray[x][y] = (float) (
                             (enumerator / denominator) * mean_v);
                        break;
                    case DIRECT:
                        floatArray[x][y] = (float) (
                            enumerator / denominator);
                        break;
                    default:
                        log.error("Unrecognized correction mode.");
                        break;
                }
            }
        }
        log.info("Image size {}, mean: {}",
                 floatArray.length, CidreMath.mean(floatArray));
        return floatArray;
    }

    public static float[][] correctPlane(
        double[][] pixelData, ModelDescriptor descriptor,
        Options.CorrectionMode correctionMode)
    {
        double minImageMean = CidreMath.mean(descriptor.minImage);
        double enumerator, denominator;
        int width = descriptor.imageSize.width,
            height = descriptor.imageSize.height;
        double mean_v = CidreMath.mean(descriptor.v);
        double mean_z = CidreMath.mean(descriptor.z);
        log.debug("{}, {}", width, height);
        log.debug("{}, {} ,{}", mean_v, mean_z, minImageMean);
        float[][] floatArray = new float[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // enumerator = pixelData[x][y] - model.z[x * height + y];
                enumerator =
                    pixelData[x][y] - (
                        descriptor.minImage[x * height + y]
                        - minImageMean + mean_z);
                denominator = descriptor.v[x * height + y];
                switch(correctionMode)
                {
                    case ZERO_LIGHT_PRESERVED:
                        floatArray[x][y] = (float) (
                            ((enumerator / denominator) * mean_v) + mean_z);
                        break;
                    case DYNAMIC_RANGE_CORRECTED:
                        floatArray[x][y] = (float) (
                             (enumerator / denominator) * mean_v);
                        break;
                    case DIRECT:
                        floatArray[x][y] = (float) (
                            enumerator / denominator);
                        break;
                    default:
                        log.error("Unrecognized correction mode.");
                        break;
                }
            }
        }
        log.info("Image size {}, mean: {}",
                 floatArray.length, CidreMath.mean(floatArray));
        return floatArray;
    }

    public void execute() throws Exception {
        if (!this.modelInitialised) {
            this.initializeModel();
        }
        String modeName = "";
        switch (this.correctionMode)
        {
            case ZERO_LIGHT_PRESERVED:
                modeName = "zero-light preserved";
                break;
            case DYNAMIC_RANGE_CORRECTED:
                modeName = "dynamic range corrected";
                break;
            case DIRECT:
                modeName = "direct";
                break;
        }
        log.info("Writing {} corrected images to {}",
                 modeName, this.outputDirectory);

        int width = this.descriptor.imageSize.width;
        int height = this.descriptor.imageSize.height;

        double[][] doubleArray;
        for (int i = 0; i < this.imageLoader.getNumberOfImages(); i++) {
            doubleArray = this.imageLoader.loadPlane(i);
            floatArray = this.correctPlane(doubleArray, width, height);
        }
    }

}
