/**
 * RatsSliceProcessor3D.java
 * 
 * This is a wrapper class with additional functions to allow 
 * RATSForAxon.java to be used more easily on 3D images.
 *
 */

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.*;
 
 
 
public class RatsSliceProcessor {
	
	ImagePlus     rawImage3D;
	ImagePlus     ratsMask3D; //The full 3D mask of the image
	String        params;     //Rats parameter string in form "noise=5 lambda=3 min=64"
	int 		  channel;
	
	/*
	* Constructor.
	* 
	* Params: rats options and a 3d imgaePlus to be processed.
	*/
	RatsSliceProcessor(int noise, int lambda, int minLeaf, ImagePlus imp3D, int c) {
		rawImage3D = imp3D;
		ratsMask3D = null;
		channel = c;
		//construct params string for RATS 
		params = "noise=" + noise + " lambda="+ lambda + " min="+minLeaf;

		//call process() immediately, no need to wait
		process();
	}
	
	
	
	/*
	* getMask()
	* get the mask imagePlus
	* 
	*/
	public ImagePlus getMask() { return ratsMask3D.duplicate(); };
	
	/*
	* getMaskValue()
	* get the value (0 or 255) from the mask at a voxel 
	*
	* Params: x, y, z coords
	*/
	public int getMaskValue(int x, int y, int z) {
		ImageProcessor ip = ratsMask3D.getStack().getProcessor(z+1);
		int val = (ip.getPixel(x ,y, null))[0];
		
		return val;
	
	
	}
	
	/*
	* process()
	* Processor method to generate the mask image using rats one each slice. 
	* 
	* Params: none 
	*/
	private void process() {
	
		int width = rawImage3D.getWidth();
		int height = rawImage3D.getHeight();
		int depth = rawImage3D.getNSlices();
		
		RATSForAxon    rats;       //internal RATS processor to get mask
		ImageStack     ratsStack = new ImageStack(width, height);
		ImageProcessor ip;
		ColorProcessor cp;
		//create and setup RATS
		rats = new RATSForAxon();
		rats.setup("", new ImagePlus());

	
			
		//loop through rawImageand process each slice with rats
		for(int z=0; z < depth; z++) {
			//System.out.println("RATS on slice: "+z );
			//set imapgeProcessor to current slice
			
			
			
			if(rawImage3D.getType() == ImagePlus.COLOR_RGB) {
			
				System.out.println( "Reading color img!" );
				ip = rawImage3D.getStack().getProcessor(z+1);
				
				cp = (ColorProcessor) ip;
				
				//cp = (ColorProcessor)rawImage3D.getProcessor(z+1);
				
				ip = cp.getChannel(channel, new ByteProcessor(width, height));
			} 	
			else {
				System.out.println( "Reading grayscale img!" );
				ip = rawImage3D.getStack().getProcessor(z+1);
				
			}
			ratsStack.addSlice( rats.run(ip, params).getProcessor() );
				
		}
		//create the full imagPlus from stack.
		ratsMask3D = new ImagePlus("RATS Mask", ratsStack);
		//ratsMask3D.show();
	
	}//endmethod
	
	
	
	
	/*************************************************************************
	* Main()
	* test class functions 
	* 
	* Params: none 
	**************************************************************************/	
	public static void main(String[] args) {
	
		ImagePlus imp = new ImagePlus("C:\\Program Files (x86)\\ImageJ\\plugins\\Synapse_anno\\test_image.tif");
		//ImagePlus imp = new ImagePlus(args[0]);
	
		RatsSliceProcessor processor = new RatsSliceProcessor(5, 3, 5, imp, 2);
		
		processor.getMask().show();
		
		System.out.println("value at 0,0,0:    " + processor.getMaskValue(0,0,0));
		System.out.println("value at 43,28,10: " + processor.getMaskValue(43,28,10));
	
		System.out.println("well it worked.");
	
	
	}



}//endclass