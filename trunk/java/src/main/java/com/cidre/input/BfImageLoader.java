package com.cidre.input;

import java.awt.Dimension;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.core.Options;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.formats.in.DefaultMetadataOptions;
import loci.formats.in.MetadataOptions;
import loci.formats.services.OMEXMLService;

public class BfImageLoader extends ImageLoader {

    private static final Logger log =
            LoggerFactory.getLogger(BfImageLoader.class);

    // passed in
    private List<Integer> series;
    private List<Integer> channels;
    private List<Integer> zSections;
    private List<Integer> timepoints;

    // derived from passed in
    private int maxS;
    private int maxC;
    private int maxT;
    private int maxZ;

    // derived from reader
    private int sizeX;
    private int sizeY;
    private int sizeS;
    private int sizeC;
    private int sizeT;
    private int sizeZ;
    private int pixelType;

    private List<ImageReader> readers;

    public BfImageLoader(
            Options options, String fileMask,
            List<Integer> series, List<Integer> channels,
            List<Integer> zSections, List<Integer> timepoints)
    {
        super(options, fileMask);
        this.series = new ArrayList<Integer>(series);
        this.timepoints = new ArrayList<Integer>(timepoints);
        this.channels = new ArrayList<Integer>(channels);
        this.zSections = new ArrayList<Integer>(zSections);
        this.readers = new ArrayList<ImageReader>();
    }

    public BfImageLoader(
            Options options, String fileMask,
            List<Integer> series, Integer channel,
            Integer zSection, Integer timepoint)
    {
        super(options, fileMask);
        this.series = new ArrayList<Integer>(series);
        this.timepoints = new ArrayList<Integer>(timepoint);
        this.channels = new ArrayList<Integer>(channel);
        this.zSections = new ArrayList<Integer>(zSection);
    }

    @Override
    public boolean loadImages() throws Exception {
        this.checkRequestedDimensions();
        // store the number of source images into the options structure
        this.options.numImagesProvided =
            options.fileNames.size() * this.series.size() *
            this.channels.size() * this.timepoints.size() *
            this.zSections.size();
        if (this.options.numImagesProvided <= 0) {
            log.error("Empty dimension found. Nothing to read.");
            return false;
        }
        log.info("Loading {} planes", this.options.numImagesProvided);
        this.populateDimensions();
        this.options.imageSize = new Dimension(this.sizeX, this.sizeY);
        this.options.workingSize = determineWorkingSize(
            this.options.imageSize, this.options.targetNumPixels);
        this.readers.clear();
        if (this.getFileList(this.source, this.fileMask)) {
            for (String fileName : this.options.fileNames) {
                ImageReader reader = new ImageReader();
                this.initializeReader(reader);
                reader.setId(fileName);
                log.info("Reading planes from {}", fileName);
                if (!this.checkReaderDimensions(reader)) {
                    return false;
                }
                this.readers.add(reader);
            }
            this.readPlanes();
            return true;
        } else {
            return false;
        }
    }

    private void populateDimensions() throws Exception {
        this.maxS = Collections.max(this.series);
        this.maxC = Collections.max(this.channels);
        this.maxT = Collections.max(this.timepoints);
        this.maxZ = Collections.max(this.zSections);
        ImageReader reader = new ImageReader();
        this.initializeReader(reader);
        reader.setId(this.options.fileNames.get(0));
        reader.setSeries(this.series.get(0));
        this.sizeS = reader.getSeriesCount();
        this.sizeC = reader.getSizeC();
        this.sizeT = reader.getSizeT();
        this.sizeZ = reader.getSizeZ();
        this.sizeX = reader.getSizeX();
        this.sizeY = reader.getSizeY();
        this.pixelType = reader.getPixelType();
        if (!this.checkDimensions(reader)) {
            throw new Exception("Dimesion check failed.");
        }
    }

    private void checkRequestedDimensions() {
        if (this.series.size() == 0) {
            this.series.add(0);
        }
        if (this.channels.size() == 0) {
            this.channels.add(0);
        }
        if (this.timepoints.size() == 0) {
            this.timepoints.add(0);
        }
        if (this.zSections.size() == 0) {
            this.zSections.add(0);
        }
    }

    private void initializeReader(ImageReader reader)
            throws DependencyException, ServiceException
    {
        reader.setOriginalMetadataPopulated(true);
        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        reader.setMetadataStore(
            service.createOMEXMLMetadata(null, null));
        MetadataOptions options = new DefaultMetadataOptions();
        options.setValidate(true);
        reader.setMetadataOptions(options);
    }

    private boolean checkDimensions(ImageReader reader)
    {
        boolean noError = true;
        if (maxS >= reader.getSeriesCount()) {
            log.error("Not enough series in {}", reader.getCurrentFile());
            noError = false;
        }
        if (maxC >= reader.getSizeC()) {
            log.error("Not enough channels in {}", reader.getCurrentFile());
            noError = false;
        }
        if (maxT >= reader.getSizeT()) {
            log.error("Not enough timpoints in {}", reader.getCurrentFile());
            noError = false;
        }
        if (maxZ >= reader.getSizeZ()) {
            log.error("Not enough z sections in {}", reader.getCurrentFile());
            noError = false;
        }
        return noError;
    }

    private boolean checkReaderDimensions(ImageReader reader) {
        boolean noError = true;
        if (this.sizeS != reader.getSeriesCount()) {
            log.error("Series count differes for {}", reader.getCurrentFile());
            noError = false;
        }
        if (this.sizeC != reader.getSizeC()) {
            log.error("Channel count differs for {}", reader.getCurrentFile());
            noError = false;
        }
        if (this.sizeT != reader.getSizeT()) {
            log.error(
                "Timepoints count differs for {}", reader.getCurrentFile());
            noError = false;
        }
        if (this.sizeZ != reader.getSizeZ()) {
            log.error(
                "Z section count differs for {}", reader.getCurrentFile());
            noError = false;
        }
        if (this.sizeX != reader.getSizeX()) {
            log.error(
                "Width differs for {}", reader.getCurrentFile());
            noError = false;
        }
        if (this.sizeY != reader.getSizeY()) {
            log.error(
                "Height differs for {}", reader.getCurrentFile());
            noError = false;
        }
        if (this.pixelType != reader.getPixelType()) {
            log.error("Pixel type differs for {}", reader.getCurrentFile());
        }
        return noError;
    }

    public static double[][] toDoubleArray(
            byte[] byteArray, int width, int height)
    {
        int times = Double.SIZE / Byte.SIZE;
        double[] doubles = new double[byteArray.length / times];
        for(int i = 0;i < doubles.length; i++) {
            doubles[i] = ByteBuffer.wrap(
                byteArray, i * times, times).getDouble();
        }
        return doubles;
    }

    private void readPlanes() throws Exception {
        if (this.readers.isEmpty()) {
            throw new Exception("No readers initialised.");
        }
        this.S.clear();
        for (ImageReader reader : this.readers)
        {
            this.loadPlanes(reader);
        }
    }

    private void loadPlanes(ImageReader reader) throws Exception
    {
        for (int s : this.series) {
            reader.setSeries(s);
            for (int c : this.channels) {
                for (int z : this.zSections) {
                    for (int t : this.timepoints) {
                       byte[] plane = reader.openBytes(
                           reader.getIndex(z, c, t));
                       double[][] planeDouble = this.toDoubleArray(
                           plane, this.sizeX, this.sizeY);
                       double[][] planeRescaled = this.imresize(
                           planeDouble, this.sizeX, this.sizeY,
                           this.options.workingSize.width,
                           this.options.workingSize.height);
                    }
                }
            }
        }
    }
}
