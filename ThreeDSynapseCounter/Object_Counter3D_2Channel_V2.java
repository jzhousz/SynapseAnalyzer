/*

  

  1/27/2012  Jie Zhou   Add functions to split an object into 2 based on contour concavity
  (And take inputs from discriminative model to decide how many objects to split into)
  
  
  10/2/2011  Jie Zhou  3D object counter using 2 channels.
  //added several more GUI elements
  // Only works with RBG file.  Thresholding is not supported for RGB in ImageJ
  
  3D objects counter v1.4, 14/06/06
  Fabrice P Cordelires, fabrice.cordelieres at curie.u-psud.fr
  Thanks to Gabriel Landini for major feedbacks and suggestions
  
  New features since v1.0:
  Threshold can now be set up using a slider
  
  Improvements since v1.0:
  Modifications in algorithm: first step of ID attribution followed by a second step of ID minimization (regularization)
  
  2006/05/31: Version 1.3 (Modifications: Jonathan Jackson, j.jackson at ucl.ac.uk)
  
  * Added "New_Results Window" option: Use to preserve existing data in the main ImageJ "Results Table"
  Unselect this option if you wish to use the commands "Analyze->Summarize" and "Analyze->Distribution"
  * Results are given in floating point precision
  * Works in "Batch Mode"
  * Improved exectution speed
  * Added methods to facilitate calling from another plugin
  
  2006/06/14: Version 1.4 (Modifications: Jonathan Jackson, j.jackson at ucl.ac.uk)
  
  * Plugin remembers user selected options
  * Fixed bug causing particles to wrap-around image boarders
  2007/06/18: Version 1.5 (Modifications: Jonathan Jackson, jjackson at familyjackson dot net )
  (released  2008/06/17)
  * Measurements now spatially calibrated 
  * Affected values:
  *    Volume
  *    Centre of Mass / Centroid
  * 'Surface' value is now expressed as the fraction of voxels on the object surface
  
  2008/06/18: Version 1.5.1  (Modifications: Jonathan Jackson, jjackson at familyjackson dot net )
  * Fixes bug introduced in version 1.5 causing 'draw particles' to fail for particles centered in the first slice
  
  */



/* programmers notes:
 *
 * Code changes since v1.2
 *  Paramarray changed from int[] to double[] to increase precision
 *  Changed the way data is read into pict[] so that it works in batch mode
 *
 * Code changes since v1.3
 *  introduced withinBounds method to prevent wrap-around of particles
 *  replaced get...() and set...() methods with direct access to arrays for faster execution
 *      (use if(withinBounds(...)) before accessing array if there is the possibility of out of bounds access)
 *  Returns error when supplied a colour or float image (code changes are necessary to support these data types)
 *  uses arrayIndex when looping over x,y,z - faster execution, as no need to check if withinBounds
 *  thr[] int array replaced with boolean array
 *  used static fields with the suffix _default to remember user options
 *  (the new _default variables were introduced otherwise static fields could cause problems if there are two instances running simultaneously)
 *
 *
 */

import ij.*;
import ij.ImagePlus.*;
import ij.plugin.*;
import ij.plugin.filter.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import ij.util.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;

public class Object_Counter3D_2Channel_V2 implements PlugIn, AdjustmentListener, TextListener, Runnable {
    static final String pluginName = "Object Counter3D 2Channel V2";
    boolean debug = false;
    boolean verbose = false;
    Vector sliders;
    Vector value;

    public static int minSize_default = 10;
    public static int maxSize_default = Integer.MAX_VALUE;
    public static boolean showParticles_default = true;
    public static boolean showEdges_default = true;//false;
    public static boolean showCentres_default = true;//false;
    public static boolean showCentresInt_default = false;
    public static int DotSize_default = 3;
    public static boolean showNumbers_default = false;
    public static boolean new_results_default = true;
    public static int FontSize_default = 12;
    public static boolean summary_default = true;
    public static boolean or_default = false;
    public static boolean og_default = true;
    public static boolean ob_default = false;
    public static boolean br_default = false;
    public static boolean bg_default = false;
    public static boolean bb_default = true;
    public static int overriddenPixelDepth_default = 400;
    public static boolean despeckle_default = false;
    public static boolean smooth_default = false;

    int ThrVal =14;//30;
    int ThrVal2 =12;//30; //for 2nd channel
    int pixelDepthInNM;
    int minSize;
    int maxSize;
    boolean showParticles;
    boolean showEdges;
    boolean showCentres;
    boolean showCentresInt;
    int DotSize;
    boolean showNumbers;
    boolean new_results;
    int FontSize;
    boolean summary;
    boolean or, og, ob, br, bg, bb;

    int Width;
    int Height;
    int NbSlices;
    int arrayLength;
    String imgtitle;
    int PixVal;
    boolean despeckle, smooth;

    boolean[] thr; // true if above threshold
    int[] pict, pict2; // original pixel values
    int[] tag;
    boolean[] surf;
    int ID;
    int[] IDarray;
    double [][] Paramarray;

    ImagePlus img, img1;
    ImageProcessor ip, ip2;
    ImageStack stackParticles;
    ImageStack stackEdges;
    ImageStack stackCentres;
    ImageStack stackCentresInt;
    Calibration cal;
    ImagePlus Particles;
    ImagePlus Edges;
    ImagePlus Centres;
    ImagePlus CentresInt;
    ImagePlus imgPlus; 
    ImagePlus img2;
    ArrayList<ImagePlus> results;
    ArrayList<VoxelPoint> myCenters;// = new ArrayList(); 
    ResultsTable rt;
    Thread thread;
   


    public void run(String arg) {
        if (! setupGUI(arg)) return;
	begin();
	analyze();
	end();
    }
    
    
    void begin(){
	//Initialize the global variables here.
	Particles=NewImage.createShortImage("Particles "+imgtitle,Width,Height,NbSlices,0);
	imgPlus = NewImage.createByteImage ("test",Width,Height,1,NewImage.FILL_WHITE);	
	img1 = NewImage.createByteImage ("test2",Width,Height,1,NewImage.FILL_WHITE);	
	results = new ArrayList<ImagePlus>();
	myCenters = new ArrayList();
    }
    
    void end(){
	//Chores to be done after the analysis is complete.
	imgPlus.show(); 
	
	stackParticles=Particles.getStack();
	//Particles.setCalibration(cal);  //just show pixel?
	for (int z=1; z<=NbSlices; z++){
	    ip=stackParticles.getProcessor(z);
	    for(int t=0; t!=myCenters.size(); t++){
		VoxelPoint vp = myCenters.get(t);
		int x1  =  vp.x;
		int y1  =  vp.y;
		ip.putPixel(x1,y1,z);
	    }
	}
	
	
    }
    





