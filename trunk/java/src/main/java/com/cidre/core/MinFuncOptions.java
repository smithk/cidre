package com.cidre.core;

public class MinFuncOptions {

    /**
     *  max iterations for optimization
     */
    public int maxIter;

    /**
     *  max evaluations of objective function
     */
    public int MaxFunEvals;

    /**
     *  progress tolerance
     */
    public double progTol;

    /**
     *  optimality tolerance
     */
    public double optTol;

    /**
     *  number of corrections to store in memory
     */
    public int Corr;
}
