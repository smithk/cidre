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




%% options that may be specified by the user
options.lambda_vreg             = [];   % default value = 6
options.lambda_zero             = [];   % default value = 0.5
options.max_lbfgs_iterations    = 500;
options.q_percent               = .25;
options.image_size              = [];
options.source_folder           = [];
options.destination_folder      = [];
options.filenames               = {};
options.num_images_provided     = [];
options.bit_depth               = [];   % specified as bit max: 2^8, 2^12, 2^16

%% internal options, should not be reset by user without expert knowledge
options.targetNumPixels         = 9400;
options.working_size            = [];
options.number_of_quantiles     = 200;





% handle the variable input parameters, provided in (string, param) pairs
for i = 1:2:numel(v)
    if i+1 <= numel(v)
        if isnumeric(v{i+1})
            param = v{i+1};
        else
            warning('CIDRE:parseInput', 'Expected numeric value\n');
        end
    end
        
    switch lower(v{i})
        case 'lambda_vreg'
            options.lambda_vreg = param;            
        case 'lambda_zero'
            options.lambda_zero = param;            
        case 'q_percent'
            options.q_percent = param;   
        case 'max_lbfgs_iterations'
            options.max_lbfgs_iterations = param;
        case 'image_size'
            options.image_size = param;
        case 'bit_depth'    
            options.bit_depth = param;
        otherwise
            warning('CIDRE:parseInput', 'Unrecognized paramaeter "%s" value: %1.3g\n', v{i}, param);
    end
end


% check for other important input warnings 
if isempty(options.destination_folder)
    warning('CIDRE:parseInput', 'No destination folder was specified. CIDRE will not store corrected images.\n');
end


