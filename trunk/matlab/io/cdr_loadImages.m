function [S options] = cdr_loadImages(source, options)  
% Loads the raw images into a STACK of images, or accepts a stack of images
% passed as an argument. Performs resizing, sorting, and compressing of the
% image data to keep the model generation fast.
% 
% Usage:  [S options] = cdr_loadImages(source) 
%
% Input: SOURCE  can be a path to a folder containing images, a path with a
%                filter (eg "/images/*.tif"), or an RxCxN array containing 
%                the images to be corrected (R = height, C = width, N = 
%                number of images)
%
% Output: S      an array containing the image data to be used by CIDRE
%         
%         OPTIONS returns the options structure with appropriatetly 
%                 modified parameters: the size of the original images
%                 [height width], the number of input images provided, the
%                 working image size, the source folder, the filenames of 
%                 the source images
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

S           = [];   %#ok<NASGU> the array containing the image data returned to CIDRE
maxI        = 0;    % the max intensity found in the supplied images



% list of filetypes imread can open
valid_filetypes = {'.bmp', '.gif', '.jpg', '.jpeg', '.tif', '.tiff', '.png',...
                   '.BMP', '.GIF', '.JPG', '.JPEG', '.TIF', '.TIFF', '.PNG'};


%% obtain an array S, containing the source image data at the working image
%% size. S can be loaded from a source path (+filter) or from an array 
%% passed as an argument
if ischar(source)
    % source is a char defining the path (+filter) of files to open

    
    % break source into a path, filter, and extension
    [pth filter ext] = fileparts(source);
    pth = [pth '/'];
    
    % store the source path in the options structure
    options.folder_source = pth;    
    
    % check if a file filter is provided, if so use it to generate a list
    % of source filenames
    if ~isempty(filter)
        d = dir([options.folder_source filter ext]);
        options.filenames = cell(numel(d),1);
        for i = 1:numel(d)
            options.filenames{i} = d(i).name;
        end
        
        
    % if a file filter is not provided, generate a list of source filanames
    % searching for all valid filetypes
    else
        for k = 1:numel(valid_filetypes)
            d = dir([options.folder_source '*' valid_filetypes{k}]);
            for i = 1:numel(d)
                options.filenames{end+1} = d(i).name;
            end
            options.num_images_provided = numel(options.filenames);
        end
        options.filenames = options.filenames';
        
    end
    
    % check if we have found any valid files in the folder
    if numel(options.filenames) == 0
        error('CIDRE:loadImages', 'No files found.'); 
    end
    
    % store the number of source images into the options structure
    options.num_images_provided = numel(options.filenames);
    
    % read the first provided image, check that it is monochromatic, store 
    % its size in the options structure, and determine the working image 
    % size we will use
    I = imread([options.folder_source options.filenames{1}]);
    if numel(size(I)) == 3
        error('CIDRE:loadImages', 'Non-monochromatic image provided. CIDRE is designed for monochromatic images. Store each channel as a separate image and re-run CIDRE.'); 
    end
    options.image_size = size(I);
    [R C] = determine_working_size(options.image_size, options.target_num_pixels);
    options.working_size = [R C];
    
    
    % read the source filenames in, covert them to the working image size, 
    % and add them to the stack    
    fprintf(' Reading %d images from %s\n .', options.num_images_provided, options.folder_source);
    t1 = tic;
    S = zeros([options.working_size options.num_images_provided]);
    for z = 1:options.num_images_provided
        if mod(z,100) == 0; fprintf('.'); end  % progress to the command line
        I = imread([options.folder_source options.filenames{z}]);
        I = double(I);
        maxI = max(maxI, max(I(:)));
        Irescaled = imresize(I, options.working_size);
        S(:,:,z) = Irescaled;
    end
    fprintf('finished in %1.2fs.\n', toc(t1));
    
else
    % source is a HxWxZ array containing the image stack
    
    if isempty(options.image_size)
        warning('CIDRE:loadImages', 'Original image size not provided, assuming %d x %d (size of passed stack)\n', size(source,1), size(source,2));
        options.image_size = [size(source,1) size(source,2)];
    end
    
    % determine the working image size
    [R C] = determine_working_size(options.image_size, options.target_num_pixels);
    options.working_size = [R C];
    
    % store the number of source images provided in the stack
    options.num_images_provided = size(source,3);    
    
    % give some feedback to the command line
    fprintf(' Image stack passed as an argument (%d images). Resizing\n .', options.num_images_provided);
    t1 = tic;
    
    % resize each element of the stack to the working image size
    if isempty(options.bit_depth); maxI = max(source(:)); end   % if bit depth is not provided, we need maxI to estimate it
    S = zeros([options.working_size options.num_images_provided]);
    for z = 1:options.num_images_provided
        if mod(z,100) == 0; fprintf('.'); end  % progress to the command line
        I = source(:,:,z);
        I = double(I);
        Irescaled = imresize(I, options.working_size);
        S(:,:,z) = Irescaled;
    end
    fprintf('finished in %1.2fs.\n', toc(t1));
end



%% apply several processing steps to the image stack S
% Now that we have loaded the stack as an RxCxN array (where R*C ~=
% options.target_num_pixels), we must do check if there is sufficient
% intensity information in the stack, sort the intensity values at each
% (x,y) image location, and compress the stack in the 3rd dimension to keep
% the computation time manageable
[S options] = cdr_preprocessData(S, maxI, options);














function [R_working C_working] = determine_working_size(image_size, N_desired)
% determines a working image size based on the original image size and
% the desired number of pixels in the working image, N_desired


R_original = image_size(1);
C_original = image_size(2);

scale_working = sqrt( N_desired/(R_original*C_original));

R_working = round(R_original * scale_working);
C_working = round(C_original * scale_working);








