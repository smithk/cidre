package com.cidre.core;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModelGenerator {

    /**
     * Build CidreModel based on option passed
     * @param model
    */

    private static final Logger log =
            LoggerFactory.getLogger(ModelGenerator.class);

    private enum Mestimator { LS, CAUCHY };

    private Options options;

    private ModelDescriptor descriptor;

    private ZLimitsResult zLimitsResult;

    public ModelGenerator(
            ModelDescriptor model, Options options,
            List<double[][]> imageStack)
    {
        this.options = options;
        this.descriptor = descriptor;
    }

    public ModelDescriptor generate(List<double[] []> imageStack)
    {
        log.info("Generating model");
        // set default values for options that are not specified 
        if (this.options.qPercent == null) {
            this.options.qPercent = 0.25; 
        }
        if (this.options.lambdaZero == null) {
            this.options.lambdaZero = 0.5;
        }
        if (this.options.maxLbgfsIterations == null) {
            this.options.maxLbgfsIterations = 500;
        }
        if (this.options.lambdaVreg == null) {
            this.options.lambdaVreg = this.getLambdaVfromN(
                this.options.numImagesProvided);
        }

        //get dimensions of the provided data stack, S
        int depth = imageStack.size();
        int width = this.options.workingSize.width;
        int height = this.options.workingSize.height;

        Double stackMin = Double.MAX_VALUE;
        for (int z = 0; z < depth; z++) {
            for (int c = 0; c < width; c++) {
                for (int r = 0; r < height; r++) {
                    if (stackMin > imageStack.get(z)[c][r])
                        stackMin = imageStack.get(z)[c][r];
                }
            }
        }

        Double lambdaVreg = Math.pow(10, options.lambdaVreg);
        Double lambdaZero = Math.pow(10, options.lambdaZero);
        // initial guesses for the correction surfaces
        double[] v0 = new double[width * height];
        double[] b0 = new double[width * height];
        Arrays.fill(v0, 1.0);
        Arrays.fill(b0,  0.0);

        this.zLimitsResult = this.getZLimits(this.options, stackMin);

        log.info("Estimating Q");
        // get an estimate of Q, the underlying intensity distribution
        double[] Q = estimateQ(imageStack);

        log.info("Pivot space transforms");
        // Transform Q and S (which contains q) to the pivot space. The pivot
        // space is just a shift of the origin to the median datum.
        // First, the shift for Q:
        int mid_ind = (depth - 1) / 2;
        double pivotShiftX = Q[mid_ind];
        for (int i = 0; i < Q.length; i++) Q[i] = Q[i] - pivotShiftX;
        
        // next, the shift for each location q
        double[][] doubleArray;
        double[] pivotShiftY = new double[width * height];
        doubleArray = imageStack.get(mid_ind);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                pivotShiftY[x * height + y] = doubleArray[x][y];
            }
        }
        for (int z = 0; z < depth; z++) {
            doubleArray = imageStack.get(z);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    doubleArray[x][y] -= pivotShiftY[x * height + y];
                }
            }
        }
        // also, account for the pivot shift in b0
        //b0 = b0 + PivotShiftX*v0 - PivotShiftY;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                b0[x * height + y] = b0[x * height + y]
                                   + pivotShiftX * v0[x * height + y]
                                   - pivotShiftY[x * height + y];
            }
        }

        log.info("Optimisation (may take few minutes) for parameters:"
                 + "\n lambda_v: {}, lambda_z: {},"
                 + " q_percent: {}\nz_limits: [{}, {}]",
                 options.lambdaVreg, options.lambdaZero, options.qPercent,
                 this.zLimitsResult.zmin, this.zLimitsResult.zmax);
        // vector containing initial values of the variables we want to estimate
        //x0 = [v0(:); b0(:); zx0; zy0];
        double[] x0 = new double[2 * width * height + 2];
        int pX = 0;
        for (int i = 0; i < v0.length; i++) {
            x0[pX++] = v0[i];
        }
        for (int i = 0; i < b0.length; i++) {
            x0[pX++] = b0[i];
        }
        x0[pX++] = this.zLimitsResult.zx0;
        x0[pX++] = this.zLimitsResult.zy0;

        MinFuncOptions minFuncOptions = new MinFuncOptions();
        // max iterations for optimization
        minFuncOptions.maxIter = options.maxLbgfsIterations;
        // max evaluations of objective function
        minFuncOptions.MaxFunEvals = 1000;
        // progress tolerance
        minFuncOptions.progTol = 1e-5;
        // optimality tolerance
        minFuncOptions.optTol = 1e-5;
        minFuncOptions.Corr = 100;
        MinFuncResult minFuncResult = this.minFunc(
            imageStack, x0, minFuncOptions, 0.0,
            pivotShiftX, pivotShiftY, Mestimator.LS, Q, 0);

        return this.descriptor;
    }

    /**
     * Determines spatial regularization weight based on number of images N.
     *
     * @param N - number of images
     * @return spatial regularization weight, lambda_vreg
     */
    private double getLambdaVfromN(int N)
    {
        // empirically deteremined sufficient number of images
        int NMAX = 200;
        // lambda_vreg for very few images
        double l0 = 9.5;
        // lambda_vreg for sufficient images 
        double l1 = 6;

        if (N < NMAX) {
            return l0 + ((l1 - l0) / (NMAX)) * N;
        } else {
            return l1;
        }
    }

    private ZLimitsResult getZLimits(Options options, Double stackMin) {
        ZLimitsResult zLimitsResult = new ZLimitsResult();

        if (options.zLimits[0] != null) {
            zLimitsResult.zmin = options.zLimits[0];
            zLimitsResult.zmax = options.zLimits[1];
            zLimitsResult.zx0 = (options.zLimits[0] + options.zLimits[1])
                                / 2.0;
            zLimitsResult.zy0 = zLimitsResult.zx0;
        } else {
            zLimitsResult.zmin = 0;
            zLimitsResult.zmax = stackMin;
            zLimitsResult.zx0 = 0.85 * stackMin;
            zLimitsResult.zy0 = zLimitsResult.zx0;
        }
        return zLimitsResult;
    }

    /**
     * Estimates Q, the underlying intensity distribution, using a robust mean
     *  of the provided intensity distributions from Q
     *  @param imageStack
     */
    private double[] estimateQ(List<double[] []> imageStack) {
      //get dimensions of the provided data stack, S
        int depth = imageStack.size();
        int width = this.options.workingSize.width;
        int height = this.options.workingSize.height;
        double qPercent = this.options.qPercent;

        // sort the means of each intensity distribution
        double[] doubleValues = new double[depth];  // for mean
        double[][] meanSurf = new double[width][height]; 

        // determine the number of points to use
        long numPointInQ = Math.round(qPercent * width * height);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    doubleValues[z] = imageStack.get(z)[x][y];
                }
                meanSurf[x][y] = this.mean(doubleValues);
            }
        }

        //[msorted inds] = sort(meanSurf(:));
        final double[] msorted = new double[width * height];
        final Integer[] inds = new Integer[width * height];

        for (int i = 0; i < width * height; i++) {
            inds[i] = i;
        }

        int i = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                msorted[i++] = meanSurf[x][y];
            }
        }

        Arrays.sort(inds, new Comparator<Integer>() {
            @Override public int compare(final Integer o1, final Integer o2) {
                return Double.compare(msorted[o1], msorted[o2]);
            }
        });

        // locations used to compute Q (M) come from the central quantile
        int mStart = (int)(Math.round((width * height) / 2.0)
                           - Math.round(numPointInQ / 2.0)) - 1;
        int mEnd   = (int)(Math.round((width * height) / 2.0)
                           + Math.round(numPointInQ / 2.0));
        int mLength = mEnd - mStart;

        doubleValues = new double[mLength]; // for mean
        int[] cList = new int[mLength];
        int[] rList = new int[mLength];
        
        for (i = 0; i < mLength; i++) {
            cList[i] = inds[mStart + i] / height;
            rList[i] = inds[mStart + i] % height;
        }

        double[] Q = new double[depth];
        for (int z = 0; z < depth; z++) {
            double[][] doubleArray = imageStack.get(z);
            for (i = 0; i < mLength; i++) {
                doubleValues[i] = doubleArray[cList[i]][rList[i]];
            }
            Q[z] = mean(doubleValues);
        }
        return Q;
    }

    private double mean(double[] a) {
        int i;
        double sum = 0;
        for (i = 0; i < a.length; i++) {
            sum += a[i];
        }
        return sum / a.length;
    }

    private MinFuncResult minFunc(
            List<double[] []> imageStack, double[] x0,
            MinFuncOptions minFuncOptions, double cauchy_w,
            double pivotShiftX, double[] pivotShiftY, Mestimator method,
            double[] Q, int TERM)
    {
        log.info("Running minimization");
        double[] x = null;
        double f = 0.0;

        int maxFunEvals = 1000;
        double c1 = 1e-4;
        double c2 = 0.9;
        int LS_interp = 2;
        int LS_multi = 0;

        int exitflag = 0;
        // Initialize
        int p = x0.length;
        double[] d = new double[p];
        x = new double[x0.length];
        for (int i = 0; i < x0.length; i++) x[i] = x0[i];
        double t = 1.0d;

        // If necessary, form numerical differentiation functions
        int funEvalMultiplier = 1;
        int numDiffType = 0;

        // Evaluate Initial Point
        ObjectiveResult objectiveResult = this.cdr_objective(
            imageStack, x, cauchy_w, pivotShiftX,
            pivotShiftY, method, Q, TERM);
        f = objectiveResult.E;
        double[] g = objectiveResult.G;
        double[] g_old = new double[g.length];
    }

    private ObjectiveResult cdr_objective(
            List<double[] []> imageStack, double[] x, double cauchy_w,
            double pivotShiftX, double[] pivotShiftY, Mestimator method,
            double[] Q, int TERM, double LAMBDA_VREG, double LAMBDA_ZERO)
    {
        double E = 0.0;
        double[] G = null;

        // some basic definitions
        // the standard number of quantiles used for empirical parameter setting
        int N_stan = 200;                
        // the barrier term coefficient
        double LAMBDA_BARR = 1e6;
        int depth = imageStack.size();
        int width = this.options.workingSize.width;
        int height = this.options.workingSize.height;

        // unpack
        double[] v_vec = Arrays.copyOfRange(x, 0, width * height);
        double[] b_vec = Arrays.copyOfRange(
            x, width * height, 2 * width * height);
        double zx = x[2 * width * height];
        double zy = x[2 * width * height + 1];

        // move the zero-light point to the pivot space (zx,zy) -> (px,py)
        // a scalar
        double px = zx - pivotShiftX;
        double[] py = new double[width * height];
        int pPy = 0;
        for (int xc = 0; xc < width; xc++) {
            for (int y = 0; y < height; y++) {
                py[pPy++] = zy - pivotShiftY[xc * height + y];
            }
        }

        // fitting energy
        // We compute the energy of the fitting term given v,b,zx,zy.
        // We also compute its gradient wrt the random variables.

        // accumulates the fit energy
        double[] energy_fit  = new double[width * height];
        // derivative of fit term wrt v
        double[] deriv_v_fit = new double[width * height];
        // derivative of fit term wrt b
        double[] deriv_b_fit = new double[width * height];

        double v;
        double b;
        double E_fit = 0;
        double[] G_V_fit = new double[width * height];
        double[] G_B_fit = new double[width * height];

        double[] mestimator_response = new double[depth];
        double[] d_est_dv = new double[depth];
        double[] d_est_db = new double[depth];

        int I = 0;
        for (int xc = 0; xc < width; xc++) {
            for (int y = 0; y < height; y++) {
                // get the quantile fit for this location and vectorize it
                double[] q = new double[depth];
                for (int z = 0; z < depth; z++) {
                    q[z] = imageStack.get(z)[xc][y];
                }
                v = v_vec[xc * height + y];
                b = b_vec[xc * height + y];
                switch (method) {
                    case LS:
                        for (int z = 0; z < depth; z++) {
                            double val = Q[z] * v + b - q[z];
                            mestimator_response[z] = val * val;
                            d_est_dv[z] = Q[z] * val;
                            d_est_db[z] = val;
                        }
                        break;
                    case CAUCHY:
                        for (int z = 0; z < depth; z++) {
                            double val = Q[z] * v + b - q[z];
                            mestimator_response[z] =
                                cauchy_w * cauchy_w
                                * Math.log(
                                    1 + (val*val) / (cauchy_w * cauchy_w)
                                ) / 2.0;
                            d_est_dv[z] =
                                (Q[z]*val) / (1.0 + (val*val)
                                / (cauchy_w * cauchy_w));
                            d_est_db[z] =
                                val / (1.0 + (val*val)
                                / (cauchy_w * cauchy_w));
                        }
                        break;
                }
                for (int z = 0; z < depth; z++) {
                    energy_fit[I] += mestimator_response[z];
                    deriv_v_fit[I] += d_est_dv[z];
                    deriv_b_fit[I] += d_est_db[z];
                }
                I++;
            }
        }
        // normalize the contribution from fitting energy term by the number
        // of data points in imageStack
        // (so our balancing of the energy terms is invariant)
        int data_size_factor = N_stan / depth;
        I = 0;
        for (int xc = 0; xc < width; xc++) { 
            for (int y = 0; y < height; y++) {
                E_fit += energy_fit[I];
                // fit term derivative wrt v
                G_V_fit[I] = deriv_v_fit[I] * data_size_factor;
                // fit term derivative wrt b
                G_B_fit[I] = deriv_b_fit[I] * data_size_factor;
                I++;
            }
        }
        E_fit *= data_size_factor;      // fit term energy

        // spatial regularization of v
        // We compute the energy of the regularization term given v,b,zx,zy.
        // We also compute its gradient wrt the random variables.

        // determine the widths we will use for the LoG filter
        int max_exp = (int) Math.max(
            1.0, Math.log(Math.floor(Math.max(width,  height) / 50.0))
            / Math.log(2.0));

        double[] sigmas = new double[max_exp + 2];
        for (int i = -1; i <= max_exp; i++)
            sigmas[i + 1] = Math.pow(2, i);

        // accumulates the vreg energy
        double[] energy_vreg = new double[sigmas.length];
        // derivative of vreg term wrt v
        double[] deriv_v_vreg = new double[width * height];
        // derivative of vreg term wrt b
        double[] deriv_b_vreg = new double[width * height];

        // apply the scale-invariant LoG filter to v for all scales in SIGMAS
        double[][][] h = new double[sigmas.length][][];
        for (int i = 0; i < sigmas.length; i++)
        {
            // define the kernel size, make certain dimension is odd
            int hsize = 6 * (int) Math.ceil(sigmas[i]); 
            if (hsize % 2 == 0)
                hsize++;
            double std2 = sigmas[i] * sigmas[i];

            // h{n} = sigmas(n)^2 * fspecial('log', hsize, sigmas(n))
            h[i] = new double[hsize][hsize];
            double[][] h1 = new double[hsize][hsize];
            double sumh = 0.0;
            for (int c = 0; c < hsize; c++) {
                for (int r = 0; r < hsize; r++) {
                    double arg = -1.0 * (
                        (c - hsize / 2) * (c - hsize / 2)
                        + (r - hsize / 2) * (r - hsize / 2)) / (2.0 * std2);
                    h[i][c][r] = Math.exp(arg);
                    sumh += h[i][c][r];
                }
            }           
            // calculate Laplacian
            double sumh1 = 0.0;
            for (int c = 0; c < hsize; c++) {
                for (int r = 0; r < hsize; r++) {
                    h[i][c][r] /= sumh;
                    h1[c][r] = h[i][c][r] * (
                        (c - hsize / 2) * (c-hsize / 2)
                        + (r - hsize /2 ) * (r-hsize / 2) - 2 * std2)
                        / (std2 * std2);
                    sumh1 += h1[c][r]; 
                }
            }
            for (int c = 0; c < hsize; c++) {
                for (int r = 0; r < hsize; r++) {
                    h[i][c][r] = (
                        h1[c][r] - sumh1 / (hsize * hsize))
                        * (sigmas[i] * sigmas[i]);
                    // h{n} = sigmas(n)^2 * fspecial('log', hsize, sigmas(n));
                }
            }
            // apply a LoG filter to v_img to penalize disagreements
            // between neighbors
            double[] v_LoG = this.imfilter_symmetric(
                v_vec, width, height, h[i]);
            for (int c = 0; c < v_LoG.length; c++) {
                // normalize by the # of sigmas used
                v_LoG[c] /= sigmas.length;
            }
            // energy is quadratic LoG response
            energy_vreg[i] = 0;
            for (int c = 0; c < v_LoG.length; c++) {
                energy_vreg[i] += v_LoG[c]*v_LoG[c];
            }
            for (int c = 0; c < v_LoG.length; c++) {
                v_LoG[c] *= 2;
            }
            double[] v_LoG2 = this.imfilter_symmetric(
                v_LoG, width, height, h[i]);
            for (int c = 0; c < v_LoG2.length; c++) {
                deriv_v_vreg[c] += v_LoG2[c];
            }
        }
        // vreg term energy
        double E_vreg = 0;
        for (int i = 0; i < sigmas.length; i++) {
            E_vreg += energy_vreg[i];
        }
        // vreg term gradient wrt v
        double[] G_V_vreg = deriv_v_vreg;
        // vreg term gradient wrt b
        double[] G_B_vreg = deriv_b_vreg;

        // The ZERO-LIGHT term
        // We compute the energy of the zero-light term given v,b,zx,zy.
        // We also compute its gradient wrt the random variables.
        double[] residual = new double[width * height];
        for (int i = 0; i < residual.length; i++) {
            residual[i] = v_vec[i] * px + b_vec[i] - py[i];
        }
        double[] deriv_v_zero = new double[width * height];
        double[] deriv_b_zero = new double[width * height];
        double deriv_zx_zero = 0.0;
        double deriv_zy_zero = 0.0;
        for (int i = 0; i < width * height; i++) {
            double val = b_vec[i] + v_vec[i] * px - py[i];
            deriv_v_zero[i] = 2 * px * val;
            deriv_b_zero[i] = 2 * val;
            deriv_zx_zero += 2 * v_vec[i] * val;
            deriv_zy_zero += - 2 * val;
        }

        double E_zero = 0;  // zero light term energy
        for (int i = 0; i < residual.length; i++)
            E_zero += residual[i] * residual[i];

        // zero light term gradient wrt v
        double[] G_V_zero = deriv_v_zero;
        // zero light term gradient wrt b
        double[] G_B_zero = deriv_b_zero;
        // zero light term gradient wrt zx
        double G_ZX_zero = deriv_zx_zero;
        // zero light term gradient wrt zy
        double G_ZY_zero = deriv_zy_zero;

        // The BARRIER term
        // We compute the energy of the barrier term given v,b,zx,zy. We also
        // compute its gradient wrt the random variables.

        // upper limit - transition from zero energy to quadratic increase
        double Q_UPPER_LIMIT = this.zLimitsResult.zmax;
        // lower limit - transition from quadratic to zero energy
        double Q_LOWER_LIMIT = this.zLimitsResult.zmin;
        // rate of increase in energy
        double Q_RATE = 0.001;

        // barrier term gradients and energy components
        double[] barrierResult = this.theBarrierFunction(
            zx, Q_LOWER_LIMIT, Q_UPPER_LIMIT, Q_RATE);
        double E_barr_xc = barrierResult[0];
        double G_ZX_barr = barrierResult[1];

        barrierResult = this.theBarrierFunction(
            zy, Q_LOWER_LIMIT, Q_UPPER_LIMIT, Q_RATE);
        double E_barr_yc = barrierResult[0];
        double G_ZY_barr = barrierResult[1];

        // barrier term energy
        double E_barr = E_barr_xc + E_barr_yc;

        // The total energy
        // Find the sum of all components of the energy.
        // TERMSFLAG switches on and off different components of the energy.
        String term_str = "";
        switch (TERM) {
            case 0:
                E = E_fit;
                term_str = "fitting only";
                break;
            case 1:
                E = E_fit + LAMBDA_VREG * E_vreg
                    + LAMBDA_ZERO * E_zero
                    + LAMBDA_BARR * E_barr;
                term_str = "all terms";
                break;
        }

        // The gradient of the energy
        double[] G_V = null;
        double[] G_B = null;
        double G_ZX = 0;
        double G_ZY = 0;
        switch (TERM) {
            case 0:
                G_V = G_V_fit;
                G_B = G_B_fit;
                G_ZX = 0;
                G_ZY = 0;
                break;
            case 1:
                for (int i = 0; i < G_V_fit.length; i++) {
                    G_V_fit[i] = G_V_fit[i] + LAMBDA_VREG * G_V_vreg[i]
                                 + LAMBDA_ZERO * G_V_zero[i];
                    G_B_fit[i] = G_B_fit[i] + LAMBDA_VREG * G_B_vreg[i]
                                 + LAMBDA_ZERO * G_B_zero[i];
                }
                G_V = G_V_fit;
                G_B = G_B_fit;
                G_ZX = LAMBDA_ZERO * G_ZX_zero + LAMBDA_BARR * G_ZX_barr;
                G_ZY = LAMBDA_ZERO * G_ZY_zero + LAMBDA_BARR * G_ZY_barr;
                break;
        }

        // vectorize the gradient
        G = new double[x.length];

        int pG = 0;
        for (int i = 0; i < G_V.length; i++)
            G[pG++] = G_V[i];
        for (int i = 0; i < G_B.length; i++)
            G[pG++] = G_B[i];
        G[pG++] = G_ZX;
        G[pG++] = G_ZY;
        log.info("Iteration = {} {} {}; zx,zy=({}, {}); E={}",
                 ITER, MESTIMATOR, term_str, zx, zy, E);
        ObjectiveResult result = new ObjectiveResult();
        result.E = E;
        result.G = G;
        return result;
    }

    private double[] imfilter_symmetric(
        double[] pixels, int width, int height, double[][] k)
    {
        int kc = k.length / 2;
        double[] kernel = new double[k.length * k.length];
        double[] result = new double[width * height];
        for (int i = 0; i < k.length; i++) {
            for (int j = 0; j < k.length; j++) {
                kernel[i * k.length + j] = k[i][j];
            }
        }
        double sum;
        int offset, i;
        int edgeDiff;
        boolean edgePixel;
        int xedge = width - kc;
        int yedge = height - kc;
        int nx, ny;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                sum = 0;
                i = 0;
                edgePixel = x < kc || x >= xedge || y < kc || y >= yedge;
                for (int u = -kc; u <= kc; u++) {
                    offset = (x+u)*height + y;
                    for (int v = -kc; v <= kc; v++) {
                        if (edgePixel) {
                            nx = x + u;
                            ny = y + v;
                            edgeDiff = 0;
                            if (nx < 0)
                                edgeDiff = (-2 * nx - 1) * height;
                            else if (nx >= width)
                                edgeDiff = (-2 * (nx - width) - 1) * height;
                            if (ny < 0)
                                edgeDiff += -2*ny - 1;
                            else if (ny >= height)
                                edgeDiff += -2 * (ny - height) - 1;
                            sum += pixels[offset + v + edgeDiff] * kernel[i++];
                        } else {
                            sum += pixels[offset + v] * kernel[i++];
                        }
                    }
                }
                result[x * height + y] = sum;
            }
        }
        return result;
    }

    /*
     * The barrier function has a well shape. It has quadratically increasing
     * energy below xmin, zero energy between xmin and xmax, and quadratically
     * increasing energy above xmax. The rate of increase is determined
     * by width
     */
    private double[] theBarrierFunction(
        double x, double xmin, double xmax, double width)
    {
        double[] result = new double[] {0.0, 0.0}; // E G
        double xl1 = xmin;
        double xl2 = xl1 + width;

        double xh2 = xmax;
        double xh1 = xh2 - width;

        if (x <= xl1) {
            result[0] = ((x - xl2) / (xl2 - xl1)) * ((x - xl2) / (xl2 - xl1));
            result[1] = (2 * (x - xl2)) / ((xl2 - xl1) * (xl2 - xl1));
        }
        else if ((x >= xl1) && (x <= xl2)) {
            result[0] = ((x - xl2) / (xl2 - xl1)) * ((x - xl2)/(xl2 - xl1));
            result[1] = (2 * (x - xl2))  / ((xl2 - xl1) * (xl2 - xl1));
        }
        else if ((x > xl2) && (x < xh1)) {
            result[0] = 0;
            result[1] = 0;
        }
        else if ((x >= xh1) && (x < xh2)) {
            result[0] = ((x - xh1)/(xh2 - xh1)) * ((x - xh1)/(xh2 - xh1));
            result[1] = (2 * (x - xh1))  / ((xh2 - xh1) * (xh2 - xh1));
        }
        else {
            result[0] = ((x - xh1) / (xh2 - xh1)) * ((x - xh1) / (xh2 - xh1));
            result[1] = (2* (x - xh1))  / ((xh2 - xh1) * (xh2 - xh1));
        }
        return result;
    }
}
