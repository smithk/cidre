package com.cidre;


import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.core.Options;
import com.cidre.input.BfImageLoader;

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

    public void correctImages() {
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
        BfImageLoader image_loader = new BfImageLoader(
            options, this.input.get(0), new ArrayList<Integer>(0), 0, 0, 0);
        try {
            image_loader.loadImages();
        } catch (Exception e) {
            log.error(e.toString());
            e.printStackTrace();
        }
    };
}
