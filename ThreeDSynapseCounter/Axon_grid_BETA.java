import ij.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import java.awt.*;
import ij.gui.*;
import ij.plugin.Duplicator;

/** This ImageJ plugin filter sections an image into grids
	
		CHANGELOG
		1/20/15 - wrote basic layout
		1/21/15 - added save functions, converted ROIS to overlay
		1/23/15 - added support for non 4x4 grids. works.

		TODO:
		-remove magic strings and numbers prep for gui
		-plugin GUI for 
			rows
			cols
			file names?
			save loc
*/

public class Axon_grid_BETA implements PlugInFilter {
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
		int GRID_WIDTH  = 3; // cols
		int GRID_HEIGHT = 7; // rows
		double cellWidth  = ip.getWidth() / (double) GRID_WIDTH;
		double cellHeight = ip.getHeight()/ (double) GRID_HEIGHT; 
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
							"_" + GRID_WIDTH + "x" + GRID_HEIGHT +
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
		IJ.save(copy, "C:\\Users\\RA3\\Desktop\\AxonGroundTruth\\"+DEST_FOLDER+"\\axon_regions.tif");
				 	
		
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
			IJ.save(cropped, "C:\\Users\\RA3\\Desktop\\AxonGroundTruth\\"+DEST_FOLDER+"\\"+ cropped.getTitle() + ".tif");
			IJ.log("region " + cropRoi.getName() + " saved.");
			
		}
	
	}

	
	
}

