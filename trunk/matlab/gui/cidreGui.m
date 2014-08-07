function varargout = cidreGui(varargin)
% CIDREGUI MATLAB code for cidreGui.fig
%      CIDREGUI, by itself, creates a new CIDREGUI or raises the existing
%      singleton*.
%
%      H = CIDREGUI returns the handle to a new CIDREGUI or the handle to
%      the existing singleton*.
%
%      CIDREGUI('CALLBACK',hObject,eventData,handles,...) calls the local
%      function named CALLBACK in CIDREGUI.M with the given input arguments.
%
%      CIDREGUI('Property','Value',...) creates a new CIDREGUI or raises the
%      existing singleton*.  Starting from the left, property value pairs are
%      applied to the GUI before cidreGui_OpeningFcn gets called.  An
%      unrecognized property name or invalid value makes property application
%      stop.  All inputs are passed to cidreGui_OpeningFcn via varargin.
%
%      *See GUI Options on GUIDE's Tools menu.  Choose "GUI allows only one
%      instance to run (singleton)".
%
% See also: GUIDE, GUIDATA, GUIHANDLES

% Edit the above text to modify the response to help cidreGui

% Last Modified by GUIDE v2.5 07-Aug-2014 01:45:26

% Begin initialization code - DO NOT EDIT
gui_Singleton = 1;
gui_State = struct('gui_Name',       mfilename, ...
                   'gui_Singleton',  gui_Singleton, ...
                   'gui_OpeningFcn', @cidreGui_OpeningFcn, ...
                   'gui_OutputFcn',  @cidreGui_OutputFcn, ...
                   'gui_LayoutFcn',  [] , ...
                   'gui_Callback',   []);
if nargin && ischar(varargin{1})
    gui_State.gui_Callback = str2func(varargin{1});
end

if nargout
    [varargout{1:nargout}] = gui_mainfcn(gui_State, varargin{:});
else
    gui_mainfcn(gui_State, varargin{:});
end
% End initialization code - DO NOT EDIT


% --- Executes just before cidreGui is made visible.
function cidreGui_OpeningFcn(hObject, eventdata, handles, varargin)
% This function has no output args, see OutputFcn.
% hObject    handle to figure
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)
% varargin   command line arguments to cidreGui (see VARARGIN)

% Choose default command line output for cidreGui
handles.output = hObject;


handles.lambdaV = 6;
handles.lambdaZ = 0.5;
handles.Zlimits = [];
handles.bit_depth = [];
handles.q_percent = 0.25;
handles.lbfgs_iterations = 500;
handles.correction_mode = 2;
handles.destination_folder = [];
if ispc()
    handles.source_folder = [pwd '\*.tif'];
else
    handles.source_folder = [pwd '/*.tif'];
end

set(handles.pushbutton_source_folder, 'BusyAction', 'cancel');
set(handles.edit_source_folder, 'BusyAction', 'cancel');
set(handles.pushbutton_destination_folder, 'BusyAction', 'cancel');
set(handles.pushbutton_destination_folder, 'BusyAction', 'cancel');
set(handles.edit_destination_folder, 'BusyAction', 'cancel');
set(handles.pushbutton_source_folder, 'BusyAction', 'cancel');
set(handles.edit_source_folder, 'BusyAction', 'cancel');
set(handles.text16, 'BusyAction', 'cancel');
set(handles.text15, 'BusyAction', 'cancel');
set(handles.text14, 'BusyAction', 'cancel');
set(handles.edit_maxZ, 'BusyAction', 'cancel');
set(handles.edit_minZ, 'BusyAction', 'cancel');
set(handles.text9, 'BusyAction', 'cancel');
set(handles.text13, 'BusyAction', 'cancel');
set(handles.edit_max_iter, 'BusyAction', 'cancel');
set(handles.text8, 'BusyAction', 'cancel');
set(handles.text6, 'BusyAction', 'cancel');
set(handles.popupmenu2, 'BusyAction', 'cancel');
set(handles.edit_Q, 'BusyAction', 'cancel');
set(handles.text11, 'BusyAction', 'cancel');
set(handles.popupmenu_mode, 'BusyAction', 'cancel');
set(handles.editZ, 'BusyAction', 'cancel');
set(handles.text4, 'BusyAction', 'cancel');
set(handles.text3, 'BusyAction', 'cancel');
set(handles.sliderZ, 'BusyAction', 'cancel');
set(handles.editV, 'BusyAction', 'cancel');
set(handles.text2, 'BusyAction', 'cancel');
set(handles.text1, 'BusyAction', 'cancel');
set(handles.sliderV, 'BusyAction', 'cancel');
set(handles.pushbutton_call_Cidre, 'BusyAction', 'cancel');
set(handles.pushbutton_call_Cidre, 'Interruptible', 'off');
set(handles.text_processing, 'Visible', 'off');