    public boolean setupGUI(String arg) {
        img = WindowManager.getCurrentImage();
        if (img==null){
            IJ.noImage();
            return false;
        } 
        /*else if (img.getStackSize() == 1) {
            IJ.error("Stack required");
            return false;
        }*/
        if (img.getType() != ImagePlus.COLOR_RGB)
        {
            IJ.error("RBG image with channels required for 2 channel analysis");
            return false;
        }
        
        Width=img.getWidth();
        Height=img.getHeight();
        NbSlices=img.getStackSize();
        arrayLength=Width*Height*NbSlices;
        imgtitle = img.getTitle();

        //does not work with RGB processor-- Thresholding of RGB images is not supported!
        ip=img.getProcessor();
        //ThrVal=ip.getAutoThreshold();
        ip.setThreshold(ThrVal,Math.pow(2,16),ImageProcessor.RED_LUT);
        img.setSlice((int)NbSlices/2);
        img.updateAndDraw();

        GenericDialog gd=new GenericDialog("3D objects counter using 2 channels");
        gd.addSlider("Threshold for object channel: ",ip.getMin(), ip.getMax(),ThrVal);
        gd.addSlider("Threshold for backbone channel: ",ip.getMin(), ip.getMax(),ThrVal2);
        gd.addSlider("Slice: ",1, NbSlices,(int) NbSlices/2);
        gd.addSlider("Pixel depth (nm): ",1, 1000, overriddenPixelDepth_default);
        sliders=gd.getSliders();
        ((Scrollbar)sliders.elementAt(0)).addAdjustmentListener(this);
        ((Scrollbar)sliders.elementAt(1)).addAdjustmentListener(this);
        ((Scrollbar)sliders.elementAt(2)).addAdjustmentListener(this);
        ((Scrollbar)sliders.elementAt(3)).addAdjustmentListener(this);
        value = gd.getNumericFields();
        ((TextField)value.elementAt(0)).addTextListener(this);
        ((TextField)value.elementAt(1)).addTextListener(this);
        ((TextField)value.elementAt(2)).addTextListener(this);
        ((TextField)value.elementAt(3)).addTextListener(this);
 
        gd.addNumericField("Min number of voxels: ",minSize_default,0);
        gd.addNumericField("Max number of voxels: ",Math.min(maxSize_default, Height*Width*NbSlices),0);
        gd.addCheckbox("Despeckle the image first", despeckle_default);
        gd.addCheckbox("Smooth the image first", smooth_default);
        //add selection for two channels. Only relevant for RGB; radiobutton will be better.
        gd.addCheckbox("Object channel is red", or_default);
        gd.addCheckbox("Object channel is green", og_default);
        gd.addCheckbox("Object channel is blue", ob_default);
        gd.addCheckbox("Backbone channel is red", br_default);
        gd.addCheckbox("Backbone channel is green", bg_default);
        gd.addCheckbox("Backbone channel is blue", bb_default);
        gd.addCheckbox("New_Results Table", new_results_default);
        gd.addMessage("Show:");
        gd.addCheckbox("Particles",showParticles_default);
        gd.addCheckbox("Edges",showNumbers_default);
        gd.addCheckbox("Geometrical centre", showCentres_default);
        gd.addCheckbox("Intensity based centre", showCentresInt_default);
        gd.addNumericField("Dot size",DotSize_default,0);
        gd.addCheckbox("Numbers",showNumbers_default);
        gd.addNumericField("Font size",FontSize_default,0);
        gd.addMessage("");
        gd.addCheckbox("Summary", summary_default);


        
        gd.showDialog();
        
        if (gd.wasCanceled()){
            ip.resetThreshold();
            img.updateAndDraw();
            return false;
        }

        ThrVal=(int) gd.getNextNumber();
        ThrVal2=(int) gd.getNextNumber();
        gd.getNextNumber();
        pixelDepthInNM = (int) gd.getNextNumber(); overriddenPixelDepth_default = pixelDepthInNM;
        minSize=(int) gd.getNextNumber();   minSize_default = minSize;
        maxSize=(int) gd.getNextNumber();   maxSize_default = maxSize;
        despeckle = gd.getNextBoolean(); 		despeckle_default = despeckle;
        smooth = gd.getNextBoolean(); 			smooth_default = smooth;
        or =gd.getNextBoolean();            or_default = or;
        og =gd.getNextBoolean();            og_default = og;
        ob =gd.getNextBoolean();            ob_default = ob;
        br =gd.getNextBoolean();            br_default = br;
        bg =gd.getNextBoolean();            bg_default = bg;
        bb =gd.getNextBoolean(); 			bb_default = bb;
        new_results=gd.getNextBoolean();    new_results_default = new_results;
        showParticles=gd.getNextBoolean();  showParticles_default = showParticles;
        showEdges=gd.getNextBoolean();      showEdges_default = showEdges;
        showCentres=gd.getNextBoolean();    showCentres_default = showCentres;
        showCentresInt=gd.getNextBoolean(); showCentresInt_default = showCentresInt;
        DotSize=(int)gd.getNextNumber();    DotSize_default = DotSize;
        showNumbers=gd.getNextBoolean();    showNumbers_default = showNumbers;
        FontSize=(int)gd.getNextNumber();   FontSize_default = FontSize;
        summary=gd.getNextBoolean();        summary_default = summary;
 
        

        IJ.register(Object_Counter3D_2Channel_V2.class); // static fields preserved when plugin is restarted
        //Reset the threshold
        ip.resetThreshold();
        img.updateAndDraw();
        return true;
    }

    //set the mask use 2 channels. Potentially this mask can be set using supervised approaches.
    // It limits the candidate of obeject pixels.
    //input: intensity value of both channels; threshold of both channels
    //output: filled thr (via argument)
    //
    //Only the objects (e.g. synapse) that are associated with (attached to) the foreground of the 2nd channel (e.g. axon)
    // are considered.
    //
    void setMaskUsing2Channels(int[] pict, int ThrVal, int[] pict2, int ThrVal2, boolean[] thr)
    {
    	 int z, y, x;
         int arrayIndex=0;
         for (z=1; z<=NbSlices; z++) {
            // ip = stack.getProcessor(z);
             for (y=0; y<Height;y++) {
                 for (x=0; x<Width;x++) {
                	 if (pict[arrayIndex]>ThrVal)
                	 {
                	    if(pict2[arrayIndex]>ThrVal2)  //on the axon
                           thr[arrayIndex]=true;
                	    else
                	    {
                	      //check if very close to axon
                	      int radius = 2; 
                	      if(neighboring(z, y, x, pict2, ThrVal2, radius))	
                	    	 thr[arrayIndex]=true;
                	    }

                     }
                     arrayIndex++;
                 }
             }
         }
    }
    
    //check neighbors In pict2
    boolean neighboring(int z, int y, int x, int[] pict2, int ThrVal2, int radius)
    { 
    	
    	int index =0; int r;
    	for(r = 1; r <= radius; r++)
    	{
    	  if(withinBounds(x,y, z-r))
    	  {
    	   //decrement z by 1 for array index (stack index start from 1 instead of 0)
    	   index = (z-r-1)*Height*Width+y*Width+x;
    	   if(pict2[index] > ThrVal2) return true;
    	  }
    	  if(withinBounds(x,y, z+r))
     	  { 
     	   index = (z+r-1)*Height*Width+y*Width+x;
 	       if(pict2[index] > ThrVal2) return true;
    	  }
       	  if(withinBounds(x,y-r, z))
       	  {
         	   index = (z-1)*Height*Width+(y-r)*Width+x;
     	       if(pict2[index]> ThrVal2) return true;
       	  }
       	  if(withinBounds(x,y+r, z))
       	  {
         	   index = (z-1)*Height*Width+(y+r)*Width+x;
     	       if(pict2[index]> ThrVal2) return true;
       	  }
       	  if(withinBounds(x-r,y, z))
       	  { 
         	   index = (z-1)*Height*Width+y*Width+(x-r);
     	       if(pict2[index]> ThrVal2) return true;
       	  }
       	  if(withinBounds(x+r,y, z))
       	  {
         	   index = (z-1)*Height*Width+y*Width+(x+r);
     	       if(pict2[index]> ThrVal2) return true;
       	  }
    	}
       	return false;
    }
    
