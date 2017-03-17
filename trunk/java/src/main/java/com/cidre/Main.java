package com.cidre;


import java.awt.Dimension;
import java.io.File;
import java.io.FilenameFilter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.core.CidreMath;
import com.cidre.core.ImageCorrection;
import com.cidre.core.ModelDescriptor;
import com.cidre.core.ModelGenerator;
import com.cidre.core.Options;
import com.cidre.input.BfImageLoader;
import com.cidre.input.BfImageWriter;

import ch.qos.logback.classic.Level;
import loci.common.services.ServiceFactory;
import loci.formats.FormatTools;
import loci.formats.ImageReader;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.out.TiffWriter;
import loci.formats.services.OMEXMLService;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

public class Main {
    @Arg
    private ArrayList<String> input;

    @Arg
    private String output;

    @Arg
    private ArrayList<Integer> channels;

    @Arg
    private Boolean debug;

    @Arg
    private Boolean overwrite;

    private static final Logger log =
        LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Throwable {
        log.info("CIDRE started");

    ArgumentParser parser =
            ArgumentParsers.newArgumentParser("stitcher");
        parser.addArgument("--input").nargs("*")
            .help("Files to generate pyramid from");
        parser.addArgument("--output").help("Output pyramid file name");
        parser.addArgument("--tileSize")
              .type(Integer.class)
              .setDefault(1024)
              .help("Set output pyramid tile size");
        parser.addArgument("--channels").nargs("*").type(Integer.class)
              .help("Select channels to write");
        parser.addArgument("--debug")
              .action(Arguments.storeTrue())
              .help("Set logging level to Debug");
        parser.addArgument("--overwrite")
              .action(Arguments.storeTrue())
              .help("Overwrite output file if exists");

        Main main = new Main();
        try {
            parser.parseArgs(args, main);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        main.correctImages();
    }

    protected class ImageNameFilter implements FilenameFilter {

        private Pattern pattern;

        public ImageNameFilter(String expression) {
            String correctedExpression = ".*";
            if (expression != null && expression != "") {
                correctedExpression = expression.replace(".", "\\.");
                correctedExpression = correctedExpression.replace("*", ".*");
            }
            pattern = Pattern.compile(
                correctedExpression, Pattern.CASE_INSENSITIVE);
        }

        @Override
        public boolean accept(File directory, String fileName) {
            return pattern.matcher(new File(fileName).getName()).matches();
        }
    }

    private ArrayList<String> getFileList(String inputDir) {
        ArrayList<String> fileList = new ArrayList<String>();
        File file = new File(inputDir);
        String path, fileName;
        if (file.isDirectory()) {
            path = file.toString() + File.separator;
            fileName = "*";
        } else {
            path = file.getParent() + File.separator;
            fileName = file.getName();
        }
        File directory = new File(path);
        log.debug("Searching for files in {}", directory.toString());
        File[] listOfFiles = directory.listFiles(
            new ImageNameFilter(fileName));
        if (listOfFiles != null) {
            log.debug("{}", listOfFiles.toString());
            for (int i = 0; i < listOfFiles.length; i++) {
                if (listOfFiles[i].getName().startsWith(".")) {
                    continue;
                }
                if (listOfFiles[i].isDirectory()) {
                    continue;
                }
                fileList.add(path + listOfFiles[i].getName());
                log.info("Adding {} to input",
                          path + listOfFiles[i].getName());
            }
        }
        log.info("Processing {} files", fileList.size());
        log.debug("File list: {}", fileList);
        return fileList;
    }

    public List<double[][]> loadPreProcessedImages(String inputDir) {
        ArrayList<String> fileNames = this.getFileList(inputDir);
        List<double[][]> stack = new ArrayList<double [][]>();
        boolean fp = false;
        boolean unsigned = false;
        for (String fileName : fileNames) {
            log.info("Reading from {}", fileName);
            ImageReader reader = new ImageReader();
            try {
                BfImageLoader.initializeReader(reader);
                reader.setId(fileName);
                if (reader.getPixelType() == FormatTools.FLOAT ||
                    reader.getPixelType() == FormatTools.DOUBLE)
                {
                    fp = true;
                }
                if (reader.getPixelType() == FormatTools.UINT8 ||
                    reader.getPixelType() == FormatTools.UINT16 ||
                    reader.getPixelType() == FormatTools.UINT32)
                {
                    unsigned = true;
                }
                byte[] plane = reader.openBytes(0);
                double[][] planeDouble = BfImageLoader.toDoubleArray(
                    plane, (int) (0.125 * reader.getBitsPerPixel()),
                    fp, reader.isLittleEndian(), unsigned,
                    reader.getSizeX(), reader.getSizeY());
                stack.add(planeDouble);
            } catch (Exception ex) {
                log.error("{}", ex.getCause());
            }
        }
        return stack;
    }

    public void setOptionsToTestValues(Options options) {
        options.zLimits[0] = 256.0;
        options.zLimits[1] = 379.0;
        options.numberOfQuantiles = 221;
        options.bitDepth = 65536;
        options.workingSize = new Dimension(106, 89);
        options.imageSize = new Dimension(1280, 1080);
    }

    public void testBuildModel(String inputDir) {
        List<double[][]> stack = this.loadPreProcessedImages(inputDir);
        Options options = new Options();
        this.setOptionsToTestValues(options);
        ModelGenerator model = new ModelGenerator(options);
        ModelDescriptor descriptor = model.generate(stack);
    }

    public void printOptions(Options options) {
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
            options.workingSize, options.numberOfQuantiles);
    }

