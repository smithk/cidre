// From the CIDRE project, an illumination correction method for optical
// microscopy (https://github.com/smithk/cidre).
// Copyright © 2015 Kevin Smith and Peter Horvath, adapted in Java by 
// Csaba Balazs. Scientific Center for Optical and Electron Microscopy 
// (SCOPEM), Swiss Federal Institute of Technology Zurich (ETH Zurich), 
// Switzerland. All rights reserved.
//
// CIDRE is free software; you can redistribute it and/or modify it 
// under the terms of the GNU General Public License version 2 (or higher) 
// as published by the Free Software Foundation. See the license file in
// the root folder. This program is distributed WITHOUT ANY WARRANTY; 
// without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
// PARTICULAR PURPOSE.  See the GNU General Public License for more details.

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.plugin.frame.PasteController;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollBar;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;

public class Cidre_Plugin extends JFrame implements PlugIn, ActionListener, AdjustmentListener, ItemListener {

	private static final long serialVersionUID = 1L;
	private static JFrame instance;

	private enum Mestimator { LS, CAUCHY };
	
	private List<double[][]> S = new ArrayList<double[][]>();
	private int S_C;	// WIDTH
	private int S_R;	// HEIGHT
	
	private double[] Q;

	private CidreModel model = null;
	
	private final int lambdaRScaleFactor = 10;
	private final int lambdaRMinValue = 0;
	private final int lambdaRMaxValue = 9;
	private final double lambdaRDefaultValue = 6;

	private final int lambdaZScaleFactor = 10;
	private final int lambdaZMinValue = -2;
	private final int lambdaZMaxValue = 5;
	private final double lambdaZDefaultValue = 0.5;
	
	// cdr_cidreModel and cdr_objective shared objects
	private double CAUCHY_W;		// width of the Cauchy function used for robust regression 
	private double PivotShiftX;		// shift on the Q axis into the pivot space 
	private double[] PivotShiftY;	// shift on the q axis into the pivot space 
	private int ITER;				// iteration count for the optimization
	private Mestimator MESTIMATOR;	// specifies "CAUCHY" or "LS" (least squares) 
	private int TERMSFLAG;			// flag specifing which terms to include in the energy function 
	private double LAMBDA_VREG;		// coefficient for the v regularization 
	private double LAMBDA_ZERO;		// coefficient for the zero-light term
	private double ZMIN;			// minimum possible value for Z
	private double ZMAX;			// maximum possible value for Z
	private double STACKMIN;

	// GUI
	
	//private Font normalFont = new Font("Dialog", Font.PLAIN, 14);
	private Font headerFont = new Font("Dialog", Font.BOLD, 16);
	private Border greenBorder = new LineBorder(new Color(0, 192, 0), 2);

	private JLabel directoryModelLabel;
	
	private JLabel sourceImagesLabel;
	private JTextField sourceImagesTextField;
	private JButton sourceImagesButton;
	
	private JLabel sourceImageMaskLabel;
	private JTextField sourceImageMaskTextField;

	private JLabel destinationImagesLabel;
	private JTextField destinationImagesTextField;
	private JButton destinationImagesButton;

	private JLabel buildModelLabel;

	private JLabel lambdaRLabel;
	private JScrollBar lambdaRScrollbar;
	private JTextField lambdaRTextField;
	private JCheckBox lambdaRCheckbox;
	private JLabel lambdaRMinLabel;
	private JLabel lambdaRMaxLabel;

	private JLabel lambdaZLabel;
	private JScrollBar lambdaZScrollbar;
	private JTextField lambdaZTextField;
	private JCheckBox lambdaZCheckbox;
	private JLabel lambdaZMinLabel;
	private JLabel lambdaZMaxLabel;
	
	private JLabel darkFrameLabel;
	private JLabel darkFrameZMinLabel;
	private JTextField darkFrameZMinTextField;
	private JLabel darkFrameZMaxLabel;
	private JTextField darkFrameZMaxTextField;	
	
	private JButton buildButton;

	private JLabel loadModelLabel;
	private JLabel correctionModelLabel;
	private JTextField correctionModelTextField;
	private JButton correctionModelButton;
	private JButton loadButton;

	private JLabel correctImagesLabel;
	private ButtonGroup correctCheckboxGroup;	
	private JCheckBox correctCheckBoxZeroLightPreserved;
	private JCheckBox correctCheckBoxDynamicRangeCorrected;
	private JCheckBox correctCheckBoxDirect;
	private JButton correctButton;
	
	private class ImageNameFilter implements FilenameFilter {
		private Pattern pattern;

		public ImageNameFilter(String expression) {
			String correctedExpression = ".*";
			if (expression != null && expression != "") {
				correctedExpression = expression.replace(".", "\\.");
				correctedExpression = correctedExpression.replace("*", ".*");
			}
			pattern = Pattern.compile(correctedExpression, Pattern.CASE_INSENSITIVE);
		}

		@Override
		public boolean accept(File dir, String name) {
			return pattern.matcher(new File(name).getName()).matches();
		}
	}
	
	// Helper functions
	/*private String getExtension(String fileName)
	{
		int i = fileName.lastIndexOf('.');
		int p = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));

		if (i > p) {
		    return fileName.substring(i+1);
		}
		return "";
	}*/

	private double mean(double[] a) {
		int i;
		double sum = 0;
	    for (i = 0; i < a.length; i++) {
	        sum += a[i];
	    }
	    return sum / a.length;
	}

	// determines a working image size based on the original image size and
	// the desired number of pixels in the working image, N_desired 
	public Dimension determineWorkingSize(Dimension imageSize, int Ndesired) 
	{
		int widthOriginal = imageSize.width;
		int heightOriginal = imageSize.height;
		
		double scaleWorking = Math.sqrt((double)Ndesired / (widthOriginal * heightOriginal));
		
		return new Dimension((int)Math.round(widthOriginal * scaleWorking), (int)Math.round(heightOriginal * scaleWorking));
	}

	public Cidre_Plugin()
	{
		super("Cidre Plugin");
		
		// Init and add frame
		WindowManager.addWindow(this);
		instance = this;
		IJ.register(PasteController.class);
	}
	
	private void GUICreate()
	{
		// Add the GUI components to the main frame 
		setSize(620, 660);
		setLayout(null);
		{
			directoryModelLabel = new JLabel();
			directoryModelLabel.setBounds(30, 10, 300, 20);
			directoryModelLabel.setText("Directories");
			directoryModelLabel.setFont(headerFont);
			add(directoryModelLabel);
		}		
		{
			sourceImagesLabel = new JLabel();
			sourceImagesLabel.setBounds(30, 40, 150, 20);
			sourceImagesLabel.setText("Source images");
			add(sourceImagesLabel);
		}
		{
			sourceImagesTextField = new JTextField("", 250);
			sourceImagesTextField.setBounds(180, 40, 270, 20);
			sourceImagesTextField.setToolTipText("The Source Image Directory.");
			add(sourceImagesTextField);
		}
		{
			sourceImagesButton = new JButton("Browse");
			sourceImagesButton.setBounds(460, 38, 120, 25);
			sourceImagesButton.addActionListener(this);
			sourceImagesButton.setToolTipText("Browse the Source Image Directory.");
			add(sourceImagesButton);
		}
		{
			sourceImageMaskLabel = new JLabel();
			sourceImageMaskLabel.setBounds(30, 70, 150, 20);
			sourceImageMaskLabel.setText("Source image mask");
			add(sourceImageMaskLabel);						
		}
		{
			sourceImageMaskTextField = new JTextField("*.tif", 200);
			sourceImageMaskTextField.setBounds(180, 70, 180, 20);
			sourceImageMaskTextField.setToolTipText("The Source Image Mask.");
			add(sourceImageMaskTextField);
		}
		{
			destinationImagesLabel = new JLabel();
			destinationImagesLabel.setBounds(30, 110, 150, 20);
			destinationImagesLabel.setText("Destination images");
			add(destinationImagesLabel);
		}
		{
			destinationImagesTextField = new JTextField("", 250);
			destinationImagesTextField.setBounds(180, 110, 270, 20);
			destinationImagesTextField.setToolTipText("The Destination Image Directory.");
			add(destinationImagesTextField);
		}
		{
			destinationImagesButton = new JButton("Browse");
			destinationImagesButton.setBounds(460, 108, 120, 25);
			destinationImagesButton.addActionListener(this);
			destinationImagesButton.setToolTipText("Browse the Destination Image Directory.");
			add(destinationImagesButton);
		}
		{
			JTextField separator = new JTextField("", 0);
			separator.setBounds(10, 150, 590, 2);
			separator.setEnabled(false);
			add(separator);
		}		
		{
			buildModelLabel = new JLabel();
			buildModelLabel.setBounds(30, 160, 300, 20);
			buildModelLabel.setText("Build a correction model");
			buildModelLabel.setFont(headerFont);
			add(buildModelLabel);
		}		
		{
			lambdaRLabel = new JLabel("Gain regularization (\u03BBv)");
			lambdaRLabel.setBounds(30, 190, 230, 20);
			add(lambdaRLabel);
		}
		{
			lambdaRScrollbar = new JScrollBar(JScrollBar.HORIZONTAL, (int)(lambdaRDefaultValue * lambdaRScaleFactor), 1, lambdaRMinValue * lambdaRScaleFactor, lambdaRMaxValue * lambdaRScaleFactor + 1);
			lambdaRScrollbar.setBounds(260, 190, 200, 20);
			lambdaRScrollbar.addAdjustmentListener(this);
			add(lambdaRScrollbar);
		}
		{
			lambdaRTextField = new JTextField("", 50);
			lambdaRTextField.setBounds(530, 190, 50, 20);
			lambdaRTextField.setHorizontalAlignment(JLabel.CENTER);
			lambdaRTextField.setEditable(false);
			add(lambdaRTextField);
		}
		{
			lambdaRCheckbox = new JCheckBox("Auto", true);
			lambdaRCheckbox.setBounds(470, 190, 60, 16);
			lambdaRCheckbox.addItemListener(this);
			add(lambdaRCheckbox);
		}
		{
			lambdaRMinLabel = new JLabel("" + lambdaRMinValue);
			lambdaRMinLabel.setBounds(265, 210, 20, 16);
			add(lambdaRMinLabel);
		}
		{
			lambdaRMaxLabel = new JLabel("" + lambdaRMaxValue);
			lambdaRMaxLabel.setBounds(445, 210, 20, 16);
			add(lambdaRMaxLabel);
		}
		{
			lambdaZLabel = new JLabel("Zero-light regularization (\u03BBz)");
			lambdaZLabel.setBounds(30, 230, 230, 25);
			add(lambdaZLabel);
		}
		{
			lambdaZScrollbar = new JScrollBar(JScrollBar.HORIZONTAL, (int)(lambdaZDefaultValue * lambdaZScaleFactor), 1, lambdaZMinValue * lambdaZScaleFactor, lambdaZMaxValue * lambdaZScaleFactor + 1);
			lambdaZScrollbar.setBounds(260, 230, 200, 20);
			lambdaZScrollbar.addAdjustmentListener(this);
			add(lambdaZScrollbar);
		}
		{
			lambdaZTextField = new JTextField("", 50);
			lambdaZTextField.setBounds(530, 230, 50, 20);
			lambdaZTextField.setHorizontalAlignment(JLabel.CENTER);
			lambdaZTextField.setEditable(false);
			add(lambdaZTextField);
		}
		{
			lambdaZCheckbox = new JCheckBox("Auto", true);
			lambdaZCheckbox.setBounds(470, 230, 60, 16);
			lambdaZCheckbox.addItemListener(this);
			add(lambdaZCheckbox);
		}
		{
			lambdaZMinLabel = new JLabel("" + lambdaZMinValue);
			lambdaZMinLabel.setBounds(265, 250, 20, 16);
			add(lambdaZMinLabel);
		}
		{
			lambdaZMaxLabel = new JLabel("" + lambdaZMaxValue);
			lambdaZMaxLabel.setBounds(445, 250, 20, 16);
			add(lambdaZMaxLabel);
		}
		{
			darkFrameLabel = new JLabel("Dark frame (z) limits");
			darkFrameLabel.setBounds(30, 280, 230, 25);
			add(darkFrameLabel);
		}
		{
			darkFrameZMinLabel = new JLabel("Min:");
			darkFrameZMinLabel.setBounds(260, 280, 30, 20);
			add(darkFrameZMinLabel);
		}
		{
			darkFrameZMinTextField = new JTextField(3);
			darkFrameZMinTextField.setBounds(300, 280, 50, 20);
			darkFrameZMinTextField.setToolTipText("The Dark frame MIN value.");
			add(darkFrameZMinTextField);		
		}
		{
			darkFrameZMaxLabel = new JLabel("Max:");
			darkFrameZMaxLabel.setBounds(360, 280, 30, 20);
			add(darkFrameZMaxLabel);
		}
		{
			darkFrameZMaxTextField = new JTextField(3);
			darkFrameZMaxTextField.setBounds(400, 280, 50, 20);
			darkFrameZMaxTextField.setToolTipText("The Dark frame MAX value.");
			add(darkFrameZMaxTextField);		
		}
		{
			buildButton = new JButton("Build");
			buildButton.setBounds(250, 320, 120, 25);
			buildButton.addActionListener(this);
			buildButton.setToolTipText("Build the Correction Model.");
			buildButton.setBorder(greenBorder);
			add(buildButton);
		}
		{
			JTextField separator = new JTextField("", 0);
			separator.setBounds(10, 360, 590, 2);
			separator.setEnabled(false);
			add(separator);
		}
		{
			loadModelLabel = new JLabel();
			loadModelLabel.setBounds(30, 370, 300, 20);
			loadModelLabel.setText("Load a correction model");
			loadModelLabel.setFont(headerFont);
			add(loadModelLabel);
		}
		{
			correctionModelLabel = new JLabel();
			correctionModelLabel.setBounds(30, 410, 150, 16);
			correctionModelLabel.setText("Correction model");
			add(correctionModelLabel);
		}
		{
			correctionModelTextField = new JTextField("", 100);
			correctionModelTextField.setBounds(180, 410, 270, 20);
			correctionModelTextField.setToolTipText("The Correction Model Directory.");
			add(correctionModelTextField);
		}
		{
			correctionModelButton = new JButton("Browse");
			correctionModelButton.setBounds(460, 408, 120, 25);
			correctionModelButton.addActionListener(this);
			correctionModelButton.setToolTipText("Browse the Correction Model Directory.");
			add(correctionModelButton);
		}
		{
			loadButton = new JButton("Load Model");
			loadButton.setBounds(250, 450, 120, 25);
			loadButton.addActionListener(this);
			loadButton.setToolTipText("Load the Correction Model from Directory.");
			add(loadButton);
		}
		{
			JTextField separator = new JTextField("", 0);
			separator.setBounds(10, 490, 590, 2);
			separator.setEnabled(false);
			add(separator);
		}
		{
			correctImagesLabel = new JLabel();
			correctImagesLabel.setBounds(30, 500, 300, 20);
			correctImagesLabel.setText("Correct images");
			correctImagesLabel.setFont(headerFont);
			add(correctImagesLabel);
		}
		{
			correctCheckboxGroup = new ButtonGroup();
			correctCheckBoxZeroLightPreserved = new JCheckBox("Zero-light preserved", true);
			correctCheckBoxZeroLightPreserved.setBounds(50, 530, 250, 16);
			correctCheckBoxDynamicRangeCorrected = new JCheckBox("Dynamic range corrected", false);
			correctCheckBoxDynamicRangeCorrected.setBounds(50, 555, 250, 16);
			correctCheckBoxDirect = new JCheckBox("Direct", false);
			correctCheckBoxDirect.setBounds(50, 580, 250, 16);

			correctCheckboxGroup.add(correctCheckBoxZeroLightPreserved);
			correctCheckboxGroup.add(correctCheckBoxDynamicRangeCorrected);
			correctCheckboxGroup.add(correctCheckBoxDirect);

			add(correctCheckBoxZeroLightPreserved);
			add(correctCheckBoxDynamicRangeCorrected);
			add(correctCheckBoxDirect);
		}
		{
			correctButton = new JButton("Correct");
			correctButton.setBounds(460, 538, 120, 25);
			correctButton.addActionListener(this);
			correctButton.setToolTipText("Correct");
			correctButton.setBorder(greenBorder);
			add(correctButton);
		}

		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent event) {
				// Disable quit if build, correct is in the backgroud
				if (sourceImagesButton.isEnabled()) {
					setVisible(false);
					
					WindowManager.removeWindow(instance);
					instance.dispose();
					dispose();
				}
			}
		});
		
		setVisible(true);

		// Initial GUI update
		GUIDoUpdate();
	}
	
