% Compile CIDRE into a standalone executable file. Matlab Compiler Runtime 
% has to be installed. 
% See: http://www.mathworks.com/products/compiler/mcr/
%
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

% get the path to this file
p = mfilename('fullpath');
p = p(1:end-12);

% compile minFunc
fprintf('Compiling minFunc files...\n');
mex('-outdir',  fullfile(p,'3rdparty', 'minFunc2012', 'minFunc', 'compiled'),...
    fullfile(p,'3rdparty', 'minFunc2012', 'minFunc', 'mex', 'mcholC.c'));
mex('-outdir',  fullfile(p,'3rdparty', 'minFunc2012', 'minFunc', 'compiled'),... 
    fullfile(p,'3rdparty', 'minFunc2012', 'minFunc', 'mex', 'lbfgsC.c'));
mex('-outdir',  fullfile(p,'3rdparty', 'minFunc2012', 'minFunc', 'compiled'),... 
    fullfile(p,'3rdparty', 'minFunc2012', 'minFunc', 'mex', 'lbfgsAddC.c'));
mex('-outdir',  fullfile(p,'3rdparty', 'minFunc2012', 'minFunc', 'compiled'),... 
    fullfile(p,'3rdparty', 'minFunc2012', 'minFunc', 'mex', 'lbfgsProdC.c'));
fprintf('Compiling minFunc files... Done.\n');

% set cidre paths
fprintf('Setting CIDRE paths...\n');
addpath(pwd)
addpath(fullfile(p,'3rdparty', 'minFunc2012', 'minFunc'));
addpath(fullfile(p,'3rdparty', 'minFunc2012', 'minFunc', 'compiled'));
addpath(fullfile(p,'gui'));
addpath(fullfile(p,'io'));
addpath(fullfile(p,'main'));
fprintf('Setting CIDRE paths... Done.\n');

% compile cidre to a standalone executable
fprintf('Compiling CIDRE...\n');
mcc -o cidre -m cidre.m 
fprintf('Compiling CIDRE... Done.\n');