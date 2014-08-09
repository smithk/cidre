function cdr_gui_toggle(options)

if isstruct(options.handles)
    handles = options.handles;
    
    state = get(handles.pushbutton_source_folder, 'Visible');
    
    if strcmpi(state,'on')
        handles = options.handles;
        set(handles.pushbutton_source_folder, 'Visible', 'off');
        set(handles.edit_source_folder, 'Visible', 'off');
        set(handles.pushbutton_destination_folder, 'Visible', 'off');
        set(handles.pushbutton_destination_folder, 'Visible', 'off');
        set(handles.edit_destination_folder, 'Visible', 'off');
        set(handles.pushbutton_source_folder, 'Visible', 'off');
        set(handles.edit_source_folder, 'Visible', 'off');
        set(handles.text16, 'Visible', 'off');
        set(handles.text15, 'Visible', 'off');
        set(handles.text14, 'Visible', 'off');
        set(handles.edit_maxZ, 'Visible', 'off');
        set(handles.edit_minZ, 'Visible', 'off');
        set(handles.text9, 'Visible', 'off');
        set(handles.text13, 'Visible', 'off');
        set(handles.edit_max_iter, 'Visible', 'off');
        set(handles.text8, 'Visible', 'off');
        set(handles.text6, 'Visible', 'off');
        set(handles.popupmenu2, 'Visible', 'off');
        set(handles.edit_Q, 'Visible', 'off');
        set(handles.text11, 'Visible', 'off');
        set(handles.popupmenu_mode, 'Visible', 'off');
        set(handles.editZ, 'Visible', 'off');
        set(handles.text4, 'Visible', 'off');
        set(handles.text3, 'Visible', 'off');
        set(handles.sliderZ, 'Visible', 'off');
        set(handles.editV, 'Visible', 'off');
        set(handles.text2, 'Visible', 'off');
        set(handles.text1, 'Visible', 'off');
        set(handles.sliderV, 'Visible', 'off');
        set(handles.pushbutton_call_Cidre, 'Visible', 'off');
        set(handles.text_processing, 'Visible', 'on');        
        guidata(handles.figure1, handles);
        drawnow;
    else
        set(handles.pushbutton_source_folder, 'Visible', 'on');
        set(handles.edit_source_folder, 'Visible', 'on');
        set(handles.pushbutton_destination_folder, 'Visible', 'on');
        set(handles.pushbutton_destination_folder, 'Visible', 'on');
        set(handles.edit_destination_folder, 'Visible', 'on');
        set(handles.pushbutton_source_folder, 'Visible', 'on');
        set(handles.edit_source_folder, 'Visible', 'on');
        set(handles.text16, 'Visible', 'on');
        set(handles.text15, 'Visible', 'on');
        set(handles.text14, 'Visible', 'on');
        set(handles.edit_maxZ, 'Visible', 'on');
        set(handles.edit_minZ, 'Visible', 'on');
        set(handles.text9, 'Visible', 'on');
        set(handles.text13, 'Visible', 'on');
        set(handles.edit_max_iter, 'Visible', 'on');
        set(handles.text8, 'Visible', 'on');
        set(handles.text6, 'Visible', 'on');
        set(handles.popupmenu2, 'Visible', 'on');
        set(handles.edit_Q, 'Visible', 'on');
        set(handles.text11, 'Visible', 'on');
        set(handles.popupmenu_mode, 'Visible', 'on');
        set(handles.editZ, 'Visible', 'on');
        set(handles.text4, 'Visible', 'on');
        set(handles.text3, 'Visible', 'on');
        set(handles.sliderZ, 'Visible', 'on');
        set(handles.editV, 'Visible', 'on');
        set(handles.text2, 'Visible', 'on');
        set(handles.text1, 'Visible', 'on');
        set(handles.sliderV, 'Visible', 'on');
        set(handles.pushbutton_call_Cidre, 'Visible', 'on');
        set(handles.text_processing, 'Visible', 'off');
        set(handles.checkbox_v,'Value', 0);
        set(handles.checkbox_z,'Value', 0);
        guidata(handles.figure1, handles);
        drawnow;
    end
end