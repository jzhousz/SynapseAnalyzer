
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import annotool.io.DataInput;

/*
 * 
 //This class contains the utilities needed for ThreeDROIAnnotation.java
 //including boundry checking and local maxima detection 
 */


/*
 * CHANGE LOG Jon Sanders
 * 10/31/13 - updated meanv calculation for more dynamic thresholding.
 * 11/04/13 - fix bugs in meanv calculation: incorrect converge and rounding error.
 * 04/10/14 - supports passed in threshold alternative to meanv calculation
 * 09/05/14 - finally supports RATS (first draft, needs testing)
 * 09/16/15 - corrected pixel access bug in inner scan loop (x,y) -> (i,j)
 * 09/18/15 - Merged thresholding and maxima detection, reducing computation expense
 *			- cleaned up comments and spacing
 */
public class AnnotatorUtility {
	/**
	 * Returns true if the given point is within the rectangular area, otherwise false
	 * 
	 */

	public static boolean isWithinBounds(int x, int y, int width, int height) {
	    return (x >= 0 && x < width && y >= 0 && y < height);
	}
	
	public static boolean isWithinBounds(int x, int y, int z, int width, int height, int depth) {
	    return (x >= 0 && x < width && y >= 0 && y < height && z >=0 && z <depth);
	}
	
	//Calculates the 1D array index for given x and y
	public static int offset(int x, int y, int width) {
	    return x + y * width;
	}	
	
