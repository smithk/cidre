package com.cidre.io;

import java.awt.Dimension;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.algorithms.CidreMath;
import com.cidre.core.Options;
import com.cidre.preprocessing.CidrePreprocess;

public abstract class ImageLoader {

    protected double maxI;

    protected List<double[][]> S;

    protected String source;

    protected Options options;

    protected int stackMin;

    protected double[][] minImage;

    private static final Logger log =
        LoggerFactory.getLogger(ImageLoader.class);

    public ImageLoader(Options options, String source) {
        this.maxI = 0.0;
        this.S = new ArrayList<double[][]>();
        this.options = options;
        this.source = source;
    }

    public abstract boolean loadImages() throws Exception;

    public abstract double[][] loadPlane(Integer planeIndex) throws Exception;

    public abstract double[][] loadPlane(
        int series, int channel, int timepoint, int zPlane) throws Exception;

    public abstract int getWidth();

    public abstract int getHeight();

    public abstract int getNumberOfImages();

    public List<double[][]> getStack() {
        return this.S;
    }

    public Options getOptions() {
        return this.options;
    }

    protected boolean getFileList(String source) {
        log.info("Searching for files");
        if (source != null && !source.equals("")) {
            // break source into a path, filter, and extension
            File file = new File(source);
            String path = file.getParent() + File.separator;
            String fileName = file.getName();
            File folder = new File(path);
            log.debug("Searching for files in {}", folder.toString());
            File[] listOfFiles = folder.listFiles(
                new ImageNameFilter(fileName));
            log.debug("Found {} files", listOfFiles.length);
            if (listOfFiles != null) {
                log.debug("{}", listOfFiles.toString());
                for (int i = 0; i < listOfFiles.length; i++) {
                    this.options.fileNames.add(
                        path + listOfFiles[i].getName());
                }
            }
            if (this.options.fileNames.size() <= 0) {
                log.error("No image file found.");
                return false;
            }
            return true;
        } else {
            log.error("Source not set");
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

    protected double findMax(double[][] image) {
        double max = Double.MIN_VALUE;
        for (int x = 0; x < this.options.workingSize.width; x++) {
            for (int y = 0; y < this.options.workingSize.height; y++) {
                max = Math.max(max, image[x][y]);
            }
        }
        return max;
    }

    protected double findMin(double[][] image) {
        double min = Double.MAX_VALUE;
        for (int x = 0; x < this.options.workingSize.width; x++) {
            for (int y = 0; y < this.options.workingSize.height; y++) {
                min = Math.min(min, image[x][y]);
            }
        }
        return min;
    }

    protected void preprocessData() {
        // determine if sufficient intensity
        // information is provided by measuring entropy

        // store the bit depth of the images in options,
        // needed for entropy measurement
        this.setBitDepth();
        // compute the stack's entropy
        double entropy = CidrePreprocess.getEntropy(this.options, this.S);
        // resample the stack if the entropy is too high
        this.scaleSpaceResampling(entropy);

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
        this.resizeStack(options);
    }

    private void setBitDepth() {
        // Sets options.bitDepth describing the provided images as 8-bit,
        // 12-bit, or  16-bit. If options.bitDepth is provided, it is used.
        // Otherwise the bit depth is estimated from the max observed
        // intensity, maxI.
        final int xy_2_8 = 256;
        final int xy_2_12 = 4096;
        final int xy_2_16 = 65536;

        if (maxI > xy_2_12)
             this.options.bitDepth = xy_2_16;
        else if (maxI > xy_2_8)
             this.options.bitDepth = xy_2_12;
        else
             this.options.bitDepth = xy_2_8;
         log.info(
             " {}-bit depth images (estimated from max intensity={})",
             Math.round(Math.log(options.bitDepth) / Math.log(2)), maxI);
    }

    private void scaleSpaceResampling(double entropy)
    {
        log.info("Scaling space resampling");
        // uses scale space resampling to compensate for regions with little
        // intensity information. if the entropy is very low, this indicates
        // that some (or many) have regions little useful information.
        // resampling from a scale space transform allows us to leverage
        // information from neighboring locations. For example, in low
        // confluency fluorescence images without a fluorescing medium,
        // the background pixels contain nearly zero contribution from
        // incident light and do not provide useful information.

        double l0 = 1;          // max lambda_vreg
        double l1 = 0;          // stable lambda_vreg
        double N  = this.S.size();   // number of images in the stack
        double a  = 7.838e+06;  // parameters of a fitted exponential function
        double b  = -1.948;     // parameters of a fitted exponential function
        double c  = 20;         // parameters of a fitted exponential function

        // emprical estimate of the number of images necessary at the reported
        // entropy level
        double N_required = a * Math.exp(b * entropy) + c;

        // alpha is a linear function from 1 (N=0) to 0 (N=N_required) and 0 
        // (N > N_required). It informs us how strong the scale space
        // resampling should be. alpha=1 means strong resampling,
        // alpha=0 skips resampling
        double alpha;

        if (N < N_required) {
            log.warn(
                "Warning: less than recommended number of images " +
                "provided ({} < {}) for the observed image entropy={} " +
                "Using scale-space resampling to compensate.",
                N, (double) Math.round(N_required), entropy);
            alpha = l0 + ((l1 - l0) / (N_required)) * N;
        } else {
            alpha = l1;
        }

        // the dimensions of the stack
        int C1 = this.options.workingSize.width;
        int R1 = this.options.workingSize.height;
        int Z1 = this.S.size();

        // scale space reduction of the stack into octaves. SCALE is a cell 
        // containing the scale space reductions of the stack:
        // {[R1xC1xZ1], [R1/2 x C1/2 x Z1], [R1/4 x C1/4 x Z1], ...}
        // cell containing the scale space reductions
        List<List<double[][]>> SCALE = new ArrayList<List<double[][]>>();
        int R = R1;                 // scale space reduced image height
        int C = C1;                 // scale space reduced image width

        SCALE.add(this.S);
        while ((R > 1) && (C > 1))
        {
            List<double[][]> elementS = new ArrayList<double[][]>();
            List<double[][]> lastElements = SCALE.get(SCALE.size() - 1);
            
            for (int i = 0; i < lastElements.size(); i++)
            {
                double[][] element = lastElements.get(i);
                double[][] rescaledElement = CidrePreprocess.imresize(
                    element, C, R, 0.5);
                elementS.add(rescaledElement);
            }
            SCALE.add(elementS);
            
            R = SCALE.get(SCALE.size() - 1).get(0)[0].length;
            C = SCALE.get(SCALE.size() - 1).get(0).length;
        }

        // determine the max octave we should keep, max_i as directed by
        // the scaling  strength alpha. alpha = 0 keeps only the original size.
        // alpha = 1 uses all available octaves
        int max_possible_i = SCALE.size();
        alpha = Math.max(0, alpha); alpha = Math.min(1, alpha);
        int max_i = (int)Math.ceil(alpha * max_possible_i);
        max_i = Math.max(max_i, 1);

        // join the octaves from the scale space reduction
        // from i=1 until i=max_i.
        if (max_i > 1)
        {
            log.info(
                "Applying scale-space resampling " +
                 "(intensity information is low)");
            List<double[][]> S2 = new ArrayList<double[][]>();
            for (int i = 0; i < max_i; i++)
            {
                R = SCALE.get(i).get(0)[0].length;
                C = SCALE.get(i).get(0).length;
                
                log.info("Octave=1/{}  size={}{}", i, R, C);
                
                for (int j = 0; j < S.size(); j++) 
                {
                    S2.add(CidrePreprocess.imresize(
                        SCALE.get(i).get(j), C, R, C1, R1));
                }
            }
            this.S = S2;
        }
        else
        {
            log.info("Scale-space resampling NOT APPLIED (alpha = {})", alpha);
        }
    }

    private void resizeStack(Options options)
    {
        log.info("Resizing Stack");
        // in order keep CIDRE computationally tractable and to ease parameter 
        // setting, we reduce the 3rd dimension of the sorted stack
        // S to Z = 200. Information is not discarded in the process,
        // but several slices from the stack are averaged into a single
        // slice in the process.

        // get the original dimensions of S
        int C = this.options.workingSize.width;
        int R = this.options.workingSize.height;
        int Z = this.S.size();

        // if Z < options.numberOfQuantiles, we do not want to further compress
        // the data. leave S as is.
        if (Z <= options.numberOfQuantiles)
        {
            return;
        }
        else
        {
            // find regionLimits, a set of indexes that breaks Z into
            // options.numberOfQuantiles evenly space pieces
            int Zmin = 0;
            int Zmax = Z;
            int Zdiff = Zmax - Zmin;
            
            int[][] regionLimits = new int[options.numberOfQuantiles][2];
            for (int i = 0; i < options.numberOfQuantiles; i++) {
                regionLimits[i][0] = Math.round(
                    Zmin + Zdiff * ((float) i / options.numberOfQuantiles));
                regionLimits[i][1] = Math.round(
                    Zmin + Zdiff *
                    ((float) (i + 1) / options.numberOfQuantiles)) - 1;
            }

            // compute the mean image of each region of S defined by
            // regionLimits, and add the mean image to S2
            List<double[][]> S2 = new ArrayList<double[][]>();
            for (int i = 0; i < options.numberOfQuantiles; i++) {
                double[][] doubleArray = new double[C][R];

                double[] doubleValues = new double[
                    regionLimits[i][1] - regionLimits[i][0] + 1];

                for (int x = 0; x < C; x++)
                    for (int y = 0; y < R; y++)
                    {
                        for (
                            int z = regionLimits[i][0];
                            z <= regionLimits[i][1]; z++)
                        {
                            doubleValues[z - regionLimits[i][0]] =
                                    S.get(z)[x][y];
                        }
                        doubleArray[x][y] = CidreMath.mean(doubleValues);
                    }
                S2.add(doubleArray);
            }
            this.S = S2;
        }
    }


    public double[][] getMinImage() {
        return this.minImage;
    }
}