//region GUI EVENT Handling

	@Override
	public void actionPerformed(final ActionEvent e) {
		if (e.getSource() == sourceImagesButton) {
		    sourceImagesTextField.setText(GUIDirectoryChooser("Source"));
		} else if (e.getSource() == destinationImagesButton) {
		    destinationImagesTextField.setText(GUIDirectoryChooser("Destination"));
		} else if (e.getSource() == correctionModelButton) {
			correctionModelTextField.setText(GUIDirectoryChooser("Correction Model"));
		} else if (e.getSource() == buildButton) {
			GUIBuildModel();
		} else if (e.getSource() == loadButton) {
			GUILoadModel();
		} else if (e.getSource() == correctButton) {
			GUICorrectImages();
		}
	}

	@Override
	public void adjustmentValueChanged(AdjustmentEvent e) {
		GUIDoUpdate();
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		GUIDoUpdate();
	}	
//endregion

//region GUI functions
	private String GUIDirectoryChooser(String directoryType) {
		JFileChooser chooser = new JFileChooser(); 
	    chooser.setCurrentDirectory(new java.io.File("."));
	    chooser.setDialogTitle(directoryType + " directory");
	    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
	    chooser.setAcceptAllFileFilterUsed(false);
	    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) { 
	    	return chooser.getSelectedFile().getAbsolutePath();
	    } else {
	    	return "";
	    }		
	}

	private void GUIDoUpdate() {
		lambdaRTextField.setText("" + ((double)lambdaRScrollbar.getValue() / lambdaRScaleFactor)); 
		lambdaZTextField.setText("" + ((double)lambdaZScrollbar.getValue() / lambdaZScaleFactor));

		lambdaRTextField.setEnabled(!lambdaRCheckbox.isSelected());
		lambdaZTextField.setEnabled(!lambdaZCheckbox.isSelected());
		lambdaRTextField.setVisible(!lambdaRCheckbox.isSelected());
		lambdaZTextField.setVisible(!lambdaZCheckbox.isSelected());
		
		lambdaRScrollbar.setEnabled(!lambdaRCheckbox.isSelected());
		lambdaZScrollbar.setEnabled(!lambdaZCheckbox.isSelected());
	}
	
	private void GUIEnableComponents() {
		sourceImagesButton.setEnabled(true);
		sourceImagesTextField.setEnabled(true);
		sourceImageMaskTextField.setEnabled(true);
		destinationImagesButton.setEnabled(true);
		destinationImagesTextField.setEnabled(true);
		lambdaRCheckbox.setEnabled(true);
		lambdaRScrollbar.setEnabled(true);
		lambdaZCheckbox.setEnabled(true);
		lambdaZScrollbar.setEnabled(true);
		darkFrameZMinTextField.setEnabled(true);
		darkFrameZMaxTextField.setEnabled(true);
		buildButton.setEnabled(true);
		correctionModelTextField.setEnabled(true);
		correctionModelButton.setEnabled(true);
		loadButton.setEnabled(true);
		correctButton.setEnabled(true);

		// Set batch processing mode
		ij.macro.Interpreter.batchMode = false;
		
		GUIDoUpdate();
	}
	
	private void GUIDisableComponents() {
		sourceImagesButton.setEnabled(false);
		sourceImagesTextField.setEnabled(false);
		sourceImageMaskTextField.setEnabled(false);
		destinationImagesButton.setEnabled(false);
		destinationImagesTextField.setEnabled(false);
		lambdaRCheckbox.setEnabled(false);
		lambdaRScrollbar.setEnabled(false);
		lambdaZCheckbox.setEnabled(false);
		lambdaZScrollbar.setEnabled(false);
		darkFrameZMinTextField.setEnabled(false);
		darkFrameZMaxTextField.setEnabled(false);
		buildButton.setEnabled(false);
		correctionModelTextField.setEnabled(false);
		correctionModelButton.setEnabled(false);
		loadButton.setEnabled(false);
		correctButton.setEnabled(false);
		
		// Set batch processing mode
		ij.macro.Interpreter.batchMode = true;
		
		GUIDoUpdate();
	}

	private void GUIBuildModel() {
		if (sourceImagesTextField.getText().isEmpty() 
				|| destinationImagesTextField.getText().isEmpty() 
				|| sourceImagesTextField.getText() == destinationImagesTextField.getText())	{
			IJ.log("Please specify the Source and Destination directories. Those should be different!");
			return;
		}
		
		Double zMin = null;
		Double zMax = null;
		if (!darkFrameZMinTextField.getText().isEmpty() || !darkFrameZMaxTextField.getText().isEmpty()) {
			try {
				zMin = Double.parseDouble(darkFrameZMinTextField.getText());
				zMax = Double.parseDouble(darkFrameZMaxTextField.getText());
			} catch (Exception e) {
				IJ.log("Dark frame Min and Max values must be numbers!");
				return;				
			}
			if (zMax < zMin) {
				IJ.log("Dark frame Min value must be smaller or equal to Max value!");
				return;
			}
		}
			
		GUIDisableComponents();

		final CidreOptions options = new CidreOptions();
			
		options.folderSource = sourceImagesTextField.getText() + File.separator;
		options.fileFilterSource = sourceImageMaskTextField.getText();
		options.folderDestination = destinationImagesTextField.getText() + File.separator;
		options.lambdaVreg = (!lambdaRCheckbox.isSelected()) ? (double)lambdaRScrollbar.getValue() / lambdaRScaleFactor : null;
		options.lambdaZero = (!lambdaZCheckbox.isSelected()) ? (double)lambdaZScrollbar.getValue() / lambdaZScaleFactor : null;
		options.zLimits[0] = zMin;
		options.zLimits[1] = zMax;
	
		new Thread()
		{
		    public void run() {
				if (!loadImages(options.folderSource, options.fileFilterSource, options)) {
					GUIEnableComponents();
					return;
				}
		
				model = cidreModel(options);

				GUIEnableComponents();
					
				IJ.log("Please press the 'Correct' button to correct the images with the built model");					
		    }
		}.start();
	}

	private void GUILoadModel() {
		if (!correctionModelTextField.getText().isEmpty())
		{
			model = null;
			
			IJ.log(String.format("Loading correction model CSV files from the Correction model folder... "));

			double[] csvArray;
			CidreModel tmpModel = new CidreModel();

			csvArray = readFromCSVFile(correctionModelTextField.getText() + File.separator + "cidre_model_imagesize.csv", 2, 1);
			if (csvArray != null) {
				tmpModel.imageSize = new Dimension((int)csvArray[0], (int)csvArray[1]);
			} else {
				IJ.log(String.format(" failed (cidre_model_imagesize.csv)"));
				return;						
			}

			csvArray = readFromCSVFile(correctionModelTextField.getText() + File.separator + "cidre_model_imagesize_small.csv", 2, 1);
			if (csvArray != null) {
				tmpModel.imageSize_small = new Dimension((int)csvArray[0], (int)csvArray[1]);
			} else {
				IJ.log(String.format(" failed (cidre_model_imagesize_small.csv)"));
				return;						
			}

			tmpModel.v = readFromCSVFile(correctionModelTextField.getText() + File.separator + "cidre_model_v.csv", tmpModel.imageSize.width, tmpModel.imageSize.height);
			if (tmpModel.v == null) {
				IJ.log(String.format(" failed (cidre_model_v.csv)"));
				return;						
			}
			
			tmpModel.z = readFromCSVFile(correctionModelTextField.getText() + File.separator + "cidre_model_z.csv", tmpModel.imageSize.width, tmpModel.imageSize.height);
			if (tmpModel.z == null) {
				IJ.log(String.format(" failed (cidre_model_z.csv)"));
				return;						
			}

			tmpModel.v_small = readFromCSVFile(correctionModelTextField.getText() + File.separator + "cidre_model_v_small.csv", tmpModel.imageSize_small.width, tmpModel.imageSize_small.height);
			if (tmpModel.v_small == null) {
				IJ.log(String.format(" failed (cidre_model_v_small.csv)"));
				return;						
			}

			tmpModel.z_small = readFromCSVFile(correctionModelTextField.getText() + File.separator + "cidre_model_z_small.csv", tmpModel.imageSize_small.width, tmpModel.imageSize_small.height);
			if (tmpModel.z_small == null) {
				IJ.log(String.format(" failed (cidre_model_z_small.csv)"));
				return;						
			}
			
			model = tmpModel;

			IJ.log(String.format(" done"));
		} else {
			IJ.log("Please specify the Model directory.");
		}
	}
	
	private void GUICorrectImages() {
		if (sourceImagesTextField.getText().isEmpty() 
				|| destinationImagesTextField.getText().isEmpty() 
				|| sourceImagesTextField.getText() == destinationImagesTextField.getText())
		{
			IJ.log("Please specify the Source and Destination directories. Those should be different!");
			return;
		}

		if (model != null) {
			GUIDisableComponents();
			new Thread()
			{
			    public void run() {
					cdr_correct(model);
					GUIEnableComponents();
			    }
			}.start();
		} else {
			IJ.log("Please 'Build' or 'Load' a Model to correct the source images");
			return;
		}
	}

