function model = cidre(source, varargin)
% Illumination correction for optical microscopy. Apply to a collection of 
% monochromatic images. Multi-channel images should be separated, and each 
% channel corrected separately.
% 
% Usage:  MODEL = cidre(source, ...)
%
% Input:  SOURCE can be a path to a folder containing images, a path with a
%                filter (eg "/images/*.tif"), or an RxCxN array containing 
%                the images to be corrected (R = height, C = width, N = 
%                number of images).
%
%                CIDRE also supports the following optional arguments,
%                which override default parameters.
%       
%         'destination' specifies a folder where corrected images and 
%                correction model are stored. If empty, CIDRE will return a
%                model but not store corrected images.
%       
%         'correction_mode' (default = 2) specifies the type of correction
%                to perform:  0 "Zero-light preserved" retains the original 
%                intensity range and zero-light level of the original 
%                images. 1 "Dynamic range corrected" retains the intensity 
%                range of the original images. 2 "Direct" subtracts the 
%                zero-light term and divides the illumination gain. 
%
%         'lambda_v' (default = 6.0) high values (eg. 9.5) increase the
%                spatial regularization strength, yielding a more smooth v
%                intensity gain surface.
%
%         'lambda_z' (default = 0.5) high values (eg. 3) increase the
%                zero-light regularization strength, enforcing a more
%                uniform z surface.
%
%         'z_limits' (default none) user may specify known limits for the z
%                z surface, using dark frame images for example. Specify
%                the limits as a 2-element array (e.g. 'z_limits',[98 105])
%
%         'bit_depth' specifies the bit depth of the images to be correct.
%                The bit depth is automatically detected, only use this
%                option if the detection fails.
%
%          'q_percent' (default = 0.25) specifies the proportion of the 
%                data used to compute the robust mean, Q.
%
%          'max_lbfgs_iterations' (default = 500) specifies the maximum
%                number of iterations allowed in the optimization.
%
% Output: MODEL  a structure containing the correction model used by CIDRE
%                to correct the source images
%        
%
% See also: cidreGui

% From the CIDRE project, a general illumination correction method for
% optical microscopy (https://github.com/smithk/cidre).
% Copyright © 2014 Kevin Smith and Peter Horvath, Light Microscopy and 
% Screening Centre (LMSC), Swiss Federal Institute of Technology Zurich 
% (ETHZ), Switzerland. All rights reserved.
%
% This program is free software; you can redistribute it and/or modify it 
% under the terms of the GNU General Public License version 2 (or higher) 
% as published by the Free Software Foundation. This program is 
% distributed WITHOUT ANY WARRANTY; without even the implied warranty of 
% MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU 
% General Public License for more details.
%
% This software includes a subdirectory "3rdparty" which contains minFunc,
% 3rd party software implementing L-BFGS. MinFunc is licensed under a 
% Creative Commons, Attribute, Non-Commercial license. If you need to use 
% this software for commercial purposes, you can replace minFunc with 
% other software such as fminlbfgs, which may be slower.


% add necessary paths
if (~isdeployed)
    p = mfilename('fullpath');
    p = p(1:end-5);
    addpath([p '/3rdparty/']); 
    addpath([p '/gui/']);
    addpath([p '/io/']);
    addpath([p '/main/']);
end


% parse the input arguments, return a structure containing parameters
options = cdr_parseInputs(varargin);
options.cidrePath = p;

cdr_gui_toggle(options)

% load the data, either from a folder or from a passed array
[S options] = cdr_loadImages(source, options);

% learn the illumination correction model from processed data stack, S
[model options] = cdr_cidreModel(S,options);

% correct the source images, if requested
cdr_correct(model,options);

cdr_gui_toggle(options)