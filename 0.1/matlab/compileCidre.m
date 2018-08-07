% Compile CIDRE into a standalone executable file. Matlab Compiler Runtime 
% has to be installed. 
% See: http://www.mathworks.com/products/compiler/mcr/
%
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

% compile minFunc
fprintf('Compiling minFunc files...\n');
mex('-outdir',  fullfile(pwd,'3rdparty', 'minFunc2012', 'minFunc', 'compiled'),...
    fullfile(pwd,'3rdparty', 'minFunc2012', 'minFunc', 'mex', 'mcholC.c'));
mex('-outdir',  fullfile(pwd,'3rdparty', 'minFunc2012', 'minFunc', 'compiled'),... 
    fullfile(pwd,'3rdparty', 'minFunc2012', 'minFunc', 'mex', 'lbfgsC.c'));
mex('-outdir',  fullfile(pwd,'3rdparty', 'minFunc2012', 'minFunc', 'compiled'),... 
    fullfile(pwd,'3rdparty', 'minFunc2012', 'minFunc', 'mex', 'lbfgsAddC.c'));
mex('-outdir',  fullfile(pwd,'3rdparty', 'minFunc2012', 'minFunc', 'compiled'),... 
    fullfile(pwd,'3rdparty', 'minFunc2012', 'minFunc', 'mex', 'lbfgsProdC.c'));
fprintf('Compiling minFunc files... Done.\n');

% set cidre paths
fprintf('Setting CIDRE paths...\n');
addpath(pwd)
addpath(fullfile(pwd,'3rdparty', 'minFunc2012', 'minFunc'));
addpath(fullfile(pwd,'3rdparty', 'minFunc2012', 'minFunc', 'compiled'));
addpath(fullfile(pwd,'gui'));
addpath(fullfile(pwd,'io'));
addpath(fullfile(pwd,'main'));
fprintf('Setting CIDRE paths... Done.\n');

% compile cidre to a standalone executable
fprintf('Compiling CIDRE...\n');
mcc -o cidre -m cidre.m 
fprintf('Compiling CIDRE... Done.\n');