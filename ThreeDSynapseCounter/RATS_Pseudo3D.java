/* RATS_pseudo3D.java - Jon Sanders 11/10/15
 * A plugin interface for RatsSliceProcessor3D to generate a mask for a complete image stack
 *
 *	INPUT: - The current selected grayscale image.
 *		   - the Rats parameters Sigma(noise), lambda(scaling), and MinLeaf size
 *
 *  OUTPUT:- A new image with the points from the .ij file drawn on in pure white 1px points
 */
 
 
 /* NOTES: 
  *		- This is NOT ACTUAL 3D RATS, it is only running 2D rats on every slice and concatenating them 
  * 	  into one stack!
  * TODO:
  * 	
  *
  */


import ij.*;
import ij.io.*;
import ij.IJ;
import ij.process.*;
import ij.gui.*;
import ij.plugin.*;
import ij.plugin.filter.PlugInFilter;
import java.util.*;
import java.io.*;

public class RATS_Pseudo3D implements PlugInFilter {
	//variables
	ImagePlus imp;
	private int minSzPx;
	private int sigma = 25; //aka "noise" as S.D. of image background
	private int lambda = 3; //scaling factor
    //gui elements
    GenericDialog gd;
	
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL;
	}


    public void run(ImageProcessor ip) {
		//suggest minimum leaf size
		int dimx,dimy;
		dimx = (int)(ip.getWidth()/5.0);
		dimy = (int)(ip.getHeight()/5.0);
		if(dimx < dimy) {
			minSzPx = dimx;
		} else {
			minSzPx = dimy;
		}
	
		if (showDialog() == false) {return;}
		
		RatsSliceProcessor3D processor = new RatsSliceProcessor3D(sigma, lambda, minSzPx, imp, 0);
		
		processor.getMask().show();
 
		
    }


	private boolean showDialog() {
        
		GenericDialog gd = new GenericDialog("RATS Pseudo_3D");
		gd.addNumericField("Noise Threshold:", sigma, 0);
		gd.addNumericField("Lambda Factor:", lambda, 0);
		gd.addNumericField("Min Leaf Size (pixels):", minSzPx, 0);
		
		gd.showDialog();
		if (gd.wasCanceled()) {return false;}
		
		sigma = (int)gd.getNextNumber();
		lambda = (int)gd.getNextNumber();
		minSzPx = (int)gd.getNextNumber();
		return true;
  }//showDialog
	
}//endPlugin
