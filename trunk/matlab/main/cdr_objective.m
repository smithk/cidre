function [E G] = cdr_objective(x)
% The objective function and the analytic gradient of the objective
% function for CIDRE.
% 
% Usage:  [E G]   = cdr_objective(x)
%
% Input: x        an array containing the random variables we are trying to
%                 optimize (v,b,zx,zy).
%
% Output: E       The energy of the objective function computed with the
%                 values provided in x.
%         
%         G       The gradient of the objective function wrt. the variables 
%                 we are trying to optimize
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

% define some global variables that must be shared with the objective
% function, cdr_objective
global S                    % the processed stack of observed data, contains q at every location
global Q                    % the estimate of the underlying intensity distribution
global CAUCHY_W             % width of the Cauchy function used for robust regression
global PivotShiftX          % shift on the Q axis into the pivot space
global PivotShiftY          % shift on the q axis into the pivot space
global ITER                 % iteration coutn for the optimization
global MESTIMATOR           % specifies "CAUCHY" or "LS" (least squares)
global TERMSFLAG            % flag specifing which terms to include in the energy function
global LAMBDA_VREG          % coefficient for the v regularization
global LAMBDA_ZERO          % coefficient for the zero-light term
global ZMIN                 % minimum value for Z surface
global ZMAX                 % maximum value for Z surface


% some basic definitions
R           = size(S,1); 	% dimensions of S containing q data
C           = size(S,2);
Z           = size(S,3);
N_stan      = 200;         	% the standard number of quantiles used for empirical parameter setting
w           = CAUCHY_W;    	% width of the Cauchy functio
LAMBDA_BARR = 10^6;         % the barrier term coefficient

% unpack the x vector into the random variables
[v_img v_vec b_img b_vec zx zy] = unpack_x(x, [R C]);

% move the zero-light point to the pivot space (zx,zy) -> (px,py)
px = zx - PivotShiftX;  % a scalar
py = zy - PivotShiftY;  % a 2D array
py = py(:);



%--------------------------------------------------------------------------
%% fitting energy
% We compute the energy of the fitting term given v,b,zx,zy. We also
% compute its gradient wrt the random variables.

energy_fit  = zeros(size(v_vec));      % accumulates the fit energy
deriv_v_fit = zeros(size(v_vec));      % derivative of fit term wrt v
deriv_b_fit = zeros(size(b_vec));      % derivative of fit term wrt b
i = 1;                                 % linear index of image location
for c = 1:C
    for r = 1:R        
        % get the quantile fit for this location and vectorize it
        q = S(r,c,:); q = double(q(:));
        
        v = v_img(r,c);
        b = b_img(r,c);

        switch MESTIMATOR
            case 'LS'
                mestimator_response = (Q.*v + b - q).^2;
                d_est_dv = Q.*(Q.*v + b - q);
                d_est_db = Q.*v + b - q;
            case 'CAUCHY'
                mestimator_response = (w^2 * log( 1+ ( Q.*v + b - q    ).^2 ./ (w^2) ))/2;
                d_est_dv = (Q.*(Q.*v + b -q))./(1 + ((Q.*v + b - q).^2)./(w^2));
                d_est_db = (Q.*v + b -q)./(1 + ((Q.*v + b - q).^2)./(w^2));
        end
        
        %         residuals = v * Q + b - q;
        energy_fit(i) = sum(mestimator_response);
        deriv_v_fit(i) = sum(d_est_dv);
        deriv_b_fit(i) = sum(d_est_db);

        % Q-q plot for location r,c
        PLOTTING = 0;
        if PLOTTING
            loc = [4 63];
            if (r == loc(1)) && (c == loc(2))
                figure(99); clf; hold on;
                plot(Q,q,'b+');
                x = min(q)-200:5:max(Q)+100;
                y = b + v*x;
                plot(x,y, 'k-');
                plot(px,py(i), 'ro', 'MarkerSize', 5, 'MarkerFaceColor', [1 0 0]);
                plot(0,0,'m+');                
                xlabel('Q');
                ylabel('q');
                str = sprintf('v = %1.3f,   b = %1.3f',v,b);
                text(200, 0, str);
                str = sprintf('Q-q %s fit for r=%d c=%d', MESTIMATOR, r, c);
                title(str);
                refresh; drawnow;
            end
        end
        i = i + 1;
    end