set(handles.sliderV, 'Value', handles.lambdaV);
set(handles.editV, 'String', handles.lambdaV);
set(handles.sliderZ, 'Value', handles.lambdaZ);
set(handles.editZ, 'String', handles.lambdaZ);
set(handles.edit_max_iter, 'String', handles.lbfgs_iterations);
set(handles.popupmenu2, 'String', {'auto', '8-bit', '12-bit', '16-bit'});
set(handles.edit_Q, 'String', handles.q_percent);
set(handles.popupmenu_mode, 'String', {'0 zero-light preserved', '1 dynamic range corrected', '2 direct'});
set(handles.edit_destination_folder, 'String', []);
if ispc()
    filestring = [pwd '\*.tif'];
else
    filestring = [pwd '/*.tif'];
end
set(handles.edit_source_folder, 'String', filestring);

% Update handles structure
guidata(hObject, handles);

% UIWAIT makes cidreGui wait for user response (see UIRESUME)
% uiwait(handles.figure1);





% --- Outputs from this function are returned to the command line.
function varargout = cidreGui_OutputFcn(hObject, eventdata, handles) 
% varargout  cell array for returning output args (see VARARGOUT);
% hObject    handle to figure
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Get default command line output from handles structure
varargout{1} = handles.output;


% --- Executes on slider movement.
function sliderV_Callback(hObject, eventdata, handles)
% hObject    handle to sliderV (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'Value') returns position of slider
%        get(hObject,'Min') and get(hObject,'Max') to determine range of slider

handles.lambdaV = get(hObject, 'Value');
handles.lambdaV = str2double(sprintf('%1.2f', handles.lambdaV));
set(handles.editV, 'String', handles.lambdaV);
guidata(hObject, handles);