    void analyze() {
        IJ.showStatus("3D Objects Counter using 2 Channels");
        long start=System.currentTimeMillis();
        debug = IJ.debugMode;
        int x, y, z;
        int xn, yn, zn;
        int i, j, k, arrayIndex, offset;
        int voisX = -1, voisY = -1, voisZ = -1;
        int maxX = Width-1, maxY=Height-1;

        int index;
        int val;
        double col;

        int minTag;
        int minTagOld;

	//@@AnilSingh 
        cal = img.getCalibration();
        if (cal == null ) {
            cal = new Calibration(img);
        }
        double pixelDepth = cal.pixelDepth;
        double pixelWidth = cal.pixelWidth;
        double pixelHeight = cal.pixelHeight;
        if(verbose==true){
        IJ.log("pixelDepth = " + pixelDepth);
        IJ.log("pixelWidth = " + pixelWidth);
        IJ.log("pixelHeight = " + pixelHeight);
	}
        //overriding depth from property e.g.if the correct info is lost during de-convolution
        pixelDepth = ((double) pixelDepthInNM)/1000.0;
        cal.pixelDepth = pixelDepth;
	//        IJ.log("Overriding default pixel depth.Set pixelDepth = "+ pixelDepth+ " micron instead!");
        
        double zOrigin = cal.zOrigin;
        double yOrigin = cal.yOrigin;
        double xOrigin = cal.xOrigin;
        double voxelSize = pixelDepth * pixelWidth * pixelHeight;
        if(verbose==true){
        IJ.log("voxelSize = " + voxelSize);
        IJ.log("unit = " + cal.getUnit());
	}
        
        if(despeckle)
        {
  		  //Apply despeckle : median filter with radius 1
          IJ.showStatus("Despeckling ...");
		  RankFilters filter = new RankFilters();
          ImageStack stack = img.getStack();
          for (z=1; z<=NbSlices; z++) {
            ip = stack.getProcessor(z);
		    filter.rank(ip, 1, RankFilters.MEDIAN);
	        IJ.showProgress(z,NbSlices);
          }
        }
        if(smooth)
        {
        	//This filter replaces each pixel with the average of its 3x3 neighborhood.
        	IJ.showStatus("Smoothing ...");
            ImageStack stack = img.getStack();
            for (z=1; z<=NbSlices; z++) {
                ip = stack.getProcessor(z);
               	ip.smooth();
                IJ.showProgress(z,NbSlices);
            }
        }

        
        pict=new int [Height*Width*NbSlices];
        pict2 = new int[Width*Height*NbSlices];

        thr=new boolean [Height*Width*NbSlices];
        tag=new int [Height*Width*NbSlices];
        surf=new boolean [Height*Width*NbSlices];
        Arrays.fill(thr,false);
        Arrays.fill(surf,false);
         
        //set channel index
        int index1 =0, index2 =0;
      	if (or == true)      	  index1 = 0;
      	else if (og == true)  	  index1 = 1;
      	else if (ob == true)      index1 = 2;
      	if (br == true)      	  index2 = 0;
      	else if (bg == true)  	  index2 = 1;
      	else if (bb == true)    index2 = 2;
      	
        int[] PixVal = null;
      	ImageStack stack = img.getStack();
        arrayIndex=0;
        for (z=1; z<=NbSlices; z++) {
            ip = stack.getProcessor(z);
            for (y=0; y<Height;y++) {
                for (x=0; x<Width;x++) {
                    PixVal=ip.getPixel(x, y, null);
                    pict[arrayIndex]=PixVal[index1];
                    pict2[arrayIndex]=PixVal[index2];
                    arrayIndex++;
                }
            }
        }
        
        //new method for using 2 channels
        setMaskUsing2Channels(pict, ThrVal, pict2, ThrVal2, thr);
               
 
        //First ID attribution
        int tagvois;
        ID=1;
        arrayIndex=0;
        for (z=1; z<=NbSlices; z++){
            for (y=0; y<Height; y++){
                for (x=0; x<Width; x++){
                    if (thr[arrayIndex]){
                        tag[arrayIndex]=ID;
                        minTag=ID;
                        i=0;
                        //Find the minimum tag in the neighbours pixels
                        for (voisZ=z-1;voisZ<=z+1;voisZ++){
                            for (voisY=y-1;voisY<=y+1;voisY++){
                                for (voisX=x-1;voisX<=x+1;voisX++){
                                    if (withinBounds(voisX, voisY, voisZ)) {
                                        offset=offset(voisX, voisY, voisZ);
                                        if (thr[offset]){
                                            i++;
                                            tagvois = tag[offset];
                                            if (tagvois!=0 && tagvois<minTag) minTag=tagvois;
                                        }
                                    }
                                }
                            }
                        }
                        if (i!=27) surf[arrayIndex]=true;
                        tag[arrayIndex]=minTag;
                        if (minTag==ID){
                            ID++;
                        }
                    }
                    arrayIndex++;
                }
            }
            if(verbose){
		IJ.showStatus("Finding structures");
		IJ.showProgress(z,NbSlices);
	    }
        }
        ID++;

        //Minimization of IDs=connection of structures
        arrayIndex=0;
        for (z=1; z<=NbSlices; z++){
            for (y=0; y<Height; y++){
                for (x=0; x<Width; x++){
                    if (thr[arrayIndex]){
                        minTag=tag[arrayIndex];
                        //Find the minimum tag in the neighbours pixels
                        for (voisZ=z-1;voisZ<=z+1;voisZ++){
                            for (voisY=y-1;voisY<=y+1;voisY++){
                                for (voisX=x-1;voisX<=x+1;voisX++){
                                    if (withinBounds(voisX, voisY, voisZ)) {
                                        offset=offset(voisX, voisY, voisZ);
                                        if (thr[offset]){
                                            tagvois = tag[offset];
                                            if (tagvois!=0 && tagvois<minTag) minTag=tagvois;
                                        }
                                    }
                                }
                            }
                        }
                        //Replacing tag by the minimum tag found
                        for (voisZ=z-1;voisZ<=z+1;voisZ++){
                            for (voisY=y-1;voisY<=y+1;voisY++){
                                for (voisX=x-1;voisX<=x+1;voisX++){
                                    if (withinBounds(voisX, voisY, voisZ)) {
                                        offset=offset(voisX, voisY, voisZ);
                                        if (thr[offset]){
                                            tagvois = tag[offset];
                                            if (tagvois!=0 && tagvois!=minTag) replacetag(tagvois,minTag);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    arrayIndex++;
                }
            }
            IJ.showStatus("Connecting structures");
            IJ.showProgress(z,NbSlices);
        }
	
	//Jie Zhou 1/30/2012
	//match process, need to pass in boolean[] detectedCenter
	//One loop through pixels to see how many centers per object
        Scanner sc = null;
        try
	    {
		sc = new Scanner(new java.io.File("detected.marker.big"));
	    }catch(java.io.	FileNotFoundException e)
	    { 
        	IJ.log("'detected.marker' is not found."); 
	    }
	
        
        HashMap<Integer, VoxelPoint> centers = new HashMap<Integer, VoxelPoint>();
        Integer ai;
        while(sc.hasNextInt())
	    {  
		x=sc.nextInt(); 
		y=sc.nextInt(); 
		z=sc.nextInt();
		ai = new Integer(z*Height*Width + y*Width + x);
		//IJ.log("before put-- "+ x + " " + y + " " + z);
		//IJ.log("before put--ai "+ ai);
		centers.put(ai, new VoxelPoint(x,y,z));
		myCenters.add(new VoxelPoint(x,y,z));
		//IJ.log("after put--ai "+ ai+ ":"+ centers.get(ai).x + " " + centers.get(ai).y + " " + centers.get(ai).z);
		
	    }
	sc.close();
	//       IJ.log("centers map:"+centers);
	
	int[] CenternumArray = new int[ID];
	ArrayList<ArrayList<VoxelPoint>> CenterCoorArray = new ArrayList<ArrayList<VoxelPoint>>(ID);  //an arraylist of arraylist
	for(i=0; i<ID; i++)  
	    CenterCoorArray.add(new ArrayList<VoxelPoint>());
	arrayIndex = 0;
	for (z=1; z<=NbSlices; z++)
	    {
		for (y=0; y<Height; y++)
		    {
			for (x=0; x<Width; x++){
			    if(centers.containsKey(new Integer(arrayIndex))) //in the detected list, collision?
				{
				    // IJ.log("get index -- "+ x + " " + y + " " + (z-1));
				    
				    index=tag[arrayIndex];
				    CenternumArray[index]++;
				    //save the centers
				    CenterCoorArray.get(index).add(centers.get(new Integer(arrayIndex)));
				}
			    arrayIndex++;
			}
		    }
	    }
	
	//Split if needed, before calculating parameters of all objects (1,2,4,6 from cropped_HSN)
	//ID is different from final nParticles because some may be to small to count.   
	IJ.log("number of object before splitting:"+ ID);
	int currentTotalObjects = ID; //ID will change after calling splitToTwo.
	VoxelPoint center1 = null, center2 = null;
	//debug-System.out.println("Total Number before splitting: "+ID);
        for(i=0; i < currentTotalObjects; i++) {   
	    //@@AnilSingh@@@@@@@
	    Tuple3D minLimits = limitsXYZ(i, "min");
	    Tuple3D maxLimits = limitsXYZ(i, "max");
	    int minx = minLimits.x, miny =minLimits.y, minz =minLimits.z;
	    int maxx = maxLimits.x, maxy =maxLimits.y, maxz =maxLimits.z;
	    int obwidth = maxx-minx+1, obheight = maxy-miny+1, obdepth = maxz-minz+1;
	    int[] xyMIP = new int[obwidth*obheight];
	    boolean[] regionFG = new boolean[obwidth*obheight];
	    xyCalcMIP(xyMIP,regionFG,i,minLimits,maxLimits);
	    ArrayList<ContourPoint> contourList = new ArrayList<ContourPoint>();       
	    //debug-System.out.println("Splitting Object :"+i+", Num of centers: " +CenterCoorArray.get(i).size());
	    IJ.log("object " +i+ " has "+ CenternumArray[i] + "centers");
	    calcObjContour(contourList,regionFG,minLimits,maxLimits);
	    splitToTwo(i, CenterCoorArray.get(i));
	}

	//debug-System.out.println("Total Number after splitting: "+ID);
	
	arrayIndex=0;
	Paramarray=new double [ID][9];
	for (z=1; z<=NbSlices; z++){
	    for (y=0; y<Height; y++){
		for (x=0; x<Width; x++){
		    index=tag[arrayIndex];
		    val=pict[arrayIndex];
		    Paramarray[index][0]++;
		       if (surf[arrayIndex]) Paramarray[index][1]++;
		       Paramarray[index][2]+=val;
		       Paramarray[index][3]+=x;
		       Paramarray[index][4]+=y;
		       Paramarray[index][5]+=z;
		       Paramarray[index][6]+=x*val;
		       Paramarray[index][7]+=y*val;
		       Paramarray[index][8]+=z*val;
		       arrayIndex++;
		}
	    }
	    IJ.showStatus("Retrieving structures' parameters");
	    IJ.showProgress(z,NbSlices);
	}
        double voxCount, intensity;
        for (i=0;i<ID;i++){
            voxCount = Paramarray[i][0];
            intensity = Paramarray[i][2]; // sum over all intensity values
            if (voxCount>=minSize && voxCount<=maxSize) {
                if (voxCount!=0){
                    Paramarray[i][2] /= voxCount;
                    Paramarray[i][3] /= voxCount;
                    Paramarray[i][4] /= voxCount;
                    Paramarray[i][5] /= voxCount;
                }
                if (intensity!=0){
                    Paramarray[i][6] /= intensity;
                    Paramarray[i][7] /= intensity;
                    Paramarray[i][8] /= intensity;
                }
            } else {
                for (j=0;j<9;j++) Paramarray[i][j]=0;
            }
            IJ.showStatus("Calculating barycenters' coordinates");
            IJ.showProgress(i,ID);
        }

        //Log data
        if (new_results) {
            rt=new ResultsTable();
        } else {
            rt = ResultsTable.getResultsTable();
        }
        IDarray=new int[ID];

        String[] head={"Volume","Surface","Intensity","Centre X","Centre Y","Centre Z","Centre int X","Centre int Y","Centre int Z"};
        for (i=0; i<head.length; i++) rt.setHeading(i,head[i]);

        k=1;
        for (i=1;i<ID;i++){
            if (Paramarray[i][0]!=0){
                rt.incrementCounter();
                IDarray[i]=k;
                voxCount = Paramarray[i][0];
                rt.addValue(0,voxCount * voxelSize);
                rt.addValue(1,Paramarray[i][1] / voxCount);
                rt.addValue(2,Paramarray[i][2]);
                rt.addValue(3,cal.getX(Paramarray[i][3]));
                rt.addValue(4,cal.getY(Paramarray[i][4]));
                rt.addValue(5,cal.getZ(Paramarray[i][5]-1));
                rt.addValue(6,cal.getX(Paramarray[i][6]));
                rt.addValue(7,cal.getY(Paramarray[i][7]));
                rt.addValue(8,cal.getZ(Paramarray[i][8]-1));
                k++;
            }
        }
        if (new_results) {
            rt.show("Results from "+imgtitle);
        } else {
            //if (! IJ.isResultsWindow()) IJ.showResults();
            rt.show("Results");
        }
        int nParticles = rt.getCounter();

        if (showParticles){ // Create 'Particles' image
      
            stackParticles=Particles.getStack();
            //Particles.setCalibration(cal);  //just show pixel?
            arrayIndex=0;
            for (z=1; z<=NbSlices; z++){
                ip=stackParticles.getProcessor(z);
                for (y=0; y<Height; y++){
                    for (x=0; x<Width; x++){
                        if (thr[arrayIndex]) {
                            index=tag[arrayIndex];
                            if (Paramarray[index][0]>0){
				col=IDarray[index]+1;
				 ip.setValue(col);
				 ip.drawPixel(x, y);
				
                            }
                        }
                        arrayIndex++;
                    }
                }
            }
            Particles.show();
            IJ.run("Fire");
        }

	if (showEdges){ // Create 'Edges' imag
	    Edges=NewImage.createShortImage("Edges "+imgtitle,Width,Height,NbSlices,0);
            stackEdges=Edges.getStack();
            Edges.setCalibration(cal);
            arrayIndex=0;
	    //	    int anil =0;
            for (z=1; z<=NbSlices; z++){
                ip=stackEdges.getProcessor(z);
                for (y=0; y<Height; y++){
                    for (x=0; x<Width; x++){
                        if (thr[arrayIndex]) {
                            index=tag[arrayIndex];
                            if (Paramarray[index][0]>0 && surf[arrayIndex]) {
                                col=IDarray[index];
			       
			
				ip.setValue(col);
                                ip.drawPixel(x, y);
                            }
                        }
                        arrayIndex++;
                    }
                }
            }
            Edges.show();
            IJ.run("Fire");
        }

        if (showCentres){ // Create 'Centres' image
            Centres=NewImage.createShortImage("Geometrical Centres "+imgtitle,Width,Height,NbSlices,0);
            stackCentres=Centres.getStack();
            Centres.setCalibration(cal);
            for (i=0;i<nParticles;i++){
                ip=stackCentres.getProcessor((int)Math.round(rt.getValue(5,i)/pixelDepth+zOrigin+1));
                ip.setValue(i+1);
                ip.setLineWidth(DotSize);
                ip.drawDot((int)Math.round(rt.getValue(3,i)/pixelWidth+xOrigin), (int)Math.round(rt.getValue(4,i)/pixelHeight+yOrigin));
            }
            Centres.show();
            IJ.run("Fire");
        }

        if (showCentresInt){ // Create 'Intensity weighted Centres' image
            CentresInt=NewImage.createShortImage("Intensity based centres "+imgtitle,Width,Height,NbSlices,0);
            stackCentresInt=CentresInt.getStack();
            CentresInt.setCalibration(cal);
            for (i=0;i<nParticles;i++){
                ip=stackCentresInt.getProcessor((int)Math.round(rt.getValue(8,i)/pixelDepth+zOrigin+1));
                ip.setValue(i+1);
                ip.setLineWidth(DotSize);
                ip.drawDot((int)Math.round(rt.getValue(6,i)/pixelWidth+xOrigin), (int)Math.round(rt.getValue(7,i)/pixelHeight+yOrigin));
            }
            CentresInt.show();
            IJ.run("Fire");
        }

        //"Volume","Surface","Intensity","Centre X","Centre Y","Centre Z","Centre int X","Centre int Y","Centre int Z"

        if (showNumbers){
            Font font = new Font("SansSerif", Font.PLAIN, FontSize);
            for (i=0;i<nParticles;i++){
                z = (int)Math.round(rt.getValue(5,i)/pixelDepth+zOrigin+1);
                y = (int)Math.round(rt.getValue(4,i)/pixelHeight+yOrigin);
                x = (int)Math.round(rt.getValue(3,i)/pixelWidth+xOrigin);
                if (debug) IJ.log(pluginName + " Draw pixels: slice=" + z + " coords" + x + "," + y);
                if (showParticles){
                    ip=stackParticles.getProcessor(z);
                    ip.setFont(font);
                    ip.setValue(nParticles);
                    ip.drawString(""+(i+1),x,y);
                }
                if (showEdges){
                    ip=stackEdges.getProcessor(z);
                    ip.setFont(font);
                    ip.setValue(nParticles);
                    ip.drawString(""+(i+1),x,y);
                }
                if (showCentres){
                    ip=stackCentres.getProcessor(z);
                    ip.setFont(font);
                    ip.setValue(nParticles);
                    ip.drawString(""+(i+1),x,y);
                }
                if (showCentresInt){
                    ip=stackCentresInt.getProcessor(z);
                    ip.setFont(font);
                    ip.setValue(nParticles);
                    ip.drawString(""+(i+1),x,y);
                }
                IJ.showStatus("Drawing numbers");
                IJ.showProgress(i,nParticles);
            }
        }

	/*
	  Look at the shortProcessor.java and decide a sensible range
	  for the min and max.
	 
	 */

        if (showParticles){
	    //            Particles.getProcessor().setMinAndMax(1,nParticles);
            Particles.getProcessor().setMinAndMax(1,nParticles);
            Particles.updateAndDraw();
        }

        if (showEdges){
            Edges.getProcessor().setMinAndMax(1,nParticles);
            Edges.updateAndDraw();
        }

        if (showCentres){
            Centres.getProcessor().setMinAndMax(1,nParticles);
            Centres.updateAndDraw();
        }

        if (showCentresInt){
            CentresInt.getProcessor().setMinAndMax(1,nParticles);
            CentresInt.updateAndDraw();
        }

        if (summary){
            double TtlVol=0;
            double TtlSurf=0;
            double TtlInt=0;
            for (i=0; i<nParticles;i++){
                TtlVol+=rt.getValueAsDouble(0,i);
                TtlSurf+=rt.getValueAsDouble(1,i);
                TtlInt+=rt.getValueAsDouble(2,i); //
            }
            int precision = Analyzer.precision;
            IJ.log(imgtitle);
            IJ.log("Number of Objects = " + nParticles);
            IJ.log("Mean Volume = " + IJ.d2s(TtlVol/nParticles,precision));
            IJ.log("Mean Surface Fraction = " + IJ.d2s(TtlSurf/nParticles,precision));
            IJ.log("Mean Intensity = " + IJ.d2s(TtlInt/nParticles,precision));
            IJ.log("Voxel Size = " + IJ.d2s(voxelSize));
        }
        IJ.showStatus("Nb of particles: "+nParticles);
        IJ.showProgress(2,1);
        IJ.showStatus(IJ.d2s((System.currentTimeMillis()-start)/1000.0, 2)+" seconds");
    }

    public boolean withinBounds(int m,int n,int o) {
        return (m >= 0 && m < Width && n >=0 && n < Height && o > 0 && o <= NbSlices );
    }

    public int offset(int m,int n,int o) {
        return m+n*Width+(o-1)*Width*Height;
    }
    
    Tuple3D limitsXYZ(int objectIndex, String opt){
	Tuple3D limitXYZ = new Tuple3D(0,0,0);
	if (opt=="max" || opt=="min"){
	    //find out the minx, miny, minz, maxx, maxy, maz of the object
	    int arrayIndex=0;
	    //int index;
	    int limx =0, limy =0, limz =1;
	    for (int z=1; z<=NbSlices; z++){
		for (int y=0; y<Height; y++){
		    for (int x=0; x<Width; x++){
			int index=tag[arrayIndex];
			if(index == objectIndex){
			    
			    if(opt=="min"){
				if (x<limx) limx = x; 
				if (y<limy) limy = y; 
				if (z<limz) limz = z;
			    }
			    
			    else if(opt=="max"){
				if (x>limx) limx =x;
				if (y>limy) limy =y;
				if (z>limz) limz =z;
			    }
			}
			arrayIndex++;
		    }
		}
	    }
	    limz = limz-1;
	    limitXYZ = new Tuple3D(limx,limy,limz);
	}
	//    else print warning
	return limitXYZ;
    }
    

    void xyCalcMIP(int[] xyMIP, boolean[] regionFG, int objIndex, Tuple3D minLimits, Tuple3D maxLimits){
	//compute MIP image of the object at xy plane
	//int[] xyMIP = new int[obwidth*obheight];
	//boolean[] regionFG = new boolean[obwidth*obheight];
	int minx = minLimits.x, miny =minLimits.y, minz =minLimits.z;
	int maxx = maxLimits.x, maxy =maxLimits.y, maxz =maxLimits.z;
	int obwidth = maxx-minx+1, obheight = maxy-miny+1, obdepth = maxz-minz+1;
	int arrayIndex = 0;
	for(int x = 0; x < obwidth; x++)
	    for( int y = 0; y < obheight; y++){
		int maxvalue = 0;   
		for ( int z = 0; z< obdepth; z++){
		    arrayIndex = (z+minz)*Width*Height + (y+miny)*Width + x+minx;
			    if  (tag[arrayIndex] == objIndex)
				regionFG[y*obwidth+x] = true;
			    int currentPixelValue = pict[arrayIndex];
			    if (currentPixelValue > maxvalue)
				maxvalue = currentPixelValue;
		}
		//set mip
		xyMIP[y*obwidth+x] = maxvalue;
	    }
    }
    
    void calcObjContour(ArrayList<ContourPoint> contourList, boolean[] regionFG, Tuple3D minLimits, Tuple3D maxLimits){
	int minx = minLimits.x, miny =minLimits.y, minz =minLimits.z;
	int maxx = maxLimits.x, maxy =maxLimits.y, maxz =maxLimits.z;
	int obwidth = maxx-minx+1, obheight = maxy-miny+1, obdepth = maxz-minz+1;

	//there is typically no holes in MIP of synapse, using region foreground to find contour
	boolean[] contourFlag =  new boolean[obwidth*obheight];      
	
	//First we need to flag all the pixels who live on contour.
	int totalContour = 0;
       	for ( int x =0 ; x < obwidth; x++)
	    for (int y=0; y< obheight; y++){
		if(regionFG[y*obwidth+x])//is foreground
		    {
			int totalNeighbors = 0;
			//check all neighbors, 9 including the pixel itself
			for(int m=x-1; m<=x+1;m++){
			    if  (m<0 || m>=obwidth) continue;
			    for(int n=y-1;n<=y+1;n++){
				if(n <0 || n >=obheight) continue;
				if (regionFG[n*obwidth+m])  totalNeighbors++;
			    }
			}
			if (totalNeighbors < 9){
			    contourFlag[y*obwidth + x] = true;
			    totalContour++;
			}
		    }//end of foreground
	    }
	//	IJ.log("number of contour pixels" + totalContour);

	//get concavity pairs based on concavity score
       
	for ( int x=0; x<obwidth; x++)
	    for ( int y=0; y<obheight; y++){
		if(contourFlag[y*obwidth+x]){
		    //calculate level of concavity
		    int convalue = evaluateConcavity(regionFG, contourFlag, obwidth, obheight, x, y);
		    contourList.add(new ContourPoint(x,y, convalue));
		}
	    }

     	
	IJ.log("list size:"+contourList.size());
	IJ.log(contourList.toString());
    }
 //---
    void splitToTwo(int objectIndex, ArrayList<VoxelPoint> voxelpoints){
	if(voxelpoints.size()>=2)  {
	    // if(((double)objectIndex%100)==0.0)
	    iterativeSplitToTwo(objectIndex, voxelpoints);
	}
		
 	// else if (voxelpoints.size() == 2){
	//     VoxelPoint center1 = voxelpoints.get(0);
	//     VoxelPoint center2 = voxelpoints.get(1);
	//     splitToTwo(objectIndex, center1.x, center1.y, center1.z, center2.x, center2.y, center2.z,voxelpoints);
	// }
    }
    
    void iterativeSplitToTwo(int objectIndex, ArrayList<VoxelPoint> voxelpoints)
    { 


	while(voxelpoints.size() > 1)
	    {
		//a comparator that calculators the accumulative distance between 1 detected marker and all other detected markers in the same object.
		//do a sorting based on that distance, descending order
		Collections.sort(voxelpoints, new VoxelComp(voxelpoints));
		
		//find the one that has the longest distance (p1),  and find the mean point of the rest (p2).
		// remove p1, return the element that was removed from the list.
		VoxelPoint p1 = voxelpoints.remove(0);
			VoxelPoint p2 = getMeanPoint(voxelpoints);
		//	VoxelPoint p2 = getFarthestPoint(voxelpoints,p1);
		
		//do a splitting between p1 and p2 by calling splitToTwo(objectIndex, p1 , p2)
		//debug-System.out.println("=======================================================");
		//debug-System.out.println("Splitting: ("+p1.x+", "+p1.y+", "+p1.z+"),  ("+p2.x+", "+p2.y+", "+p2.z+")");
			
	//debug-System.out.println("On Object :"+objectIndex+", Remaining enters: " + voxelpoints.size());

		splitToTwo(objectIndex, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z,voxelpoints);
		//debug-System.out.println("Out: "+n);
		//	 break;
	    }
	
	
    }
	
    
    private VoxelPoint getMeanPoint(ArrayList<VoxelPoint> voxelpoints)
    {
	//	//debug-System.out.print("Num Points: "+voxelpoints.size());
	VoxelPoint meanP = new VoxelPoint(0,0,0);
	for (VoxelPoint p : voxelpoints)
	    {

		meanP.x += p.x;
		meanP.y += p.y;
		meanP.z += p.z;
	    }
	meanP.x /= voxelpoints.size();		
	meanP.y /= voxelpoints.size();		
	meanP.z /= voxelpoints.size();	
    	
	return meanP;
    }
	
    private VoxelPoint getFarthestPoint(ArrayList<VoxelPoint> voxelpoints, VoxelPoint vp)
    {
	VoxelPoint meanP = new VoxelPoint(0,0,0);
	int dist = 0;
	for (VoxelPoint p : voxelpoints)
	    {
		if(getAbsDist(vp.x,p.x,vp.y,p.y)>dist){
		    meanP.x = p.x;
		    meanP.y = p.y;
		    meanP.z = p.z;
		    dist=getAbsDist(vp.x,p.x,vp.y,p.y);
		}
	    }
	// meanP.x /= voxelpoints.size();		
	// meanP.y /= voxelpoints.size();		
	// meanP.z /= voxelpoints.size();	
	
	return meanP;
    }
	



    void splitToTwo(int objectIndex, int cx1, int cy1, int cz1, int cx2, int cy2, int cz2,ArrayList<VoxelPoint> voxelpoints)
    {
        int index,arrayIndex;
	Tuple3D minLimits = limitsXYZ(objectIndex, "min");
	Tuple3D maxLimits = limitsXYZ(objectIndex, "max");
	int minx = minLimits.x, miny =minLimits.y, minz =minLimits.z;
	int maxx = maxLimits.x, maxy =maxLimits.y, maxz =maxLimits.z;

	int obwidth = maxx-minx+1, obheight = maxy-miny+1, obdepth = maxz-minz+1;
       
	if (obwidth*obheight*obdepth < minSize){
	    //debug-System.out.println("Object :"+objectIndex+"is too small to print\n");

	    return;  //too small to split
	}
       //Calculate MIP image of object
       int[] xyMIP = new int[obwidth*obheight];
       boolean[] regionFG = new boolean[obwidth*obheight];
       xyCalcMIP(xyMIP,regionFG,objectIndex,minLimits,maxLimits);
       //Calculate the Contour of calculated MIP image
       ArrayList<ContourPoint> contourList = new ArrayList<ContourPoint>();   
       calcObjContour(contourList,regionFG,minLimits,maxLimits);
       if(contourList.size() < 2){
	   //	   IJ.log("Not enough contour points for this object: "+objectIndex+ "? Why?");
	   return;  //not enough contour points?
       }
       
       Collections.sort(contourList, new Comparator<ContourPoint>(){
	       public int compare(ContourPoint p1, ContourPoint p2) {
		   return  p2.concavescore - p1.concavescore;
	       }
	   });
       
       
       //Find the pair of concavity point for splitting: find the 2 contours that are 
       // 1) physically close (Euclidean distance), 2) contour-wise apart, and  3) concave enough
       // 4) The two detected centers need to be on different side of the splitting line
       //For more centers: each line should split 1 from the rest, while satisfy the above 3 conditions.
       int y1 = contourList.get(0).y;
       int x1 = contourList.get(0).x;
       int x2, y2;
       float a = 0, b = 0;
       int mindist = 999, dist;
       //int mindist = -1, dist;
       float founda =0, foundb =0;
       ArrayList<Tuple3D> Nodes = new ArrayList<Tuple3D>();
       //  //debug-System.out.println("First Point Concavity: "+ contourList.get(0).concavescore);
       //use the top half concave  points? if will search over all possibilities
       int counter=1;
       // for(int contourindex = 1; contourindex < contourList.size()/2; contourindex++)
       int i=0;
       int secondConcav=0;
       int px,py;
       int iStore=i;
       boolean fnd=true;
       while(i<contourList.size()-1){
	    y1 = contourList.get(i).y;
	    x1 = contourList.get(i).x;
	    mindist=999;
	    //    mindist=-1;
	    Tuple3D d = new Tuple3D(i,0,0);
	    for(int contourindex = i+1; contourindex < contourList.size()/2; contourindex++)
		{
	       //get a line between
		    // mindist=999;
	       x2 = contourList.get(contourindex).x;
	       y2 = contourList.get(contourindex).y;
	       a =  (y1 - y2)/ (float)(x1-x2);
	       b =   y1 - a*x1;
	       //IJ.log("a:"+a+" b:"+b+" "+"1:" +(cy1-a*cx1-b)+ "2:"+(cy2-a*cx2-b));
	       
	         if ((cy1-a*cx1-b)*(cy2-a*cx2-b)  < 0) //on two different side
		   {
		     
		       //Minimum distance cut between center and border.
		       //check if the contour sets current and all other points apart.
		       boolean dec = true;
		       dec = checkPath(a,b,cy1,cx1,voxelpoints);
		       if(Math.abs(cy1-a*cx1-b)<=2)dec=false;
		       
		       //break;
		       //  IJ.log("this can split: centers on 2 different sides");
		       dist =  getAbsDist(x1,x2,y1,y2);  //use abs distance to simulate Euclidean distance for speed
		       // IJ.log("dist between contour pairs:" + dist);
		       if(dec)
			   if (dist < mindist)		 
			       {   
				   d.y = contourindex;
				   mindist = dist;
				   founda = a;
				   foundb = b;
				   iStore=i;
				   counter=contourindex;
				   secondConcav=contourList.get(counter).concavescore;
			       }
		   }
		}
	    if(mindist<900){
		Nodes.add(d);
	    }
	    //  i++;
	    i=i+2;
       }
       
       
       
       int like=-1;
       for (Tuple3D p : Nodes)
	   {
	       //debug-System.out.println("Node: "+ p.x+" "+p.y);
       int myLike=Math.abs(getSplitLike(p.x,p.y,contourList));
	       if(myLike>like){
		   x1 = contourList.get(p.x).x;
		   y1 = contourList.get(p.x).y;
		   x2 = contourList.get(p.y).x;
		   y2 = contourList.get(p.y).y;
		   founda =  (y1 - y2)/ (float)(x1-x2);
		   foundb =   y1 - founda*x1;
		   like=myLike;

	       }
	   }
     	   
        if (founda==0 && foundb==0)
        {
	      	IJ.log("cann't find a good pair to split!");
        	return; //cann't find a good pair to split!
        }
        
        //separate (based on the lines between pairs), if z-depth < 3, just use x-y plane. 
	//Otoherwise, do splilt on two more planes and vote
	    ID++;
	    arrayIndex = 0;
	    for(int z =0; z < obdepth; z++)
		for(int y =0; y < obheight; y++)
       		    for(int x =0; x < obwidth; x++)
       			{ 
			    arrayIndex = (z+minz)*Height*Width+(y+miny)*Width+x+minx;
			    
			    if(tag[arrayIndex]== objectIndex) //is the one we are looking at
				{
				    //3/11/2014 Decide that the side containing 
				    //cx1, cy1, cz1 will always have the incremented obj tag.  
				    //The other contains the original obj tag. 
				    if ((cy1 - founda*cx1 - foundb ) < 0)
					{
					    //if ((y-a*x-b) > 0)
					    if ((y-founda*x-foundb) > 0)
						{
						    //the voxel belongs to object 1 (original object tag); do nothing;
						}
					    else
						{  //belongs to object 2
						    tag[arrayIndex] = ID-1; //start from 0		    	
						}
					}
				    else //flip the obj tag assignment
					{
					    if ((y-founda*x-foundb) > 0) //belongs to object 2
						{
						    tag[arrayIndex] = ID-1; //start from 0		    	
						}
					}
				    
				}
			    arrayIndex++;
       			}//end of x
	    
	    
    }//end of method splitToTwo
    
    boolean checkPath(float a, float b,int cy1, int cx1,ArrayList<VoxelPoint> voxelpoints){
	    boolean dec=true;
	    for (VoxelPoint p : voxelpoints)
		{
		    if(Math.abs(p.y-a*p.x-b)<=2)dec=false;
		    
		    //debug-System.out.println("Printing: "+(p.y-a*p.x-b));
		    if((cy1-a*cx1-b)*(p.y-a*p.x-b)>=0)
			dec=false;
		}
	if(Math.abs(cy1-a*cx1-b)<=2)dec=false;
	    return dec;
	}

   static public int getSplitLike(int ind1, int ind2, ArrayList<ContourPoint> contourList){
	int x1 = contourList.get(ind1).x;
	int x2 = contourList.get(ind2).x;
	int y1 = contourList.get(ind1).y;
	int y2 = contourList.get(ind2).y;
	int conc1 = contourList.get(ind1).concavescore;
	int conc2 = contourList.get(ind2).concavescore;
	
	return (conc1*conc2)/getAbsDist(x1,x2,y1,y2);
	
    }
    static public int getAbsDist(int x1, int x2, int y1, int y2)
    {
    	int ydist = y2 - y1, xdist = x2-x1;
    	if (y1>y2)  ydist = y1-y2;
    	if (x1>x2)  xdist = x1-x2;
    	return xdist+ydist;
    }

    //calculate concavity given a 2D binary image	
    public int evaluateConcavity(boolean[] binaryImage, boolean[] contourflag, int width, int height,
		    int conx, int cony)
    {
	    int sum =0;
 	    sum+=sumWindow(binaryImage, width, height, conx, cony);
	    //check neightbors, contour pixel should have at least 2 neighbors who are also countour pixels	
	    //calculate the same thing for  neighboring contour pixels, averaging for stability of contour test.
	    int numContourNeighbor = 0;
  	    for(int i= conx-1; i<= conx+1;i++)
		 for(int j=cony-1; j<= cony+1; j++)
		 {
		  if(i<0 || i>=width || j<0 || j>=height) continue;	
		  if (contourflag[j*width+i])
		  {
		      sum+=sumWindow(binaryImage, width, height, i,j);	  
		      numContourNeighbor++;
		  }
		}
	    sum = sum/numContourNeighbor;
	    return sum;
    }
	    
	    
   private int sumWindow(boolean[] binaryImage, int width, int height,
		    int conx, int cony)
    {
	    int windowx =5, windowy =5;

	    //caclulate the 5*5 sum
	    int sum = 0;
	    for(int i= conx-windowx/2; i<= conx+windowx/2;i++)
		for(int j= cony-windowy/2; j<= cony+windowy/2;j++)
		{
		   if(i<0 || i>=width || j<0 || j>=height) continue;	
 		   if(binaryImage[j*width+i])
		     sum++;
		}


	 return sum;
    }
    //---

    public void replacetag(int m,int n){
        for (int i=0; i<tag.length; i++) if (tag[i]==m) tag[i]=n;
    }

    public void adjustmentValueChanged(AdjustmentEvent e) {
    	
        //does not work with RGB processor-- Thresholding of RGB images is not supported!
        ThrVal=((Scrollbar)sliders.elementAt(0)).getValue();
        ip.setThreshold(ThrVal,Math.pow(2,16),ImageProcessor.RED_LUT);
        img.setSlice(((Scrollbar)sliders.elementAt(2)).getValue()); //changed from 1 to 2
        img.updateAndDraw();
    }

    public void textValueChanged(TextEvent e) {
        ((Scrollbar)sliders.elementAt(0)).setValue((int) Tools.parseDouble(((TextField)value.elementAt(0)).getText()));
        
        //check the range of slice
        ((Scrollbar)sliders.elementAt(2)).setValue((int) Tools.parseDouble(((TextField)value.elementAt(2)).getText()));
        if ((int) Tools.parseDouble(((TextField)value.elementAt(2)).getText())>NbSlices){
            ((Scrollbar)sliders.elementAt(2)).setValue(NbSlices);
            ((TextField)value.elementAt(2)).setText(""+NbSlices);
        }
        if ((int) Tools.parseDouble(((TextField)value.elementAt(2)).getText())<1){
            ((Scrollbar)sliders.elementAt(2)).setValue(1);
            ((TextField)value.elementAt(2)).setText("1");
        }
        //check the range of pixel depth
        ((Scrollbar)sliders.elementAt(3)).setValue((int) Tools.parseDouble(((TextField)value.elementAt(3)).getText()));
        if ((int) Tools.parseDouble(((TextField)value.elementAt(3)).getText())>1000){
            ((Scrollbar)sliders.elementAt(3)).setValue(1000);
            ((TextField)value.elementAt(3)).setText("1000");
        }
        if ((int) Tools.parseDouble(((TextField)value.elementAt(3)).getText())<1){
            ((Scrollbar)sliders.elementAt(3)).setValue(1);
            ((TextField)value.elementAt(3)).setText("1");
        }
        
        //does not work with RGB processor-- Thresholding of RGB images is not supported!
        ThrVal=((Scrollbar)sliders.elementAt(0)).getValue();
        ip.setThreshold(ThrVal,Math.pow(2,16),ImageProcessor.RED_LUT);
        img.setSlice(((Scrollbar)sliders.elementAt(2)).getValue());
        img.updateAndDraw();
    }

    public ImagePlus getParticles() {
        return Particles;
    }

    public ImagePlus getEdges() {
        return Edges;
    }

    public ResultsTable getResults() {
        return rt;
    }

    public Thread runThread(ImagePlus img, int ThrVal, int minSize, int maxSize, boolean showParticles, boolean showEdges, boolean showCentres, boolean showCentresInt, boolean new_results) {
        this.img = img;
        this.ThrVal = ThrVal;
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.showParticles = showParticles;
        this.showEdges = showEdges;
        this.showCentres = showCentres;
        this.showCentresInt = showCentresInt;
        this.new_results = new_results;
        this.summary = false;
        thread = new Thread(Thread.currentThread().getThreadGroup(), this, "Object_Counter3D " + img.getTitle());
        thread.start();
        return thread;
    }
    public void run() {
        Width=img.getWidth();
        Height=img.getHeight();
        NbSlices=img.getStackSize();
        imgtitle = img.getTitle();
        analyze();
    }


}



class Tuple3D
   {
    int x;
    int y;
    int z;
    Tuple3D(int x, int y, int z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    public String toString() { return "["+ this.x + ", " + this.y + ", " + this.z + "], "; }
  }   




class ContourPoint extends Tuple3D
{
    int x;
    int y;
    int concavescore;
    ContourPoint(int x, int y, int score)
    {
	super(x,y,score);
    }
}    



class VoxelPoint extends Tuple3D
{
    int x;
    int y;
    int z;
    
    VoxelPoint(int x, int y, int z)
    {
	super(x,y,z);
    }
    
    public int getAccumulativeDistance(final ArrayList<VoxelPoint> list)
    {
	double distance = 0;
	for(VoxelPoint p : list)
	    {
		distance += Math.sqrt((p.x-x)*(p.x-x)+(p.y-y)*(p.y-y)+(p.z-z)*(p.z-z));
	    }
	return (int)distance;
    }
}   

class VoxelComp implements Comparator<VoxelPoint>
{
    ArrayList<VoxelPoint> voxelpoints;
    VoxelComp(ArrayList<VoxelPoint> voxelpoints)
	{
	    this.voxelpoints = voxelpoints;
	}
    
    public int compare(VoxelPoint p1, VoxelPoint p2) {
	return  p2.getAccumulativeDistance(voxelpoints) - p1.getAccumulativeDistance(voxelpoints);
    }
}