end
% normalize the contribution from fitting energy term by the number of data 
% points in S (so our balancing of the energy terms is invariant)
data_size_factor = N_stan/Z;  
E_fit = sum(energy_fit) * data_size_factor;     % fit term energy
G_V_fit = deriv_v_fit(:) * data_size_factor;    % fit term derivative wrt v
G_B_fit = deriv_b_fit(:) * data_size_factor;    % fit term derivative wrt b
%--------------------------------------------------------------------------


%--------------------------------------------------------------------------
%% spatial regularization of v
% We compute the energy of the regularization term given v,b,zx,zy. We also
% compute its gradient wrt the random variables.

% determine the widths we will use for the LoG filter
max_exp = max(1, log2(floor(max([R C]/50))));
sigmas = 2.^([-1:max_exp]); %#ok<NBRAK>

energy_vreg     = zeros(size(sigmas));      % accumulates the vreg energy
deriv_v_vreg    = zeros(size(v_img));       % derivative of vreg term wrt v
deriv_b_vreg    = zeros(size(b_img));       % derivative of vreg term wrt b

% apply the scale-invariant LoG filter to v for all scales in SIGMAS
for n = 1:numel(sigmas)
    % define the kernel size, make certain dimension is odd
    hsize = [6*ceil(sigmas(n))  6*ceil(sigmas(n))]; 
    if mod(hsize(1),2) == 0
        hsize = hsize + 1;
    end
    h{n} = sigmas(n)^2 * fspecial('log', hsize, sigmas(n));  %#ok<AGROW>
    
    % apply a LoG filter to v_img to penalize disagreements between neighbors
    v_LoG = imfilter(v_img, h{n}, 'symmetric');
    v_LoG = v_LoG / numel(sigmas);	% normalize by the # of sigmas used
    
    % energy is quadratic LoG response
    energy_vreg(n) = sum(v_LoG(:).^2);
    deriv_v_vreg = deriv_v_vreg + imfilter(2*v_LoG, h{n}, 'symmetric');    
end
E_vreg = sum(energy_vreg);          % vreg term energy
G_V_vreg = deriv_v_vreg(:);         % vreg term gradient wrt v
G_B_vreg = deriv_b_vreg(:);         % vreg term gradient wrt b
%--------------------------------------------------------------------------



%--------------------------------------------------------------------------
%% The ZERO-LIGHT term
% We compute the energy of the zero-light term given v,b,zx,zy. We also
% compute its gradient wrt the random variables.

residual = v_vec * px + b_vec - py;

deriv_v_zero = 2*px*(b_vec + v_vec*px - py);
deriv_b_zero = 2*(b_vec + v_vec*px -py);
deriv_zx_zero = sum(2*v_vec.*(b_vec + v_vec*px - py));
deriv_zy_zero = sum(-2.*(b_vec + v_vec*px - py));

E_zero = sum(residual.^2);          % zero light term energy     
G_V_zero = deriv_v_zero;            % zero light term gradient wrt v
G_B_zero = deriv_b_zero;            % zero light term gradient wrt b
G_ZX_zero = deriv_zx_zero;          % zero light term gradient wrt zx
G_ZY_zero = deriv_zy_zero;          % zero light term gradient wrt zy
%--------------------------------------------------------------------------


%--------------------------------------------------------------------------
%% The BARRIER term
% We compute the energy of the barrier term given v,b,zx,zy. We also
% compute its gradient wrt the random variables.

Q_UPPER_LIMIT = ZMAX;   % upper limit - transition from zero energy to quadratic increase 
Q_LOWER_LIMIT = ZMIN;   % lower limit - transition from quadratic to zero energy
Q_RATE = .001; %2;             % rate of increase in energy 