% --- Executes during object creation, after setting all properties.
function sliderV_CreateFcn(hObject, eventdata, handles)
% hObject    handle to sliderV (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: slider controls usually have a light gray background.
if isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor',[.9 .9 .9]);
end







function editV_Callback(hObject, eventdata, handles)
% hObject    handle to editV (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of editV as text
%        str2double(get(hObject,'String')) returns contents of editV as a double

handles.lambdaV = str2double(get(hObject, 'String'));
handles.lambdaV = str2double(sprintf('%1.2f', handles.lambdaV));
set(handles.sliderV, 'Value', handles.lambdaV);
guidata(hObject, handles);


% --- Executes during object creation, after setting all properties.
function editV_CreateFcn(hObject, eventdata, handles)
% hObject    handle to editV (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end


% --- Executes on slider movement.
function sliderZ_Callback(hObject, eventdata, handles)
% hObject    handle to sliderZ (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'Value') returns position of slider
%        get(hObject,'Min') and get(hObject,'Max') to determine range of slider

handles.lambdaZ = get(hObject, 'Value');
handles.lambdaZ = str2double(sprintf('%1.2f', handles.lambdaZ));
set(handles.editZ, 'String', handles.lambdaZ);
guidata(hObject, handles);



% --- Executes during object creation, after setting all properties.
function sliderZ_CreateFcn(hObject, eventdata, handles)
% hObject    handle to sliderZ (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: slider controls usually have a light gray background.
if isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor',[.9 .9 .9]);
end



function editZ_Callback(hObject, eventdata, handles)
% hObject    handle to editZ (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of editZ as text
%        str2double(get(hObject,'String')) returns contents of editZ as a double

handles.lambdaZ = str2double(get(hObject, 'String'));
handles.lambdaZ = str2double(sprintf('%1.2f', handles.lambdaZ));
set(handles.sliderZ, 'Value', handles.lambdaZ);
guidata(hObject, handles);



% --- Executes during object creation, after setting all properties.
function editZ_CreateFcn(hObject, eventdata, handles)
% hObject    handle to editZ (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end



function edit_max_iter_Callback(hObject, eventdata, handles)
% hObject    handle to edit_max_iter (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of edit_max_iter as text
%        str2double(get(hObject,'String')) returns contents of edit_max_iter as a double

handles.lbfgs_iterations = str2double(get(hObject, 'String'));
handles.lbfgs_iterations = round(handles.lbfgs_iterations);
set(hObject, 'String', handles.lbfgs_iterations);
guidata(hObject, handles);


% --- Executes during object creation, after setting all properties.
function edit_max_iter_CreateFcn(hObject, eventdata, handles)
% hObject    handle to edit_max_iter (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end


% --- Executes on selection change in popupmenu2.
function popupmenu2_Callback(hObject, eventdata, handles)
% hObject    handle to popupmenu2 (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: contents = cellstr(get(hObject,'String')) returns popupmenu2 contents as cell array
%        contents{get(hObject,'Value')} returns selected item from popupmenu2

contents = cellstr(get(hObject,'String'));

switch contents{get(hObject,'Value')}
    case 'auto'
        handles.bit_depth = [];
    case '8-bit'
        handles.bit_depth = 8;
    case '12-bit'
        handles.bit_depth = 12;
    case '16-bit'
        handles.bit_depth = 16;
end
guidata(hObject, handles);


% --- Executes during object creation, after setting all properties.
function popupmenu2_CreateFcn(hObject, eventdata, handles)
% hObject    handle to popupmenu2 (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: popupmenu controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end



function edit_Q_Callback(hObject, eventdata, handles)
% hObject    handle to edit_Q (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of edit_Q as text
%        str2double(get(hObject,'String')) returns contents of edit_Q as a double

handles.q_percent = str2double(get(hObject, 'String'));
handles.q_percent = str2double(sprintf('%1.2f', handles.q_percent));
set(hObject, 'String', handles.q_percent);
guidata(hObject, handles);



% --- Executes during object creation, after setting all properties.
function edit_Q_CreateFcn(hObject, eventdata, handles)
% hObject    handle to edit_Q (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end


% --- Executes on selection change in popupmenu_mode.
function popupmenu_mode_Callback(hObject, eventdata, handles)
% hObject    handle to popupmenu_mode (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: contents = cellstr(get(hObject,'String')) returns popupmenu_mode contents as cell array
%        contents{get(hObject,'Value')} returns selected item from popupmenu_mode

contents = cellstr(get(hObject,'String'));

switch contents{get(hObject,'Value')}
    case '0 zero-light preserved'
        handles.correction_mode = 0;
    case '1 dynamic range corrected'
        handles.correction_mode = 1;
    case '2 direct'
        handles.correction_mode = 2;
end
guidata(hObject, handles);


% --- Executes during object creation, after setting all properties.
function popupmenu_mode_CreateFcn(hObject, eventdata, handles)
% hObject    handle to popupmenu_mode (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: popupmenu controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end



function edit_minZ_Callback(hObject, eventdata, handles)
% hObject    handle to edit_minZ (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of edit_minZ as text
%        str2double(get(hObject,'String')) returns contents of edit_minZ as a double

handles.Zlimits(1) = str2double(get(hObject,'String'));
guidata(hObject, handles);



% --- Executes during object creation, after setting all properties.
function edit_minZ_CreateFcn(hObject, eventdata, handles)
% hObject    handle to edit_minZ (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end



function edit_maxZ_Callback(hObject, eventdata, handles)
% hObject    handle to edit_maxZ (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of edit_maxZ as text
%        str2double(get(hObject,'String')) returns contents of edit_maxZ as a double

handles.Zlimits(2) = str2double(get(hObject,'String'));
guidata(hObject, handles);


% --- Executes during object creation, after setting all properties.
function edit_maxZ_CreateFcn(hObject, eventdata, handles)
% hObject    handle to edit_maxZ (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end



function edit_source_folder_Callback(hObject, eventdata, handles)
% hObject    handle to edit_source_folder (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of edit_source_folder as text
%        str2double(get(hObject,'String')) returns contents of edit_source_folder as a double

dname = get(hObject,'String');
if ~exist(dname, 'dir')
    warning('cidreGui:invalid folder specified');
else
    set(handles.edit_source_folder, 'String', dname);
    handles.source_folder = dname;
    guidata(hObject, handles);
end

% --- Executes during object creation, after setting all properties.
function edit_source_folder_CreateFcn(hObject, eventdata, handles)
% hObject    handle to edit_source_folder (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end


% --- Executes on button press in pushbutton_source_folder.
function pushbutton_source_folder_Callback(hObject, eventdata, handles)
% hObject    handle to pushbutton_source_folder (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

[pth name ext] = fileparts(get(handles.edit_source_folder,'String'));

dname = uigetdir(pth);
if ispc()
    filestring = [dname '\*.tif'];
else
    filestring = [dname '/*.tif'];
end
set(handles.edit_source_folder, 'String', filestring);
handles.source_folder = filestring;
guidata(hObject, handles);




function edit_destination_folder_Callback(hObject, eventdata, handles)
% hObject    handle to edit_destination_folder (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

% Hints: get(hObject,'String') returns contents of edit_destination_folder as text
%        str2double(get(hObject,'String')) returns contents of edit_destination_folder as a double

dname = get(hObject,'String');
if ~exist(dname, 'dir')
    warning('cidreGui:invalid folder specified');
else
    set(handles.edit_destination_folder, 'String', dname);
    handles.destination_folder = dname;
    guidata(hObject, handles);
end


% --- Executes during object creation, after setting all properties.
function edit_destination_folder_CreateFcn(hObject, eventdata, handles)
% hObject    handle to edit_destination_folder (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    empty - handles not created until after all CreateFcns called

% Hint: edit controls usually have a white background on Windows.
%       See ISPC and COMPUTER.
if ispc && isequal(get(hObject,'BackgroundColor'), get(0,'defaultUicontrolBackgroundColor'))
    set(hObject,'BackgroundColor','white');
end




% --- Executes on button press in pushbutton_destination_folder.
function pushbutton_destination_folder_Callback(hObject, eventdata, handles)
% hObject    handle to pushbutton_destination_folder (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)

dname = uigetdir(get(handles.edit_destination_folder,'String'));
set(handles.edit_destination_folder, 'String', dname);
handles.destination_folder = dname;
guidata(hObject, handles);





% --- Executes on button press in pushbutton_call_Cidre.
function pushbutton_call_Cidre_Callback(hObject, eventdata, handles)
% hObject    handle to pushbutton_call_Cidre (see GCBO)
% eventdata  reserved - to be defined in a future version of MATLAB
% handles    structure with handles and user data (see GUIDATA)



param_str = {};

if ~isempty(handles.destination_folder)
    param_str{end+1} = 'destination';
    param_str{end+1} = handles.destination_folder;
end

if ~isempty(handles.lambdaV)
    param_str{end+1} = 'lambda_v';
    param_str{end+1} = handles.lambdaV;
end
if ~isempty(handles.lambdaZ)
    param_str{end+1} = 'lambda_z';
    param_str{end+1} = handles.lambdaZ;
end
if ~isempty(handles.Zlimits)
    if handles.Zlimits(2) <= handles.Zlimits(1)
        warning('max Z should be greater than Min Z');
    else
        param_str{end+1} = 'z_limits';
        param_str{end+1} = handles.Zlimits;
    end
end
if ~isempty(handles.bit_depth)
    param_str{end+1} = 'bit_depth';
    param_str{end+1} = handles.bit_depth;
end
if ~isempty(handles.q_percent)
    param_str{end+1} = 'q_percent';
    param_str{end+1} = handles.q_percent;
end
if ~isempty(handles.lbfgs_iterations)
    param_str{end+1} = 'max_lbfgs_iterations';
    param_str{end+1} = handles.lbfgs_iterations;
end
if ~isempty(handles.correction_mode)
    param_str{end+1} = 'correction_mode';
    param_str{end+1} = handles.correction_mode;
end
param_str{end+1} = handles;



model = cidre(handles.source_folder, param_str);



guidata(hObject, handles);



% 185 187 confocal

