

t_root = '/home/ksmith/temp/t/';
m_root = '/home/ksmith/temp/m/';
d = dir(t_root);
d = d(3:end);


for i = 1:numel(d)
    
    input_folder = [t_root d(i).name '/'];

    in = [input_folder '*.tif'];
    out = [m_root d(i).name '/' ];
    
    fprintf('CIDRE %s -> %s\n', in, out);
    
    cidre(in, out);
end

