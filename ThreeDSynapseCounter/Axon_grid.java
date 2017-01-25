import ij.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import java.awt.*;
import ij.gui.*;
import ij.plugin.Duplicator;

/** This ImageJ plugin filter sections an image into grids

*/

public class Axon_grid implements PlugInFilter {
	static String DEST_FOLDER = "plugin_dump";

	public int setup(String arg, ImagePlus imp) {
		return DOES_ALL+SUPPORTS_MASKING;
	}
	
	public void run(ImageProcessor ip) {
		
		//copy the image to preserve original
		ImagePlus currentImage = WindowManager.getCurrentImage();
		Duplicator d = new Duplicator();
		ImagePlus copy = new ImagePlus();
		copy = d.run(currentImage);
		copy.show();
		
		
		//set up grid size
		int GRID_WIDTH  = 4; // rows
		int GRID_HEIGHT = 4; // cols
		double cellWidth  = ip.getWidth()* .25;
		double cellHeight = ip.getHeight()* .25; 
		IJ.log("cellwidth = " + cellWidth + " cellHeight = " + cellHeight);
		
		//create the overlay to hold the ROIS
		Overlay ov = new Overlay();
		ov.drawBackgrounds(true);
		ov.drawLabels(true);
		ov.drawNames(true);
		Roi roi;
		String name;
		
		//loop across and down image, creating ROIS
		//adding them to overlay
		for( int x = 0; x < GRID_WIDTH; x++) {
				for (int y =0; y < GRID_HEIGHT; y++) {
						roi = new Roi( x*cellWidth , y* cellHeight, cellWidth, cellHeight);
						name = "axon" + (1+y+(4*x)) +
							"_" + (int)(x*cellWidth) +
							"_" + (int)(y*cellHeight) +
							"_" + "1" + 
							"_" + (int)cellWidth +
							"_" + (int)cellHeight + 
							"_" + 19;
						roi.setName(name);
						IJ.log( roi.getName() + ": " + roi.toString() );
						ov.add( roi );
				}	
		}
		
		//assign the completed overlay to image
		copy.setOverlay(ov);
		IJ.log( "Overlay size: " + ov.size());
		//IJ.save(copy, "C:\\Users\\Jonathan\\Desktop\\AxonGroundTruth\\"+DEST_FOLDER+"\\axon_regions.tif");
		IJ.save(copy, "C:\\Users\\RA3\\Desktop\\axon things\\AxonGroundTruth\\"+DEST_FOLDER+"\\axon_regions.tif");
				 	
		
		//crop out each roi
		Roi cropRoi; 
		ImagePlus cropCopy, cropped;
		ImageProcessor cropIp;
		for(int i = 0; i < ov.size(); i++) {
			cropRoi = ov.get(i);
			copy.setRoi(cropRoi);
			cropped = d.run(copy);
			cropped.setTitle(cropRoi.getName());
			//cropped.show();
			IJ.log("region " + cropRoi.getName() + " cropped.");
			//save the cropped regions
			IJ.save(cropped, "C:\\Users\\RA3\\Desktop\\axon things\\AxonGroundTruth\\"+DEST_FOLDER+"\\"+ cropped.getTitle() + ".tif");
			IJ.log("region " + cropRoi.getName() + " saved.");
			
		}
	
	}

	
	
}

