package com.cidre;


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
        for (int i = 0; i < 100; i++) {
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
        ModelGenerator model = new ModelGenerator(image_loader.getOptions());
        ModelDescriptor descriptor = model.generate(image_loader.getStack());
        ImageCorrection corrector = new ImageCorrection(
                descriptor, Options.CorrectionMode.ZERO_LIGHT_PRESERVED,
                this.output, image_loader);
        log.info("Image size: {}}", descriptor.imageSize);
        log.info("Image size small: {}", descriptor.imageSize_small);
        double[][] pixels;
        float[][] pixelsFloat;
        int channel = 0;
        int zPlane = 0;
        int timepoint = 0;
        String imageName = output + '/';
        String fileName;
        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        IMetadata meta = service.createOMEXMLMetadata();
        MetadataTools.populateMetadata(
            meta, 0, null, false, "XYZCT",
            FormatTools.getPixelTypeString(image_loader.getPixelType()),
            image_loader.getTileSizeX(), image_loader.getTileSizeY(),
            1, 1, 1, 1);
        meta.setPixelsBigEndian(false, 0);
        for (int i = 0; i < 100; i++) {
            pixels = image_loader.loadPlane(i, channel, timepoint, zPlane);
            pixelsFloat = corrector.correctPlane(pixels);
            TiffWriter writer = new TiffWriter();
            fileName = imageName + String.format("%03d", i) + ".tif";
            writer.setId(fileName);
            writer.saveBytes(0, pixelsFloat);
            writer.close();
        }
        
    };
}
