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
    options.source_folder = pth;    
    
    % check if a file filter is provided, if so use it to generate a list
    % of source filenames
    if ~isempty(filter)
        d = dir([options.source_folder filter ext]);
        options.filenames = cell(numel(d),1);
        for i = 1:numel(d)
            options.filenames{i} = d(i).name;
        end
        
        
    % if a file filter is not provided, generate a list of source filanames
    % searching for all valid filetypes
    else
        for k = 1:numel(valid_filetypes)
            d = dir([options.source_folder '*' valid_filetypes{k}]);
            for i = 1:numel(d)
                options.filenames{end+1} = d(i).name;
            end
            options.num_images_provided = numel(options.filenames);
        end
        options.filenames = options.filenames';
        
    end
    
    % store the number of source images into the options structure
    options.num_images_provided = numel(options.filenames);
    
    % read the first provided image, check that it is monochromatic, store 
    % its size in the options structure, and determine the working image 
    % size we will use
    I = imread([options.source_folder options.filenames{1}]);
    if numel(size(I)) == 3
        error('CIDRE:loadImages', 'Non-monochromatic image provided. CIDRE is designed for monochromatic images. Store each channel as a separate image and re-run CIDRE.'); 
    end
    options.image_size = size(I);
    [R C] = determine_working_size(options.image_size, options.targetNumPixels);
    options.working_size = [R C];
    
    
    % read the source filenames in, covert them to the working image size, 
    % and add them to the stack    
    fprintf(' Reading %d images from %s\n .', options.num_images_provided, options.source_folder);
    t1 = tic;
    S = zeros([options.working_size options.num_images_provided]);
    for z = 1:options.num_images_provided
        if mod(z,100) == 0; fprintf('.'); end  % progress to the command line
        I = imread([options.source_folder options.filenames{z}]);
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
    [R C] = determine_working_size(options.image_size, options.targetNumPixels);
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



%% determine if sufficient intensity information is provided by measuring entropy
options = get_bit_depth(options, maxI); % store the bit depth of the images in options, needed for entropy measurement
entropy = get_entropy(S, options);      % compute the stack's entropy
S = scale_space_resampling(S,entropy); 	% resample the stack if the entropy is too high


%% sort the intensity values at every location in the image stack
% at every pixel location (r,c), we sort all the recorded intensities from
% the provided images and replace the stack with the new sorted values.
% The new S has the same data as before, but sorted in ascending order in
% the 3rd dimension.
t1 = tic;
fprintf(' Sorting intensity by pixel location and resizing...');
S = sort(S,3);

%% compress the stack: reduce the effective number of images for efficiency
S = resizeStack(S, options);
fprintf('finished in %1.2fs.\n', toc(t1));


% keyboard;












function [R_working C_working] = determine_working_size(image_size, N_desired)
% determines a working image size based on the original image size and
% the desired number of pixels in the working image, N_desired


R_original = image_size(1);
C_original = image_size(2);

scale_working = sqrt( N_desired/(R_original*C_original));

R_working = round(R_original * scale_working);
C_working = round(C_original * scale_working);




function options = get_bit_depth(options, maxI)
% Sets options.bit_depth describing the provided images as 8-bit, 12-bit, or 
% 16-bit. If options.bit_depth is provided, it is used. Otherwise the bit 
% depth is estimated from the max observed intensity, maxI.

if ~isempty(options.bit_depth)
    if ~ismember(options.bit_depth, [2^8 2^12 2^16])
        error('CIDRE:loadImages', 'Provide bit depth as max integer value, eg 2^12');
    else
        fprintf(' %d-bit depth\n', log2(options.bit_depth));
    end
else    
    if maxI > 2^12
        options.bit_depth = 2^16;
    elseif maxI > 2^8
        options.bit_depth = 2^12;
    else
        options.bit_depth = 2^8;
    end    
    fprintf(' %d-bit depth (estimated from max intensity=%1.0f)\n', log2(options.bit_depth), maxI);
end



function H = get_entropy(S, options)
% gets the entropy of an image stack. A very low entropy indicates that
% there may be insufficient intensity information to build a good model.
% This can happen when only a few images are provided and the background
% does not provide helpful information. For example in low confluency
% fluorescence images from a glass slide, the background pixels have nearly
% zero contribution from incident light and do not provide useful
% information.


% set one bin for every potential intensity level
bins = 1:options.bit_depth;

% get a distrubtion representing all of S
P = hist(S(:),bins);
P = P/sum(P);

% compute the entropy of the distribution
if sum(~isfinite(P(:)))
   error('CIDRE:loadImages', 'the inputs contain non-finite values!') 
end
P = P(:) ./ sum(P(:));
P(P == 0) = []; % In the case of p(xi) = 0 for some i, the value of the 
                % corresponding sum and 0 logb(0) is taken to be 0
