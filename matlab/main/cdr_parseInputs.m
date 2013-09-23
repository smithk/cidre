function options = cdr_parseInputs(v)  
% Parses the command line arguments and fills the options structure with
% default and overridden parameter values 
% 
% Usage:  OPTIONS = parseInputs(v)
%
% Input:  v - a cell containing arguments from the cidre.m (from varargin)
%
% Output: OPTIONS - a structure containing various parameter values needed by
%        CIDRE
%
% See also: cidre, cidreGui


options.max_lbfgs_iterations    = 500;
options.lambda_vreg             = 6;
options.lambda_zero             = 0.5;
options.q_percent               = .25;
options.workingImageSize        = 9400;
options.num_quantiles           = 200;
options.image_size              = [];

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
        otherwise
            warning('CIDRE:parseInput', 'Unrecognized paramaeter "%s" value: %1.3g\n', v{i}, param);
    end
end