    public void correctImages() throws Exception {
     // Setup logger
        ch.qos.logback.classic.Logger root =
            (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(
                Logger.ROOT_LOGGER_NAME);
        if (this.debug) {
            root.setLevel(Level.DEBUG);
        } else {
            root.setLevel(Level.INFO);
        }
        String dir = output + File.separator +  "image_loader_output";
        //this.testBuildModel(dir);
        Options options = new Options();
        List<Integer> series = new ArrayList<Integer>();
        for (int i = 0; i < 221; i++) {
            series.add(i);
        }
        options.zLimits[0] = 256.0;
        options.zLimits[1] = 379.0;
        options.numberOfQuantiles = 221;
        BfImageLoader image_loader = new BfImageLoader(
            options, this.input.get(0), series, 0, 0, 0);
        try {
            image_loader.loadImages();
        } catch (Exception e) {
            log.error(e.toString());
            e.printStackTrace();
        }
        this.printOptions(options);
        List<double[][]> stack = image_loader.getStack();
        String imageName = output + File.separator + "File_";
        String fileName;
        options = image_loader.getOptions();
        for (int i = 0; i < stack.size(); i++) {
            fileName = output + File.separator +  "image_loader_output"
                     + File.separator + "img_loader_"
                     + String.format("%03d", i) + ".tif";
            BfImageWriter writer = new BfImageWriter(
                fileName, options.workingSize.width,
                options.workingSize.height,
                FormatTools.getPixelTypeString(FormatTools.DOUBLE));
            writer.initialise();
            writer.write(stack.get(i), 0);
            writer.close();
        }
        ModelGenerator model = new ModelGenerator(options);
        ModelDescriptor descriptor = model.generate(image_loader.getStack());
        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        IMetadata meta = service.createOMEXMLMetadata();
        int width = image_loader.getWidth();
        int height = image_loader.getHeight();
        log.info("Write image size [{}, {}]", width, height);

        MetadataTools.populateMetadata(
            meta, 0, null, false, "XYZCT",
            FormatTools.getPixelTypeString(FormatTools.DOUBLE),
            width, height,
            1, 1, 1, 1);
        //meta.setPixelsBigEndian(false, 0);
        TiffWriter writera = new TiffWriter();
        writera.setMetadataRetrieve(meta);
        fileName = imageName + "model_v_before" + ".tif";
        writera.setId(fileName);
        ByteBuffer buffer1 = ByteBuffer.allocate(8 * width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffer1.putDouble(descriptor.v[x * height + y]);
            }
        }
        writera.saveBytes(0, buffer1.array());
        writera.close();
        TiffWriter writerb = new TiffWriter();
        writerb.setMetadataRetrieve(meta);
        fileName = imageName + "model_z_before" + ".tif";
        writerb.setId(fileName);
        ByteBuffer buffer2 = ByteBuffer.allocate(8 * width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffer2.putDouble(descriptor.z[x * height + y]);
            }
        }
        writerb.saveBytes(0, buffer2.array());
        writerb.close();
        ImageCorrection corrector = new ImageCorrection(
                descriptor, Options.CorrectionMode.DYNAMIC_RANGE_CORRECTED,
                this.output, image_loader);
        log.info("Image size: {}}", descriptor.imageSize);
        log.info("Image size small: {}", descriptor.imageSize_small);
        double[][] pixels;
        float[][] pixelsFloat;
        int channel = 0;
        int zPlane = 0;
        int timepoint = 0;

        fileName = imageName + "min_image" + ".tif";
        MetadataTools.populateMetadata(
                meta, 0, null, false, "XYZCT",
                FormatTools.getPixelTypeString(FormatTools.DOUBLE),
                width, height,
                1, 1, 1, 1);
        TiffWriter writerMin = new TiffWriter();
        writerMin.setMetadataRetrieve(meta);
        writerMin.setId(fileName);
        ByteBuffer bufferMin = ByteBuffer.allocate(8 * width * height);
        double[][] minImage = image_loader.getMinImage();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bufferMin.putDouble(minImage[x][y]);
            }
        }
        writerMin.saveBytes(0, bufferMin.array());
        writerMin.close();

        MetadataTools.populateMetadata(
                meta, 0, null, false, "XYZCT",
                FormatTools.getPixelTypeString(FormatTools.FLOAT),
                width, height,
                1, 1, 1, 1);
        for (int i = 0; i < 221; i++) {
            pixels = image_loader.loadPlane(i, channel, timepoint, zPlane);
            pixelsFloat = corrector.correctPlane(pixels);
            fileName = imageName + String.format("%03d", i) + ".tif";
            log.info("Saving {}", fileName);
            TiffWriter writer = new TiffWriter();
            writer.setMetadataRetrieve(meta);
            writer.setId(fileName);
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
}