% barrier term gradients and energy components
[E_barr_xc G_ZX_barr]   = theBarrierFunction(zx, Q_LOWER_LIMIT, Q_UPPER_LIMIT, Q_RATE);
[E_barr_yc G_ZY_barr]   = theBarrierFunction(zy, Q_LOWER_LIMIT, Q_UPPER_LIMIT, Q_RATE);

E_barr = E_barr_xc + E_barr_yc;     % barrier term energy

%--------------------------------------------------------------------------





%--------------------------------------------------------------------------
%% The total energy 
% Find the sum of all components of the energy. TERMSFLAG switches on and
% off different components of the energy.
switch TERMSFLAG
    case 0
        E = E_fit;
        term_str = 'fitting only';  
    case 1
        E = E_fit + LAMBDA_VREG*E_vreg + LAMBDA_ZERO*E_zero + LAMBDA_BARR*E_barr;
        term_str = 'all terms';    
end
%--------------------------------------------------------------------------


%--------------------------------------------------------------------------
%% The gradient of the energy
switch TERMSFLAG
    case 0
        G_V = G_V_fit;
        G_B = G_B_fit;
        G_ZX = 0;
        G_ZY = 0;
    case 1
        G_V = G_V_fit + LAMBDA_VREG*G_V_vreg + LAMBDA_ZERO*G_V_zero;
        G_B = G_B_fit + LAMBDA_VREG*G_B_vreg + LAMBDA_ZERO*G_B_zero;
        G_ZX = LAMBDA_ZERO*G_ZX_zero + LAMBDA_BARR*G_ZX_barr;
        G_ZY = LAMBDA_ZERO*G_ZY_zero + LAMBDA_BARR*G_ZY_barr;
end
      
% vectorize the gradient
G = [G_V(:); G_B(:); G_ZX; G_ZY];
%--------------------------------------------------------------------------

% loc = [10 10];
% fprintf('iter = %d  %s %s    zx,zy=(%1.2f,%1.2f)    v(loc)=%1.3f   b(loc)=%1.3f  E=%g\n', ITER, MESTIMATOR, term_str, zx,zy,v_img(loc(1),loc(2)),b_img(loc(1),loc(2)), E);
ITER = ITER + 1;





function [v v_vec b b_vec zx zy] = unpack_x(x, working_size)
% unpacks the v surface, b surface, xc, and yc from x, where the elements
% have been vectorized

R = working_size(1);
C = working_size(2);

v_vec	= double(x(1:R*C));
b_vec	= double(x(R*C + 1:2*R*C));
v       = reshape(v_vec,[R C]);
b       = reshape(b_vec,[R C]);
zx      = x(end-1);
zy      = x(end);


function [E G] = theBarrierFunction(x, xmin, xmax, width)
% the barrier function has a well shape. It has quadratically increasing
% energy below xmin, zero energy between xmin and xmax, and quadratically
% increasing energy above xmax. The rate of increase is determined by width

E = zeros(size(x));
G = zeros(size(x));

xl1 = xmin;
xl2 = xl1 + width;

xh2 = xmax;
xh1 = xh2 - width;

for i = 1:numel(x)
    if x(i) <= xl1
        E(i) = ((x(i)-xl2)/(xl2-xl1))^2;
        G(i) = (2*(x(i)-xl2))  / ((xl2-xl1)^2);  

    elseif (x(i) >= xl1) && (x(i) <= xl2)
        E(i) = ((x(i)-xl2)/(xl2-xl1))^2;
        G(i) = (2*(x(i)-xl2))  / ((xl2-xl1)^2);

    elseif (x(i) > xl2) && (x(i) < xh1)
        E(i) = 0;
        G(i) = 0; 
        
    elseif (x(i) >= xh1) && (x(i) < xh2)
        E(i) = ((x(i)-xh1)/(xh2-xh1))^2;
        G(i) = (2*(x(i)-xh1))  / ((xh2-xh1)^2);
    else
        E(i) = ((x(i)-xh1)/(xh2-xh1))^2;
        G(i) = (2*(x(i)-xh1))  / ((xh2-xh1)^2);
    end
    
end
