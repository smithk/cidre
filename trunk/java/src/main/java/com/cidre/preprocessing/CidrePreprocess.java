package com.cidre.preprocessing;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.algorithms.CidreMath;
import com.cidre.core.Options;

public class CidrePreprocess {

    private static final Logger log =
            LoggerFactory.getLogger(CidrePreprocess.class);

    public static double getEntropy(Options options, List<double[][]> stack)
    {
        log.info("Computing entropy");
        // gets the entropy of an image stack. A very low entropy indicates that
        // there may be insufficient intensity information to build a good model.
        // This can happen when only a few images are provided and the background
        // does not provide helpful information. For example in low confluency
        // fluorescence images from a glass slide, the background pixels have nearly
        // zero contribution from incident light and do not provide useful
        // information.

        // get a distribution representing all of S
        int[] hist = new int[options.bitDepth];
        double[] P = new double[options.bitDepth];
        double value = 0;
        boolean reported = false;
        for (int z = 0; z < stack.size(); z++) {
            double[][] doubleArray = stack.get(z);
            for (int x = 0; x < options.workingSize.width; x++) {
                for (int y = 0; y < options.workingSize.height; y++) {
                    value = doubleArray[x][y];
                    if (!reported && (value < 0 || value >= options.bitDepth - 1)) {
                        log.error("Stack contains pixels values out of range;"
                                + " e.g.: Plane: {}, pixel: [{}, {}],"
                                + " Value: {}", z, x, y, Math.round(value));
                        log.info("Switch to debug mode to see all values");
                        reported = true;
                        continue;
                    } else if (value < 0 || value >= options.bitDepth - 1) {
                        log.debug("Pixel Out of range: Plane: {},"
                                + "pixel: [{}, {}], Value: {}",
                                z, x, y, Math.round(value));
                        continue;
                    }
                    //log.info("Hist value: {}", (int) Math.round(value));
                    hist[(int) Math.round(value)]++;
                }
            }
        }
        double sumP = 0;
        for (int i = 0; i < hist.length; i++)
            sumP += hist[i];

        for (int i = 0; i < P.length; i++)
            P[i] = hist[i] / sumP;

        // compute the entropy of the distribution
        //if (sum(~isfinite(P(:)))) {
        //  IJ.error("CIDRE:loadImages", "the inputs contain non-finite values!");
        //}
        //P = P(:) ./ sum(P(:));
        //P(P == 0) = []; // In the case of p(xi) = 0 for some i, the value of the
                            // corresponding sum and 0 logb(0) is taken to be 0
        double H = 0;
        for (int i = 0; i < P.length; i++)
        {
            if (P[i] != 0)
            {
                H += P[i] * Math.log(P[i]) / Math.log(2.0);
            }
        }
        H *= -1;

        log.info("Entropy of the stack: {}", H);
        return H;
    }

    public static double[][] imresize(
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
            u[j] = (j + 1.0) / hScale + 0.5 * (1.0 - 1.0 / hScale);
            left[j] = (int) Math.floor(u[j] - kernel_width / 2.0);
        }
        int P = (int) Math.ceil(kernel_width) + 2;
        int hIndices[][] = new int[P][newHeight];
        double hWeights[][] = new double[P][newHeight];
        for (int p = 0; p < P; p++) {
            for (int j = 0; j < newHeight; j++) {
                hIndices[p][j] = left[j] + p;
                if (hScale < 1.0) {
                    hWeights[p][j] = hScale * CidreMath.cubic(
                        hScale * (u[j] - hIndices[p][j]));
                } else {
                    hWeights[p][j] = CidreMath.cubic(u[j] - hIndices[p][j]);
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
                    wWeights[p][j] = wScale * CidreMath.cubic(
                        wScale * (u[j] - wIndices[p][j]));
                } else {
                    wWeights[p][j] = CidreMath.cubic(u[j] - wIndices[p][j]);
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

    public static double[][] imresize(
            double[][] doubleArray, int origWidth,
            int origHeight, double scale)
        {
            // Height
            int newHeight = (int) Math.round(origHeight * scale);
            double kernel_width = 4.0;
            if (scale < 1.0)
                kernel_width /= scale;
            double[] u = new double[newHeight];
            int[] left = new int[newHeight];
            for (int j = 0; j < newHeight; j++) {
                u[j] = (j+1) / scale + 0.5 * (1.0 - 1.0 / scale);
                left[j] = (int)Math.floor(u[j] - kernel_width/2.0);
            }
            int P = (int)Math.ceil(kernel_width) + 2;
            int hIndices[][] = new int[P][newHeight];
            double hWeights[][] = new double[P][newHeight];
            for (int p = 0; p < P; p++) {
                for (int j = 0; j < newHeight; j++) {
                    hIndices[p][j] = left[j] + p;
                    if (scale < 1.0) {
                        hWeights[p][j] =
                            scale * CidreMath.cubic(
                                scale * (u[j] - hIndices[p][j]));
                    } else {
                        hWeights[p][j] = CidreMath.cubic(
                            u[j] - hIndices[p][j]);
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
            // Clamp out-of-range indices; has the effect of replicating end-points.
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
                        doubleArrayH[i][j] +=
                            (doubleArray[i][hIndices[p][j]]) * hWeights[p][j];
                    }
                }
            }

            // Width
            int newWidth = (int)Math.round(origWidth * scale);
            kernel_width = 4.0;
            if (scale < 1.0)
                kernel_width /= scale;
            u = new double[newWidth];
            left = new int[newWidth];
            for (int j = 0; j < newWidth; j++) {
                u[j] = (j+1) / scale + 0.5 * (1.0 - 1.0 / scale);
                left[j] = (int)Math.floor(u[j] - kernel_width/2.0);
            }
            P = (int)Math.ceil(kernel_width) + 2;
            int wIndices[][] = new int[P][newWidth];
            double wWeights[][] = new double[P][newWidth];
            for (int p = 0; p < P; p++) {
                for (int j = 0; j < newWidth; j++) {
                    wIndices[p][j] = left[j] + p;
                    if (scale < 1.0)
                        wWeights[p][j] =
                            scale * CidreMath.cubic(
                            scale * (u[j] - wIndices[p][j]));
                    else
                        wWeights[p][j] = CidreMath.cubic(
                            u[j] - wIndices[p][j]);
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
            // Clamp out-of-range indices; has the effect of replicating end-points.
            for (int p = 0; p < P; p++) {
                for (int j = 0; j < newWidth; j++) {
                    wIndices[p][j]--;
                    if (wIndices[p][j] < 0) {
                        wIndices[p][j] = 0;
                    } else if (wIndices[p][j] >= origWidth - 1) {
                        wIndices[p][j] = origWidth - 1;
                    }
                }
            }
            // resizeDimCore - width
            double[][] doubleArrayW = new double[newWidth][newHeight];
            for (int i = 0; i < newWidth; i++) {
                for (int p = 0; p < P; p++) {
                    for(int j = 0; j < newHeight; j++) {
                        doubleArrayW[i][j] +=
                            (doubleArrayH[wIndices[p][i]][j]) * wWeights[p][i];
                    }
                }
            }
            return doubleArrayW;
        }
}
