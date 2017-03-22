package com.cidre.core;

import java.awt.Dimension;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.algorithms.CidreMath;
import com.cidre.algorithms.LbfgsAddResult;
import com.cidre.algorithms.MinFuncOptions;
import com.cidre.algorithms.MinFuncResult;
import com.cidre.algorithms.ObjectiveResult;
import com.cidre.algorithms.WolfeLineSearchResult;
import com.cidre.algorithms.ZLimitsResult;
import com.cidre.io.BfImageWriter;

import loci.formats.FormatTools;

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

    public ModelGenerator(Options options)
    {
        this.options = options;
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
            this.options.lambdaVreg = 12.0; //this.getLambdaVfromN(
                //this.options.numImagesProvided);
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
        log.info("StackMin: {}", stackMin);
        double lambdaVreg = Math.pow(10, options.lambdaVreg);
        double lambdaZero = Math.pow(10, options.lambdaZero);
        // initial guesses for the correction surfaces
        double[] v0 = new double[width * height];
        double[] b0 = new double[width * height];
        Arrays.fill(v0, 1.0);
        Arrays.fill(b0, 1.0);

        this.zLimitsResult = this.getZLimits(this.options, stackMin);
        
        log.info(
           "Generating model with paramters\n" +
           "lambdaVreg:         {}\n" +
           "lambdaZero:         {}\n" +
           "maxLbgfsIterations: {}\n" +
           "zLimits:            [{} {}]\n" +
           "imageSize:          [{}, {}]\n" +
           "numImagesProvided:  {}\n" +
           "bitDepth:           {}\n" +
           "correctionMode:     {}\n" +
           "targetNumPixels:    {}\n" +
           "workingSize:        [{}, {}]\n" +
           "numberOfQuantiles:  {}\n",
           this.options.lambdaVreg, this.options.lambdaZero,
           this.options.maxLbgfsIterations,
           this.options.zLimits[0], this.options.zLimits[1],
           this.options.imageSize.width, this.options.imageSize.height,
           this.options.numImagesProvided, this.options.bitDepth,
           this.options.correctionMode, this.options.targetNumPixels,
           this.options.workingSize.width, this.options.workingSize.height,
           this.options.numberOfQuantiles);

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
        // vector containing initial values of the variables
        // we want to estimate
        // x0 = [v0(:); b0(:); zx0; zy0];
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
            pivotShiftX, pivotShiftY, Mestimator.LS,
            Q, 0, lambdaVreg, lambdaZero);
        double[] x  = minFuncResult.x;
        double fval = minFuncResult.f;

        // unpack
        double[] v1 = Arrays.copyOfRange(x, 0, width * height);
        double[] b1 = Arrays.copyOfRange(
            x, width * height, 2 * width * height);
        double zx1 = x[2 * width * height];
        double zy1 = x[2 * width * height + 1];

        // 2nd optimization using REGULARIZED ROBUST fitting
        // use the mean standard error of the LS fitting to set the width of the
        // CAUCHY function
        double mse = computeStandardError(imageStack, v1, b1, Q);

        // vector containing initial values of the variables we want to estimate
        double[] x1 = new double[2 * width * height + 2];

        int pX1 = 0;
        for (int i = 0; i < v1.length; i++)
            x1[pX1++] = v1[i];
        for (int i = 0; i < b1.length; i++)
            x1[pX1++] = b1[i];
        x1[pX1++] = zx1;
        x1[pX1++] = zy1;

        minFuncResult = this.minFunc(
            imageStack, x1, minFuncOptions, mse,
            pivotShiftX, pivotShiftY, Mestimator.CAUCHY,
            Q, 1, lambdaVreg, lambdaZero);
        x = minFuncResult.x;
        fval = minFuncResult.f;

        // unpack the optimized v surface, b surface, xc, and yc from the vector x
        double[] v = Arrays.copyOfRange(x, 0, width * height);
        double[] b_pivoted = Arrays.copyOfRange(
            x, width * height, 2 * width * height);
        double zx = x[2 * width * height];
        double zy = x[2 * width * height + 1];

        // Build the final correction model

        // Unpivot b: move pivot point back to the original location
        double[] b_unpivoted = new double[width * height];
        for (int c = 0; c < width; c++) {
            for (int r = 0; r < height; r++) {
                b_unpivoted[c * height + r] =
                    pivotShiftY[c * height + r]
                    + b_pivoted[c * height + r]
                    - pivotShiftX * v[c * height + r];
            }
        }

        // shift the b surface to the zero-light surface
        double[] z = new double[width * height];
        for (int c = 0; c < width; c++) {
            for (int r = 0; r < height; r++) {
                z[c * height + r] = b_unpivoted[c * height + r]
                                    + zx * v[c * height + r];
            }
        }
        this.descriptor = new ModelDescriptor();
        this.descriptor.imageSize = new Dimension(
            options.imageSize.width, options.imageSize.height);
        this.descriptor.v = imresize_bilinear(
            v, width, height,
            options.imageSize.width, options.imageSize.height);
        this.descriptor.z = imresize_bilinear(
            z, width, height,
            options.imageSize.width, options.imageSize.height);
        this.descriptor.imageSize_small = new Dimension(
            options.workingSize.width, options.workingSize.height);
        this.descriptor.v_small   = v;
        this.descriptor.z_small   = z;
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
                meanSurf[x][y] = CidreMath.mean(doubleValues);
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
            Q[z] = CidreMath.mean(doubleValues);
        }
        log.info("Q mean value: {}", CidreMath.mean(Q));
        String fileName = "/Users/emil/Documents/Data/HMS/output/details/Q.tif";
        BfImageWriter writer = new BfImageWriter(
                fileName, depth, 1,
                FormatTools.getPixelTypeString(FormatTools.DOUBLE));
        try {
            writer.initialise();
            writer.write(Q, 0);
            writer.close();
        } catch (Exception ex) {
            log.error("Couldn't save Q");
        }
        return Q;
    }

    private MinFuncResult minFunc(
            List<double[] []> imageStack, double[] x0,
            MinFuncOptions minFuncOptions, double cauchy_w,
            double pivotShiftX, double[] pivotShiftY, Mestimator method,
            double[] Q, int TERM, double lambdaVreg, double lambdaZero)
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
            pivotShiftY, method, Q, TERM, lambdaVreg, lambdaZero);
        f = objectiveResult.E;
        double[] g = objectiveResult.G;
        double[] g_old = new double[g.length];

        int computeHessian = 0;
        int funEvals = 1;

        // Compute optimality of initial point
        double optCond = Double.MIN_VALUE;
        for (int j = 0; j < g.length; j++)
        {
            double absValue = Math.abs(g[j]);
            if (optCond < absValue)
                optCond = absValue;
        }

        // Exit if initial point is optimal
        if (optCond <= minFuncOptions.optTol)
        {
            exitflag=1;
            log.info("Optimality Condition below optTol");
            MinFuncResult minFuncResult = new MinFuncResult();
            minFuncResult.x = x;
            minFuncResult.f = f;
            return minFuncResult;
        }

        double[][] S = new double[p][minFuncOptions.Corr];
        double[][] Y = new double[p][minFuncOptions.Corr];
        double[]  YS = new double[minFuncOptions.Corr];
        int lbfgs_start = 0;
        int lbfgs_end = 0;
        double Hdiag = 1.0;

        // Perform up to a maximum of 'maxIter' descent steps:
        for (int i = 0; i < minFuncOptions.maxIter; i++)
        {
            // LBFGS
            if (i == 0)
            {
                // Initially use steepest descent direction
                for (int j = 0; j < g.length; j++) d[j] = -g[j];
                lbfgs_start = 0;
                lbfgs_end = -1;
                Hdiag = 1.0;
            } else {
                double[] gMg_old = new double[g.length];
                for (int j = 0; j < g.length; j++) {
                    gMg_old[j] = g[j] - g_old[j];
                }
                double[] tPd = new double[d.length];
                for (int j = 0; j < d.length; j++) {
                    tPd[j] = t * d[j];
                }
                LbfgsAddResult lbfgsAddResult = this.lbfgsAdd(
                    gMg_old, tPd, S, Y, YS, lbfgs_start, lbfgs_end, Hdiag);
                S = lbfgsAddResult.S;
                Y = lbfgsAddResult.Y;
                YS = lbfgsAddResult.YS;
                lbfgs_start = lbfgsAddResult.lbfgs_start;
                lbfgs_end = lbfgsAddResult.lbfgs_end;
                Hdiag = lbfgsAddResult.Hdiag;
                boolean skipped = lbfgsAddResult.skipped;

                d = this.lbfgsProd(g, S, Y, YS, lbfgs_start, lbfgs_end, Hdiag);
            }
            for (int j = 0; j < g.length; j++) {
                g_old[j] = g[j];
            }

            // ****************** COMPUTE STEP LENGTH ************************

            // Directional Derivative
            double gtd = 0.0;
            for (int j = 0; j < g.length; j++)
                gtd += g[j] * d[j];

            // Check that progress can be made along direction
            if (gtd > - minFuncOptions.progTol)
            {
                exitflag = 2;
                log.info("Directional Derivative below progTol");
                break;
            }

            // Select Initial Guess
            if (i == 0) {
                double sumAbsG = 0.0;
                for (int j = 0; j < g.length; j++) {
                    sumAbsG += Math.abs(g[j]);
                }
                t = Math.min(1.0, 1.0/sumAbsG);
            } else {
                //if (LS_init == 0)
                // Newton step
                t = 1.0;
            }
            double f_old = f;
            double gtd_old = gtd;

            int Fref = 1;
            double fr;
            // Compute reference fr if using non-monotone objective
            if (Fref == 1) {
                fr = f;
            }

            computeHessian = 0;
            // Line Search
            f_old = f;

            WolfeLineSearchResult wolfeLineSearchResult = WolfeLineSearch(
                imageStack, x, t, d, f, g, gtd, c1, c2, LS_interp,
                LS_multi, 25, minFuncOptions.progTol, 1,
                cauchy_w, pivotShiftX, pivotShiftY, method,
                Q, TERM, lambdaVreg, lambdaZero);
            t = wolfeLineSearchResult.t;
            f = wolfeLineSearchResult.f_new;
            g = wolfeLineSearchResult.g_new;
            int LSfunEvals = wolfeLineSearchResult.funEvals;

            funEvals = funEvals + LSfunEvals;
            for (int j = 0; j < x.length; j++)
                x[j] += t * d[j];

            // Compute Optimality Condition
            optCond = Double.MIN_VALUE;
            for (int j = 0; j < g.length; j++)
            {
                double absValG = Math.abs(g[j]);
                if (optCond < absValG)
                    optCond = absValG;
            }

            // Check Optimality Condition
            if (optCond <= minFuncOptions.optTol)
            {
                exitflag=1;
                log.info("Optimality Condition below optTol");
                break;
            }

            // ***** Check for lack of progress ******
            double maxAbsTD = Double.MIN_VALUE;
            for (int j = 0; j < d.length; j++) {
                double absValG = Math.abs(t * d[j]);
                if (maxAbsTD < absValG)
                    maxAbsTD = absValG;
            }
            if (maxAbsTD <= minFuncOptions.progTol)
            {
                exitflag = 2;
                log.info("Step Size below progTol");
                break;
            }

            if (Math.abs(f - f_old) < minFuncOptions.progTol)
            {
                exitflag = 2;
                log.info("Function Value changing by less than progTol");
                break;
            }

            // **** Check for going over iteration/evaluation limit ****

            if (funEvals * funEvalMultiplier >= maxFunEvals)
            {
                exitflag = 0;
                log.info("Reached Maximum Number of Function Evaluations");
                break;
            }

            if (i == minFuncOptions.maxIter)
            {
                exitflag = 0;
                log.info("Reached Maximum Number of Iterations");
                break;
            }
        }

        MinFuncResult minFuncResult = new MinFuncResult();
        minFuncResult.x = x;
        minFuncResult.f = f;
        return minFuncResult;
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

        double v = 0;
        double b = 0;
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
        log.info("mres: {}, energy_fit: {}, deriv_v_fit: {}, deriv_b_fit: {}",
        		 CidreMath.mean(mestimator_response), CidreMath.mean(energy_fit),
        		 CidreMath.mean(deriv_v_fit), CidreMath.mean(deriv_b_fit));
        // normalize the contribution from fitting energy term by the number
        // of data points in imageStack
        // (so our balancing of the energy terms is invariant)
        double data_size_factor = (double) N_stan / (double) depth;
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
        log.debug("Term str = {}; zx,zy = ({}, {}); E = {}",
                  term_str, zx, zy, E);
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

    private LbfgsAddResult lbfgsAdd(
            double[] y, double[] s, double[][] S, double[][] Y,
            double[] YS, int lbfgs_start, int lbfgs_end, double Hdiag)
    {
        double ys = 0.0;
        for (int j = 0; j < y.length; j++)
            ys += y[j] * s[j];
        boolean skipped = false;
        int corrections = S[0].length;
        if (ys > 1e-10d)
        {
            if (lbfgs_end < corrections - 1)
            {
                lbfgs_end = lbfgs_end+1;
                if (lbfgs_start != 0)
                {
                    if (lbfgs_start == corrections - 1)
                        lbfgs_start = 0;
                    else
                        lbfgs_start = lbfgs_start+1;
                }
            } else {
                lbfgs_start = Math.min(1, corrections);
                lbfgs_end = 0;
            }
            for (int j = 0; j < s.length; j++)
            {
                S[j][lbfgs_end] = s[j];
                Y[j][lbfgs_end] = y[j];
            }
            YS[lbfgs_end] = ys;
            // Update scale of initial Hessian approximation
            double yy = 0.0;
            for (int j = 0; j < y.length; j++) {
                yy += y[j] * y[j];
            }
            Hdiag = ys/yy;
        } else {
            skipped = true;
        }
        LbfgsAddResult lbfgsAddResult = new LbfgsAddResult();
        lbfgsAddResult.S = S;
        lbfgsAddResult.Y = Y;
        lbfgsAddResult.YS = YS;
        lbfgsAddResult.lbfgs_start = lbfgs_start;
        lbfgsAddResult.lbfgs_end = lbfgs_end;
        lbfgsAddResult.Hdiag = Hdiag;
        lbfgsAddResult.skipped = skipped;
        return lbfgsAddResult;
    }

    private double[] lbfgsProd(
            double[] g, double[][] S, double[][] Y, double[] YS,
            int lbfgs_start, int lbfgs_end, double Hdiag)
    {
        // BFGS Search Direction
        // This function returns the (L-BFGS) approximate inverse Hessian,
        // multiplied by the negative gradient

        // Set up indexing
        int nVars = S.length;
        int maxCorrections = S[0].length;
        int nCor;
        int[] ind;
        if (lbfgs_start == 0)
        {
            ind = new int[lbfgs_end + 1];
            for (int j = 0; j < ind.length; j++)
                ind[j] = j;
            nCor = lbfgs_end-lbfgs_start + 1;
        } else {
            ind = new int[maxCorrections];
            for (int j = lbfgs_start; j < maxCorrections; j++)
                ind[j - lbfgs_start] = j;
            for (int j = 0; j <= lbfgs_end; j++)
                ind[j + maxCorrections - lbfgs_start] = j;
            nCor = maxCorrections;
        }

        double[] al = new double[nCor];
        double[] be = new double[nCor];

        double[] d = new double[g.length];
        for (int j = 0; j < g.length; j++)
            d[j] = - g[j];
        for (int j = 0; j < ind.length; j++)
        {
            int i = ind[ind.length-j-1];
            double sumSD = 0.0;
            for (int k = 0; k < S.length; k++)
                sumSD += (S[k][i] * d[k]) / YS[i];
            al[i] = sumSD;
            for (int k = 0; k < d.length; k++) {
                d[k] -= al[i] * Y[k][i];
            }
        }

        // Multiply by Initial Hessian
        for (int j = 0; j < d.length; j++) {
            d[j] = Hdiag * d[j];
        }
        for (int i = 0; i < ind.length; i++)
        {
            double sumYd = 0.0;
            for (int j = 0; j < Y.length; j++) {
                sumYd += Y[j][ind[i]] * d[j];
            }
            be[ind[i]] = sumYd / YS[ind[i]];
            for (int j = 0; j < d.length; j++) {
                d[j] += S[j][ind[i]] * (al[ind[i]] - be[ind[i]]);
            }
        }
        return d;
    }

    private WolfeLineSearchResult WolfeLineSearch(
            List<double[] []> imageStack, double[] x, double t, double[] d,
            double f, double[] g, double gtd, double c1, double c2,
            int LS_interp, int LS_multi, int maxLS, double progTol,
            int saveHessianComp, double cauchy_w, double pivotShiftX,
            double[] pivotShiftY, Mestimator method, double[] Q, int TERM,
            double LAMBDA_VREG, double LAMBDA_ZERO)
    {
        double[] x2 = new double[x.length];
        for (int j = 0; j < x.length; j++)
            x2[j] = x[j] + t * d[j];
        ObjectiveResult cdrObjectiveResult = this.cdr_objective(
            imageStack, x2, cauchy_w, pivotShiftX,
            pivotShiftY, method, Q, TERM, LAMBDA_VREG, LAMBDA_ZERO);
        double f_new = cdrObjectiveResult.E;
        double[] g_new = cdrObjectiveResult.G;
        int funEvals = 1;

        double gtd_new = 0.0;
        for (int j = 0; j < g.length; j++) {
            gtd_new += g_new[j] * d[j];
        }
        // Bracket an Interval containing a point satisfying the
        // Wolfe criteria

        int LSiter = 0;
        double t_prev = 0.0;
        double f_prev = f;
        double[] g_prev = new double[g.length];
        for (int j = 0; j < g.length; j++) {
            g_prev[j] = g[j];
        }
        double gtd_prev = gtd;
        double nrmD = Double.MIN_VALUE;
        for (int j = 0; j < d.length; j++) {
            double absValD = Math.abs(d[j]);
            if (nrmD < absValD) {
                nrmD = absValD;
            }
        }
        boolean done = false;

        int bracketSize = 0;
        double[] bracket = new double[2];
        double[] bracketFval = new double[2];
        double[] bracketGval = new double[2 * x.length];

        while (LSiter < maxLS) {
            if (f_new > f + c1 * t * gtd || (LSiter > 1 && f_new >= f_prev)) {
                bracketSize = 2;
                bracket[0] = t_prev; bracket[1] = t;
                bracketFval[0] = f_prev; bracketFval[1] = f_new;
                for (int j = 0; j < g_prev.length; j++) {
                    bracketGval[j] = g_prev[j];
                }
                for (int j = 0; j < g_new.length; j++) {
                    bracketGval[g_prev.length + j] = g_new[j];
                }
                break;
            }
            else if (Math.abs(gtd_new) <= - c2 * gtd) {
                bracketSize = 1;
                bracket[0] = t;
                bracketFval[0] = f_new;
                for (int j = 0; j < g_new.length; j++) {
                    bracketGval[j] = g_new[j];
                }
                done = true;
                break;
            }
            else if (gtd_new >= 0) {
                bracketSize = 2;
                bracket[0] = t_prev; bracket[1] = t;
                bracketFval[0] = f_prev; bracketFval[1] = f_new;
                for (int j = 0; j < g_prev.length; j++) {
                    bracketGval[j] = g_prev[j];
                }
                for (int j = 0; j < g_new.length; j++) {
                    bracketGval[g_prev.length + j] = g_new[j];
                }
                break;
            }

            double temp = t_prev;
            t_prev = t;
            double minStep = t + 0.01 * (t-temp);
            double maxStep = t * 10;
            if (LS_interp <= 1) {
                t = maxStep;
            }
            else if (LS_interp == 2) {
                double[] points = new double[2 * 3];
                points[0] = temp; points[1] = f_prev; points[2] = gtd_prev;
                points[3] = t;    points[4] = f_new;  points[5] = gtd_new;
                t = this.polyinterp(points, minStep, maxStep);
            }

            f_prev = f_new;
            for (int j = 0; j < g_new.length; j++) {
                g_prev[j] = g_new[j];
            }
            gtd_prev = gtd_new;

            x2 = new double[x.length];
            for (int j = 0; j < x.length; j++) {
                x2[j] = x[j] + t * d[j];
            }
            cdrObjectiveResult = cdr_objective(
                imageStack, x2, cauchy_w, pivotShiftX,
                pivotShiftY, method, Q, TERM, LAMBDA_VREG, LAMBDA_ZERO);
            f_new = cdrObjectiveResult.E;
            g_new = cdrObjectiveResult.G;
            funEvals++;
            gtd_new = 0.0;
            for (int j = 0; j < g.length; j++) {
                gtd_new += g_new[j] * d[j];
            }
            LSiter++;
        }

        if (LSiter == maxLS) {
            bracketSize = 2;
            bracket[0] = 0; bracket[1] = t;
            bracketFval[0] = f; bracketFval[1] = f_new;
            for (int j = 0; j < g.length; j++) {
                bracketGval[j] = g[j];
            }
            for (int j = 0; j < g_new.length; j++) {
                bracketGval[g.length + j] = g_new[j];
            }
        }

        // Zoom Phase

        // We now either have a point satisfying the criteria, or a bracket
        // surrounding a point satisfying the criteria
        // Refine the bracket until we find a point satisfying the criteria
        boolean insufProgress = false;
        //int Tpos = 1;
        //int LOposRemoved = 0;
        int LOpos;
        int HIpos;
        double f_LO;

        while (!done && LSiter < maxLS) {
            // Find High and Low Points in bracket
            //[f_LO LOpos] = min(bracketFval);
            //HIpos = -LOpos + 3;

            if (bracketSize < 2) {
                f_LO = bracketFval[0];
                LOpos = 0; HIpos = 1;
            } else  {
                if (bracketFval[0] <= bracketFval[1]) {
                    f_LO = bracketFval[0];
                    LOpos = 0; HIpos = 1;
                } else {
                    f_LO = bracketFval[1];
                    LOpos = 1; HIpos = 0;
                }
            }

            // LS_interp == 2
            //t = polyinterp([bracket(1) bracketFval(1) bracketGval(:,1)'*d
            //    bracket(2) bracketFval(2) bracketGval(:,2)'*d],doPlot);

            {
                double val0 = 0.0;
                for (int j = 0; j < g.length; j++) {
                    val0 += bracketGval[j] * d[j];
                }
                double val1 = 0.0;
                for (int j = 0; j < g.length; j++) {
                    val1 += bracketGval[g.length + j] * d[j];
                }
                double[] points = new double[2 * 3];
                points[0] = bracket[0]; points[1] = bracketFval[0];
                points[2] = val0;
                points[3] = bracket[1]; points[4] = bracketFval[1];
                points[5] = val1;
                t = this.polyinterp(points, null, null);
            }

            // Test that we are making sufficient progress
            double enumerator = Math.min(
                Math.max(bracket[0], bracket[1]) - t,
                t - Math.min(bracket[0], bracket[1]));
            double denominator = Math.max(bracket[0], bracket[1])
                                 - Math.min(bracket[0], bracket[1]);
            if (enumerator / denominator < 0.1)
            {
                if (insufProgress
                    || t >= Math.max(bracket[0], bracket[1])
                    || t <= Math.min(bracket[0], bracket[1]))
                {
                    double term1 = Math.abs(
                        t - Math.max(bracket[0], bracket[1]));
                    double term2 = Math.abs(
                        t - Math.min(bracket[0], bracket[1]));
                    if (term1 < term2)
                    {
                        t = Math.max(bracket[0], bracket[1])
                            - 0.1 * (Math.max(bracket[0], bracket[1])
                            - Math.min(bracket[0], bracket[1]));
                    } else {
                        t = Math.min(bracket[0], bracket[1])
                            + 0.1 * (Math.max(bracket[0], bracket[1])
                            - Math.min(bracket[0], bracket[1]));
                    }
                    insufProgress = false;
                } else {
                    insufProgress = true;
                }
            } else {
                insufProgress = false;
            }

            // Evaluate new point
            x2 = new double[x.length];
            for (int j = 0; j < x.length; j++) {
                x2[j] = x[j] + t * d[j];
            }
            cdrObjectiveResult = cdr_objective(
                imageStack, x2, cauchy_w, pivotShiftX,
                pivotShiftY, method, Q, TERM, LAMBDA_VREG, LAMBDA_ZERO);
            f_new = cdrObjectiveResult.E;
            g_new = cdrObjectiveResult.G;
            funEvals++;
            gtd_new = 0.0;
            for (int j = 0; j < g.length; j++) {
                gtd_new += g_new[j] * d[j];
            }
            LSiter++;

            boolean armijo = f_new < f + c1 * t * gtd;
            if (!armijo || f_new >= f_LO) {
                // Armijo condition not satisfied
                // or not lower than lowest point
                bracket[HIpos] = t;
                bracketFval[HIpos] = f_new;
                for (int j = 0; j < g.length; j++) {
                    bracketGval[g.length * HIpos + j] = g_new[j];
                }
                //Tpos = HIpos;
            } else {
                if (Math.abs(gtd_new) <= - c2 * gtd) {
                    // Wolfe conditions satisfied
                    done = true;
                } else if (gtd_new * (bracket[HIpos] - bracket[LOpos]) >= 0) {
                    // Old HI becomes new LO
                    bracket[HIpos] = bracket[LOpos];
                    bracketFval[HIpos] = bracketFval[LOpos];
                    for (int j = 0; j < g.length; j++) {
                        bracketGval[g.length * HIpos + j] =
                            bracketGval[g.length * LOpos + j];
                    }
                }
                // New point becomes new LO
                bracket[LOpos] = t;
                bracketFval[LOpos] = f_new;
                for (int j = 0; j < g.length; j++) {
                    bracketGval[g.length * LOpos + j] = g_new[j];
                }
                //Tpos = LOpos;
            }

            if (!done && Math.abs(bracket[0] - bracket[1]) * nrmD < progTol)
                break;
        }

        if (bracketSize < 2) {
            f_LO = bracketFval[0];
            LOpos = 0; HIpos = 1;
        } else {
            if (bracketFval[0] <= bracketFval[1])
            {
                f_LO = bracketFval[0];
                LOpos = 0; HIpos = 1;
            } else {
                f_LO = bracketFval[1];
                LOpos = 1; HIpos = 0;
            }
        }

        t = bracket[LOpos];
        f_new = bracketFval[LOpos];
        for (int j = 0; j < g.length; j++)
            g_new[j] = bracketGval[g.length * LOpos + j];
        WolfeLineSearchResult wolfeLineSearchResult =
            new WolfeLineSearchResult();
        wolfeLineSearchResult.t = t;
        wolfeLineSearchResult.f_new = f_new;
        wolfeLineSearchResult.g_new = g_new;
        wolfeLineSearchResult.funEvals = funEvals;
        return wolfeLineSearchResult;
    }

    private double polyinterp(
            double[] points, Double xminBound, Double xmaxBound)
    {
        double xmin = Math.min(points[0], points[3]);
        double xmax = Math.max(points[0], points[3]);

        // Compute Bounds of Interpolation Area
        if (xminBound == null)
            xminBound = xmin;
        if (xmaxBound == null)
            xmaxBound = xmax;

        // Code for most common case:
        //   - cubic interpolation of 2 points
        //       w/ function and derivative values for both

        // Solution in this case (where x2 is the farthest point):
        // d1 = g1 + g2 - 3*(f1-f2)/(x1-x2);
        // d2 = sqrt(d1^2 - g1*g2);
        // minPos = x2 - (x2 - x1)*((g2 + d2 - d1)/(g2 - g1 + 2*d2));
        // t_new = min(max(minPos,x1),x2);

        int minPos;
        int notMinPos;
        if (points[0] < points[3]) {
            minPos = 0;
        } else {
            minPos = 1;
        }
        notMinPos = (1 - minPos) * 3;
        double d1 =
            points[minPos + 2] + points[notMinPos + 2]
            - 3 * (points[minPos + 1] - points[notMinPos + 1])
            / (points[minPos] - points[notMinPos]);
        double d2_2 = d1 * d1 - points[minPos + 2] * points[notMinPos + 2];

        if (d2_2 >= 0.0) {
            double d2 = Math.sqrt(d2_2);
            double t =
               points[notMinPos]
               - (points[notMinPos] - points[minPos])
               * ((points[notMinPos + 2] + d2 - d1) / (points[notMinPos + 2]
               - points[minPos + 2] + 2 * d2));
            return Math.min(Math.max(t, xminBound), xmaxBound);
        } else {
            return (xmaxBound+xminBound) / 2.0;
        }
    }

    private double computeStandardError(
            List<double[] []> imageStack, double[] v, double[] b, double[] Q)
    {
        // computes the mean standard error of the regression
        int Z = imageStack.size();
        int width = this.options.workingSize.width;
        int height = this.options.workingSize.height;

        // initialize a matrix to contain all the standard error calculations
        double[] se = new double[width * height];

        // compute the standard error at each location
        double[] q = new double[Z];
        double[] fitvals = new double[Z];
        double[] residuals = new double[Z];
        for (int c = 0; c < width; c++) {
            for (int r = 0; r < height; r++) {
                
                double vi = v[c * height + r];
                double bi = b[c * height + r];

                for (int z = 0; z < Z; z++) {
                    q[z] = imageStack.get(z)[c][r];
                    fitvals[z] = bi + Q[z] * vi;
                    residuals[z] = q[z] - fitvals[z];
                }
                double sum_residuals2 = 0;
                for (int z = 0; z < Z; z++) {
                    sum_residuals2 += residuals[z] * residuals[z];
                }
                se[c * height + r] = Math.sqrt(sum_residuals2 / (Z-2));
            }
        }
        return CidreMath.mean(se);
    }

    private double[] imresize_bilinear(
            double[] doubleArray, int origWidth, int origHeight,
            int newWidth, int newHeight)
    {
        // Width
        double wScale = (double) newWidth / origWidth;
        double kernel_width = 2;
        double[] u = new double[newWidth];
        int[] left = new int[newWidth];
        for (int j = 0; j < newWidth; j++) {
            u[j] = (j + 1) / wScale + 0.5 * (1.0 - 1.0 / wScale);
            left[j] = (int) Math.floor(u[j] - kernel_width / 2.0);
        }
        int P = (int) Math.ceil(kernel_width) + 2;
        int wIndices[][] = new int[P][newWidth];
        double wWeights[][] = new double[P][newWidth];
        for (int p = 0; p < P; p++) {
            for (int j = 0; j < newWidth; j++) {
                wIndices[p][j] = left[j] + p;
                wWeights[p][j] = triangle(u[j] - wIndices[p][j]);
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
                if (wIndices[p][j] < 0)
                    wIndices[p][j] = 0;
                else if (wIndices[p][j] >= origWidth - 1)
                    wIndices[p][j] = origWidth - 1;
            }
        }

        // resizeDimCore - width
        double[] doubleArray1 = new double[newWidth * origHeight];
        for (int i = 0; i < newWidth; i++) {
            for (int p = 0; p < P; p++) {
                for(int j = 0; j < origHeight; j++) {
                    doubleArray1[i * origHeight + j] +=
                        (doubleArray[wIndices[p][i] * origHeight + j])
                        * wWeights[p][i];
                }
            }
        }

        // Height
        double hScale = (double) newHeight / origHeight;
        kernel_width = 2.0;
        u = new double[newHeight];
        left = new int[newHeight];
        for (int j = 0; j < newHeight; j++) {
            u[j] = (j + 1) / hScale + 0.5 * (1.0 - 1.0 / hScale);
            left[j] = (int) Math.floor(u[j] - kernel_width / 2.0);
        }
        P = (int) Math.ceil(kernel_width) + 2;
        int hIndices[][] = new int[P][newHeight];
        double hWeights[][] = new double[P][newHeight];
        for (int p = 0; p < P; p++) {
            for (int j = 0; j < newHeight; j++) {
                hIndices[p][j] = left[j] + p;
                hWeights[p][j] = triangle(u[j] - hIndices[p][j]);
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
        double[] doubleArray2 = new double[newWidth * newHeight];
        for(int j = 0; j < newHeight; j++) {
            for (int p = 0; p < P; p++) {
                for (int i = 0; i < newWidth; i++) {
                    doubleArray2[i * newHeight + j] +=
                        (doubleArray1[i * origHeight + hIndices[p][j]])
                        * hWeights[p][j];
                }
            }
        }
        return doubleArray2;
    }

    private final double triangle(double x) {
        return (x+1.0) * ((-1.0 <= x)
               && (x < 0.0) ? 1.0 : 0.0) + (1.0-x) * ((0.0 <= x)
               && (x <= 1.0) ? 1.0 : 0.0);
    }
}
