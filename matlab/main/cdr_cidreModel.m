function [model options] = cdr_cidreModel(S, options)
% Loads the raw images into a STACK of images, or accepts a stack of images
% passed as an argument. Performs resizing, sorting, and compressing of the
% image data to keep the model generation fast.
% 
% Usage:  [model options] = cdr_cidreModel(S, options)
%
% Input: S        an array containing the processed image data as returned
%                 by cdr_preprocessData.
%
%        OPTIONS  a data structure containing the various parameters values
%                 required by CIDRE.
%
% Output: MODEL   a data structure containing the correction model learned 
%                 from the data, S, which can be used to correct the
%                 illumination of the source images
%         
%         OPTIONS returns the options structure with appropriatetly 
%                 modified parameters: 
%
% See also: cidre, cidreGui


model = [];
