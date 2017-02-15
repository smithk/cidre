package com.cidre.input;

import java.awt.Dimension;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.core.Options;


public abstract class ImageLoader {

    protected double maxI;
    protected List<double[][]> S;
    protected String source;
    protected Options options;
    protected String fileMask;

    private static final Logger log =
        LoggerFactory.getLogger(ImageLoader.class);

    public ImageLoader(Options options, String fileMask) {
        this.maxI = 0;
        this.S = new ArrayList<double[][]>();
        this.options = options;
        this.fileMask = fileMask;
    }

    public abstract boolean loadImages() throws Exception;

    protected boolean getFileList(String source, String fileMask) {
        if (source != null && !source.equals("")) {
            // break source into a path, filter, and extension
            File file = new File(source);
            String path = file.getPath() + File.separator;
            File folder = new File(path);
            File[] listOfFiles = folder.listFiles(
                new ImageNameFilter(fileMask));
            if (listOfFiles != null) {
                for (int i = 0; i < listOfFiles.length; i++) {
                    this.options.fileNames.add(listOfFiles[i].getName());
                }
            }
            if (this.options.fileNames.size() <= 0) {
                log.error("No image file found.");
                return false;
            }
            return true;
        } else {
            return false;
        }
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
        public boolean accept(File dir, String name) {
            return pattern.matcher(new File(name).getName()).matches();
        }
    }

    protected double[][] imresize(
        double[][] doubleArray, int origWidth, int origHeight,
        int newWidth, int newHeight)
    {
        // Height
        double hScale = (double) newHeight / origHeight;
        double kernel_width = 4.0;
        if (hScale < 1.0)
            kernel_width /= hScale;
        double[] u = new double[newHeight];
        int[] left = new int[newHeight];
        for (int j = 0; j < newHeight; j++) {
            u[j] = (j+1) / hScale + 0.5 * (1.0 - 1.0 / hScale);
            left[j] = (int) Math.floor(u[j] - kernel_width / 2.0);
        }
        int P = (int ) Math.ceil(kernel_width) + 2;
        int hIndices[][] = new int[P][newHeight];
        double hWeights[][] = new double[P][newHeight];
        for (int p = 0; p < P; p++) {
            for (int j = 0; j < newHeight; j++) {
                hIndices[p][j] = left[j] + p;
                if (hScale < 1.0) {
                    hWeights[p][j] = hScale * cubic(
                        hScale * (u[j] - hIndices[p][j]));
                } else {
                    hWeights[p][j] = cubic(u[j] - hIndices[p][j]);
                }
            }
        }
        // Normalize the weights matrix so that each row sums to 1.
        for (int j = 0; j < newHeight; j++) {
            double sum = 0;
            for (int p = 0; p < P; p++) {
                sum += hWeights[p][j];
            }
            for (int p = 0; p < P; p++) {
                hWeights[p][j] /= sum;
            }
        }
        // Clamp out-of-range indices;
        // has the effect of replicating end-points.
        for (int p = 0; p < P; p++) {
            for (int j = 0; j < newHeight; j++) {
                hIndices[p][j]--;
                if (hIndices[p][j] < 0)
                    hIndices[p][j] = 0;
                else if (hIndices[p][j] >= origHeight - 1)
                    hIndices[p][j] = origHeight - 1;
            }
        }

        // resizeDimCore - height
        double[][] doubleArrayH = new double[origWidth][newHeight];
        for(int j = 0; j < newHeight; j++) {
            for (int p = 0; p < P; p++) {
                for (int i = 0; i < origWidth; i++) {
                    doubleArrayH[i][j] += (doubleArray[i][hIndices[p][j]])
                                          * hWeights[p][j];
                }
            }
        }

        // Width
        double wScale = (double) newWidth / origWidth;
        kernel_width = 4.0;
        if (wScale < 1.0)
            kernel_width /= wScale;
        u = new double[newWidth];
        left = new int[newWidth];
        for (int j = 0; j < newWidth; j++) {
            u[j] = (j+1) / wScale + 0.5 * (1.0 - 1.0 / wScale);
            left[j] = (int) Math.floor(u[j] - kernel_width/2.0);
        }
        P = (int) Math.ceil(kernel_width) + 2;
        int wIndices[][] = new int[P][newWidth];
        double wWeights[][] = new double[P][newWidth];
        for (int p = 0; p < P; p++) {
            for (int j = 0; j < newWidth; j++) {
                wIndices[p][j] = left[j] + p;
                if (wScale < 1.0) {
                    wWeights[p][j] = wScale * cubic(
                        wScale * (u[j] - wIndices[p][j]));
                } else {
                    wWeights[p][j] = cubic(u[j] - wIndices[p][j]);
                }
            }
        }
        // Normalize the weights matrix so that each row sums to 1.
        for (int j = 0; j < newWidth; j++) {
            double sum = 0;
            for (int p = 0; p < P; p++) {
                sum += wWeights[p][j];
            }
            for (int p = 0; p < P; p++) {
                wWeights[p][j] /= sum;
            }
        }
        // Clamp out-of-range indices;
        // has the effect of replicating end-points.
        for (int p = 0; p < P; p++) {
            for (int j = 0; j < newWidth; j++) {
                wIndices[p][j]--;
                if (wIndices[p][j] < 0)
                    wIndices[p][j] = 0;
                else if (wIndices[p][j] >= origWidth - 1)
                    wIndices[p][j] = origWidth - 1;
            }
        }

        // resizeDimCore - width
        double[][] doubleArrayW = new double[newWidth][newHeight];
        for (int i = 0; i < newWidth; i++) {
            for (int p = 0; p < P; p++) {
                for(int j = 0; j < newHeight; j++) {
                    doubleArrayW[i][j] += (doubleArrayH[wIndices[p][i]][j])
                                          * wWeights[p][i];
                }
            }
        }
        return doubleArrayW;
    }

