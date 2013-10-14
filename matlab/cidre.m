function model = cidre(source, varargin)
% A general illumination correction method for optical microscopy. Should
% be applied to collections of images. Images must be monochromatic. Multi-
% channel images should be separated and each channel corrected separately.
% 
% Usage:  MODEL = cidre(source, ...)
%
% Input:  SOURCE can be a path to a folder containing images, a path with a
%                filter (eg "/images/*.tif"), or an RxCxN array containing 
%                the images to be corrected (R = height, C = width, N = 
%                number of images).
%
%                Optional arguments pairs can override default parameters.
%
% Output: MODEL  a structure containing the correction model used by CIDRE
%                to correct the source images
%        
%
% See also: cidreGui

% From the CIDRE project, a general illumination correction method for
% optical microscopy (https://github.com/smithk/cidre).
% Copyright © 2013 Kevin Smith and Peter Horvath, Light Microscopy and 
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
addpath 3rdparty/ gui/ io/ main/

% parse the input arguments, return a structure containing parameters
options = cdr_parseInputs(varargin);

% load the data, either from a folder or from a passed array
[S options] = cdr_loadImages(source, options);

% learn the illumination correction model from processed data stack, S
[model options] = cdr_cidreModel(S,options);

% correct the source images, if requested
cdr_correct(model,options);