//endregion	

	private boolean loadImages(String source, String fileMask, CidreOptions options)
	{
		double maxI = 0;
		S.clear();
		
		if (source != null && source != "")
		{
		    // break source into a path, filter, and extension
			File file = new File(source);
			String pth = file.getPath() + File.separator;
			//String filter = file.getName();
		    //String ext = getExtension(source);
		    
		    // store the source path in the options structure
		    options.folderSource = pth;    
	    
		   	// generate a list of source filenames searching for all valid filetypes
	    	File folder = new File(pth);
	    	File[] listOfFiles = folder.listFiles(new ImageNameFilter(fileMask)); 

	    	if (listOfFiles != null) {
		    	for (int i = 0; i < listOfFiles.length; i++) {
					options.fileNames.add(listOfFiles[i].getName());	    		
		    	}
	    	}

	    	// store the number of source images into the options structure
	    	options.numImagesProvided = options.fileNames.size();

		    // read the first provided image, check that it is monochromatic, store 
		    // its size in the options structure, and determine the working image 
		    // size we will use
			if (options.numImagesProvided <= 0) {
				IJ.error("CIDRE:loadImages", "No image file found.");
				return false;
			}

			IJ.open(options.folderSource + options.fileNames.get(0));
	    	ImagePlus imp = IJ.getImage();
	    	ImageProcessor ip = imp.getProcessor();
	    	
			if (imp.getStackSize() == 3) {
				imp.close();
				IJ.error("CIDRE:loadImages", "Non-monochromatic image provided. CIDRE is designed for monochromatic images. Store each channel as a separate image and re-run CIDRE.");
				return false;
			}
			
			options.imageSize = new Dimension(ip.getWidth(), ip.getHeight());
			options.workingSize = determineWorkingSize(options.imageSize, options.targetNumPixels);
			
			imp.close();

		    // read the source filenames in, covert them to the working image size, 
		    // and add them to the stack    
		    IJ.log(" Reading " + options.numImagesProvided + " images from " + options.folderSource + "\n .");
		    long t1 = System.currentTimeMillis();
		    
		    for (int z = 0; z < options.numImagesProvided; z++)
		    {
		        if (z > 0 && z % 100 == 0) IJ.log(".");	// progress to the command line
		    	IJ.open(options.folderSource + options.fileNames.get(z));
		    	imp = IJ.getImage();
		    	ip = imp.getProcessor();

		    	float[][] floatArray = ip.getFloatArray();
		    	
		    	double[][] doubleArray = new double[ip.getWidth()][ip.getHeight()];
		    	
		    	for (int x = 0; x < ip.getWidth(); x++) 
		    		for (int y = 0; y < ip.getHeight(); y++)
		    			doubleArray[x][y] = floatArray[x][y];

		    	double[][] Irescaled = imresize(doubleArray, options.imageSize.width, options.imageSize.height, 
		    												options.workingSize.width, options.workingSize.height);
		    	
		    	for (int x = 0; x < options.workingSize.width; x++) 
		    		for (int y = 0; y < options.workingSize.height; y++)
		    			maxI = Math.max(maxI, (int)Irescaled[x][y]);
		    	
		    	S.add(Irescaled);
		    	S_C = options.workingSize.width;
		    	S_R = options.workingSize.height;
		    	imp.close();		    	
		    }
		    long t2 = System.currentTimeMillis();
		    IJ.log(String.format("finished in %1.2fs.", (t2 - t1)/1000.0));
		} else {
			return false;
		}
		
		// apply several processing steps to the image stack S
		// Now that we have loaded the stack as an RxCxN array (where R*C ~=
		// options.targetNumPixels), we must do check if there is sufficient
		// intensity information in the stack, sort the intensity values at each
		// (x,y) image location, and compress the stack in the 3rd dimension to keep
		// the computation time manageable
		preprocessData(maxI, options);
		
		return true;
	}
	
	private double[][] imresize(double[][] doubleArray, int origWidth, int origHeight, int newWidth, int newHeight)
	{
		// Height
		double hScale = (double)newHeight / origHeight;		    		
		double kernel_width = 4.0;
		if (hScale < 1.0)
			kernel_width /= hScale;
		double[] u = new double[newHeight];
		int[] left = new int[newHeight];
		for (int j = 0; j < newHeight; j++) {
			u[j] = (j+1) / hScale + 0.5 * (1.0 - 1.0 / hScale);
			left[j] = (int)Math.floor(u[j] - kernel_width/2.0);
		}
		int P = (int)Math.ceil(kernel_width) + 2;
		int hIndices[][] = new int[P][newHeight];
		double hWeights[][] = new double[P][newHeight];
		for (int p = 0; p < P; p++) {
			for (int j = 0; j < newHeight; j++) {
				hIndices[p][j] = left[j] + p;
				if (hScale < 1.0)
					hWeights[p][j] = hScale * cubic(hScale * (u[j] - hIndices[p][j]));
				else
					hWeights[p][j] = cubic(u[j] - hIndices[p][j]);
			}	    				
		}
		// Normalize the weights matrix so that each row sums to 1.
		for (int j = 0; j < newHeight; j++) {
			double sum = 0;
			for (int p = 0; p < P; p++) {
				sum += hWeights[p][j]; 
			}
			for (int p = 0; p < P; p++) {
				hWeights[p][j] /= sum;
			}
		}    				
		// Clamp out-of-range indices; has the effect of replicating end-points.
		for (int p = 0; p < P; p++) {
			for (int j = 0; j < newHeight; j++) {
				hIndices[p][j]--;
				if (hIndices[p][j] < 0)
					hIndices[p][j] = 0;
				else if (hIndices[p][j] >= origHeight - 1)
					hIndices[p][j] = origHeight - 1;
			}
		}
		
		// resizeDimCore - height
    	double[][] doubleArrayH = new double[origWidth][newHeight];
		for(int j = 0; j < newHeight; j++) {
    		for (int p = 0; p < P; p++) {
		    	for (int i = 0; i < origWidth; i++) {
    				doubleArrayH[i][j] += (doubleArray[i][hIndices[p][j]]) * hWeights[p][j];
    			}
    		}			    		
    	}

		// Width
    	double wScale = (double)newWidth / origWidth;		    		
		kernel_width = 4.0;
		if (wScale < 1.0)
			kernel_width /= wScale;
		u = new double[newWidth];
		left = new int[newWidth];
		for (int j = 0; j < newWidth; j++) {
			u[j] = (j+1) / wScale + 0.5 * (1.0 - 1.0 / wScale);
			left[j] = (int)Math.floor(u[j] - kernel_width/2.0);
		}
		P = (int)Math.ceil(kernel_width) + 2;
		int wIndices[][] = new int[P][newWidth];
		double wWeights[][] = new double[P][newWidth];
		for (int p = 0; p < P; p++) {
			for (int j = 0; j < newWidth; j++) {
				wIndices[p][j] = left[j] + p;
				if (wScale < 1.0)
					wWeights[p][j] = wScale * cubic(wScale * (u[j] - wIndices[p][j]));
				else
					wWeights[p][j] = cubic(u[j] - wIndices[p][j]);
			}	    				
		}
		// Normalize the weights matrix so that each row sums to 1.
		for (int j = 0; j < newWidth; j++) {
			double sum = 0;
			for (int p = 0; p < P; p++) {
				sum += wWeights[p][j]; 
			}
			for (int p = 0; p < P; p++) {
				wWeights[p][j] /= sum;
			}
		}    				
		// Clamp out-of-range indices; has the effect of replicating end-points.
		for (int p = 0; p < P; p++) {
			for (int j = 0; j < newWidth; j++) {
				wIndices[p][j]--;
				if (wIndices[p][j] < 0)
					wIndices[p][j] = 0;
				else if (wIndices[p][j] >= origWidth - 1)
					wIndices[p][j] = origWidth - 1;
			}
		}
		
		// resizeDimCore - width
    	double[][] doubleArrayW = new double[newWidth][newHeight];
    	for (int i = 0; i < newWidth; i++) {
    		for (int p = 0; p < P; p++) {
    			for(int j = 0; j < newHeight; j++) {
    				doubleArrayW[i][j] += (doubleArrayH[wIndices[p][i]][j]) * wWeights[p][i];
    			}
    		}			    		
    	}
		
		return doubleArrayW;
	}

	private double[][] imresize(double[][] doubleArray, int origWidth, int origHeight, double scale)
	{
		// Height
		int newHeight = (int)Math.round(origHeight * scale);
		double kernel_width = 4.0;
		if (scale < 1.0)
			kernel_width /= scale;
		
		double[] u = new double[newHeight];
		int[] left = new int[newHeight];
		for (int j = 0; j < newHeight; j++) {
			u[j] = (j+1) / scale + 0.5 * (1.0 - 1.0 / scale);
			left[j] = (int)Math.floor(u[j] - kernel_width/2.0);
		}
		int P = (int)Math.ceil(kernel_width) + 2;
		int hIndices[][] = new int[P][newHeight];
		double hWeights[][] = new double[P][newHeight];
		for (int p = 0; p < P; p++) {
			for (int j = 0; j < newHeight; j++) {
				hIndices[p][j] = left[j] + p;
				if (scale < 1.0)
					hWeights[p][j] = scale * cubic(scale * (u[j] - hIndices[p][j]));
				else
					hWeights[p][j] = cubic(u[j] - hIndices[p][j]);

			}	    				
		}
		// Normalize the weights matrix so that each row sums to 1.
		for (int j = 0; j < newHeight; j++) {
			double sum = 0;
			for (int p = 0; p < P; p++) {
				sum += hWeights[p][j]; 
			}
			for (int p = 0; p < P; p++) {
				hWeights[p][j] /= sum;
			}
		}    				
		// Clamp out-of-range indices; has the effect of replicating end-points.
		for (int p = 0; p < P; p++) {
			for (int j = 0; j < newHeight; j++) {
				hIndices[p][j]--;
				if (hIndices[p][j] < 0)
					hIndices[p][j] = 0;
				else if (hIndices[p][j] >= origHeight - 1)
					hIndices[p][j] = origHeight - 1;
			}
		}
		
		// resizeDimCore - height
    	double[][] doubleArrayH = new double[origWidth][newHeight];
		for(int j = 0; j < newHeight; j++) {
    		for (int p = 0; p < P; p++) {
		    	for (int i = 0; i < origWidth; i++) {
    				doubleArrayH[i][j] += (doubleArray[i][hIndices[p][j]]) * hWeights[p][j];
    			}
    		}			    		
    	}

		// Width
		int newWidth = (int)Math.round(origWidth * scale);
		kernel_width = 4.0;
		if (scale < 1.0)
			kernel_width /= scale;
		u = new double[newWidth];
		left = new int[newWidth];
		for (int j = 0; j < newWidth; j++) {
			u[j] = (j+1) / scale + 0.5 * (1.0 - 1.0 / scale);
			left[j] = (int)Math.floor(u[j] - kernel_width/2.0);
		}
		P = (int)Math.ceil(kernel_width) + 2;
		int wIndices[][] = new int[P][newWidth];
		double wWeights[][] = new double[P][newWidth];
		for (int p = 0; p < P; p++) {
			for (int j = 0; j < newWidth; j++) {
				wIndices[p][j] = left[j] + p;
				if (scale < 1.0)
					wWeights[p][j] = scale * cubic(scale * (u[j] - wIndices[p][j]));
				else
					wWeights[p][j] = cubic(u[j] - wIndices[p][j]);
			}	    				
		}
		// Normalize the weights matrix so that each row sums to 1.
		for (int j = 0; j < newWidth; j++) {
			double sum = 0;
			for (int p = 0; p < P; p++) {
				sum += wWeights[p][j]; 
			}
			for (int p = 0; p < P; p++) {
				wWeights[p][j] /= sum;
			}
		}    				
		// Clamp out-of-range indices; has the effect of replicating end-points.
		for (int p = 0; p < P; p++) {
			for (int j = 0; j < newWidth; j++) {
				wIndices[p][j]--;
				if (wIndices[p][j] < 0)
					wIndices[p][j] = 0;
				else if (wIndices[p][j] >= origWidth - 1)
					wIndices[p][j] = origWidth - 1;
			}
		}
		
		// resizeDimCore - width
    	double[][] doubleArrayW = new double[newWidth][newHeight];
    	for (int i = 0; i < newWidth; i++) {
    		for (int p = 0; p < P; p++) {
    			for(int j = 0; j < newHeight; j++) {
    				doubleArrayW[i][j] += (doubleArrayH[wIndices[p][i]][j]) * wWeights[p][i];
    			}
    		}			    		
    	}
		
		return doubleArrayW;
	}

	private double[] imresize_bilinear(double[] doubleArray, int origWidth, int origHeight, int newWidth, int newHeight)
	{
		// Width
    	double wScale = (double)newWidth / origWidth;		    		
		double kernel_width = 2;
		double[] u = new double[newWidth];
		int[] left = new int[newWidth];
		for (int j = 0; j < newWidth; j++) {
			u[j] = (j+1) / wScale + 0.5 * (1.0 - 1.0 / wScale);
			left[j] = (int)Math.floor(u[j] - kernel_width/2.0);
		}
		int P = (int)Math.ceil(kernel_width) + 2;
		int wIndices[][] = new int[P][newWidth];
		double wWeights[][] = new double[P][newWidth];
		for (int p = 0; p < P; p++) {
			for (int j = 0; j < newWidth; j++) {
				wIndices[p][j] = left[j] + p;
				wWeights[p][j] = triangle(u[j] - wIndices[p][j]);
			}	    				
		}
		// Normalize the weights matrix so that each row sums to 1.
		for (int j = 0; j < newWidth; j++) {
			double sum = 0;
			for (int p = 0; p < P; p++) {
				sum += wWeights[p][j]; 
			}
			for (int p = 0; p < P; p++) {
				wWeights[p][j] /= sum;
			}
		}    				
		// Clamp out-of-range indices; has the effect of replicating end-points.
		for (int p = 0; p < P; p++) {
			for (int j = 0; j < newWidth; j++) {
				wIndices[p][j]--;
				if (wIndices[p][j] < 0)
					wIndices[p][j] = 0;
				else if (wIndices[p][j] >= origWidth - 1)
					wIndices[p][j] = origWidth - 1;
			}
		}
		
		// resizeDimCore - width
    	double[] doubleArray1 = new double[newWidth * origHeight];
    	for (int i = 0; i < newWidth; i++) {
    		for (int p = 0; p < P; p++) {
    			for(int j = 0; j < origHeight; j++) {
    				doubleArray1[i * origHeight + j] += (doubleArray[wIndices[p][i] * origHeight + j]) * wWeights[p][i];
    			}
    		}			    		
    	}
    	
		// Height
		double hScale = (double)newHeight / origHeight;
		kernel_width = 2.0;
		u = new double[newHeight];
		left = new int[newHeight];
		for (int j = 0; j < newHeight; j++) {
			u[j] = (j+1) / hScale + 0.5 * (1.0 - 1.0 / hScale);
			left[j] = (int)Math.floor(u[j] - kernel_width/2.0);
		}
		P = (int)Math.ceil(kernel_width) + 2;
		int hIndices[][] = new int[P][newHeight];
		double hWeights[][] = new double[P][newHeight];
		for (int p = 0; p < P; p++) {
			for (int j = 0; j < newHeight; j++) {
				hIndices[p][j] = left[j] + p;
				hWeights[p][j] = triangle(u[j] - hIndices[p][j]);
			}	    				
		}
		// Normalize the weights matrix so that each row sums to 1.
		for (int j = 0; j < newHeight; j++) {
			double sum = 0;
			for (int p = 0; p < P; p++) {
				sum += hWeights[p][j]; 
			}
			for (int p = 0; p < P; p++) {
				hWeights[p][j] /= sum;
			}
		}    				
		// Clamp out-of-range indices; has the effect of replicating end-points.
		for (int p = 0; p < P; p++) {
			for (int j = 0; j < newHeight; j++) {
				hIndices[p][j]--;
				if (hIndices[p][j] < 0)
					hIndices[p][j] = 0;
				else if (hIndices[p][j] >= origHeight - 1)
					hIndices[p][j] = origHeight - 1;
			}
		}

		// resizeDimCore - height
    	double[] doubleArray2 = new double[newWidth * newHeight];
		for(int j = 0; j < newHeight; j++) {
    		for (int p = 0; p < P; p++) {
		    	for (int i = 0; i < newWidth; i++) {
    				doubleArray2[i * newHeight + j] += (doubleArray1[i * origHeight + hIndices[p][j]]) * hWeights[p][j];
    			}
    		}			    		
    	}
		return doubleArray2;
	}

	private void getBitDepth(CidreOptions options, double maxI)
	{
		// Sets options.bitDepth describing the provided images as 8-bit, 12-bit, or 
		// 16-bit. If options.bitDepth is provided, it is used. Otherwise the bit 
		//depth is estimated from the max observed intensity, maxI.
		
		final int xy_2_8 = 256;
		final int xy_2_12 = 4096;
		final int xy_2_16 = 65536;

		/*if (options.bitDepth != null)
		{
			    if (options.bitDepth !=	xy_2_8 || options.bitDepth != xy_2_12 || options.bitDepth != xy_2_16)
			        IJ.error("CIDRE:loadImages", "Provide bit depth as max integer value, eg 2^12");
			    else
			        IJ.log(String.format(" log2 %d-bit depth", options.bitDepth));
		} 
		else*/
		//{
			    if (maxI > xy_2_12)
			        options.bitDepth = xy_2_16;
			    else if (maxI > xy_2_8)
			        options.bitDepth = xy_2_12;
			    else
			        options.bitDepth = xy_2_8;
			    IJ.log(String.format(" %d-bit depth images (estimated from max intensity=%1.0f)", Math.round(Math.log(options.bitDepth)/Math.log(2)), maxI));
		//}
	}
	
	private double getEntropy(CidreOptions options)
	{
		// gets the entropy of an image stack. A very low entropy indicates that
		// there may be insufficient intensity information to build a good model.
		// This can happen when only a few images are provided and the background
		// does not provide helpful information. For example in low confluency
		// fluorescence images from a glass slide, the background pixels have nearly
		// zero contribution from incident light and do not provide useful
		// information.

		// get a distribution representing all of S
		int[] hist = new int[options.bitDepth];
		double[] P = new double[options.bitDepth];
		
		for (int z = 0; z < S.size(); z++) {
	    	double[][] doubleArray = S.get(z);

	    	for (int x = 0; x < S_C; x++) {
	    		for (int y = 0; y < S_R; y++) {
	    			hist[(int)doubleArray[x][y]]++;
	    		}
	    	}
		}
		double sumP = 0;
		for (int i = 0; i < hist.length; i++)
			sumP += hist[i];

		for (int i = 0; i < P.length; i++)
			P[i] = hist[i] / sumP;
		
		// compute the entropy of the distribution
		//if (sum(~isfinite(P(:)))) {
		//	IJ.error("CIDRE:loadImages", "the inputs contain non-finite values!"); 
		//}
		//P = P(:) ./ sum(P(:));
		//P(P == 0) = []; // In the case of p(xi) = 0 for some i, the value of the 
			                // corresponding sum and 0 logb(0) is taken to be 0
		double H = 0;
		for (int i = 0; i < P.length; i++)
		{
			if (P[i] != 0)
			{
				H += P[i] * Math.log(P[i]) / Math.log(2.0);
			}
		}
		H *= -1;

		IJ.log(String.format(" Entropy of the stack = %1.2f", H));
		return H;
	}
	
	private void scaleSpaceResampling(double entropy)
	{
		// uses scale space resampling to compensate for regions with little
		// intensity information. if the entropy is very low, this indicates that 
		// some (or many) have regions little useful information. resampling from
		// a scale space transform allows us to leverage information from 
		// neighboring locations. For example, in low confluency fluorescence images
		// without a fluorescing medium, the background pixels contain nearly zero 
		// contribution from incident light and do not provide useful information.

		double l0 = 1;			// max lambda_vreg
		double l1 = 0;			// stable lambda_vreg
		double N  = S.size();	// number of images in the stack
		double a  = 7.838e+06;	// parameters of a fitted exponential function
		double b  = -1.948;		// parameters of a fitted exponential function
		double c  = 20;			// parameters of a fitted exponential function

		// emprical estimate of the number of images necessary at the reported entropy level
		double N_required = a * Math.exp(b*entropy) + c; 

		// alpha is a linear function from 1 (N=0) to 0 (N=N_required) and 0 
		// (N > N_required). It informs us how strong the scale space resampling
		// should be. alpha=1 means strong resampling, alpha=0 skips resampling
		double alpha;

	    if (N < N_required) {
		    String warnmsg = String.format(" Warning: less than recommended number\n of images provided (%.0f < %.0f) for the\n observed image entropy=%1.2f.\n\n Using scale-space resampling to compensate.", N, (double)Math.round(N_required), entropy);
		    JOptionPane.showMessageDialog(new JFrame(), warnmsg, "Warning", JOptionPane.ERROR_MESSAGE);
		    IJ.log(String.format("\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n%s\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n\n", warnmsg));
			alpha = l0 + ((l1-l0)/(N_required)) * N;
		} else {
			alpha = l1;
		}

		// the dimensions of the stack
		int C1 = S_C;
		int R1 = S_R;
		int Z1 = S.size();

		// scale space reduction of the stack into octaves. SCALE is a cell 
		// containing the scale space reductions of the stack: {[R1xC1xZ1], [R1/2 x 
		// C1/2 x Z1], [R1/4 x C1/4 x Z1], ...}
		List<List<double[][]>> SCALE = new ArrayList<List<double[][]>>();	// cell containing the scale space reductions
		int R = R1;         		// scale space reduced image height
		int C = C1;         		// scale space reduced image width

		SCALE.add(S);
		while ((R > 1) && (C > 1))
		{
			List<double[][]> elementS = new ArrayList<double[][]>();
			List<double[][]> lastElements = SCALE.get(SCALE.size() - 1);
			
			for (int i = 0; i < lastElements.size(); i++)
			{
				double[][] element = lastElements.get(i);
				double[][] rescaledElement = imresize(element, C, R, 0.5);
				
				elementS.add(rescaledElement);
			}
			SCALE.add(elementS);
			
			R = SCALE.get(SCALE.size() - 1).get(0)[0].length;
			C = SCALE.get(SCALE.size() - 1).get(0).length;
		}

		// determine the max octave we should keep, max_i as directed by the scaling
		// strength alpha. alpha = 0 keeps only the original size. alpha = 1 uses 
		// all available octaves
		int max_possible_i = SCALE.size();
		alpha = Math.max(0, alpha); alpha = Math.min(1, alpha);
		int max_i = (int)Math.ceil(alpha * max_possible_i);
		max_i = Math.max(max_i, 1);

		// join the octaves from the scale space reduction from i=1 until i=max_i. 
		if (max_i > 1)
		{
			IJ.log(" Applying scale-space resampling (intensity information is low)");
			List<double[][]> S2 = new ArrayList<double[][]>();
			for (int i = 0; i < max_i; i++)
			{
				R = SCALE.get(i).get(0)[0].length;
				C = SCALE.get(i).get(0).length;
				
		        IJ.log(String.format("  octave=1/(2^%d)  size=%dx%d", i, R, C));
		        
		        for (int j = 0; j < S.size(); j++) 
		        {
		        	S2.add(imresize(SCALE.get(i).get(j), C, R, C1, R1));
		        }
	
			}
			
			S = S2;
		}
		else
		{
			IJ.log(String.format(" Scale-space resampling NOT APPLIED (alpha = %f)", alpha));
		}
	}

	private void resizeStack(CidreOptions options)
	{
		// in order keep CIDRE computationally tractable and to ease parameter 
		// setting, we reduce the 3rd dimension of the sorted stack S to Z = 200. 
		// Information is not discarded in the process, but several slices from the 
		// stack are averaged into a single slice in the process.

		// get the original dimensions of S
		int C = S_C;
		int R = S_R;
		int Z = S.size();

		// if Z < options.numberOfQuantiles, we do not want to further compress
		// the data. leave S as is.
		if (Z <= options.numberOfQuantiles)
		{
    		return;
		}
		else
		{
		    // find regionLimits, a set of indexes that breaks Z into
		    // options.numberOfQuantiles evenly space pieces
		    int Zmin = 0;
		    int Zmax = Z;
		    int Zdiff = Zmax - Zmin;
		    
		    int[][] regionLimits = new int[options.numberOfQuantiles][2];
		    for (int i = 0; i < options.numberOfQuantiles; i++) {
		        regionLimits[i][0] = Math.round(Zmin + Zdiff*((float)i/options.numberOfQuantiles));
		        regionLimits[i][1] = Math.round(Zmin + Zdiff*((float)(i+1)/options.numberOfQuantiles)) - 1;
		    }
		    
		    // compute the mean image of each region of S defined by regionLimits,
		    // and add the mean image to S2
		    List<double[][]> S2 = new ArrayList<double[][]>();

	    	for (int i = 0; i < options.numberOfQuantiles; i++) {
		    	double[][] doubleArray = new double[S_C][S_R];

				double[] doubleValues = new double[regionLimits[i][1] - regionLimits[i][0] + 1];

		    	for (int x = 0; x < S_C; x++)
		    		for (int y = 0; y < S_R; y++)
		    		{
		    			for (int z = regionLimits[i][0]; z <= regionLimits[i][1]; z++) 
		    				doubleValues[z - regionLimits[i][0]] = S.get(z)[x][y];

		    	    	doubleArray[x][y] = mean(doubleValues); 
		    		}

		    	S2.add(doubleArray);
		    }	    	
	    	S = S2;
		}
	}
	
	private void preprocessData(double maxI, CidreOptions options)
	{
		// determine if sufficient intensity information is provided by measuring entropy
		getBitDepth(options, maxI);					// store the bit depth of the images in options, needed for entropy measurement
		double entropy = getEntropy(options);		// compute the stack's entropy
		scaleSpaceResampling(entropy); 		// resample the stack if the entropy is too high

		// sort the intensity values at every location in the image stack
		// at every pixel location (r,c), we sort all the recorded intensities from
		// the provided images and replace the stack with the new sorted values.
		// The new S has the same data as before, but sorted in ascending order in
		// the 3rd dimension.
		long t1 = System.currentTimeMillis();
		IJ.log(" Sorting intensity by pixel location and resizing...");

		//S = sort(S,3);
		double[] doubleValues = new double[S.size()];

    	for (int x = 0; x < S_C; x++) {
    		for (int y = 0; y < S_R; y++) {
    			for (int z = 0; z < S.size(); z++) 
    				doubleValues[z] = S.get(z)[x][y];

    	    	Arrays.sort(doubleValues);
    	    	
    			for (int z = 0; z < S.size(); z++) 
    				S.get(z)[x][y] = doubleValues[z];
    		}
    	}    	
	
		// compress the stack: reduce the effective number of images for efficiency
		resizeStack(options);
	    long t2 = System.currentTimeMillis();
	    IJ.log(String.format("finished in %1.2fs.", (t2 - t1)/1000.0));
	}
	
	private ZLimitsResult getZLimits(CidreOptions cidreOptions)
	{
		ZLimitsResult zLimitsResult = new ZLimitsResult();
		
		if (cidreOptions.zLimits[0] != null) {
			zLimitsResult.zmin = cidreOptions.zLimits[0];
			zLimitsResult.zmax = cidreOptions.zLimits[1];
			zLimitsResult.zx0 = (cidreOptions.zLimits[0] + cidreOptions.zLimits[1]) / 2.0;
			zLimitsResult.zy0 = zLimitsResult.zx0;
		} else {
			zLimitsResult.zmin = 0;
			zLimitsResult.zmax = STACKMIN;
			zLimitsResult.zx0 = 0.85 * STACKMIN;
			zLimitsResult.zy0 = zLimitsResult.zx0;
		}
		
		return zLimitsResult;		
	}
	
	private double getLambdaVfromN(int N)
	{
		// sets the spatial regularization weight, lambda_vreg. For sufficient
		// images, lambda_vreg=6 was determined empirically to be a good value. For
		// fewer images, it helps to increase the regularization linearly

		int NMAX = 200;		// empirically deteremined sufficient number of images
		double l0 = 9.5;	// lambda_vreg for very few images
		double l1 = 6;		// lambda_vreg for sufficient images 

		if (N < NMAX) {
			return l0 + ((l1-l0)/(NMAX)) * N;
		} else {
			return l1;
		}
	}
	
	private double computeStandardError(double[] v, double[] b) {
		// computes the mean standard error of the regression
		int Z = S.size();

		// initialize a matrix to contain all the standard error calculations
		double[] se = new double[S_C * S_R];

		// compute the standard error at each location
		double[] q = new double[Z];
		double[] fitvals = new double[Z];
		double[] residuals = new double[Z];
		for (int c = 0; c < S_C; c++) { 
			for (int r = 0; r < S_R; r++) {
				
		        double vi = v[c * S_R + r];
		        double bi = b[c * S_R + r];

		        for (int z = 0; z < Z; z++) {
		        	q[z] = S.get(z)[c][r];
		        	fitvals[z] = bi + Q[z] * vi;
		        	residuals[z] = q[z] - fitvals[z];
		        }
		        double sum_residuals2 = 0;
		        for (int z = 0; z < Z; z++) {
		        	sum_residuals2 += residuals[z] * residuals[z];
		        }		        
		        se[c * S_R + r] = Math.sqrt(sum_residuals2 / (Z-2));
			}
		}
		return mean(se);
	}
	
	private double[] estimateQ(double qPercent) {
		// We estimate Q, the underlying intensity distribution, using a robust mean
		// of the provided intensity distributions from Q

		//get dimensions of the provided data stack, S
		int C = S_C;
		int R = S_R;
		int Z = S.size();

		// determine the number of points to use
		long numPointInQ = Math.round(qPercent * R*C);
		//IJ.log(String.format(" number of points used to compute Q = %d  (%1.2f%%)", numPointInQ, qPercent*100));

		// sort the means of each intensity distribution
		//meanSurf = mean(S,3);
		double[] doubleValues = new double[Z];	// for mean
		double[][] meanSurf = new double[C][R]; 

		for (int x = 0; x < C; x++) {
    		for (int y = 0; y < R; y++)
    		{
    			for (int z = 0; z < Z; z++) 
    				doubleValues[z] = S.get(z)[x][y];

    			meanSurf[x][y] = mean(doubleValues);
    		}
		}
		
		//[msorted inds] = sort(meanSurf(:)); %#ok<ASGLU>
		final double[] msorted = new double[C * R];
		final Integer[] inds = new Integer[C * R];

		int i = 0;
		for (i = 0; i < C * R; i++)
			inds[i] = i;
		i = 0;
		for (int x = 0; x < C; x++)
    		for (int y = 0; y < R; y++)
    			msorted[i++] = meanSurf[x][y];
		
		
		Arrays.sort(inds, new Comparator<Integer>() {
		    @Override public int compare(final Integer o1, final Integer o2) {
		        return Double.compare(msorted[o1], msorted[o2]);
		    }
		});

		// locations used to compute Q (M) come from the central quantile
		int mStart = (int)(Math.round((C*R)/2.0) - Math.round(numPointInQ/2.0)) - 1;
		int mEnd   = (int)(Math.round((C*R)/2.0) + Math.round(numPointInQ/2.0));
		int mLength = mEnd - mStart;
		
		doubleValues = new double[mLength];	// for mean
		int[] cList = new int[mLength];
		int[] rList = new int[mLength];
		
		for (i = 0; i < mLength; i++) {
			cList[i] = inds[mStart + i] / R;
			rList[i] = inds[mStart + i] % R;
		}
		
		Q = new double[Z];
		for (int z = 0; z < Z; z++) {
			double[][] doubleArray = S.get(z);
			for (i = 0; i < mLength; i++) {
				doubleValues[i] = doubleArray[cList[i]][rList[i]];
			}
			Q[z] = mean(doubleValues);
		}
		return Q;
	}
	
	private double[] theBarrierFunction(double x, double xmin, double xmax, double width)
	{
		// the barrier function has a well shape. It has quadratically increasing
		// energy below xmin, zero energy between xmin and xmax, and quadratically
		// increasing energy above xmax. The rate of increase is determined by width

		double[] result = new double[] {0.0, 0.0}; // E G
		
		double xl1 = xmin;
		double xl2 = xl1 + width;

		double xh2 = xmax;
		double xh1 = xh2 - width;

		if (x <= xl1) {
			result[0] = ((x-xl2)/(xl2-xl1)) * ((x-xl2)/(xl2-xl1));
			result[1] = (2*(x-xl2)) / ((xl2-xl1)*(xl2-xl1));
		}		 
		else if ((x >= xl1) && (x <= xl2)) {
			result[0] = ((x-xl2)/(xl2-xl1))*((x-xl2)/(xl2-xl1));
			result[1] = (2*(x-xl2))  / ((xl2-xl1)*(xl2-xl1));
		}
		else if ((x > xl2) && (x < xh1)) {
			result[0] = 0;
			result[1] = 0;
		}
		else if ((x >= xh1) && (x < xh2)) {
			result[0] = ((x-xh1)/(xh2-xh1))*((x-xh1)/(xh2-xh1));
			result[1] = (2*(x-xh1))  / ((xh2-xh1)*(xh2-xh1));
		}
		else {
			result[0] = ((x-xh1)/(xh2-xh1))*((x-xh1)/(xh2-xh1));
			result[1] = (2*(x-xh1))  / ((xh2-xh1)*(xh2-xh1));
		}
		
		return result;
	}
	
	private double[] imfilter_symmetric(double[] pixels, int width, int height, double[][] k)
	{
		int kc = k.length / 2;
		double[] kernel = new double[k.length * k.length];

		double[] result = new double[width*height];
		
		for (int i = 0; i < k.length; i++)
			for (int j = 0; j < k.length; j++)
				kernel[i * k.length + j] = k[i][j];
		
		double sum;
		int offset, i;
		int edgeDiff;
		boolean edgePixel;
		int xedge = width - kc;
		int yedge = height - kc;
		int nx, ny;
		
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				sum = 0;
				i = 0;
				edgePixel = x < kc || x >= xedge || y < kc || y >= yedge;
				for (int u = -kc; u <= kc; u++) {
					offset = (x+u)*height + y;
					for (int v = -kc; v <= kc; v++) {
						if (edgePixel) {
							nx = x + u;
							ny = y + v;
							edgeDiff = 0;
							if (nx < 0)
								edgeDiff = (-2*nx - 1) * height;
							else if (nx >= width)
								edgeDiff = (-2 * (nx - width) - 1) * height;
								
							if (ny < 0)
								edgeDiff += -2*ny - 1;
							else if (ny >= height)
								edgeDiff += -2 * (ny - height) - 1;

							sum += pixels[offset + v + edgeDiff] * kernel[i++];
						}
						else
						{
							sum += pixels[offset + v] * kernel[i++];
						}
					}
				}
				result[x * height + y] = sum;
			}
		}
		
		return result;
	}
	
	public CdrObjectiveResult cdr_objective(double[] x)
	{
		double E = 0.0;
		double[] G = null;
		
		// some basic definitions
		int N_stan = 200;				// the standard number of quantiles used for empirical parameter setting
		double w           = CAUCHY_W;  // width of the Cauchy function
		double LAMBDA_BARR = 1e6;		// the barrier term coefficient
		int Z = S.size();
		
		// unpack
		double[] v_vec = Arrays.copyOfRange(x, 0, S_C * S_R);
		double[] b_vec = Arrays.copyOfRange(x, S_C * S_R, 2 * S_C * S_R);
		double zx = x[2 * S_C * S_R];
		double zy = x[2 * S_C * S_R + 1];

		// move the zero-light point to the pivot space (zx,zy) -> (px,py)
		double px = zx - PivotShiftX;		// a scalar
		double[] py = new double[S_C * S_R];
		int pPy = 0;
		for (int c = 0; c < S_C; c++)
			for (int r = 0; r < S_R; r++)
				py[pPy++] = zy - PivotShiftY[c * S_R + r];


		//--------------------------------------------------------------------------
		// fitting energy
		// We compute the energy of the fitting term given v,b,zx,zy. We also
		// compute its gradient wrt the random variables.

		double[] energy_fit  = new double[S_C * S_R];		// accumulates the fit energy
		double[] deriv_v_fit = new double[S_C * S_R];		// derivative of fit term wrt v
		double[] deriv_b_fit = new double[S_C * S_R];		// derivative of fit term wrt b

		double v;
		double b;
		double E_fit = 0;
		double[] G_V_fit = new double[S_C * S_R];
		double[] G_B_fit = new double[S_C * S_R];
		
		double[] mestimator_response = new double[Z];
        double[] d_est_dv = new double[Z];
        double[] d_est_db = new double[Z];
        
        int I = 0;
		for (int c = 0; c < S_C; c++) { 
			for (int r = 0; r < S_R; r++) { 
		        // get the quantile fit for this location and vectorize it
		        double[] q = new double[Z];
		        for (int z = 0; z < Z; z++)
		        	q[z] = S.get(z)[c][r];
		        
		        v = v_vec[c * S_R + r];
		        b = b_vec[c * S_R + r];

		        switch (MESTIMATOR) {
		            case LS:
		            	for (int z = 0; z < Z; z++) {
		            		double val = Q[z] * v + b - q[z];
		            		mestimator_response[z] = val * val;
		            		d_est_dv[z] = Q[z] * val;
		            		d_est_db[z] = val;
		            	}
		                break;
		            case CAUCHY:
		            	for (int z = 0; z < Z; z++) {
		            		double val = Q[z] * v + b - q[z];
		            		mestimator_response[z] = w*w * Math.log(1 + (val*val) / (w*w)) / 2.0;
		            		d_est_dv[z] = (Q[z]*val) / (1.0 + (val*val) / (w*w));
		            		d_est_db[z] = val / (1.0 + (val*val) / (w*w));
		            	}
		                break;
		        }
		        
		        for (int z = 0; z < Z; z++) {
		        	energy_fit[I] += mestimator_response[z];
		        	deriv_v_fit[I] += d_est_dv[z];
		        	deriv_b_fit[I] += d_est_db[z];
		        }
		        I++;
			}
		}
		// normalize the contribution from fitting energy term by the number of data 
		// points in S (so our balancing of the energy terms is invariant)
		int data_size_factor = N_stan/Z;
		I = 0;
		for (int c = 0; c < S_C; c++) { 
			for (int r = 0; r < S_R; r++) {
				E_fit += energy_fit[I];
				G_V_fit[I] = deriv_v_fit[I] * data_size_factor;	// fit term derivative wrt v
				G_B_fit[I] = deriv_b_fit[I] * data_size_factor;	// fit term derivative wrt b
				I++;
			}
		}
		E_fit *= data_size_factor;		// fit term energy
		
		//--------------------------------------------------------------------------
		// spatial regularization of v
		// We compute the energy of the regularization term given v,b,zx,zy. We also
		// compute its gradient wrt the random variables.

		// determine the widths we will use for the LoG filter
		int max_exp = (int)Math.max(1.0, Math.log(Math.floor(Math.max(S_C,  S_R) / 50.0))/Math.log(2.0));

		double[] sigmas = new double[max_exp + 2];
		for (int i = -1; i <= max_exp; i++)
			sigmas[i + 1] = Math.pow(2, i);
		
		double[] energy_vreg = new double[sigmas.length];	// accumulates the vreg energy
		double[] deriv_v_vreg = new double[S_C * S_R];			// derivative of vreg term wrt v
		double[] deriv_b_vreg = new double[S_C * S_R];			// derivative of vreg term wrt b

		// apply the scale-invariant LoG filter to v for all scales in SIGMAS
		double[][][] h = new double[sigmas.length][][];
		for (int i = 0; i < sigmas.length; i++)
		{
		    // define the kernel size, make certain dimension is odd
		    int hsize = 6 * (int)Math.ceil(sigmas[i]); 
		    if (hsize % 2 == 0)
		        hsize++;
		    double std2 = sigmas[i] * sigmas[i];

		    // h{n} = sigmas(n)^2 * fspecial('log', hsize, sigmas(n))
		    h[i] = new double[hsize][hsize];
		    double[][] h1 = new double[hsize][hsize];
		    double sumh = 0.0;
		    for (int c = 0; c < hsize; c++) {
		    	for (int r = 0; r < hsize; r++) {
		    		double arg = -1.0 * ((c-hsize/2)*(c-hsize/2) + (r-hsize/2)*(r-hsize/2)) / (2.0*std2);
		    		h[i][c][r] = Math.exp(arg);
		    		sumh += h[i][c][r];
		    	}
		    }		    
		    // calculate Laplacian
		    double sumh1 = 0.0;
		    for (int c = 0; c < hsize; c++) {
		    	for (int r = 0; r < hsize; r++) {
		    		h[i][c][r] /= sumh;
		    		h1[c][r] = h[i][c][r] * ((c-hsize/2)*(c-hsize/2) + (r-hsize/2)*(r-hsize/2) - 2 * std2) / (std2 * std2);
		    		sumh1 += h1[c][r]; 
		    	}
		    }
		    for (int c = 0; c < hsize; c++) {
		    	for (int r = 0; r < hsize; r++) {
		    		h[i][c][r] = (h1[c][r] - sumh1/(hsize*hsize)) * (sigmas[i] * sigmas[i]); // h{n} = sigmas(n)^2 * fspecial('log', hsize, sigmas(n));
		    	}
		    }
		    
		    // apply a LoG filter to v_img to penalize disagreements between neighbors
		    double[] v_LoG = imfilter_symmetric(v_vec, S_C, S_R, h[i]);
		    for (int c = 0; c < v_LoG.length; c++)
		    	v_LoG[c] /= sigmas.length;	// normalize by the # of sigmas used

		    // energy is quadratic LoG response
		    energy_vreg[i] = 0;
		    for (int c = 0; c < v_LoG.length; c++)
			    energy_vreg[i] += v_LoG[c]*v_LoG[c];

		    for (int c = 0; c < v_LoG.length; c++)
			    v_LoG[c] *= 2;
		    double[] v_LoG2 = imfilter_symmetric(v_LoG, S_C, S_R, h[i]);
		    for (int c = 0; c < v_LoG2.length; c++)		    
		    	deriv_v_vreg[c] += v_LoG2[c];
		}

		double E_vreg = 0;							// vreg term energy
		for (int i = 0; i < sigmas.length; i++)
			E_vreg += energy_vreg[i];
		double[] G_V_vreg = deriv_v_vreg;			// vreg term gradient wrt v
		double[] G_B_vreg = deriv_b_vreg;			// vreg term gradient wrt b
		//--------------------------------------------------------------------------

		//--------------------------------------------------------------------------
		// The ZERO-LIGHT term
		// We compute the energy of the zero-light term given v,b,zx,zy. We also
		// compute its gradient wrt the random variables.

		double[] residual = new double[S_C * S_R];
		for (int i = 0; i < residual.length; i++)
			residual[i] = v_vec[i] * px + b_vec[i] - py[i];
		
		double[] deriv_v_zero = new double[S_C * S_R];
		double[] deriv_b_zero = new double[S_C * S_R];
		double deriv_zx_zero = 0.0;
		double deriv_zy_zero = 0.0;
		for (int i = 0; i < S_C * S_R; i++) {
			double val = b_vec[i] + v_vec[i] * px - py[i];
			deriv_v_zero[i] = 2 * px * val;
			deriv_b_zero[i] = 2 * val;
			deriv_zx_zero += 2 * v_vec[i] * val;
			deriv_zy_zero += -2 * val;
		}

		double E_zero = 0;	// zero light term energy
		for (int i = 0; i < residual.length; i++)
			E_zero += residual[i] * residual[i];

		double[] G_V_zero = deriv_v_zero;		// zero light term gradient wrt v
		double[] G_B_zero = deriv_b_zero;		// zero light term gradient wrt b
		double G_ZX_zero = deriv_zx_zero;		// zero light term gradient wrt zx
		double G_ZY_zero = deriv_zy_zero;		// zero light term gradient wrt zy
		//--------------------------------------------------------------------------

		//--------------------------------------------------------------------------
		// The BARRIER term
		// We compute the energy of the barrier term given v,b,zx,zy. We also
		// compute its gradient wrt the random variables.

		double Q_UPPER_LIMIT = ZMAX;	// upper limit - transition from zero energy to quadratic increase 
		double Q_LOWER_LIMIT = ZMIN;	// lower limit - transition from quadratic to zero energy
		double Q_RATE = 0.001;			// rate of increase in energy 

		// barrier term gradients and energy components
		double[] barrierResult = theBarrierFunction(zx, Q_LOWER_LIMIT, Q_UPPER_LIMIT, Q_RATE);
		double E_barr_xc = barrierResult[0];
		double G_ZX_barr = barrierResult[1];

		barrierResult = theBarrierFunction(zy, Q_LOWER_LIMIT, Q_UPPER_LIMIT, Q_RATE);
		double E_barr_yc = barrierResult[0];
		double G_ZY_barr = barrierResult[1];

		double E_barr = E_barr_xc + E_barr_yc;		// barrier term energy

		//--------------------------------------------------------------------------

		//--------------------------------------------------------------------------
		// The total energy 
		// Find the sum of all components of the energy. TERMSFLAG switches on and
		// off different components of the energy.
		String term_str = "";
		switch (TERMSFLAG) {
		    case 0:
		        E = E_fit;
		        term_str = "fitting only";
		        break;
		    case 1:
		        E = E_fit + LAMBDA_VREG*E_vreg + LAMBDA_ZERO*E_zero + LAMBDA_BARR*E_barr;
		        term_str = "all terms";
		        break;
		}
		//--------------------------------------------------------------------------

		//--------------------------------------------------------------------------
		// The gradient of the energy
		double[] G_V = null;
		double[] G_B = null;
		double G_ZX = 0;
		double G_ZY = 0;
		
		switch (TERMSFLAG) {
		    case 0:
		        G_V = G_V_fit;
		        G_B = G_B_fit;
		        G_ZX = 0;
		        G_ZY = 0;
		        break;
		    case 1:
		    	for (int i = 0; i < G_V_fit.length; i++) {
		    		G_V_fit[i] = G_V_fit[i] + LAMBDA_VREG*G_V_vreg[i] + LAMBDA_ZERO*G_V_zero[i];
		    		G_B_fit[i] = G_B_fit[i] + LAMBDA_VREG*G_B_vreg[i] + LAMBDA_ZERO*G_B_zero[i];
		    	}
		    	G_V = G_V_fit;
		    	G_B = G_B_fit;
		        G_ZX = LAMBDA_ZERO*G_ZX_zero + LAMBDA_BARR*G_ZX_barr;
		        G_ZY = LAMBDA_ZERO*G_ZY_zero + LAMBDA_BARR*G_ZY_barr;
		        break;
		}
		      
		// vectorize the gradient
		G = new double[x.length];
		
		int pG = 0;
		for (int i = 0; i < G_V.length; i++)
			G[pG++] = G_V[i];
		for (int i = 0; i < G_B.length; i++)
			G[pG++] = G_B[i];
		G[pG++] = G_ZX;
		G[pG++] = G_ZY;
		
		//--------------------------------------------------------------------------

		/*double[] mlX2 = readFromCSVFile(PathIn + "csv\\g_ml", 1, 2 * S_C * S_R + 2);

		double mmax = Double.MIN_VALUE;
		double mmin = Double.MAX_VALUE;
		
		for (int t = 0; t < mlX2.length; t++) {
			mlX2[t] -= G[t];
			if (mmax < mlX2[t])
				mmax = mlX2[t];
			if (mmin > mlX2[t])
				mmin = mlX2[t];
		}*/
		
		//writeToCSVFile(PathIn + "csv\\x_ml_res", mlX2, 1, n);
		
		IJ.log(String.format("iter = %d  %s %s    zx,zy=(%1.2f,%1.2f)    E=%g", ITER, MESTIMATOR, term_str, zx,zy, E));
		//IJ.log(String.format("min = %f, max = %f", mmin, mmax));
		ITER++;
		
		CdrObjectiveResult result = new CdrObjectiveResult();
		result.E = E;
		result.G = G;		
		return result;
	}

	private final double cubic(double x) {
		double absx = Math.abs(x);
		double absx2 = absx * absx;
		double absx3 = absx2 * absx;
		
		return (1.5 * absx3 - 2.5 * absx2 + 1.0) * (absx <= 1.0 ? 1.0 : 0.0) +
                (-0.5 * absx3 + 2.5 * absx2 - 4.0 * absx + 2.0) * ((1 < absx) && (absx <= 2) ? 1.0 : 0.0);
	}
	
	private final double triangle(double x) {
		return (x+1.0) * ((-1.0 <= x) && (x < 0.0) ? 1.0 : 0.0) + (1.0-x) * ((0.0 <= x) && (x <= 1.0) ? 1.0 : 0.0);
	}


	static final double a = 0.5; // Catmull-Rom interpolation
	private final double cubic2(double x) {
		if (x < 0.0) x = -x;
		double z = 0.0;
		if (x < 1.0) 
			z = x*x*(x*(-a+2.0) + (a-3.0)) + 1.0;
		else if (x < 2.0) 
			z = -a*x*x*x + 5.0*a*x*x - 8.0*a*x + 4.0*a;
		return z;
	}
	
	

	private double[] resize_bicubic(double[] pixels, int srcWidth, int srcHeight, int dstWidth, int dstHeight)
	{
		double[] result = new double[dstWidth * dstHeight];

		double srcCenterX = srcWidth / 2.0;
		double srcCenterY = srcHeight / 2.0;
		double dstCenterX = dstWidth / 2.0;
		double dstCenterY = dstHeight / 2.0;		
		double xScale = (double)dstWidth/srcWidth;
		double yScale = (double)dstHeight/srcHeight; 
		dstCenterX += xScale / 2.0;
		dstCenterY += yScale / 2.0;		

		int index;
		double xs, ys;
		int u0, v0;

		for (int x = 0; x < dstWidth; x++) {
			xs = (x - dstCenterX) / xScale + srcCenterX;
			index = x * dstHeight;
			for (int y = 0; y < dstHeight; y++) {
				ys = (y - dstCenterY) / yScale + srcCenterY;
				//getBicubicInterpolatedPixel
				u0 = (int)Math.floor(xs);
				v0 = (int)Math.floor(ys);
				
				if (u0 <= 0 || u0 >= srcWidth - 2 || v0 <= 0 || v0 >= srcHeight - 2)
				{
				} else {
					double q = 0.0;
					for (int i = 0; i <= 3; i++) {
						int u = u0 - 1 + i;
						double p = 0.0;
						for (int j = 0; j <= 3; j++) {
							int v = v0 - 1 + j;
							p = p + pixels[u * srcHeight + v] * cubic(ys - v);
						}
						q = q + p * cubic(xs - u);
					}
					result[index + y] = q;
				}				
			}
		}
		
		return result;
	}

	private double polyinterp(double[] points, Double xminBound, Double xmaxBound)
	{
		double xmin = Math.min(points[0], points[3]);
		double xmax = Math.max(points[0], points[3]);
		
		// Compute Bounds of Interpolation Area
		if (xminBound == null)
		    xminBound = xmin;
		if (xmaxBound == null)
		    xmaxBound = xmax;		
		
		// Code for most common case:
		//   - cubic interpolation of 2 points
		//       w/ function and derivative values for both

		// Solution in this case (where x2 is the farthest point):
		// d1 = g1 + g2 - 3*(f1-f2)/(x1-x2);
		// d2 = sqrt(d1^2 - g1*g2);
		// minPos = x2 - (x2 - x1)*((g2 + d2 - d1)/(g2 - g1 + 2*d2));
		// t_new = min(max(minPos,x1),x2);
		
		int minPos;
		int notMinPos;
		if (points[0] < points[3])
		{
			minPos = 0;
		} else {
			minPos = 1;
		}
		notMinPos = (1 - minPos) * 3;
		double d1 = points[minPos + 2] + points[notMinPos + 2] - 3*(points[minPos + 1]-points[notMinPos + 1])/(points[minPos]-points[notMinPos]);
		double d2_2 = d1*d1 - points[minPos+2]*points[notMinPos+2];
		
		if (d2_2 >= 0.0) 
		{
		    double d2 = Math.sqrt(d2_2);
	        double t = points[notMinPos] - (points[notMinPos] - points[minPos])*((points[notMinPos + 2] + d2 - d1)/(points[notMinPos + 2] - points[minPos + 2] + 2*d2));
	        return Math.min(Math.max(t, xminBound), xmaxBound);
		} else {
			return (xmaxBound+xminBound)/2.0;
		}
	}

	private WolfeLineSearchResult WolfeLineSearch(double[] x, double t, double[] d, double f, double[] g, double gtd, double c1, double c2, 
			int LS_interp, int LS_multi, int maxLS, double progTol, int saveHessianComp)
	{
		
		
		double[] x2 = new double[x.length];
		for (int j = 0; j < x.length; j++)
			x2[j] = x[j] + t * d[j];
		CdrObjectiveResult cdrObjectiveResult = cdr_objective(x2);
		double f_new = cdrObjectiveResult.E;
		double[] g_new = cdrObjectiveResult.G;
		int funEvals = 1;
	
		double gtd_new = 0.0;			
		for (int j = 0; j < g.length; j++)
			gtd_new += g_new[j] * d[j];
		
		// Bracket an Interval containing a point satisfying the
		// Wolfe criteria

		int LSiter = 0;
		double t_prev = 0.0;
		double f_prev = f;
		double[] g_prev = new double[g.length];
		for (int j = 0; j < g.length; j++)
			g_prev[j] = g[j];
		double gtd_prev = gtd;
		double nrmD = Double.MIN_VALUE;
		for (int j = 0; j < d.length; j++)
		{
			double absValD = Math.abs(d[j]);
			if (nrmD < absValD)
				nrmD = absValD;
		}
		boolean done = false;
		
		int bracketSize = 0;
		double[] bracket = new double[2];
		double[] bracketFval = new double[2];
		double[] bracketGval = new double[2 * x.length];
		
		while (LSiter < maxLS)
		{
		    if (f_new > f + c1*t*gtd || (LSiter > 1 && f_new >= f_prev))
		    {
		    	bracketSize = 2;
		    	bracket[0] = t_prev; bracket[1] = t;
		    	bracketFval[0] = f_prev; bracketFval[1] = f_new;
		    	for (int j = 0; j < g_prev.length; j++)
		    		bracketGval[j] = g_prev[j];
		    	for (int j = 0; j < g_new.length; j++)
		    		bracketGval[g_prev.length + j] = g_new[j];		    	
		    	break;
		    }
		    else if (Math.abs(gtd_new) <= -c2*gtd)
		    {
		    	bracketSize = 1;
		        bracket[0] = t;
		        bracketFval[0] = f_new;
		    	for (int j = 0; j < g_new.length; j++)
		    		bracketGval[j] = g_new[j];
		        done = true;
		        break;
		    }
		    else if (gtd_new >= 0)
		    {
		    	bracketSize = 2;
		    	bracket[0] = t_prev; bracket[1] = t;
		    	bracketFval[0] = f_prev; bracketFval[1] = f_new;
		    	for (int j = 0; j < g_prev.length; j++)
		    		bracketGval[j] = g_prev[j];
		    	for (int j = 0; j < g_new.length; j++)
		    		bracketGval[g_prev.length + j] = g_new[j];		    	
		    	break;
		    }
	    
		    double temp = t_prev;
		    t_prev = t;
		    double minStep = t + 0.01*(t-temp);
		    double maxStep = t*10;
		    if (LS_interp <= 1)
		    	t = maxStep;
		    else if (LS_interp == 2)
		    {
		    	double[] points = new double[2*3];
		    	points[0] = temp; points[1] = f_prev; points[2] = gtd_prev;
		    	points[3] = t;    points[4] = f_new;  points[5] = gtd_new;
		    	t = polyinterp(points, minStep, maxStep);
		    }
	    
		    f_prev = f_new;
		    for (int j = 0; j < g_new.length; j++)
		    	g_prev[j] = g_new[j];
		    gtd_prev = gtd_new;
		    
			x2 = new double[x.length];
			for (int j = 0; j < x.length; j++)
				x2[j] = x[j] + t * d[j];
		    cdrObjectiveResult = cdr_objective(x2);
			f_new = cdrObjectiveResult.E;
			g_new = cdrObjectiveResult.G;
			funEvals++;
			gtd_new = 0.0;			
			for (int j = 0; j < g.length; j++)
				gtd_new += g_new[j] * d[j];
			LSiter++;
		}
		
		if (LSiter == maxLS)
		{
	    	bracketSize = 2;
	    	bracket[0] = 0; bracket[1] = t;
	    	bracketFval[0] = f; bracketFval[1] = f_new;
	    	for (int j = 0; j < g.length; j++)
	    		bracketGval[j] = g[j];
	    	for (int j = 0; j < g_new.length; j++)
	    		bracketGval[g.length + j] = g_new[j];		    	
		}
		
		// Zoom Phase

		// We now either have a point satisfying the criteria, or a bracket
		// surrounding a point satisfying the criteria
		// Refine the bracket until we find a point satisfying the criteria
		boolean insufProgress = false;
		//int Tpos = 1;
		//int LOposRemoved = 0;
		int LOpos;
		int HIpos;
		double f_LO;

		while (!done && LSiter < maxLS)
		{
		    // Find High and Low Points in bracket
		    //[f_LO LOpos] = min(bracketFval);
		    //HIpos = -LOpos + 3;
			
			if (bracketSize < 2)
			{
				f_LO = bracketFval[0];
				LOpos = 0; HIpos = 1;
			} 
			else 
			{
				if (bracketFval[0] <= bracketFval[1])
				{
					f_LO = bracketFval[0];
					LOpos = 0; HIpos = 1;
				} else {
					f_LO = bracketFval[1];
					LOpos = 1; HIpos = 0;
				}
			}
			
			// LS_interp == 2
			//t = polyinterp([bracket(1) bracketFval(1) bracketGval(:,1)'*d
			//            bracket(2) bracketFval(2) bracketGval(:,2)'*d],doPlot);
			            
		    {
				double val0 = 0.0;			
				for (int j = 0; j < g.length; j++)
					val0 += bracketGval[j] * d[j];
				
				double val1 = 0.0;			
				for (int j = 0; j < g.length; j++)
					val1 += bracketGval[g.length + j] * d[j];
		    	
		    	double[] points = new double[2*3];
		    	points[0] = bracket[0]; points[1] = bracketFval[0]; points[2] = val0;
		    	points[3] = bracket[1]; points[4] = bracketFval[1];  points[5] = val1;
		    	t = polyinterp(points, null, null);
		    }
		    
		    // Test that we are making sufficient progress
		    if (Math.min(Math.max(bracket[0], bracket[1])-t,t-Math.min(bracket[0], bracket[1]))/(Math.max(bracket[0], bracket[1])-Math.min(bracket[0], bracket[1])) < 0.1)
		    {
		        if (insufProgress || t>=Math.max(bracket[0], bracket[1]) || t <= Math.min(bracket[0], bracket[1]))
		        {
		            if (Math.abs(t-Math.max(bracket[0], bracket[1])) < Math.abs(t-Math.min(bracket[0], bracket[1])))
		            {
		                t = Math.max(bracket[0], bracket[1])-0.1*(Math.max(bracket[0], bracket[1])-Math.min(bracket[0], bracket[1]));
		            } else {
		                t = Math.min(bracket[0], bracket[1])+0.1*(Math.max(bracket[0], bracket[1])-Math.min(bracket[0], bracket[1]));
		            }
		            insufProgress = false;
		        } else {
		            insufProgress = true;
		        }
		    } else {
		        insufProgress = false;
		    }

		    // Evaluate new point
			x2 = new double[x.length];
			for (int j = 0; j < x.length; j++)
				x2[j] = x[j] + t * d[j];
		    cdrObjectiveResult = cdr_objective(x2);
			f_new = cdrObjectiveResult.E;
			g_new = cdrObjectiveResult.G;
			funEvals++;
			gtd_new = 0.0;			
			for (int j = 0; j < g.length; j++)
				gtd_new += g_new[j] * d[j];
			LSiter++;

			boolean armijo = f_new < f + c1*t*gtd;
		    if (!armijo || f_new >= f_LO)
		    {
		        // Armijo condition not satisfied or not lower than lowest point
		        bracket[HIpos] = t;
		        bracketFval[HIpos] = f_new;
		    	for (int j = 0; j < g.length; j++)
		    		bracketGval[g.length * HIpos + j] = g_new[j];    	
		        //Tpos = HIpos;
		    } else {
		        if (Math.abs(gtd_new) <= - c2*gtd)
		        {
		            // Wolfe conditions satisfied
		            done = true;
		        } else if (gtd_new*(bracket[HIpos]-bracket[LOpos]) >= 0)
		        {
		            // Old HI becomes new LO
		            bracket[HIpos] = bracket[LOpos];
		            bracketFval[HIpos] = bracketFval[LOpos];
			    	for (int j = 0; j < g.length; j++)
			    		bracketGval[g.length * HIpos + j] = bracketGval[g.length * LOpos + j];	    	
		        }
		        // New point becomes new LO
		        bracket[LOpos] = t;
		        bracketFval[LOpos] = f_new;
		    	for (int j = 0; j < g.length; j++)
		    		bracketGval[g.length * LOpos + j] = g_new[j];
		        //Tpos = LOpos;
		    }

		    if (!done && Math.abs(bracket[0]-bracket[1])*nrmD < progTol)
		    	break;
		}
		
		if (bracketSize < 2)
		{
			f_LO = bracketFval[0];
			LOpos = 0; HIpos = 1;
		} 
		else 
		{
			if (bracketFval[0] <= bracketFval[1])
			{
				f_LO = bracketFval[0];
				LOpos = 0; HIpos = 1;
			} else {
				f_LO = bracketFval[1];
				LOpos = 1; HIpos = 0;
			}
		}
	
		t = bracket[LOpos];
		f_new = bracketFval[LOpos];
    	for (int j = 0; j < g.length; j++)
    		g_new[j] = bracketGval[g.length * LOpos + j];
		
    	WolfeLineSearchResult wolfeLineSearchResult = new WolfeLineSearchResult();
    	wolfeLineSearchResult.t = t;
    	wolfeLineSearchResult.f_new = f_new;
    	wolfeLineSearchResult.g_new = g_new;
    	wolfeLineSearchResult.funEvals = funEvals;
    	return wolfeLineSearchResult;
	}

	private LbfgsAddResult lbfgsAdd(double[] y, double[] s, double[][] S, double[][] Y, double[] YS, int lbfgs_start, int lbfgs_end, double Hdiag)
	{
		double ys = 0.0;
		for (int j = 0; j < y.length; j++)
			ys += y[j] * s[j];
		boolean skipped = false;
		int corrections = S[0].length;
		if (ys > 1e-10d)
		{
			if (lbfgs_end < corrections - 1)
			{
				lbfgs_end = lbfgs_end+1;
				if (lbfgs_start != 0)
				{
					if (lbfgs_start == corrections - 1)
						lbfgs_start = 0;
					else
						lbfgs_start = lbfgs_start+1;
				}
			} else {
				lbfgs_start = Math.min(1, corrections);
				lbfgs_end = 0;
			}
			
			for (int j = 0; j < s.length; j++)
			{
				S[j][lbfgs_end] = s[j];
				Y[j][lbfgs_end] = y[j];
			}
			YS[lbfgs_end] = ys;
			
			// Update scale of initial Hessian approximation
			double yy = 0.0;
			for (int j = 0; j < y.length; j++)
				yy += y[j]*y[j];
			Hdiag = ys/yy;
		} else {
			skipped = false;
		}
		
		LbfgsAddResult lbfgsAddResult = new LbfgsAddResult();
		lbfgsAddResult.S = S;
		lbfgsAddResult.Y = Y;
		lbfgsAddResult.YS = YS;
		lbfgsAddResult.lbfgs_start = lbfgs_start;
		lbfgsAddResult.lbfgs_end = lbfgs_end;
		lbfgsAddResult.Hdiag = Hdiag;
		lbfgsAddResult.skipped = skipped;

		return lbfgsAddResult;
	}
	
	private double[] lbfgsProd(double[] g, double[][] S, double[][] Y, double[] YS, int lbfgs_start, int lbfgs_end, double Hdiag)
	{
		// BFGS Search Direction
		// This function returns the (L-BFGS) approximate inverse Hessian,
		// multiplied by the negative gradient

		// Set up indexing
		int nVars = S.length;
		int maxCorrections = S[0].length;
		int nCor;
		int[] ind;
		if (lbfgs_start == 0)
		{
			ind = new int[lbfgs_end];
			for (int j = 0; j < ind.length; j++)
				ind[j] = j;
			nCor = lbfgs_end-lbfgs_start+1;
		} else {
			ind = new int[maxCorrections];
			for (int j = lbfgs_start; j < maxCorrections; j++)
				ind[j - lbfgs_start] = j;
			for (int j = 0; j <= lbfgs_end; j++)
				ind[j + maxCorrections - lbfgs_start] = j;			
			nCor = maxCorrections;			
		}

		double[] al = new double[nCor];
		double[] be = new double[nCor];

		double[] d = new double[g.length];
		for (int j = 0; j < g.length; j++)
			d[j] = -g[j];
		for (int j = 0; j < ind.length; j++)
		{
			int i = ind[ind.length-j-1];
			double sumSD = 0.0;
			for (int k = 0; k < S.length; k++)
				sumSD += (S[k][i] * d[k]) / YS[i];
			al[i] = sumSD;

			for (int k = 0; k < d.length; k++)
				d[k] -= al[i] * Y[k][i];
		}

		// Multiply by Initial Hessian
		for (int j = 0; j < d.length; j++)
			d[j] = Hdiag * d[j];

		for (int i = 0; i < ind.length; i++)
		{
			double sumYd = 0.0;
			for (int j = 0; j < Y.length; j++)
				sumYd += Y[j][ind[i]] * d[j];
			be[ind[i]] = sumYd / YS[ind[i]];
			
			for (int j = 0; j < d.length; j++)
				d[j] += S[j][ind[i]] * (al[ind[i]] - be[ind[i]]);
		}
		return d;
	}
	
	private MinFuncResult minFunc(double[] x0, MinFuncOptions minFuncOptions)
	{
		double[] x = null;
		double f = 0.0;
		
		int maxIter      = minFuncOptions.maxIter;
		int MaxFunEvals  = minFuncOptions.MaxFunEvals;
		double progTol   = minFuncOptions.progTol;
		double optTol    = minFuncOptions.optTol;
		int corrections  = minFuncOptions.Corr;
		
		int maxFunEvals = 1000;
		double c1 = 1e-4;
		double c2 = 0.9;
		int LS_interp = 2;
		int LS_multi = 0;
	
		int exitflag = 0;
		String msg = null;
		
		// Initialize
		int p = x0.length;
		double[] d = new double[p];
		x = new double[x0.length];
		for (int i = 0; i < x0.length; i++)
			x[i] = x0[i];
		double t = 1.0d;
		
		// If necessary, form numerical differentiation functions
		int funEvalMultiplier = 1;
		int numDiffType = 0;

		// Evaluate Initial Point
		CdrObjectiveResult cdrObjectiveResult = cdr_objective(x);
		f = cdrObjectiveResult.E;
		double[] g = cdrObjectiveResult.G;
		double[] g_old = new double[g.length];
		
		int computeHessian = 0;
		
		int funEvals = 1;

		// Compute optimality of initial point
		double optCond = Double.MIN_VALUE;
		for (int j = 0; j < g.length; j++)
		{
			double absValue = Math.abs(g[j]);
			if (optCond < absValue)
				optCond = absValue;
		}
		
		// Exit if initial point is optimal
		if (optCond <= optTol)
		{
		    exitflag=1;
		    msg = "Optimality Condition below optTol";
		    MinFuncResult minFuncResult = new MinFuncResult();
		    minFuncResult.x = x;
		    minFuncResult.f = f;
		    return minFuncResult;
		}

		double[][] S = new double[p][corrections]; 
		double[][] Y = new double[p][corrections]; 
		double[]  YS = new double[corrections]; 
		int lbfgs_start = 0;
		int lbfgs_end = 0;
		double Hdiag = 1.0;
	
		// Perform up to a maximum of 'maxIter' descent steps:
		for (int i = 0; i < maxIter; i++)
		{
			// LBFGS
			if (i == 0)
			{
					// Initially use steepest descent direction
					for (int j = 0; j < g.length; j++)
						d[j] = -g[j];
					lbfgs_start = 0;
					lbfgs_end = -1;
					Hdiag = 1.0;
			}
			else
			{
				double[] gMg_old = new double[g.length];
				for (int j = 0; j < g.length; j++)
					gMg_old[j] = g[j] - g_old[j];
				
				double[] tPd = new double[d.length];
				for (int j = 0; j < d.length; j++)
					tPd[j] = t * d[j];

				LbfgsAddResult lbfgsAddResult = lbfgsAdd(gMg_old, tPd, S, Y, YS, lbfgs_start, lbfgs_end, Hdiag);
				S = lbfgsAddResult.S;
				Y = lbfgsAddResult.Y;
				YS = lbfgsAddResult.YS;
				lbfgs_start = lbfgsAddResult.lbfgs_start;
				lbfgs_end = lbfgsAddResult.lbfgs_end;
				Hdiag = lbfgsAddResult.Hdiag;
				boolean skipped = lbfgsAddResult.skipped;

				d = lbfgsProd(g, S, Y, YS, lbfgs_start, lbfgs_end, Hdiag);
			}
			for (int j = 0; j < g.length; j++)
				g_old[j] = g[j];

		    // ****************** COMPUTE STEP LENGTH ************************

		    // Directional Derivative
			double gtd = 0.0;			
			for (int j = 0; j < g.length; j++)
				gtd += g[j] * d[j];

		    // Check that progress can be made along direction
		    if (gtd > -progTol)
		    {
		        exitflag = 2;
		        msg = "Directional Derivative below progTol";
		        break;
		    }
		    
		    // Select Initial Guess
		    if (i == 0)
		    {
		    	double sumAbsG = 0.0;
				for (int j = 0; j < g.length; j++)
					sumAbsG += Math.abs(g[j]);
				t = Math.min(1.0, 1.0/sumAbsG);
		    } else {
		        //if (LS_init == 0)
		    	// Newton step
		    	t = 1.0;		    	
		    }
		    double f_old = f;
		    double gtd_old = gtd;
		    
		    int Fref = 1;
		    double fr;
		    // Compute reference fr if using non-monotone objective
		    if (Fref == 1)
		    {
		        fr = f;
		    }
		    
		    computeHessian = 0; 

		    // Line Search
		    f_old = f;

		    WolfeLineSearchResult wolfeLineSearchResult = WolfeLineSearch(x,t,d,f,g,gtd,c1,c2,LS_interp,LS_multi,25,progTol,1);
		    t = wolfeLineSearchResult.t;
		    f = wolfeLineSearchResult.f_new;
		    g = wolfeLineSearchResult.g_new;
		    int LSfunEvals = wolfeLineSearchResult.funEvals;
		    
		    funEvals = funEvals + LSfunEvals;
		    for (int j = 0; j < x.length; j++)
		    	x[j] += t * d[j];
					    
			// Compute Optimality Condition
			optCond = Double.MIN_VALUE;
			for (int j = 0; j < g.length; j++)
			{
				double absValG = Math.abs(g[j]);
				if (optCond < absValG)
					optCond = absValG;
			}
			
		    // Check Optimality Condition
		    if (optCond <= optTol)
		    {
		        exitflag=1;
		        msg = "Optimality Condition below optTol";
		        break;
		    }
		    
		    // ******************* Check for lack of progress *******************

			double maxAbsTD = Double.MIN_VALUE;
			for (int j = 0; j < d.length; j++)
			{
				double absValG = Math.abs(t * d[j]);
				if (maxAbsTD < absValG)
					maxAbsTD = absValG;
			}
		    if (maxAbsTD <= progTol)
		    {
		    	exitflag=2;
		        msg = "Step Size below progTol";
		        break;
			}

		    if (Math.abs(f-f_old) < progTol)
		    {
		        exitflag=2;
		        msg = "Function Value changing by less than progTol";
		        break;
		    }
		    
		    // ******** Check for going over iteration/evaluation limit *******************

		    if (funEvals*funEvalMultiplier >= maxFunEvals)
		    {
		        exitflag = 0;
		        msg = "Reached Maximum Number of Function Evaluations";
		        break;
		    }

		    if (i == maxIter)
		    {
		        exitflag = 0;
		        msg="Reached Maximum Number of Iterations";
		        break;
		    }
		}
		
		IJ.log("Msg: " + msg);
		
	    MinFuncResult minFuncResult = new MinFuncResult();
	    minFuncResult.x = x;
	    minFuncResult.f = f;
	    return minFuncResult;
	}

	private CidreModel cidreModel(CidreOptions options)
	{
		CidreModel model = null;

		// set default values for options that are not specified 
		if (options.qPercent == null) {
			options.qPercent = 0.25; 
		}
		if (options.lambdaZero == null) {
			options.lambdaZero = 0.5;
		}
		if (options.maxLbgfsIterations == null) {
			options.maxLbgfsIterations = 500;
		}
		if (options.lambdaVreg == null) {
			options.lambdaVreg = getLambdaVfromN(options.numImagesProvided);
		}
		
		//get dimensions of the provided data stack, S
		int Z = S.size();

		STACKMIN = Double.MAX_VALUE;
		for (int z = 0; z < S.size(); z++)
		{
			for (int c = 0; c < S_C; c++)
				for (int r = 0; r < S_R; r++)
					if (STACKMIN > S.get(z)[c][r])
						STACKMIN = S.get(z)[c][r];
		}		
		LAMBDA_VREG = Math.pow(10, options.lambdaVreg);
		LAMBDA_ZERO = Math.pow(10, options.lambdaZero);

		// initial guesses for the correction surfaces
		double[] v0 = new double[S_C * S_R];
		double[] b0 = new double[S_C * S_R];
		for (int c = 0; c < S_C; c++) {
				for (int r = 0; r < S_R; r++) {
					v0[c * S_R + r] = 1.0;
					b0[c * S_R + r] = 0.0;
				}
		}

		ZLimitsResult zLimitsResult = getZLimits(options);
		ZMIN = zLimitsResult.zmin;
		ZMAX = zLimitsResult.zmax;
		double zx0 = zLimitsResult.zx0;
		double zy0 = zLimitsResult.zy0;

		// get an estimate of Q, the underlying intensity distribution
		Q = estimateQ(options.qPercent);

		// Transform Q and S (which contains q) to the pivot space. The pivot
		// space is just a shift of the origin to the median datum. First, the shift
		// for Q:
		int mid_ind = (Z - 1) / 2;
		PivotShiftX = Q[mid_ind];
		
		for (int i = 0; i < Q.length; i++)
			Q[i] = Q[i] - PivotShiftX;

		// next, the shift for each location q
		double[][] doubleArray;
		PivotShiftY = new double[S_C * S_R];
		doubleArray = S.get(mid_ind);
		for (int x = 0; x < S_C; x++)
			for (int y = 0; y < S_R; y++)
				PivotShiftY[x * S_R + y] = doubleArray[x][y];		
		
		for (int z = 0; z < Z; z++) {
			doubleArray = S.get(z);
			for (int c = 0; c < S_C; c++) {
				for (int r = 0; r < S_R; r++) {
					doubleArray[c][r] -= PivotShiftY[c * S_R + r];
				}
			}
		}

		// also, account for the pivot shift in b0
		//b0 = b0 + PivotShiftX*v0 - PivotShiftY;
		for (int c = 0; c < S_C; c++)
			for (int r = 0; r < S_R; r++)
				b0[c * S_R + r] = b0[c * S_R + r] + PivotShiftX * v0[c * S_R + r] - PivotShiftY[c * S_R + r];

		IJ.log(String.format(" Optimizing using the following parameters:\n lambda_v  = %1.2f\n lambda_z  = %1.2f\n q_percent = %1.2f\n z_limits = [%.0f %.0f]\n(this may take a few minutes)\n", options.lambdaVreg, options.lambdaZero, options.qPercent, ZMIN, ZMAX));

		long t1 = System.currentTimeMillis();

		ITER = 1;
		MESTIMATOR = Mestimator.LS;
		TERMSFLAG = 0;

		// vector containing initial values of the variables we want to estimate
		//x0 = [v0(:); b0(:); zx0; zy0];
		double[] x0 = new double[2 * S_C * S_R + 2];
		
		int pX = 0;
		for (int i = 0; i < v0.length; i++)
			x0[pX++] = v0[i];
		for (int i = 0; i < b0.length; i++)
			x0[pX++] = b0[i];
		x0[pX++] = zx0;
		x0[pX++] = zy0;		

		MinFuncOptions minFuncOptions = new MinFuncOptions();
		minFuncOptions.maxIter      = options.maxLbgfsIterations;	// max iterations for optimization
		minFuncOptions.MaxFunEvals  = 1000;							// max evaluations of objective function
		minFuncOptions.progTol      = 1e-5;							// progress tolerance
		minFuncOptions.optTol       = 1e-5;							// optimality tolerance
		minFuncOptions.Corr         = 100;							// number of corrections to store in memory (default: 100)*/
		

		MinFuncResult minFuncResult = minFunc(x0, minFuncOptions);		
		double[] x  = minFuncResult.x;
		double fval = minFuncResult.f;

		// unpack
		double[] v1 = Arrays.copyOfRange(x, 0, S_C * S_R);
		double[] b1 = Arrays.copyOfRange(x, S_C * S_R, 2*S_C * S_R);
		double zx1 = x[2 * S_C * S_R];
		double zy1 = x[2 * S_C * S_R + 1];
		
		// 2nd optimization using REGULARIZED ROBUST fitting
		// use the mean standard error of the LS fitting to set the width of the
		// CAUCHY function
		double mse = computeStandardError(v1, b1);
		CAUCHY_W = mse;

		// assign the remaining global variables needed in cdr_objective
		ITER = 1;
		MESTIMATOR = Mestimator.CAUCHY;
		TERMSFLAG = 1;                          

		// vector containing initial values of the variables we want to estimate
		double[] x1 = new double[2 * S_C * S_R + 2];
		
		int pX1 = 0;
		for (int i = 0; i < v1.length; i++)
			x1[pX1++] = v1[i];
		for (int i = 0; i < b1.length; i++)
			x1[pX1++] = b1[i];
		x1[pX1++] = zx1;
		x1[pX1++] = zy1;		

		minFuncResult = minFunc(x1, minFuncOptions);		
		x = minFuncResult.x;
		fval = minFuncResult.f;		

		// unpack the optimized v surface, b surface, xc, and yc from the vector x
		double[] v = Arrays.copyOfRange(x, 0, S_C * S_R);
		double[] b_pivoted = Arrays.copyOfRange(x, S_C * S_R, 2*S_C * S_R);
		double zx = x[2 * S_C * S_R];
		double zy = x[2 * S_C * S_R + 1];

		// Build the final correction model 

		// Unpivot b: move pivot point back to the original location
		double[] b_unpivoted = new double[S_C * S_R];
		for (int c = 0; c < S_C; c++)
			for (int r = 0; r < S_R; r++)
				b_unpivoted[c * S_R + r] = PivotShiftY[c * S_R + r] + b_pivoted[c * S_R + r] - PivotShiftX * v[c * S_R + r];

		// shift the b surface to the zero-light surface
		double[] z = new double[S_C * S_R];
		for (int c = 0; c < S_C; c++)
			for (int r = 0; r < S_R; r++)
				z[c * S_R + r] = b_unpivoted[c * S_R + r] + zx * v[c * S_R + r];

		model = new CidreModel();
		model.imageSize = new Dimension(options.imageSize.width, options.imageSize.height);		
		model.v = imresize_bilinear(v, S_C, S_R, options.imageSize.width, options.imageSize.height);
		model.z = imresize_bilinear(z, S_C, S_R, options.imageSize.width, options.imageSize.height);
		model.imageSize_small = new Dimension(options.workingSize.width, options.workingSize.height);
		model.v_small   = v;
		model.z_small   = z;
		
		long t2 = System.currentTimeMillis();
	    IJ.log(String.format(" Finished in %1.2fs.", (t2 - t1)/1000.0));
		
		// Save the correction model to the destination folder

		IJ.log(String.format("Saving correction model CSV files to the Destination folder... "));
    	writeToCSVFile(options.folderDestination + "cidre_model_imagesize.csv", new double[] {options.imageSize.width, options.imageSize.height}, 2, 1);
    	writeToCSVFile(options.folderDestination + "cidre_model_imagesize_small.csv", new double[] {options.workingSize.width, options.workingSize.height}, 2, 1);	    
    	writeToCSVFile(options.folderDestination + "cidre_model_v.csv", model.v, options.imageSize.width, options.imageSize.height);
    	writeToCSVFile(options.folderDestination + "cidre_model_z.csv", model.z, options.imageSize.width, options.imageSize.height);
    	writeToCSVFile(options.folderDestination + "cidre_model_v_small.csv", model.v_small, options.workingSize.width, options.workingSize.height);
    	writeToCSVFile(options.folderDestination + "cidre_model_z_small.csv", model.z_small, options.workingSize.width, options.workingSize.height);

		/*// Save the correction model 'representation' images to the destination folder, experimental
    	ImagePlus imp;
    	ImageProcessor ip;
    	float[][] floatArray;

		// V
    	IJ.open(options.folderSource + options.fileNames.get(0));
    	imp = IJ.getImage();
    	ip = imp.getProcessor();
    	floatArray = ip.getFloatArray();
    	float minValue = Float.MAX_VALUE;
    	for (int c = 0; c < ip.getWidth(); c++) { 
    		for (int r = 0; r < ip.getHeight(); r++) {
    			if (minValue > (float)(model.v[c * ip.getHeight() + r]))
    				minValue = (float)(model.v[c * ip.getHeight() + r]);
    		}    		
    	}    	
    	for (int c = 0; c < ip.getWidth(); c++) { 
    		for (int r = 0; r < ip.getHeight(); r++) {
    			floatArray[c][r] = (float)(model.v[c * ip.getHeight() + r] - minValue) * options.bitDepth;
    		}
    	}
		ip.setFloatArray(floatArray);		
    	IJ.saveAs("TIFF", options.folderDestination + "cidre_model_v.tif");
    	imp.close();
    	
    	// Z
    	IJ.open(options.folderSource + options.fileNames.get(0));
    	imp = IJ.getImage();;
    	ip = imp.getProcessor();
    	floatArray = ip.getFloatArray();	    	
    	for (int c = 0; c < ip.getWidth(); c++) { 
    		for (int r = 0; r < ip.getHeight(); r++) {
    			floatArray[c][r] = (float)model.z[c * ip.getHeight() + r];
    		}
    	}
		ip.setFloatArray(floatArray);		
    	IJ.saveAs("TIFF", options.folderDestination + "cidre_model_z.tif");
    	imp.close();
    	
    	// V small
    	IJ.open(options.folderSource + options.fileNames.get(0));
    	imp = IJ.getImage();
    	ip = imp.getProcessor();
    	imp.setProcessor(ip.resize(S_C, S_R));
    	floatArray = new float[S_C][S_R];	    	
    	for (int c = 0; c < S_C; c++) { 
    		for (int r = 0; r < S_R; r++) {
    			floatArray[c][r] = (float)(model.v_small[c * S_R + r] * options.bitDepth);
    		}
    	}
		imp.getProcessor().setFloatArray(floatArray);		
    	IJ.saveAs("TIFF", options.folderDestination + "cidre_model_v_small.tif");
    	imp.close();
    	
    	// Z small
    	IJ.open(options.folderSource + options.fileNames.get(0));
    	imp = IJ.getImage();
    	ip = imp.getProcessor();
    	imp.setProcessor(ip.resize(S_C, S_R));
    	floatArray = new float[S_C][S_R];	    	
    	for (int c = 0; c < S_C; c++) { 
    		for (int r = 0; r < S_R; r++) {
    			floatArray[c][r] = (float)(model.z_small[c * S_R + r]);
    		}
    	}
		imp.getProcessor().setFloatArray(floatArray);		
    	IJ.saveAs("TIFF", options.folderDestination + "cidre_model_z_small.tif");
    	imp.close();*/
		
		IJ.log(String.format(" done"));
		
		return model;
	}

	private void cdr_correct(CidreModel model)
	{
    	ImagePlus imp;
    	ImageProcessor ip;
    	float[][] floatArray;
    	CidreOptions.CorrectionMode correctionMode = null;

    	 for (Enumeration<AbstractButton> jCheckBoxes = correctCheckboxGroup.getElements(); jCheckBoxes.hasMoreElements(); ) {
         	JCheckBox jCheckBox = (JCheckBox) jCheckBoxes.nextElement();

             if (jCheckBox.isSelected()) {
             	if (jCheckBox == correctCheckBoxDynamicRangeCorrected)
             		correctionMode = CidreOptions.CorrectionMode.dynamic_range_corrected;
             	else if (jCheckBox == correctCheckBoxDirect)
             		correctionMode = CidreOptions.CorrectionMode.direct;
             	else 
             		correctionMode = CidreOptions.CorrectionMode.zero_light_perserved;
             }
         }
		
		String folderSource = sourceImagesTextField.getText() + File.separator; 
		String folderDestination = destinationImagesTextField.getText() + File.separator;
		List<String> fileNames = new ArrayList<String>();

		// loop through all the source images, correct them, and write them to the destination folder
		String str = "";
		switch (correctionMode)
		{
		    case zero_light_perserved:
		        str = "zero-light preserved";
		        break;
		    case dynamic_range_corrected:
		    	str = "dynamic range corrected";
		    	break;
		    case direct:
		        str = "direct";
		        break;
		}
		IJ.log(String.format("  Writing %s corrected images to %s" , str.toUpperCase(), folderDestination));

	    long t1 = System.currentTimeMillis();
	    
	   	// generate a list of source filenames searching for all valid filetypes
    	File folder = new File(sourceImagesTextField.getText());
    	File[] listOfFiles = folder.listFiles(new ImageNameFilter(sourceImageMaskTextField.getText())); 
    	  
    	if (listOfFiles != null) {
	    	for (int i = 0; i < listOfFiles.length; i++) {
				fileNames.add(listOfFiles[i].getName());
	    	}
    	}

    	if (fileNames.size() > 0) {
			double mean_v = mean(model.v);
			double mean_z = mean(model.z);
		    for (int z = 0; z < fileNames.size(); z++)
		    {
		        if (z > 0 && z % 100 == 0) IJ.log(".");	// progress to the command line
		    	IJ.open(folderSource + fileNames.get(z));
		    	imp = IJ.getImage();
		    	ip = imp.getProcessor();
	
		    	floatArray = ip.getFloatArray();	    	
		    	
			    // check which type of correction we want to do
				switch (correctionMode)
				{
			        case zero_light_perserved:
			            //Icorrected = ((I - model.z)./model.v) * mean(model.v(:))  + mean(model.z(:));
			        	{
			    	    	for (int c = 0; c < ip.getWidth(); c++) { 
			    	    		for (int r = 0; r < ip.getHeight(); r++) {
			    	    			floatArray[c][r] = (float)(((((double)floatArray[c][r] - model.z[c * ip.getHeight() + r]) / model.v[c * ip.getHeight() + r]) * mean_v) + mean_z);
			    	    		}
			    	    	}
			        	}
			            break;		                    
			        case dynamic_range_corrected:
			            //Icorrected = ((I - model.z)./model.v) * mean(model.v(:));
			        	{
			    	    	for (int c = 0; c < ip.getWidth(); c++) { 
			    	    		for (int r = 0; r < ip.getHeight(); r++) {
			    	    			floatArray[c][r] = (float)(((((double)floatArray[c][r] - model.z[c * ip.getHeight() + r]) / model.v[c * ip.getHeight() + r]) * mean_v));
			    	    		}
			    	    	}
			        	}
			        	break;
			        case direct:    
			            //Icorrected = ((I - model.z)./model.v);
			        	{
			    	    	for (int c = 0; c < ip.getWidth(); c++) { 
			    	    		for (int r = 0; r < ip.getHeight(); r++) {
			    	    			floatArray[c][r] = (float)(((((double)floatArray[c][r] - model.z[c * ip.getHeight() + r]) / model.v[c * ip.getHeight() + r])));
			    	    		}
			    	    	}
			        	}
			        	break;		            
			        default:
						IJ.error("CIDRE:correction", "Unrecognized correction mode.");
			            break;
				}
				
				ip.setFloatArray(floatArray);
				
		    	IJ.save(folderDestination + fileNames.get(z));
		    	imp.close();
		    }
    	}

	    long t2 = System.currentTimeMillis();
	    IJ.log(String.format("  finished in %1.2fs.", (t2 - t1)/1000.0));
	}

	private void writeToCSVFile(String fileName, double[] array, int width, int height) 
	{
        BufferedWriter writer = null;
        try {
            File file = new File(fileName);

            writer = new BufferedWriter(new FileWriter(file));
            for(int y = 0; y < height; y++) {
            	for (int x = 0; x < width; x++) {
            		if (x > 0)
                		writer.write(",");
            		writer.write(String.format("%f", array[x * height + y]));
            	}
                writer.write("\n");
            }            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
			if (writer != null) {
	            try {
	                writer.close();
	            } catch (IOException e) {
					e.printStackTrace();
	            }
			}
        }
	}
	
	private double[] readFromCSVFile(String fileName, int width, int height)
	{
		int rows = 0;
		double[] result = new double[width * height];
		BufferedReader reader = null;
		String line = "";
		String cvsSeparator = ",";
	 
		try {	 
			reader = new BufferedReader(new FileReader(fileName));
			rows = 0;
			
			while ((line = reader.readLine()) != null) {
				String[] values = line.split(cvsSeparator);
				if (values.length != width)
					throw new Exception("Value count: " + values.length + " (Expected: " + width + ") in line " + rows);
				for (int i = 0; i < values.length; i++)
					result[i * height + rows] = Double.parseDouble(values[i]);
				rows++;
				if (rows > height)
					throw new Exception("Current row count: " + rows + " (Expected: " + height + ")");
			}
			if (rows != height)
				throw new Exception("Row count: " + rows + " (Expected: " + height + ")");
		} catch (Exception e) {
			//e.printStackTrace();
			result = null;
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
					result = null;
				}
			}
		}
		return result;
	}

	// Implementing the ImageJ PlugIn interface
	@Override
	public void run(String arg0) {
        if (IJ.versionLessThan("1.43t")) return;
        GUICreate();
	}

	// Main: only for testing
	public static void main(String args[])
	{
		Cidre_Plugin cidre = new Cidre_Plugin();
		cidre.GUICreate();
	}
	
}
