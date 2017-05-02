package com.cidre.core;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.algorithms.CidreMath;
import com.cidre.io.BfImageLoader;
import com.cidre.io.BfImageWriter;
import com.cidre.io.BfModelLoader;
import com.cidre.io.BfModelWriter;

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

    private ArrayList<ModelDescriptor> descriptors =
        new ArrayList<ModelDescriptor> ();

    private String input = null;

    private String modelInput = null;

    private String outputDir = null;

    private String modelOutputDir = null;

    private BfImageLoader imageLoader = null;

    private boolean useMinImage = false;

    private boolean skipPreProcessing = false;

    public Cidre(String fileName, String outputDir) {
        this.input = fileName;
        this.outputDir = outputDir;
    };

    public Cidre(String fileName, String outputDir,
                 String modelInput, String modelOutputDir,
                 boolean useMinImage, boolean skipPreprocessing)
    {
        this.input = fileName;
        this.outputDir = outputDir;
        this.modelInput = modelInput;
        this.modelOutputDir = modelOutputDir;
        this.useMinImage = useMinImage;
        this.skipPreProcessing = skipPreprocessing;
    }

    public void setModelInput(String modelInput) {
        this.modelInput = modelInput;
    }

    public void setModelOutputDir(String modelOutputDir) {
        this.modelOutputDir = modelOutputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public void setUseMinImage(boolean useMinImage) {
        this.useMinImage = useMinImage;
    }

    public void setSkipPreprocessing(boolean skipPreprocessing) {
        this.skipPreProcessing = skipPreprocessing;
    }

    private void printSettings() {
        log.info("CIDRE settings:\n"
                 + "\tInput file:        {}\n"
                 + "\tOutput Dir:        {}\n"
                 + "\tModel input:       {}\n"
                 + "\tModel output:      {}\n"
                 + "\tuseMinImage:       {}\n"
                 + "\tskipPreprocessing: {}",
                 this.input, this.outputDir, this.modelInput,
                 this.modelOutputDir, this.useMinImage, this.skipPreProcessing
        );
    }

    public void execute() throws Exception {
        this.printSettings();
        if (this.input == null) {
            throw new Exception("No input or empty.");
        }
        Options options = new Options();
        this.imageLoader = new BfImageLoader(options, this.input);
        if (this.modelInput == null) {
            log.info("Building model");
            this.buildModel();
        } else {
            log.info("Loading model");
            this.loadModel();
        }
        if (this.modelOutputDir != null) {
            log.info("Saving model files");
            this.saveModel();
        }
        if (this.outputDir != null) {
            log.info("Correcting files");
            this.applyModel();
        }
    }

    public ArrayList<ModelDescriptor> getModel() {
        return this.descriptors;
    }

    /**
     * Build CIDRE model
     */
    public ArrayList<ModelDescriptor> buildModel()
    {
        log.info("Building CIDRE model");
        this.descriptors.clear();
        Options options = new Options();
        this.imageLoader = new BfImageLoader(options, this.input);
        try {
            this.imageLoader.initialise();
        } catch (Exception e) {
            log.error(e.toString());
            e.printStackTrace();
            return null;
        }
        options.numberOfQuantiles = this.imageLoader.getSizeS();
        options.numImagesProvided = this.imageLoader.getSizeS();
        log.info("Building model from {} images [{}, {}]",
                 this.imageLoader.getSizeS(), this.imageLoader.getWidth(),
                 this.imageLoader.getHeight());
        for (int channel = 0;
             channel < this.imageLoader.getSizeC(); channel++)
        {
            try {
                this.imageLoader.loadImages(channel);
            } catch (Exception e) {
                log.error(e.toString());
                e.printStackTrace();
                return null;
            }
            double[] zLimits = CidreMath.zLimitsFromPercentiles(
                this.imageLoader.getMinImage());
            options.zLimits[0] = zLimits[0];
            options.zLimits[1] = zLimits[1];
            ModelGenerator model = new ModelGenerator(options);
            ModelDescriptor descriptor = model.generate(
                this.imageLoader.getStack());
            double[][] minImage = this.imageLoader.getMinImage();
            double[] buffer = new double[
                this.imageLoader.getHeight() * this.imageLoader.getWidth()];
            int width = this.imageLoader.getWidth();
            int height = this.imageLoader.getHeight();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    buffer[x * height + y] = minImage[x][y];
                }
            }
            descriptor.minImage = buffer;
            this.descriptors.add(descriptor);
        }
        return this.descriptors;
    }

    /**
     * Load CIDRE model
     * @throws Exception 
     */
    public void loadModel() throws Exception {
        BfModelLoader loader = new BfModelLoader(this.modelInput);
        this.descriptors = loader.loadModel();
    };

    /**
     * Save CIDRE model
     */
    public void saveModel() throws Exception
    {
        if (this.descriptors != null) {
            this.saveModel(this.descriptors);
        } else {
            log.error("Could not save model Descriptor is Null");
        }
    }

    public void saveModel(ArrayList<ModelDescriptor> descriptor) throws Exception
    {
        log.info("Saving model to {}", this.modelOutputDir);
        File modelOut = new File(this.modelOutputDir);
        String fileName = modelOut.getName().split("\\.")[0] + ".ill.cor.ome.tif";
        fileName = this.outputDir + File.separator + fileName;
        BfModelWriter writer = new BfModelWriter(fileName, descriptors);
        writer.saveModel();
    };

    /**
     * Apply model
     * @throws Exception 
     */
    public void applyModel() throws Exception {
        if (this.imageLoader == null) {
            Options options = new Options();
            this.imageLoader = new BfImageLoader(options, this.input);
        }
        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        IMetadata meta = service.createOMEXMLMetadata();
        int channel = 0, timepoint = 0, zPlane = 0;
        double[][] pixels;
        float[][] pixelsFloat;
        String fileName;
        ImageCorrection corrector = new ImageCorrection(
            this.descriptors.get(channel),
            Options.CorrectionMode.DYNAMIC_RANGE_CORRECTED,
            this.outputDir, this.imageLoader);
        MetadataTools.populateMetadata(
            meta, 0, null, false, "XYZCT",
            FormatTools.getPixelTypeString(FormatTools.FLOAT),
            this.imageLoader.getWidth(), this.imageLoader.getHeight(),
            1, 1, 1, 1);
        for (int s = 0; s < this.imageLoader.getSizeS(); s++) {
            pixels = this.imageLoader.loadPlane(
                s, channel, timepoint, zPlane);
            pixelsFloat = corrector.correctPlane(pixels);
            fileName = this.outputDir + File.separator
                     + "Output_" + String.format("%03d", s) + ".tif";
            TiffWriter writer = new TiffWriter();
            writer.setMetadataRetrieve(meta);
            writer.setId(fileName);
            int width = this.imageLoader.getWidth();
            int height = this.imageLoader.getHeight();
            ByteBuffer buffer = ByteBuffer.allocate(4 * width * height);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    buffer.putFloat(pixelsFloat[x][y]);
                }
            }
            writer.saveBytes(0, buffer.array());
            writer.close();
        }
    };

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
