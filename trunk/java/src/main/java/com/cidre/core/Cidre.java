package com.cidre.core;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.algorithms.CidreMath;
import com.cidre.io.BfImageLoader;
import com.cidre.io.BfImageWriter;
import com.cidre.preprocessing.CidrePreprocess;

import loci.common.services.ServiceFactory;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.out.TiffWriter;
import loci.formats.services.OMEXMLService;

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
        int widthImage = descriptor.imageSize.width;
        int heightImage = descriptor.imageSize.height;
        int widthSmall = descriptor.imageSize_small.width;
        int heightSmall = descriptor.imageSize_small.height;

        log.info("Writing model V");
        String fileName = this.outputDir + File.separator + "Model_V" + ".tif";
        BfImageWriter writer = new BfImageWriter(
            fileName, widthImage, heightImage,
            FormatTools.getPixelTypeString(FormatTools.DOUBLE));
        writer.initialise();
        writer.write(descriptor.v, 0);
        writer.close();

        fileName = this.outputDir + File.separator + "Model_V_small" + ".tif";
        writer = new BfImageWriter(
            fileName, widthSmall, heightSmall,
            FormatTools.getPixelTypeString(FormatTools.DOUBLE));
        writer.initialise();
        writer.write(descriptor.v_small, 0);
        writer.close();

        log.info("Writing model z");
        fileName = this.outputDir + File.separator + "Model_Z" + ".tif";
        writer = new BfImageWriter(
            fileName, widthImage, heightImage,
            FormatTools.getPixelTypeString(FormatTools.DOUBLE));
        writer.initialise();
        writer.write(descriptor.z, 0);
        writer.close();

        fileName = this.outputDir + File.separator + "Model_Z_small" + ".tif";
        writer = new BfImageWriter(
            fileName, widthSmall, heightSmall,
            FormatTools.getPixelTypeString(FormatTools.DOUBLE));
        writer.initialise();
        writer.write(descriptor.z_small, 0);
        writer.close();
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
