package com.cidre.input;

import java.awt.Dimension;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
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
}
