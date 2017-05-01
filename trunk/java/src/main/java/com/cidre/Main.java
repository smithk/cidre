package com.cidre;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.core.Cidre;
import com.cidre.core.Options;

import ch.qos.logback.classic.Level;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

public class Main {

    @Arg
    private ArrayList<String> input;

    @Arg
    private ArrayList<String> modelFiles;

    @Arg
    private String modelOutput;

    @Arg
    private String output;

    @Arg
    private ArrayList<Integer> channels;

    @Arg
    private Boolean skipPreprocessing;

    @Arg
    private Boolean useMinImage;

    @Arg
    private Boolean planePerFile;

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
        parser.description("Passing Folder or ");
        parser.addArgument("--input").nargs("*")
              .help("Directory path, file path, list of file paths or "
                   + "file path masks are valid inputs.\n"
                   + "One model file per input file will be created unless "
                   + "`planePerFile` flag is specified.");
        parser.addArgument("--modelFiles")
              .nargs("*")
              .help("Select files or directory with model files."
                  + "One model file per input file is required unless"
                  + " `planePerFile` flag is specified.");
        parser.addArgument("--modelOutput")
              .help("Output directory.\n"
                  + "A multi-channel model file per input file / set of files "
                  + "will be created.");
        parser.addArgument("--output")
              .help("Output directory for corrected images.");
        parser.addArgument("--channels").nargs("*").type(Integer.class)
              .help("Select channels to calculate "
                    + "the illumination correction for.");
        parser.addArgument("--illuminationCorrectionMode")
              .choices(Options.CorrectionMode.values())
              .setDefault(Options.CorrectionMode.ZERO_LIGHT_PRESERVED)
              .help("Select IlluminationCorrection mode.");
        parser.addArgument("--planePerFile")
              .action(Arguments.storeTrue())
              .help("Use this option if the planes are stored one per file"
                  + " rather then all in a single file.");
        parser.addArgument("--useMinImage")
              .action(Arguments.storeTrue())
              .help("Use min(stack) as zero current image.");
        parser.addArgument("--skipPreprocessing")
              .action(Arguments.storeTrue())
              .help("Skip preprocessing.");
        parser.addArgument("--debug")
              .action(Arguments.storeTrue())
              .help("Set logging level to Debug");
        parser.addArgument("--overwrite")
              .action(Arguments.storeTrue())
              .help("Overwrite output files if exist");

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

    private ArrayList<String> getFileList(ArrayList<String> inputList) {
        ArrayList<String> fileList = new ArrayList<String>();
        if (inputList == null) {
            return fileList;
        }
        for (String inputName : inputList) {
            File file = new File(inputName);
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
        }
        log.debug("File list: {}", fileList);
        return fileList;
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

        ArrayList<String> fileNames = this.getFileList(this.input);

        if (this.planePerFile) {
            
        } else {
            for (String fileName : fileNames) {
                Cidre cidre = new Cidre(fileName, this.output);
                cidre.buildModel();
                cidre.saveModel();
                cidre.applyModel();
            }
        }
        log.info("Done");
    };
}
