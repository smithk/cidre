

t_root = '/home/ksmith/temp/t/';
m_root = '/home/ksmith/temp/m/';
f_root = '/home/ksmith/temp/f/'; 
d = dir(t_root);
d = d(3:end);


for i = 1:numel(d)
    
    close all;
    
    
    m_folder = [m_root d(i).name '/'];
    load([m_folder 'cidre_model.mat']);
    
    
    f_folder = [f_root d(i).name '/'];
    
    Fiji_v = csvread([f_folder 'cidre_model_v.csv']);
    Fiji_z = csvread([f_folder 'cidre_model_z.csv']);
    
    fprintf('%s\n', m_folder);

    
    v_range = [min( [Fiji_v(:); model.v(:)]) max( [Fiji_v(:); model.v(:)])];

    f1 = figure; 
    imagesc(Fiji_v, v_range);
    title(sprintf('Fiji - V - %s', d(i).name)); colorbar;
    set(f1, 'Position', [91 537 560 420]);
    
    f2 = figure; 
    imagesc(model.v, v_range);
    title(sprintf('Matlab - V - %s', d(i).name)); colorbar;
    set(f2, 'Position', [683 537 560 420]);
    
    f3 = figure; 
    imagesc(Fiji_v - model.v);
    title(sprintf('Difference - V - %s', d(i).name)); colorbar;
    set(f3, 'Position', [1267 536 560 420]);
    
    z_range = [min( [Fiji_z(:); model.z(:)]) max( [Fiji_z(:); model.z(:)])];
    
    f4 = figure; 
    imagesc(Fiji_z, z_range);
    title(sprintf('Fiji - Z - %s', d(i).name)); colorbar;
    set(f4, 'Position', [90 24 560 420]);
    
    f5 = figure; 
    imagesc(model.z, z_range);
    title(sprintf('Matlab - Z - %s', d(i).name)); colorbar;
    set(f5, 'Position', [683 23 560 420]);
    
    f6 = figure; 
    imagesc(Fiji_z - model.z);
    title(sprintf('Difference - Z - %s', d(i).name)); colorbar;
    set(f6, 'Position', [1267 24 560 420]);
    
    
    keyboard;
end

