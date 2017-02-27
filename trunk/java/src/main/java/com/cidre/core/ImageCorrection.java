package com.cidre.core;

import java.io.Writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.input.ImageLoader;


public class ImageCorrection {

    private static final Logger log =
        LoggerFactory.getLogger(ImageCorrection.class);

    private ModelDescriptor model;

    private Options.CorrectionMode correctionMode;

    private String outputDirectory;

    private ImageLoader imageLoader;

    private double mean_v;

    private double mean_z;

    private boolean modelInitialised;

    public ImageCorrection(
            ModelDescriptor model, Options.CorrectionMode correctionMode,
            String outputDirectory, ImageLoader imageLoader)
    {
        this.model = model;
        this.correctionMode = correctionMode;
        this.outputDirectory = outputDirectory;
        this.imageLoader = imageLoader;
        this.modelInitialised = false;
    }

    private void initializeModel() {
        this.mean_v = CidreMath.mean(model.v);
        this.mean_z = CidreMath.mean(model.z);
        this.modelInitialised = true;
    }

    public float[][] correctPlane(double[][] pixelData) {
        if (!this.modelInitialised) {
            this.initializeModel();
        }
        return this.correctPlane(
            pixelData, this.model.imageSize.width,
            this.model.imageSize.height);
    }

    private float[][] correctPlane(double[][] pixelData, int width, int height)
    {
        
        double enumerator, denominator;
        float[][] floatArray = new float[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                enumerator = pixelData[x][y] - model.z[x * height + y];
                denominator = model.v[x * height + y];
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

        int width = this.model.imageSize.width;
        int height = this.model.imageSize.height;

        double[][] doubleArray;
        float[][] floatArray;
        for (int i = 0; i < this.imageLoader.getNumberOfImages(); i++) {
            doubleArray = this.imageLoader.loadPlane(i);
            floatArray = this.correctPlane(doubleArray, width, height);
        }
    }

}