temp = P .* log2(P);
H = -sum(temp);
fprintf(' Entropy of the stack = %1.2f\n', H);




function S2 = scale_space_resampling(S,entropy)
% uses scale space resampling to compensate for regions with little
% intensity information. if the entropy is very low, this indicates that 
% some (or many) have regions little useful information. resampling from
% a scale space transform allows us to leverage information from 
% neighboring locations. For example, in low confluency fluorescence images
% without a fluorescing medium, the background pixels contain nearly zero 
% contribution from incident light and do not provide useful information.



l0      = 1;            % max lambda_vreg
l1      = 0;            % stable lambda_vreg
N       = size(S,3);    % number of images in the stack
a       = 7.838e+06;    % parameters of a fitted exponential function
b       = -1.948;       % parameters of a fitted exponential function
c       = 20;           % parameters of a fitted exponential function

% emprical estimate of the number of images necessary at the reported entropy level
N_required = a*exp(b*entropy) + c; 

% alpha is a linear function from 1 (N=0) to 0 (N=N_required) and 0 
% (N > N_required). It informs us how strong the scale space resampling
% should be. alpha=1 means strong resampling, alpha=0 skips resampling
if N < N_required
    alpha = l0 + ((l1-l0)/(N_required)) * N;
else
    alpha = l1;
end

% the dimensions of the stack
R1 = size(S,1);
C1 = size(S,2);
Z1 = size(S,3);


% scale space reduction of the stack into octaves. SCALE is a cell 
% containing the scale space reductions of the stack: {[R1xC1xZ1], [R1/2 x 
% C1/2 x Z1], [R1/4 x C1/4 x Z1], ...}
i = 1;              % the octave counter
SCALE{i} = S;       % cell containing the scale space reductions
R = R1;             % scale space reduced image height
C = C1;             % scale space reduced image width
while (R > 1) && (C > 1)    
    i = i + 1;
    SCALE{i} = imresize(SCALE{i-1}, 0.5);
    R = size(SCALE{i},1);
    C = size(SCALE{i},2);
end

% determine the max octave we should keep, max_i as directed by the scaling
% strength alpha. alpha = 0 keeps only the original size. alpha = 1 uses 
% all available octaves
max_possible_i = i;
alpha = max([0 alpha]); alpha = min([1 alpha]);
max_i = ceil(alpha * max_possible_i);
max_i = max([max_i 1]);

% join the octaves from the scale space reduction from i=1 until i=max_i. 
if max_i > 1
    fprintf(' Applying scale-space resampling\n');
    S2 = zeros(R1,C1);
    for i = 1:max_i 
        R = size(SCALE{i},1);
        C = size(SCALE{i},2);

        ind1 = Z1*(i-1) + 1;
        ind2 = Z1*(i);

        fprintf('  octave=1/%d  size=%dx%d  (image %d to %d)\n', 2^(i-1), R, C,ind1,ind2);
        S_RESIZE = imresize(SCALE{i}, [R1 C1]);
        S2(:,:,ind1:ind2) = S_RESIZE;
    end
else
    fprintf(' Scale-space resampling NOT APPLIED (alpha = %d)\n', alpha);
    S2 = SCALE{1};
end



function S2 = resizeStack(S, options)
% in order keep CIDRE computationally tractable and to ease parameter 
% setting, we reduce the 3rd dimension of the sorted stack S to Z = 200. 
% Information is not discarded in the process, but several slices from the 
% stack are averaged into a single slice in the process.

% get the original dimensions of S
R = size(S,1);
C = size(S,2);
Z = size(S,3);

% if Z < options.number_of_quantiles, we do not want to further compress
% the data. leave S as is.
if Z <= options.number_of_quantiles
    %fprintf('Warning: number of images (%d) is less than sorted_regions (%d)\n', Z, sorted_regions);
    S2 = S;

% otherwise, we will reduce the 3rd dimension of S to be options.number_of_quantiles    
else    
    % find regionLimits, a set of indexes that breaks Z into
    % options.number_of_quantiles evenly space pieces
    Zmin = 1;
    Zmax = Z;
    Zdiff = Zmax - Zmin;
    regionLimits(1,1) = 1; regionLimits(1,2) = round(  Zmin +  Zdiff*(1/options.number_of_quantiles));
    for i = 2:options.number_of_quantiles
        regionLimits(i,1) = round(Zmin + Zdiff*( (i-1)/options.number_of_quantiles)) + 1;
        regionLimits(i,2) = round(Zmin + Zdiff*(i/options.number_of_quantiles));
    end
    
    % compute the mean image of each region of S defined by regionLimits,
    % and add the mean image to S2
    S2 = zeros(R,C,options.number_of_quantiles);
    for i = 1:options.number_of_quantiles
        I = mean(S(:,:,regionLimits(i,1):regionLimits(i,2)),3);
        S2(:,:,i) = I;
    end
end
    
    
  



