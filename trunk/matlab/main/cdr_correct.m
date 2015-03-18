function cdr_correct(model,options)
% Using the model learned in cdr_cidreModel, this function corrects the
% source images and saves the corrected images to the destination folder
% provided in the options structure.
% 
% Usage:          cdr_objective(model)
%
% Input: MODEL    The illumination correction model learned from
%                 cdr_CidreModel
%
%        OPTIONS  a data structure containing the various parameters values
%                 required by CIDRE. The correction mode option has 3
%                 possible values:
%                 0 = 'zero-light perserved' (default), 
%                 1 = 'intensity range preserved', or 
%                 2 = 'direct'
%
% Output:         Stores corrected images to the destination folder
%                 specified in options.
%
% See also: cidre, cidreGui, cdr_cidreModel

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

% don't do anything if no destination folder is given
if isempty(options.folder_destination)
    return;
end

% if the destination folder doesn't exist, create it
if ~exist(options.folder_destination, 'dir')
    mkdir(options.folder_destination);
end

% make sure the path ends with a slash
if ~strcmpi(options.folder_destination(end), '/') && ~strcmpi(options.folder_destination(end), '\')
    options.folder_destination(end+1) = '/';
end

if isempty(options.correction_mode)
    options.correction_mode = 0;
end



% save the correction model to the destination folder
filename = sprintf('%s%s', options.folder_destination, 'cidre_model.mat');
save(filename, 'model', 'options');
fprintf(' Saved the correction model to %s\n', filename);



% loop through all the source images, correct them, and write them to the 
% destination folder
switch options.correction_mode
    case 0
        str = 'zero-light perserved';
    case 1
        str = 'intensity range corrected';
    case 2
        str = 'direct';
end
fprintf(' Writing %s corrected images to %s\n ', upper(str), options.folder_destination);
t1 = tic;
for z = 1:options.num_images_provided
    if mod(z,100) == 0; fprintf('.'); end  % progress to the command line
    I = imread([options.folder_source options.filenames{z}]);
    imageClass = class(I);
    I = double(I);
    
    % check which type of correction we want to do
    switch options.correction_mode
        case 0  %'zero-light preserving'
            Icorrected = ((I - model.z)./model.v) * mean(model.v(:))  + mean(model.z(:));
                    
        case 1 % 'intensity range preserving'
            Icorrected = ((I - model.z)./model.v) * mean(model.v(:));
        
        case 2 %'direct'    
            Icorrected = ((I - model.z)./model.v);
            
        otherwise
            error('CIDRE:correction', 'Unrecognized correction mode: %s', lower(options.correction_mode));
    end
    
    
    Icorrected = cast(Icorrected, imageClass);
    [pth name ext] = fileparts(options.filenames{z});
    filename = sprintf('%s%s%s', options.folder_destination, name, ext);
    %fprintf('writing %s\n', filename);
    imwrite(Icorrected, filename);
end


fprintf(' finished in %1.2fs.\n', toc(t1));




