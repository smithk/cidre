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
	public Dimension imageSize;
	public String folderSource;
	public String folderDestination;
	public List<String> fileNames = new ArrayList<String>();
	public int numImagesProvided;
	public Integer bitDepth = null;
	public CorrectionMode correctionMode = null;
	public int targetNumPixels = 9400;
	public Dimension workingSize;
	public int numberOfQuantiles = 200;
	
}
