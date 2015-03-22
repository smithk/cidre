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

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

public class CidreOptions
{
	public enum CorrectionMode  { zero_light_perserved, dynamic_range_corrected, direct};
	
	public Double lambdaVreg = null;
	public Double lambdaZero = null;
	public Integer maxLbgfsIterations = null;
	public Double qPercent = null;
	public Double[] zLimits = new Double[2];
	public Dimension imageSize;
	public String folderSource;
	public String fileFilterSource;
	public String folderDestination;
	public List<String> fileNames = new ArrayList<String>();
	public int numImagesProvided;
	public Integer bitDepth = null;
	public CorrectionMode correctionMode = null;
	public int targetNumPixels = 9400;
	public Dimension workingSize;
	public int numberOfQuantiles = 200;	
}
