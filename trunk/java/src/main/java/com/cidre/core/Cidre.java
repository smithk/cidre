package com.cidre.core;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.algorithms.CidreMath;
import com.cidre.io.BfImageLoader;
import com.cidre.io.BfImageWriter;
import com.cidre.io.BfModelWriter;

import loci.formats.FormatTools;

public class Cidre {

    private static final Logger log =
        LoggerFactory.getLogger(Cidre.class);

    private static final long serialVersionUID = 1L;

    private final int lambdaRScaleFactor = 10;

    private final int lambdaRMinValue = 0;

    private final int lambdaRMaxValue = 9;

    private final double lambdaRDefaultValue = 6;

    private final int lambdaZScaleFactor = 10;

    private final int lambdaZMinValue = -2;

    private final int lambdaZMaxValue = 5;

    private final double lambdaZDefaultValue = 0.5;

    private ModelDescriptor descriptor = null;

    private String input;

    private String outputDir;

    private int width;

    private int height;

    public Cidre(String fileName, String outputDir) {
        this.input = fileName;
        this.outputDir = outputDir;
    };

    /**
     * Build CIDRE model
     */
    public void buildModel()
    {
        log.info("Building CIDRE model");
        Options options = new Options();
        BfImageLoader image_loader = new BfImageLoader(options, this.input);
        try {
            image_loader.initialise();
        } catch (Exception e) {
            log.error(e.toString());
            e.printStackTrace();
            return;
        }
        options.numberOfQuantiles = image_loader.getSizeS();
        options.numImagesProvided = image_loader.getSizeS();
        log.info("Building model from {} images [{}, {}]",
                 image_loader.getSizeS(), image_loader.getWidth(),
                 image_loader.getHeight());
        try {
            image_loader.loadImages(0);
        } catch (Exception e) {
            log.error(e.toString());
            e.printStackTrace();
            return;
        }
        double[] zLimits = CidreMath.zLimitsFromPercentiles(
                image_loader.getMinImage());
        options.zLimits[0] = zLimits[0];
        options.zLimits[1] = zLimits[1];
        ModelGenerator model = new ModelGenerator(options);
        this.descriptor = model.generate(image_loader.getStack());
    }

    /**
     * Load CIDRE model
     */
    public void loadModel() {};

    /**
     * Save CIDRE model
     */
    public void saveModel() throws Exception
    {
        if (this.descriptor != null) {
            this.saveModel(this.descriptor);
        } else {
            log.error("Could not save model Descriptor is Null");
        }
    }

    public void saveModel(ModelDescriptor descriptor) throws Exception
    {
        log.info("Saving model to {}", this.outputDir);
        String fileName = this.outputDir + File.separator
                        + "Cidre_model.ome.tif";
        BfModelWriter writer = new BfModelWriter(fileName, descriptor);
        writer.saveModel();
    };

    /**
     * Apply model
     */
    public void applyModel() {};

    private void printOptions(Options options){
        log.info(
            "CidreOptions:\n\tlambdaVreg: {}\n\tlambdaZero: {}" +
            "\n\tmaxLbgfsIterations: {}\n\tqPercent: {}\n\tzLimits:{}, {}" +
            "\n\timageSize: {}\n\tnumImagesProvided: {}\n\tbitDepth: {}" +
            "\n\tcorrectionMode: {}\n\ttargetNumPixels: {}" +
            "\n\tworkingSize: {}\n\tnumberOfQuantiles: {}",
            options.lambdaVreg, options.lambdaZero, options.maxLbgfsIterations,
            options.qPercent, options.zLimits[0], options.zLimits[1],
            options.imageSize, options.numImagesProvided, options.bitDepth,
            options.correctionMode, options.targetNumPixels,
            options.workingSize, options.numberOfQuantiles
        );
    }

}
