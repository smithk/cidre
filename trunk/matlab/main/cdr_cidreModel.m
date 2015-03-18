function [model options] = cdr_cidreModel(S_input, options)
% Loads the raw images into a STACK of images, or accepts a stack of images
% passed as an argument. Performs resizing, sorting, and compressing of the
% image data to keep the model generation fast.
% 
% Usage:  [model options] = cdr_cidreModel(S, options)
%
% Input: S        an array containing the processed image data as returned
%                 by cdr_preprocessData.
%
%        OPTIONS  a data structure containing the various parameters values
%                 required by CIDRE.
%
% Output: MODEL   a data structure containing the correction model learned 
%                 from the data, S, which can be used to correct the
%                 illumination of the source images
%         
%         OPTIONS returns the options structure with appropriatetly 
%                 modified parameters: 
%
% See also: cidre, cidreGui, cdr_objective

% From the CIDRE project, an illumination correction method for optical
% microscopy (https://github.com/smithk/cidre).
% Copyright © 2015 Kevin Smith and Peter Horvath. Scientific Center for 
% Optical and Electron Microscopy (SCOPEM), Swiss Federal Institute of 
% Technology Zurich (ETH Zurich), Switzerland. All rights reserved.
%
% CIDRE is free software; you can redistribute it and/or modify it 
% under the terms of the GNU General Public License version 2 (or higher) 
% as published by the Free Software Foundation. See the license file in
% the root folder. This program is distributed WITHOUT ANY WARRANTY; 
% without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
% PARTICULAR PURPOSE.  See the GNU General Public License for more details.
%
% This software includes a folder "3rdparty" containing minFunc, a 3rd
% party software implementing L-BFGS. MinFunc is licensed under the
% Creative Commons, Attribute, Non-Commercial license. To use this software 
% for commercial purposes, you must replace minFunc with other software. 
% Matlab offers an alternative (slower) implementation in the function 
% fminlbfgs.


% define some global variables that must be shared with the objective
% function, cdr_objective
global S                    % the processed stack of observed data, contains q at every location
global Q                    % the estimate of the underlying intensity distribution
global CAUCHY_W             % width of the Cauchy function used for robust regression
global PivotShiftX          % shift on the Q axis into the pivot space
global PivotShiftY          % shift on the q axis into the pivot space
global ITER                 % iteration coutn for the optimization
global MESTIMATOR           % specifies "CAUCHY" or "LS" (least squares)
global TERMSFLAG            % flag specifing which terms to include in the energy function
global LAMBDA_VREG          % coefficient for the v regularization
global LAMBDA_ZERO          % coefficient for the zero-light term
global ZMIN                 % minimum possible value for Z
global ZMAX                 % maximum possible value for Z


% set default values for options that are not specified
if isempty(options.q_percent)
    options.q_percent = .25;
end
if isempty(options.lambda_zero)
    options.lambda_zero = 0.5;
end
if isempty(options.max_lbfgs_iterations)
    options.max_lbfgs_iterations = 500;
end
if isempty(options.lambda_vreg)
    options.lambda_vreg = getLambdaVfromN(options.num_images_provided);
end

% assign some of the global variables
S               = S_input;
STACKMIN        = min(S(:));
LAMBDA_VREG     = 10^(options.lambda_vreg);
LAMBDA_ZERO     = 10^(options.lambda_zero);



% initial guesses for the correction surfaces
v0  = ones(options.working_size);
b0  = ones(options.working_size);  
[ZMIN ZMAX zx0 zy0] = get_z_limits(options, STACKMIN);

% get an estimate of Q, the underlying intensity distribution
Q  = estimateQ(S,options.q_percent);



% Transform Q and S (which contains q) to the pivot space. The pivot
% space is just a shift of the origin to the median datum. First, the shift
% for Q:
mid_ind = round(size(S,3)/2);
PivotShiftX = Q(mid_ind);
Q = Q - PivotShiftX;

% next, the shift for each location q
PivotShiftY = S(:,:,mid_ind);
for z = 1:size(S,3)
    S(:,:,z) = S(:,:,z) - PivotShiftY;
end

% also, account for the pivot shift in b0
b0 = b0 + PivotShiftX*v0 - PivotShiftY;



fprintf(' Optimizing using the following parameters:\n lambda_v  = %1.2f\n lambda_z  = %1.2f\n q_percent = %1.2f\n z_limits = [%d %d]\n (this may take a few minutes)\n', options.lambda_vreg, options.lambda_zero, options.q_percent, ZMIN, ZMAX);
t1 = tic;


%% 1st optimization using LEAST-SQUARES fitting

% add minFunc, the LMBFGS optimizing software, to the path
addpath([options.cidrePath '/3rdparty/minFunc2012/']);
addpath([options.cidrePath '/3rdparty/minFunc2012/minFunc/']);
addpath([options.cidrePath '/3rdparty/minFunc2012/minFunc/compiled/']);
addpath([options.cidrePath '/3rdparty/minFunc2012/autoDif/']);

% set the options for minFunc
mf_opt.Method       = 'lbfgs';          % optitmization scheme
mf_opt.Display      = 'off';            % display mode %'final'
mf_opt.maxIter      = options.max_lbfgs_iterations; % max iterations for optimization
mf_opt.MaxFunEvals  = 1000;             % max evaluations of objective function
mf_opt.progTol      = 1e-150;           % progress tolerance
mf_opt.optTol       = 1e-150;           % optimality tolerance
mf_opt.Corr         = 100;           	% number of corrections to store in memory (default: 100)

% assign the remaining global variables needed in cdr_objective
ITER = 1;                               %#ok<NASGU>
MESTIMATOR = 'LS';                      %#ok<NASGU>
TERMSFLAG = 0;                          %#ok<NASGU>

% vector containing initial values of the variables we want to estimate
x0 = [v0(:); b0(:); zx0; zy0];

% call minFunc to do the optimization
[x fval] = minFunc(@cdr_objective, x0, mf_opt); %#ok<NASGU>

% unpack the optimized v surface, b surface, xc, and yc from the vector x
[v1 b1 zx1 zy1] = unpack_x(x, options.working_size);




%% 2nd optimization using REGULARIZED ROBUST fitting


% use the mean standard error of the LS fitting to set the width of the
% CAUCHY function
mse = computeStandardError(v1,b1,S,Q);
CAUCHY_W = mse;

% assign the remaining global variables needed in cdr_objective
ITER = 1;                              
MESTIMATOR = 'CAUCHY';                  
TERMSFLAG = 1;                          

% vector containing initial values of the variables we want to estimate
x1 = [v1(:); b1(:); zx1; zy1];

% call minFunc to do the optimization
[x fval] = minFunc(@cdr_objective, x1, mf_opt); %#ok<NASGU>

% unpack the optimized v surface, b surface, xc, and yc from the vector x
[v b_pivoted zx zy] = unpack_x(x, options.working_size); %#ok<NASGU>





%% Build the final correction model 

% Unpivot b: move pivot point back to the original location
b_unpivoted = PivotShiftY + b_pivoted - PivotShiftX.*v;

% shift the b surface to the zero-light surface
z = b_unpivoted + zx * v;


model.method    = 'CIDRE';
model.v         = imresize(v, options.image_size, 'bilinear');
model.z         = imresize(z, options.image_size, 'bilinear');
model.v_small   = v;
model.z_small   = z;



fprintf(' Finished in %1.2fs with %d iterations.\n', toc(t1), ITER);




function [zmin zmax zx0 zy0] = get_z_limits(options, STACKMIN)
% determines the limits for the z surface based on user input or using the
% minimum intensity of the stack. Also determines initial guess for the Z
% surface


if ~isempty(options.z_limits)
    zmin = options.z_limits(1);
    zmax = options.z_limits(2);
    zx0 = mean(options.z_limits);
    zy0 = zx0;
else
    zmin = 0;
    zmax = STACKMIN;
    zx0 = .85 * STACKMIN;
    zy0 = zx0;
end




function [v b zx zy] = unpack_x(x, working_size)
% unpacks the v surface, b surface, xc, and yc from x, where the elements
% have been vectorized

R = working_size(1);
C = working_size(2);

v_vec	= double(x(1:R*C));
b_vec	= double(x(R*C + 1:2*R*C));
v  	= reshape(v_vec,[R C]);
b      = reshape(b_vec,[R C]);
zx     = x(end-1);
zy     = x(end);


function lambda_vreg = getLambdaVfromN(N)
% sets the spatial regularization weight, lambda_vreg. For sufficient
% images, lambda_vreg=6 was determined empirically to be a good value. For
% fewer images, it helps to increase the regularization linearly

NMAX = 200;     % empirically deteremined sufficient number of images
l0 = 9.5;       % lambda_vreg for very few images
l1 = 6;         % lambda_vreg for sufficient images 

if N < NMAX
    lambda_vreg = l0 + ((l1-l0)/(NMAX)) * N;
else
    lambda_vreg = l1;
end


function mse = computeStandardError(v,b,S,Q)
% computes the mean standard error of the regression

% get the dimensions of the data
R = size(S,1);
C = size(S,2);

% initialize a matrix to contain all the standard error calculations
se = zeros(R,C);

% compute the standard error at each location
for r = 1:R
    for c = 1:C        
        vi = v(r,c);
        bi = b(r,c);        
        q = S(r,c,:); q = q(:);        
        fitvals = bi + Q*vi;        
        residuals = q - fitvals;
        se(r,c) = sqrt(  sum(residuals.^2)/(numel(residuals)-2));        
    end
end
mse = mean(se(:));


function Q  = estimateQ(S,q_percent)
% We estimate Q, the underlying intensity distribution, using a robust mean
% of the provided intensity distributions from Q

% get dimensions of the provided data stack, S
R = size(S,1);
C = size(S,2);
Z = size(S,3);

% determine the number of points to use
numPointInQ = round(q_percent * R*C);
%fprintf(' number of points used to compute Q = %d  (%1.2f%%)\n', numPointInQ, q_percent*100);

% sort the means of each intensity distribution
meanSurf = mean(S,3);
[msorted inds] = sort(meanSurf(:)); %#ok<ASGLU>

% locations used to compute Q (M) come from the central quantile
M = inds( round((R*C)/2) - round(numPointInQ/2) : round((R*C)/2) + round(numPointInQ/2));
[rList cList] = ind2sub([R C], M);

% perform the robust mean 
Q = zeros(numel(M), Z);
for i = 1:numel(rList)
    r = rList(i);
    c = cList(i);
    thisQ = S(r,c,:);
    Q(i,:) =  thisQ(:);
end
Q = mean(Q,1); Q = squeeze(Q); Q = Q(:);