	/**
	 * Determines which pixels/voxels in the image passed is local maxima and returns the boolean array or result
     * @Deprecated 4-14-14
	 */
	public static boolean[] getLocalMaxima(float[] data, int width, int height, int depth,
											int wX, int wY, int wZ) {
		int size = width * height * depth;
		boolean[] isMaxima = new boolean[size];
		float[] maxVal = new float[size];
		
		int lowth = 3;
		float sum = 0;
		int num = 0;
		int index = 0;
		int offsetj = 0;
		int offsetk = 0;
		float meanv; 
		
		//Adding all pixels
		for(int z = 0; z < depth; z++) {
			offsetk = z*width*height;		    
			for(int y = 0; y < height; y++) {
				offsetj = y * width;
		        for(int x = 0; x < width; x++) {
		        	index = offsetk + offsetj + x;
	                if(data[index] > lowth) {
	                	sum += data[index];
	                	num ++;
		            }
		        } //End of x
		    } //End of y
		} //End of z
		
		meanv = sum / num;
		
		int numIteration = 3;
		for(int it = 0; it < numIteration; it++) {
			float sum1 = 0, sum2 = 0;
			int num1 = 0, num2 = 0;
			
			for(int z = 0; z < depth; z++) {
				offsetk = z*width*height;		    
				for(int y = 0; y < height; y++) {
					offsetj = y * width;
			        for(int x = 0; x < width; x++) {
			        	index = offsetk + offsetj + x;
			        	
			        	if(data[index] > meanv) {
			        		sum1 += data[index];
			        		num1++;
			        	}
			        	else if(data[index] > lowth) {
			        		sum2 += data[index];
			        		num2++;
			        	}
			        } //End of x
			    } //End of y
			} //End of z
			
			//Adjust new threshold
		     meanv = (int)(0.5 * (sum1/num1 + sum2/num2)); //Is typecasting necessary?
		     
		} //End of iteration
		
		float max = 0;
		int offsetkl, offsetjl;
		float val;
		
		for(int z = 0; z < depth; z++) {
			offsetk = z*width*height;		    
			for(int y = 0; y < height; y++) {
				offsetj = y * width;
		        for(int x = 0; x < width; x++) {
		        	index = offsetk + offsetj + x;
		        	max = 0;
		        	
		        	int xb = x - wX; if(xb < 0) xb = 0;
		        	int xe = x + wX; if(xe >= width - 1) xe = width - 1;
		        	int yb = y - wY; if(yb < 0) yb = 0;
		        	int ye = y + wY; if(ye >= height - 1) ye = height - 1;
		        	int zb = z - wZ; if(zb < 0) zb = 0;
		        	int ze = z + wZ; if(ze >= depth - 1) ze = depth - 1;
                    
		        	for(int k = zb; k <= ze; k++) {
                        offsetkl = k*width*height;
                        for(int j = yb; j <= ye; j++) {
                            offsetjl = j*width;
                            for(int i = xb; i <= xe; i++) {
                                val = data[offsetkl + offsetjl + i];
                                if(max < val) max = val;
                            }
                        }
                   }
		        	
		        	maxVal[index] = max;
		        } //End of x
		    } //End of y
		} //End of z
		
		for(int z = 0; z < depth; z++) {
			offsetk = z*width*height;		    
			for(int y = 0; y < height; y++) {
				offsetj = y * width;
		        for(int x = 0; x < width; x++) {
		        	index = offsetk + offsetj + x;
	                if((data[index] == maxVal[index]) && (data[index] > meanv))
	                	isMaxima[index] = true;
	                else
	                	isMaxima[index] = false;
		        } //End of x
		    } //End of y
		} //End of z
		
		return isMaxima;
	}

//the faster version that does not require a copy of all data
//uses get or getPixel.  get is faster without doing boundary check.
//only operates on bytes (int), not float comparison
//Note that the order of storage is z, then y, then x

public static boolean[] getLocalMaxima(ImagePlus imp, int channel, int wX, int wY, int wZ, int threshold, int noise, int lambda, int minLeaf)
{
	///////// VARIABLES //////////////
	ImageProcessor ip = imp.getProcessor();
	
	boolean ratsFlag = false;
	RatsSliceProcessor ratsProcessor = null;
	
	int width = ip.getWidth();
	int height = ip.getHeight();
	int depth = imp.getStackSize();
	int imageType = imp.getType();
	if (imageType != DataInput.GRAY8 && imageType != DataInput.GRAY16 && imageType != DataInput.COLOR_RGB) {
		System.out.println("Only grayscale 8 or 16 or RGB supported");
		return null;
	}
	
	int size = width * height * depth;
	boolean[] isMaxima = new boolean[size];

	int index = 0;
    int pixelvalue;
    ImageProcessor currentimagep = null; 
	ImageProcessor subimagep = null;

    float currentThresholdValue = 0;
    
    
    /////// THRESHOLDING ///////////
	//user spec global
    if(threshold>0) {
        currentThresholdValue = (float) threshold;
        System.out.println("User Specified meanv of: " + currentThresholdValue);
        }
	//do RATS currantThresholdValue will be updated each pixel	
	else if(threshold == -1) {
		currentThresholdValue = threshold;
		System.out.println("Switching to RATS mode.");
		System.out.println("using rats options: n:" + noise + " l:" + lambda + " m:" +minLeaf);
		ratsFlag = true;
	}
	//use a global threshold, calculated by meanshift
    else if(threshold == 0) {	
        currentThresholdValue = calcThreshold(imp, channel);
    }
  
  
   ////// processing /////////
   System.out.println("Processing ..");
   
   boolean scanFlag = false;
   int total = 0;
   ImageStack ratsStack = new ImageStack(width, height);
   int max = 0;
   int val;

   int xb;
   int xe;
   int yb;
   int ye;
   int zb;
   int ze;
	
   index =0;
   for(int z = 0; z < depth; z++) {
	System.out.println("processing slice: " + (z+1));  
	
	currentimagep = imp.getStack().getProcessor(z+1); //set processor to slice
			
    //setup for inline rats
    if(ratsFlag == true) {
        System.out.println("RATS on slice "+(z+1));
        ratsProcessor = new RatsSliceProcessor(noise, lambda, minLeaf, new ImagePlus("", currentimagep),0);

        ratsStack.addSlice(ratsProcessor.getMask().getProcessor());
    
    }   
	
	for(int y = 0; y < height; y++) {
		for(int x = 0; x < width; x++) {
			
			//get pixel value
			if (imageType != DataInput.COLOR_RGB)
				pixelvalue = currentimagep.get(x,y);
			else
				pixelvalue = (currentimagep.getPixel(x,y, null))[channel];
		
            max = -1;  //set max to impossible value
            scanFlag=false;
			
            //decide to do local scan based on threshold
            //Use RATS
			if(ratsFlag) {
				if( ratsProcessor.getMaskValue(x,y,0) > 0)
				{
                    scanFlag = true;
                }
			//Use GLOBAL	
            } else {
                if( currentimagep.get(x,y) > currentThresholdValue ) {
                    scanFlag = true;
                }
            }
            
            //if above threshold, do local scan 
            if (scanFlag){
                max = 0;

                xb = x - wX; if(xb < 0) xb = 0;
                xe = x + wX; if(xe >= width - 1) xe = width - 1;
                yb = y - wY; if(yb < 0) yb = 0;
                ye = y + wY; if(ye >= height - 1) ye = height - 1;
                zb = z - wZ; if(zb < 0) zb = 0;
                ze = z + wZ; if(ze >= depth - 1) ze = depth - 1;

                for(int k = zb; k <= ze; k++) {
                    subimagep = imp.getStack().getProcessor(k+1);
                    for(int j = yb; j <= ye; j++) {
                        for(int i = xb; i <= xe; i++) {
                            if (imageType != DataInput.COLOR_RGB)
                             val = subimagep.get(i,j);
                            else
                             val = (subimagep.getPixel(i,j, null))[channel];

                            if(max < val) max = val;
                        }
                    }
                }
            }
            
			//if in threshold, and is == to max, then is a Maxima
			if( pixelvalue == max ) {
				isMaxima[index] = true;
				total++;
			} else {
				isMaxima[index] = false;
			}	
			
			index++;
		  } //End of x
	   } //End of y
    } //End of z

 System.out.println("total local maximum pixels:"+total);

 //show the mask image as a complete stack 
 if(ratsFlag == true){
	new ImagePlus("ratsMask" , ratsStack).show();
 }
 
 return isMaxima;
 }
  
  
  
