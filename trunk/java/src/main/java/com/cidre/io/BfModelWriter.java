package com.cidre.io;

import java.util.ArrayList;
import java.util.List;

import com.cidre.core.ModelDescriptor;

import loci.common.services.DependencyException;
import loci.common.services.ServiceFactory;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.out.OMETiffWriter;
import loci.formats.services.OMEXMLService;

public class BfModelWriter {

    private OMETiffWriter writer;

    private String fileName;

    private int sizeC = 1;

    private int sizeZ = 1;

    private int sizeT = 1;

    private String pixelType ;

    private boolean littleEndian = false;

    private int samplesPerPixel = 1;

    /** Writer metadata */
    IMetadata metadata;

    private List<ModelDescriptor> descriptors;

    public BfModelWriter(String fileName, ModelDescriptor descriptor) {
        this.fileName = fileName;
        this.descriptors = new ArrayList<ModelDescriptor>();
        this.descriptors.add(descriptor);
    }

    public BfModelWriter(String fileName, List<ModelDescriptor> descriptors) {
        this.fileName = fileName;
        this.descriptors = descriptors;
        this.sizeC = descriptors.size();
    }

    private void initialise() throws Exception {
        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        this.metadata = service.createOMEXMLMetadata();

        MetadataTools.populateMetadata(
            this.metadata, 0, "Model_V", true, "XYZCT", pixelType,
            this.descriptors.get(0).imageSize.width,
            this.descriptors.get(0).imageSize.height,
            this.sizeZ, this.sizeC, this.sizeT, this.samplesPerPixel);

        MetadataTools.populateMetadata(
            this.metadata, 1, "Model_Z", true, "XYZCT", pixelType,
            this.descriptors.get(0).imageSize.width,
            this.descriptors.get(0).imageSize.height,
            this.sizeZ, this.sizeC, this.sizeT, this.samplesPerPixel);

        MetadataTools.populateMetadata(
            this.metadata, 2, "Model_V_small", true, "XYZCT", pixelType,
            this.descriptors.get(0).imageSize_small.width,
            this.descriptors.get(0).imageSize_small.height,
            this.sizeZ, this.sizeC, this.sizeT, this.samplesPerPixel);

        MetadataTools.populateMetadata(
            this.metadata, 2, "Model_Z_small", true, "XYZCT", pixelType,
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
            if (heightSmall != descriptor.imageSize_small.width) {
                throw new Exception(
                    "All descriptors should have the same smal height");
            }
        }
    }

    public void saveModel() throws Exception {
        this.checkDescriptorDimensions();
        this.initialise();
    }

    public void close() {
        
    }
}
