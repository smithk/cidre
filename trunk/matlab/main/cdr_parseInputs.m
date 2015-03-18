function options = cdr_parseInputs(v)  
% Parses the command line arguments and fills the options structure with
% default and overridden parameter values 
% 
% Usage:  OPTIONS = parseInputs(v)
%
% Input:  V a cell containing arguments from the cidre.m (from varargin)
%
% Output: OPTIONS a structure containing various parameter values needed by
%         CIDRE
%
% See also: cidre, cidreGui

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

% handle case where arguments are passed as a cell
if ~isempty(v)
    if iscell(v{1})
        v = v{1};
    end
end

%% options that may be specified by the user
options.lambda_vreg             = [];   % default value = 6
options.lambda_zero             = [];   % default value = 0.5
options.max_lbfgs_iterations    = [];   % default value = 500
options.q_percent               = [];   % default value = 0.25
options.z_limits                = [];
options.image_size              = [];
options.folder_source           = [];
options.folder_destination      = [];
options.filenames               = {};
options.num_images_provided     = [];
options.bit_depth               = [];   % specified as maximum integer: 2^8, 2^12, 2^16
options.correction_mode         = [];   % 0 ='illumination preserving' (default), 1='zero-light_perserving', or 2='direct'
options.handles                 = [];   % used for GUI handles

%% internal options, should not be reset by user without expert knowledge
options.target_num_pixels     	= 9400;
options.working_size            = [];
options.number_of_quantiles     = 200;
%options.lambda_barr             = [];





% handle the variable input parameters, provided in (string, param) pairs
for i = 1:numel(v)
    if ischar(v{i})
            switch lower(v{i})
            case {'lambda_vreg', 'lambda_v'}
                options.lambda_vreg = getParam(v,i);            
            case {'lambda_zero', 'lambda_z'}
                options.lambda_zero = getParam(v,i);            
            case 'q_percent'
                options.q_percent = getParam(v,i);   
            case 'z_limits'
                options.z_limits = getParam(v,i);
            case 'max_lbfgs_iterations'
                options.max_lbfgs_iterations = getParam(v,i);
            case 'image_size'
                options.image_size = getParam(v,i);
            case 'bit_depth'    
                options.bit_depth = getParam(v,i);
            case 'correction_mode'
                options.correction_mode = getParam(v,i);
            case 'destination'
                options.folder_destination = getFolder(v,i);            
            end       
    end
    
    if isstruct(v{i})
        options.handles = v{i};
    end
end

% check for other important input warnings 
if isempty(options.folder_destination)
    warning('CIDRE:parseInput', 'No destination folder was specified. CIDRE will not store corrected images.\n');
end


% sort the options alphabetically so they are easier to read
options = orderfields(options);



function param = getParam(v,i)

param = [];
if i+1 <= numel(v)
    if isnumeric(v{i+1})
        param = v{i+1};
    else
        warning('CIDRE:parseInput', 'Expected numeric value\n');
    end
end


function param = getFolder(v,i)

param = [];
if i+1 <= numel(v)
    if isdir(v{i+1})
        param = v{i+1};
    else
        if isstr(v{i+1});
            param = v{i+1};
            [success message] = mkdir(param);
            if ~success
                warning('CIDRE:parseInput', message);
            end
        else
            warning('CIDRE:parseInput', 'Expected destination path value\n');
        end
    end
end

