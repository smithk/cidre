package com.cidre.io;

import java.awt.Dimension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.core.ModelDescriptor;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.formats.in.DefaultMetadataOptions;
import loci.formats.in.MetadataOptions;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.meta.MetadataStore;
import loci.formats.services.OMEXMLService;

public class BfModelLoader {

    private static final Logger log =
        LoggerFactory.getLogger(BfModelLoader.class);

    private List<ModelDescriptor> descriptors;

    private ImageReader reader;

    private int numberOfChannels;

    private boolean fp;

    private boolean unsigned;

    String fileName;

    public BfModelLoader(String fileName) {
        this.fileName = fileName;
        this.reader = new ImageReader();
    }

    private void initializeReader() throws Exception
    {
        this.reader.setOriginalMetadataPopulated(true);
        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
       this. reader.setMetadataStore(
            service.createOMEXMLMetadata(null, null));
        MetadataOptions options = new DefaultMetadataOptions();
        options.setValidate(true);
        this.reader.setMetadataOptions(options);
        this.reader.setId(this.fileName);

        this.fp = false;
        this.unsigned = false;
        if (this.reader.getPixelType() == loci.formats.FormatTools.FLOAT ||
            this.reader.getPixelType() == loci.formats.FormatTools.DOUBLE)
        {
            this.fp = true;
        }
        if (this.reader.getPixelType() == loci.formats.FormatTools.UINT8 ||
            this.reader.getPixelType() == loci.formats.FormatTools.UINT16 ||
            this.reader.getPixelType() == loci.formats.FormatTools.UINT32)
        {
            this.unsigned = true;
        }
    }

    private void checkDimensions() throws Exception {
        if (this.reader.getSeriesCount() != 4) {
            log.error(
                "Expected 4 series, found {}", this.reader.getSeriesCount());
            throw new Exception("Number of series invalid");
        }
        this.reader.setSeries(0);
        this.numberOfChannels = this.reader.getSizeC();
        for (int s = 0; s < this.reader.getSeriesCount(); s++) {
            this.reader.setSeries(s);
            if (this.reader.getSizeC() != this.numberOfChannels) {
                log.error("Series {} number of channel differs", s);
                throw new Exception(
                    "Number of channels not the same between the series");
            }
        }
    }

    

    private ModelDescriptor loadDescriptor(int channel) throws Exception
    {
        log.info("Loading descriptor");
        ModelDescriptor descriptor = new ModelDescriptor();
        descriptor.imageSize = new Dimension();
        descriptor.imageSize_small = new Dimension();
        this.reader.setSeries(0);
        MetadataStore ms = reader.getMetadataStore();
        MetadataRetrieve mr = ms instanceof MetadataRetrieve ?
            (MetadataRetrieve) ms : null;
        String imageName;
        byte[] plane;
        boolean v = false, z = false, z_small = false, v_small = false;
        for (int s = 0; s < this.reader.getSeriesCount(); s++) {
            imageName = mr.getImageName(s);
            this.reader.setSeries(s);
            switch (imageName) {
                case "Model_V":
                    descriptor.imageSize.width = this.reader.getSizeX();
                    descriptor.imageSize.height = this.reader.getSizeY();
                    plane = this.reader.openBytes(
                        this.reader.getIndex(0, channel, 0));
                    descriptor.v = BfImageLoader.toDoubleVector(
                        plane, (int) (0.125 * reader.getBitsPerPixel()),
                        fp, reader.isLittleEndian(), unsigned,
                        this.reader.getSizeX(), this.reader.getSizeY());
                    v = true;
                    break;
                case "Model_Z":
                    plane = this.reader.openBytes(
                        this.reader.getIndex(0, channel, 0));
                    descriptor.z = BfImageLoader.toDoubleVector(
                        plane, (int) (0.125 * reader.getBitsPerPixel()),
                        fp, reader.isLittleEndian(), unsigned,
                        this.reader.getSizeX(), this.reader.getSizeY());
                    z = true;
                    break;
                case "Model_V_small":
                    descriptor.imageSize_small.width = this.reader.getSizeX();
                    descriptor.imageSize_small.height = this.reader.getSizeY();
                    plane = this.reader.openBytes(
                        this.reader.getIndex(0, channel, 0));
                    descriptor.v_small = BfImageLoader.toDoubleVector(
                        plane, (int) (0.125 * reader.getBitsPerPixel()),
                        fp, reader.isLittleEndian(), unsigned,
                        this.reader.getSizeX(), this.reader.getSizeY());
                    v_small = true;
                    break;
                case "Model_Z_small":
                    plane = this.reader.openBytes(
                        this.reader.getIndex(0, channel, 0));
                    descriptor.z_small = BfImageLoader.toDoubleVector(
                        plane, (int) (0.125 * reader.getBitsPerPixel()),
                        fp, reader.isLittleEndian(), unsigned,
                        this.reader.getSizeX(), this.reader.getSizeY());
                    z_small = true;
                    break;
                default:
                    throw new Exception(
                        "Image name " + imageName + " unrecognised");
            }
            log.info("{}", mr.getImageName(s));
        }
        if (!z || !z_small || !v || !v_small) {
            log.error("Did not find one of the images v: {}, z: {} "
                    + "v_small: {}, z_small: {}", v, z, v_small, z_small);
            throw new Exception("Model image missing");
        }
        return descriptor;
    }

    public List<ModelDescriptor> loadModel() throws Exception {
        List<ModelDescriptor> descriptors = new ArrayList<ModelDescriptor>();
        this.initializeReader();
        this.checkDimensions();
        for (int channel = 0; channel < this.numberOfChannels; channel++) {
            descriptors.add(this.loadDescriptor(channel));
        }
        return descriptors;
    }
}