    private final double cubic(double x) {
        double absx = Math.abs(x);
        double absx2 = absx * absx;
        double absx3 = absx2 * absx;

        return (1.5 * absx3 - 2.5 * absx2 + 1.0) *
               (absx <= 1.0 ? 1.0 : 0.0) +
               (-0.5 * absx3 + 2.5 * absx2 - 4.0 * absx + 2.0) *
               ((1 < absx) && (absx <= 2) ? 1.0 : 0.0);
    }

    // determines a working image size based on the original image size and
    // the desired number of pixels in the working image, N_desired
    protected Dimension determineWorkingSize(Dimension imageSize, int Ndesired)
    {
        int widthOriginal = imageSize.width;
        int heightOriginal = imageSize.height;

        double scaleWorking = Math.sqrt(
            (double) Ndesired / (widthOriginal * heightOriginal));

        return new Dimension(
            (int) Math.round(widthOriginal * scaleWorking),
            (int) Math.round(heightOriginal * scaleWorking));
    }

    protected void findMax(double[][] image) {
        for (int x = 0; x < this.options.workingSize.width; x++) {
            for (int y = 0; y < this.options.workingSize.height; y++) {
                this.maxI = Math.max(this.maxI, (int) image[x][y]);
            }
        }
    }

    protected void preprocessData() {
        // determine if sufficient intensity
        // information is provided by measuring entropy

        // store the bit depth of the images in options,
        // needed for entropy measurement
        this.setBitDepth();
        // compute the stack's entropy
        double entropy = getEntropy(options);
        // resample the stack if the entropy is too high
        scaleSpaceResampling(entropy);

        // sort the intensity values at every location in the image stack
        // at every pixel location (r,c), we sort all the recorded
        // intensities from the provided images and replace the stack
        // with the new sorted values.
        // The new S has the same data as before, but sorted in ascending
        // order in the 3rd dimension.
        
        log.info(" Sorting intensity by pixel location and resizing...");
        //S = sort(S,3);
        double[] doubleValues = new double[S.size()];
        for (int x = 0; x < this.options.workingSize.width; x++) {
            for (int y = 0; y < this.options.workingSize.height; y++) {
                for (int z = 0; z < S.size(); z++) 
                    doubleValues[z] = S.get(z)[x][y];
                Arrays.sort(doubleValues);
                for (int z = 0; z < S.size(); z++) 
                    S.get(z)[x][y] = doubleValues[z];
            }
        }
        // compress the stack: reduce the effective number
        // of images for efficiency
        resizeStack(options);
    }

    private void setBitDepth() {
        // Sets options.bitDepth describing the provided images as 8-bit, 12-bit, or 
        // 16-bit. If options.bitDepth is provided, it is used. Otherwise the bit 
        //depth is estimated from the max observed intensity, maxI.
        final int xy_2_8 = 256;
        final int xy_2_12 = 4096;
        final int xy_2_16 = 65536;

        if (maxI > xy_2_12)
             this.options.bitDepth = xy_2_16;
        else if (maxI > xy_2_8)
             this.options.bitDepth = xy_2_12;
        else
             thisoptions.bitDepth = xy_2_8;
         log.info(
             " {}-bit depth images (estimated from max intensity={})",
             Math.round(Math.log(options.bitDepth) / Math.log(2)), maxI);
    }
}
