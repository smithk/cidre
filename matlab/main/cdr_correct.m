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
%                 1 = 'dynamic range corrected', or 
%                 2 = 'direct'
%
% Output:         Stores corrected images to the destination folder
%                 specified in options.
%
% See also: cidre, cidreGui, cdr_cidreModel


% if the destination folder doesn't exist, make it
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
        str = 'dynamic range corrected';
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
        case 0  %'intensity range _preserving'
            Icorrected = ((I - model.z)./model.v) * mean(model.v(:))  + mean(model.z(:));
                    
        case 1 % 'zero-light_preserving'
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




