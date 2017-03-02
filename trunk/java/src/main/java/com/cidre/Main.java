package com.cidre;


import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.core.ImageCorrection;
import com.cidre.core.ModelDescriptor;
import com.cidre.core.ModelGenerator;
import com.cidre.core.Options;
import com.cidre.input.BfImageLoader;

import ch.qos.logback.classic.Level;
import loci.common.services.ServiceFactory;
import loci.formats.FormatTools;
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
        Options options = new Options();
        List<Integer> series = new ArrayList<Integer>();
        for (int i = 0; i < 221; i++) {
            series.add(i);
        }
        BfImageLoader image_loader = new BfImageLoader(
            options, this.input.get(0), series, 0, 0, 0);
        try {
            image_loader.loadImages();
        } catch (Exception e) {
            log.error(e.toString());
            e.printStackTrace();
        }
        options = image_loader.getOptions();
        options.zLimits[0] = 256.0;
        options.zLimits[1] = 379.0;
        options.numberOfQuantiles = 221;
        ModelGenerator model = new ModelGenerator(options);
        ModelDescriptor descriptor = model.generate(image_loader.getStack());
        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        IMetadata meta = service.createOMEXMLMetadata();
        int width = image_loader.getWidth();
        int height = image_loader.getHeight();
        log.info("Write image size [{}, {}]", width, height);
        String imageName = output + File.separator + "File_";
        String fileName;
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
        if (true)
            return;
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