 //auto threshold an image
 public static float calcThreshold(ImagePlus imp, int channel) {
 
        float sum1, sum2;
        float sum = 0;
        int num1, num2;
        int num= 0;
        float meanv, meanvLast;
        int convergeCheck = 0;
        int iterationCount = 0, iterationCap = 50;
        int pixelvalue;
        int index;
        ImageProcessor currentimagep = null; 
        int lowth = 3;
 
 
        int height = imp.getHeight();
        int width = imp.getWidth();
        int depth = imp.getStackSize();
        int imageType = imp.getType();


        
        
        System.out.println("calculating mean ..");
        //iteratively find the valley of histogram
        //Adding all pixels
        for(int z = 0; z < depth; z++) {
            currentimagep = imp.getStack().getProcessor(z+1);
                //offsetk = z*width*height;		    
            for(int y = 0; y < height; y++) 
            {
             //offsetj = y * width;
                for(int x = 0; x < width; x++) {
                    //index = offsetk + offsetj + x;
                    if (imageType != DataInput.COLOR_RGB)
                     pixelvalue = currentimagep.get(x,y);
                    else
                     pixelvalue = (currentimagep.getPixel(x,y, null))[channel];

                    if(pixelvalue > lowth) 
                    {
                        sum += pixelvalue;
                        num ++;
                    }
             } //End of x
           } //End of y
        } //End of z

        meanv = sum / num;

        //loop until meanv converges
        while(true){
            sum1 = 0; sum2 = 0;
            num1 = 0; num2 = 0;
        
            for(int z = 0; z < depth; z++) {
                currentimagep = imp.getStack().getProcessor(z+1);
                for(int y = 0; y < height; y++) {
                    for(int x = 0; x < width; x++) {
                        
                        if(imageType != DataInput.COLOR_RGB)
                             pixelvalue = currentimagep.get(x,y);
                            else
                             pixelvalue = (currentimagep.getPixel(x,y, null))[channel];
        
                        if(pixelvalue > meanv) {
                            sum1 += pixelvalue;
                            num1++;
                        }
                        else if(pixelvalue > lowth) {
                            sum2 += pixelvalue;
                            num2++;
                        }
                    } //End of x
                } //End of y
            } //End of z
        
            //store old meanv.
            //Adjust new threshold
            meanvLast = meanv;
            meanv = (int)(0.5 * (sum1/num1 + sum2/num2)); //Is typecasting necessary?
            iterationCount++;
            
            System.out.println("meanv: " + meanv);
            //check to end iteration
            if(meanv == meanvLast)
                convergeCheck++;  
            else
                convergeCheck = 0;
            
            //run until meanv stays constant for 3 iterations or reaches hard cap
            if((convergeCheck >= 2) || (iterationCount >= iterationCap))
                break;    
       } //End of iteration
       System.out.println("Threshold calculation converged at: " + meanv + " after " + iterationCount + " iterations.");
       
       return meanv;
    }
}//endclass

