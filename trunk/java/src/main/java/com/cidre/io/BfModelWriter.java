package com.cidre.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.core.Cidre;
import com.cidre.core.ModelDescriptor;
import com.esotericsoftware.minlog.Log;

import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.out.OMETiffWriter;
import loci.formats.services.OMEXMLService;

public class BfModelWriter {

    private static final Logger log =
        LoggerFactory.getLogger(BfModelWriter.class);

    private OMETiffWriter writer;

    private String fileName;

    private int sizeC = 1;

    private int sizeZ = 1;

    private int sizeT = 1;

    private String pixelType ;

    private int samplesPerPixel = 1;

    /** Writer metadata */
    IMetadata metadata;

    private List<ModelDescriptor> descriptors;

    public BfModelWriter(String fileName, ModelDescriptor descriptor) {
        this.fileName = fileName;
        this.descriptors = new ArrayList<ModelDescriptor>();
        this.descriptors.add(descriptor);
        this.pixelType = FormatTools.getPixelTypeString(FormatTools.DOUBLE);
    }

    public BfModelWriter(String fileName, List<ModelDescriptor> descriptors) {
        this.fileName = fileName;
        this.descriptors = descriptors;
        this.sizeC = descriptors.size();
        this.pixelType = FormatTools.getPixelTypeString(FormatTools.DOUBLE);
    }

    private void initialise() throws Exception {
        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        this.metadata = service.createOMEXMLMetadata();

        MetadataTools.populateMetadata(
            this.metadata, 0, "Model_V", false, "XYZCT", pixelType,
            this.descriptors.get(0).imageSize.width,
            this.descriptors.get(0).imageSize.height,
            this.sizeZ, this.sizeC, this.sizeT, this.samplesPerPixel);

        MetadataTools.populateMetadata(
            this.metadata, 1, "Model_Z", false, "XYZCT", pixelType,
            this.descriptors.get(0).imageSize.width,
            this.descriptors.get(0).imageSize.height,
            this.sizeZ, this.sizeC, this.sizeT, this.samplesPerPixel);

        MetadataTools.populateMetadata(
            this.metadata, 2, "Model_V_small", false, "XYZCT", pixelType,
            this.descriptors.get(0).imageSize_small.width,
            this.descriptors.get(0).imageSize_small.height,
            this.sizeZ, this.sizeC, this.sizeT, this.samplesPerPixel);

        MetadataTools.populateMetadata(
            this.metadata, 3, "Model_Z_small", false, "XYZCT", pixelType,
            this.descriptors.get(0).imageSize_small.width,
            this.descriptors.get(0).imageSize_small.height,
            this.sizeZ, this.sizeC, this.sizeT, this.samplesPerPixel);

        this.writer = new OMETiffWriter();
        this.writer.setMetadataRetrieve(this.metadata);
        this.writer.setId(this.fileName);
    }

    private void checkDescriptorDimensions() throws Exception {
        int width = this.descriptors.get(0).imageSize.width;
        int height = this.descriptors.get(0).imageSize.height;
        int widthSmall = this.descriptors.get(0).imageSize_small.width;
        int heightSmall = this.descriptors.get(0).imageSize_small.height;

        for (ModelDescriptor descriptor : this.descriptors) {
            if (width != descriptor.imageSize.width) {
                throw new Exception(
                    "All descriptors should have the same width");
            }
            if (height != descriptor.imageSize.height) {
                throw new Exception(
                    "All descriptors should have the same height");
            }
            if (widthSmall != descriptor.imageSize_small.width) {
                throw new Exception(
                    "All descriptors should have the same small width");
            }
            if (heightSmall != descriptor.imageSize_small.height) {
                throw new Exception(
                    "All descriptors should have the same small height");
            }
        }
    }

    public void saveModel() throws Exception {
        this.checkDescriptorDimensions();
        this.initialise();
        this.writeToFile();
        this.close();
    }

    private void writeToFile() throws FormatException, IOException {
        log.info("Writing planes to file");
        int width = this.descriptors.get(0).imageSize.width;
        int height = this.descriptors.get(0).imageSize.height;
        int widthSmall = this.descriptors.get(0).imageSize_small.width;
        int heightSmall = this.descriptors.get(0).imageSize_small.height;
        for (int channel = 0; channel < this.sizeC; channel++) {
            this.write(
                this.descriptors.get(channel).v,
                0, channel, width, height);
            this.write(
                this.descriptors.get(channel).z,
                1, channel, width, height);
            this.write(
                this.descriptors.get(channel).v_small,
                2, channel, widthSmall, heightSmall);
            this.write(
                this.descriptors.get(channel).z_small,
                3, channel, widthSmall, heightSmall);
        }
    }

    private void write(
            double[] b, int series, int imageNumber, int width, int height)
            throws FormatException, IOException
    {
        this.writer.setSeries(series);
        ByteBuffer buffer = ByteBuffer.allocate(8 * width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffer.putDouble(b[x * height + y]);
            }
        }
        this.writer.saveBytes(imageNumber, buffer.array());
    }

    private void close() throws IOException {
        this.writer.close();
    }
}
