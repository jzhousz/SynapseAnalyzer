
import ij.*;
import ij.gui.Roi;
import ij.process.*;
import ij.plugin.*;

import java.awt.Color;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

import annotool.Annotation;
import annotool.Annotator;
import annotool.extract.*;
import annotool.gui.model.*;
//import annotool.analysis.Utility;
import annotool.classify.*;
import annotool.io.ChainIO;
import annotool.io.DataInput;

/*
 * 1/24/2012
 //This class is for synapse analysis using ROI recognition
 //It reads in a 3D image, and a list of tagged 3D ROIs -current GUI can't take 3D image set yet as of Jan 2012. 
 //It will then load a model (or do it in a hard-coded way)
 //Training
 //Apply to 3D local maxima
 //Output a set of detected candidates (synapses).
 //The output file will be combined with detection based on component analysis
 //Go through 3D splitting, to output final synapse count information.

 //This class is independent of other BioCAT classes for testing purpose.

 //Tricky: Because I converted the data to float BEFORE passing it to
 //the extractor, I need to change the imageType correspondingly.
 //Is there another way to do it?
 */

 /* NOTES:
 * -width, height, and depth are ROI NOT IMAGE, Image width is totalWidth and so on.
 * -rats enabled threshold value is -1 NOT 0!
 *
 *
 *
 */
/*
 * CHANGE LOG Jon Sanders
 * 11/03/13 - provide utility for output to file for object quantification
 *            to facilitate better detected.marker generation.
 * 11/04/13 - file writing for v3D file type.
 *          - file saving for generated local maxima image as .tif
 * 11/10/13 - fix missing else in test3DRoi, either use isRaw settings or not, not both.
 * 11/15/13 - rework get3DFeaturesViaHaar into a more general name, get3DFeaViaExtrator,
 *            and able to call other FEs.
 * 11/18/13 - adjust logic in test3droi to ignore edge pixels.
 * 11/20/13 - adjust logic in train3droi to ignore edge pixels.
 *          - switch classifier type to weka W_RandomForest.
 *          
 * 01/22/14 - begin overhaul conversion to DataInput style
 * 01/25/14 - implement new buildDataFromRoi() logic copied from annImageTable
 * 01/28/14 - write main() to take command args and run without gui.
 * 			- NOTE: all gui dependent and alldata code to be deprecated soon.
 * 01/29/14	- Loading from chain files. a little scattered though, but works.
 * 			- EXCEPT for getting a savable classifier by name.
 * 02/01/14 - finish getting savable classifier from file
 * 			- fix opaque data passing and poor function calls from main
 * 02/03/14 - begin refactoring all logic to be contained inside class, not main
 * 			- edit all currently used by command line code to load assets dynamically
 * 02/12/14 - fix last magic numbers, edit out now defunct code blocks
 * 02/17/14 - move exception handling to throws in most methods, let plugin handle exceptions where smart
 * 			- renamed test3DRoi to annotate and made non static			
 * 02/20/14 - draw static method now returns ImagePlus, need to write a public access for it
 * 			- annotate private, new public version to also handle file saving 
 * 02/24/14	- file saving now handled by various methods, not in private annotate
 * 			- trainAndtest returns rate now, for gui access
 * 02/26/14 - files now can be written to any path, will make necessary dirs
 * 04/10/14 - supports passed in threshold on annotation
 * 11/25/15 - remove local maxima calculation, switching ideology to rely more on Machine Learning step
 * 12/21/15 - changed logic for adding Point3D to centers hashmap. add all, then do neighborhood search AFTER complete annotation.
 * 12/23/15 - added dummy output stream to stop extra biocat prints.
 */

 /*TODO
  * generalize code to give control to plugins
  * -dont abuse System.out, pass back data structures of results for better display?
  * -edge case and error handling
  * -still need to use both ArrayList Alldata AND DataInput Problem, do not like. fix?
  * 	-requires contrived get3DFeaturesViaExtractor overloads.
  * iclean up removal of local maxima
  * -optimize ... a lot of stuff.
  */
public class ThreeDROISynapseDriver {
	
	//file output modes
	static final int NONE = 0;
	static final int MARKER_ONLY = 1;
	static final int VAA3D_ONLY = 2;
	static final int MARKER_VAA3D_BOTH = 3;
	
	
	//data members
	static SavableClassifier	classifier = null;
	ImagePlus 					imp, localMax, mask;
	int 						depth = 1, width = 1, height = 1;
	int 						synapseChannel = 0;
	annotool.io.DataInput 		problem = null;
	ArrayList<Chain> 			chainList;
	double  					trainingRate = 0;
	float[][] 					extractedFea = null;
	HashSet<Point3D>  			detectedCenters;
	
	//dummy print stream to supress output from BioCAT
	PrintStream originalStream = System.out;
	PrintStream dummyStream    = new PrintStream(new OutputStream(){
		public void write(int b) {
			//empty 
		}
	});	

	
	/////////////////////
	// deprecated in command line version
	// anisotropic microscopic image cube: 9*9*3
	static boolean useRaw = false; //still need for annotate.


	//constructor
	//assign members and build data into DataInput type
	public ThreeDROISynapseDriver(ImagePlus im, String[] rp, int d, int w, int h, String chains) throws Exception 
	{
		//simple assignments
		imp = im;
		depth = d;
		width = w;
		height= h;
		
		try{ //build a DataInput object to encapsulate data
			problem = buildDataFromROI(im, rp, d, w, h);
		} catch( Exception e){
			throw new Exception("ERROR: Problem building data from ROI", e);
		}		
		
		//debugging check class names
		HashMap<String, String> classNameCheck = new HashMap<String, String>();
		classNameCheck = problem.getClassNames();
		for(String key : classNameCheck.keySet()){
			System.out.println("name is: " + classNameCheck.get(key) + " " + "key: " + key  );
		}
		
		//debug pause
		//new java.util.Scanner(System.in).nextInt();		
			
		ChainIO chainLoader = new ChainIO();
		
			chainList = chainLoader.load(new File(chains));
		
	}//end constructor
	

	// train the model based on the contents of the chain file and 
	// return the rate after testing on the training ROIs
	public float trainAndTest() throws Exception
	{
		// feature extraction, selection
		extractedFea = null;
		Annotator annotator = new Annotator();

		extractedFea = get3DFeaViaExtrator(problem, chainList);
		
	    
		// set up training targets
		int[] target = new int[getLength()];
		target = problem.getTargets()[0];
		
		
		// get classifier 
		classifier = getSavableClassifier(chainList);
		classifier.trainingOnly(extractedFea, target);
		
		//test classifier
		Annotation[] ann = new Annotation[extractedFea.length];
		for (int i = 0; i < ann.length; i++){
			ann[i] = new Annotation();
		}

		float rate = (new Validator()).classify(extractedFea, extractedFea,
			target, target, classifier, ann);
		System.out.println("rate" + rate);

		
		
		System.out.println("C :: T");
		for (int i = 0; i < ann.length; i++){
			System.out.println(ann[i].anno + " :: "+ target[i]);
		}
		
		return rate;
	}//end trainAndTest
	

	//"test3droi" self contained logic using DataInput
	//incomplete, not going to use for now
	/*
	public void annotate(ImagePlus image)
	{
		int totalDepth  = image.getStackSize();
		int totalWidth  = image.getWidth();
		int totalHeight = image.getHeight();
		ImageProcessor ip = image.getProcessor();
		
		//first check for supported image
		if (image.getType() != DataInput.GRAY8) {
			System.out.println("Only grayscale 8 supported");
			return;
		}
		
		System.out.println("get localmaxima");
		boolean[] isMaxima = Utility.getLocalMaxima(imp, 0, 3, 3, 2); //2nd arg is channel
		draw(isMaxima, imp, totalDepth, totalHeight, totalWidth);
		
		
		//
		
	}//end annotate
	*/
	
	// will be called by ROITagger "train3droi button"
	//* DEPRACATED 01/25/14 (still functional)
	/*
	public static SavableClassifier train3droi(ImagePlus imp)
	{
		// read ROIm -- ROITAGGER
		RoiManager manager = RoiManager.getInstance();
		if (manager == null) {
			System.out.println("No ROI manager available.");
			return null;
		}

		ImageProcessor ip = imp.getProcessor();
		int totalwidth = ip.getWidth();
		int totalheight = ip.getHeight();
		int totaldepth = imp.getStackSize();
		int imageType = imp.getType();
		
		//System.out.println("image type: " + imageType);//debug

		if (imageType == DataInput.COLOR_RGB) {
			System.out.println("Only grayscale supported");
			return null;
		}

		// get data from ROI
		Roi[] rois = manager.getRoisAsArray();

		// ArrayList (image set) of ArrayList (stacks) of byte[]/int[]/float[]
		int x, y, w, h, cx, cy, cz;
		String name;

		ArrayList<ArrayList> alldata = new ArrayList<ArrayList>();
		for (int i = 0; i < rois.length; i++) {
			x = rois[i].getBounds().x;
			y = rois[i].getBounds().y;
			w = rois[i].getBounds().width;
			h = rois[i].getBounds().height;
			cx = x + w / 2;
			cy = y + h / 2;
			name = rois[i].getName();
			cz = manager.getSliceNumber(name);
			cz = cz -1; //slide starts from 1 but cz starts from 0.
			System.out.println("x:" + cx + " y:" + cy + " z:" + cz);

			// data for one ROI
			ArrayList imageStacks = new ArrayList(2 * rz + 1);
			boolean cubeComplete = true;//true if we can get complete cube data, (i.e. false if the pixel is too close to boundary)
			for (int zi = cz - rz; zi <= cz + rz; zi++) {
				if (zi < 0 || zi >= totaldepth) {//check z bound
					cubeComplete = false;
					break; //stop getting data for this whole slice
				}
				if (!cubeComplete){ //xi or yi out of bounds
					break;
				}
				// get current stack, stack starts from 1.
				ImageProcessor currentip = imp.getStack().getProcessor(zi + 1);
				float[] oneframe = new float[(2 * rx + 1) * (2 * ry + 1)];
				int indexsmall = 0;
				for (int xi = cx - rx; xi <= cx + rx; xi++) {
					if(!cubeComplete) break;//break from xi
					for (int yi = cy - ry; yi <= cy + ry; yi++) {
						if (!checkBound(xi, yi, zi, totalwidth, totalheight,totaldepth )) {
							cubeComplete = false;
							break;//break one layer of yi
						} else {
							indexsmall = (yi - (cy - ry)) * (2 * rx + 1) + (xi - (cx - rx));
							if (checkBound(xi, yi, zi, totalwidth, totalheight,totaldepth)) {
								oneframe[indexsmall] = (float) currentip.getPixel(xi, yi);
								System.out.print(oneframe[indexsmall] + " ");
							} else {
								oneframe[indexsmall] = 0; // fill 0?
							}
						}
					}//end yi				
				}//end xi
				imageStacks.add(oneframe);
			}// end of zi
			
			if(!cubeComplete) continue;//ignore this pixel and the cube around it if near bound.
			
			System.out.println();
			alldata.add(imageStacks);
		}// end of roi list

		// feature extraction, selection
		float[][] extractedFea = null;

		// no feature extraction, flatten all stacks for each image
		if (useRaw) {
			extractedFea = new float[alldata.size()][(2 * rx + 1)
					* (2 * ry + 1) * (2 * rz + 1)];
			int feaIndex = 0;
			for (int i = 0; i < alldata.size(); i++) {
				feaIndex = 0;
				for (int j = 0; j < alldata.get(i).size(); j++) {
					float[] oneimage = (float[]) alldata.get(i).get(j);
					for (int k = 0; k < oneimage.length; k++)
						extractedFea[i][feaIndex++] = oneimage[k];
				}
			}
		} else {
			extractedFea = get3DFeaViaExtrator(alldata,  imageType);
			
		    System.out.println("Extracted features:");
		    for(int i=0; i<(alldata.size()); i++ ) { //debug
		      System.out.println("");
		      for(int j=0; j<(2 * rx + 1); j++){
		    	  System.out.print(extractedFea[i][j] + ", ");
		      }
		    }
		}

		// set up training targets
		int[] target = new int[rois.length];
		
		for (int i = 0; i < positiveIndex; i++)
			target[i] = 1;
		for (int i = positiveIndex ; i < rois.length; i++)
			target[i] = 0;

		// new classifier
		for (int m = 0; m < extractedFea.length; m++) {
			for (int n = 0; n < extractedFea[0].length; n++)
				System.out.print(extractedFea[m][n] + " ");
			System.out.println();
		}

		//classifier = new SVMClassifier((2 * rz + 1) * (2 * ry + 1) * (2 * rx + 1));
		classifier = new MLPClassifier();
		
		//set the type of the weka classifier.
		HashMap<String,String> params = new HashMap<String, String>();
		params.put("W_RandomForest", "W_RandomForest");
	
		//classifier = new WekaClassifiers();
		//classifier.setParameters(params);
		
		try {
			classifier.trainingOnly(extractedFea, target);
		} catch (Exception e) {
			e.printStackTrace();
		}

		Annotation[] ann = new Annotation[extractedFea.length];
		for (int i = 0; i < ann.length; i++)
			ann[i] = new Annotation();
		try {
			float rate = (new Validator()).classify(extractedFea, extractedFea,
					target, target, classifier, ann);
			System.out.println("rate" + rate);
		} catch (Exception e) {
			e.printStackTrace();
		}
		for (int i = 0; i < ann.length; i++)
			System.out.println(ann[i].anno);

		return classifier;

	}//end train3DROI
	*/
	

	//public access for annotate, handles annotation and file saving 
	public void annotate(ImagePlus imp, String fileName, int fileMode, int thr, int noise, int lambda, int minLeaf) throws Exception
	{
        
		//do the annotation
		detectedCenters = annotate(imp, thr, noise, lambda, minLeaf);
		
		//handle file saving
		//based on mode.
		writeToFile(imp.getHeight(), fileName, fileMode);
			
	}
    
	
	
	//Write out the detected centers to a specified file.
	public void writeToFile(int height, String fileName, int fileMode) throws Exception 
	{
		
		//for file saving and naming convention
		//Date date = new Date();
		//SimpleDateFormat fmat =new SimpleDateFormat("_MM_dd_yyyy_hhmmss");
		if(detectedCenters.equals(null))
		{
			System.out.println("no centers detected. not writing files");
			return;
		}
		
		switch (fileMode) {
			case MARKER_ONLY: 	writeMarkerFile(detectedCenters, fileName);
								break;
			case VAA3D_ONLY:  	writeVaa3DFile(detectedCenters, fileName, height);
								break;
			case MARKER_VAA3D_BOTH: writeMarkerFile(detectedCenters, fileName);
								writeVaa3DFile(detectedCenters, fileName, height);
								break;
			default: 			break;
		}
		
	}

	public HashSet<Point3D> getDetectedCenters() throws Exception
	{
		if(detectedCenters.equals(null))
		{
			throw new Exception("error: centers not detected yet.");
		}
		
		return detectedCenters;
	}

	//older logic, but keeping this over re-writing the code from Annotator
	private HashSet<Point3D> annotate(ImagePlus imp, int threshold, int noise, int lambda, int minLeaf) throws Exception
	{ 
		// get data from imp
		ImageProcessor ip = imp.getProcessor();
		int totalwidth = ip.getWidth();
		int totalheight = ip.getHeight();
		int totaldepth = imp.getStackSize();
		int imageType = imp.getType();
		
		HashSet<Point3D> centers = new HashSet<Point3D>();
		
		//if incorrect image type
		if (imageType != DataInput.GRAY8) {
			System.out.println("Only grayscale 8 supported");
			return centers;
		}

		int rx, ry, rz;
		rx = calcHalfDim(width);
		ry = calcHalfDim(height);
		rz = calcHalfDim(depth);
		
		//* DEBUG REMOVE LOCAL MAX *//
		// use a window of 3*3*2 (radius, 7*7*5 actual) to get local maxim
		//System.out.println("get localmaxima");
		//boolean[] isMaxima = AnnotatorUtility.getLocalMaxima(imp, synapseChannel, 3, 3, 2, threshold, noise, lambda, minLeaf); //2nd arg is channel
		
		//try using just mask, relying on machine learning and meanshift to do maxima detection
		/*
		RatsSliceProcessor3D ratsProc = new RatsSliceProcessor3D(noise, lambda, minLeaf, imp, 1);
		ratsProc.getMask().show(); //be warned of bug when closing this window, crashes due to throwing out mask!
		mask = ratsProc.getMask();
		*/
		//IN PRGRESS MASK SELECTION CONVERSION
		if(threshold < 0) {
			RatsSliceProcessor ratsProc = new RatsSliceProcessor(noise, lambda, minLeaf, imp, 1);
			//ratsProc.getMask().show(); //be warned of bug when closing this window, crashes due to throwing out mask!
			mask = ratsProc.getMask();
			IJ.log("Converted to RATS mask!");
		} else {
			Duplicator d = new Duplicator();
			mask = d.run(imp);
			/*
			ImageProcessor globalIp;
			//convert global threshold to mask
			for (int z = 0; cz < totaldepth; cz++) {
				globalIp = mask.getStack().getProcessor(z+1); //get the correct slice for comparison
				globalIp.setThreshold(threshold,trheshold,)
				
			} // end Z
			mask = new ImagePlus("global mask" globalStack)
			*/
			
			//set all images to global threshold.
			IJ.setThreshold(mask,threshold,255,"Black & White");
			IJ.run(mask,"Convert to Mask","method=Default background=Default");
			IJ.log("converted to global binary mask!");
		} 
		mask.show();
		
		//draw(isMaxima, imp, totaldepth, totalheight, totalwidth);
		
		
		//swap out stream to suppress BioCAT!
		System.setOut(dummyStream);
		
		// get cube around local maxima, send to classifier
		// get cube around local maxima, send to classifier
		int total =0;
		int cubeDimension = (2 * rz + 1) * (2 * rx + 1) * (2 * ry + 1);
		float[] fea = new float[cubeDimension];
		double[] prob = new double[2]; // actually only need 1 spot
		int index = 0;
		int indexForCube = 0; // used for the cube
		// storage for extraction
		ArrayList imageStacks = new ArrayList(2 * rz + 1);
		float[] oneframe = new float[(2 * rx + 1) * (2 * ry + 1)];
		int indexsmall = 0;
		boolean cubeComplete = true; //indicates if we can get complete cube data (i.e. the pixel is not too close to boundary)
		ImageProcessor maskIp = null;
		
		//debug check ratio of classification
		int totalPixelsClassified = 0;
		
		//System.out.println("getting cube around each feature..."); //debug
		for (int cz = 0; cz < totaldepth; cz++) {
			maskIp = mask.getStack().getProcessor(cz+1); //get the correct mask for comparison
			for (int cy = 0; cy < totalheight; cy++) {
				for (int cx = 0; cx < totalwidth; cx++) {
					//if (isMaxima[index++]) { //DEGUG REMOVE LOCAL MAXIMA
					if (maskIp.getPixel(cx,cy) > 0) {	
						cubeComplete = true;
						// get the cube round it, order needs to be the same as training
						indexForCube = 0;
						if (!useRaw)	imageStacks.clear();
						for (int zi = cz - rz; zi <= cz + rz; zi++) {
							if (zi < 0 || zi >= totaldepth) // check bound on z
							{
								cubeComplete = false;
								break; //stop getting more data for this cube for the slice. Ignore the pixels that are too close to z-boundary
							}
							if (!cubeComplete) //due to xi or yi being out of boundary
							{ 
								break;  //break out of xi
							}
							// get current stack, stack starts from 1.
							ImageProcessor currentip = imp.getStack()
									.getProcessor(zi + 1);
							if (!useRaw)
								indexsmall = 0;
							for (int xi = cx - rx; xi <= cx + rx; xi++)
							{
								if (!cubeComplete) 
								{ 
									break;  //break out of xi
								}
								for (int yi = cy - ry; yi < cy + ry; yi++) 
								{
									if (!checkBound(xi, yi, zi, totalwidth, totalheight,totaldepth))
									{
										cubeComplete = false;
										break; //one layer break out of yi.
									}
									else
									{	
									   if (!useRaw)
									   {
										indexsmall = (yi - (cy - ry))
												* (2 * rx + 1)
												+ (xi - (cx - rx));
										oneframe[indexsmall] = (float) currentip.getPixel(xi, yi);
									   }
									   else {
										   fea[indexForCube] = (float) currentip.getPixel(xi, yi);
									  
									   //System.out.print(fea[indexForCube] + " ");//debug
									   indexForCube++;
									   }
									}
								} //end yi
							} //end xi
						    if (!useRaw) {//add current slice to the stacks
							  imageStacks.add(oneframe);
						   }
						}// end of zi
						
						if (!cubeComplete)
						{
							continue;   //ignore this pixel (and the cube around it) if it is too close to the boundary
						}
						
						//now do extraction (if not using raw data) and classification on the cube	
						if(!useRaw)
						{
						  // pass in an arraylist of an arraylist of an array
						  ArrayList list = new ArrayList();
						  list.add(imageStacks);
						  float[][] fea3D = get3DFeaViaExtrator(list, imageType, height, width, depth, chainList );
						  fea = fea3D[0];
						}
						
						try {
							
							int result = classifier.classifyUsingModel(
									classifier.getModel(), fea, prob);		
							
							
							/*if(result == 1) 
							{
								//mean shift
								//7x7x3 region
								Point3D shifted=getMassCenter(imp, totalwidth, totalheight, totaldepth, cx, cy, cz, 3,3,1);
								
								//add to master list
								centers.add(shifted);
								total ++;	
							}*/

							totalPixelsClassified++;
							
							//debug add any positive SVM result
							if(result == 1) {
								centers.add(new Point3D(cx,cy,cz));
								total ++;
							}
							
						} catch (Exception e) {
							e.printStackTrace();
						}

					}// end of the local maxima
				}// end of cx
			}// end of cy
		}// end of cz  

		
		//new 12/21/15 try post-scan search and remove for nearby centers
		
		/*Iterator<Point3D> it = centers.iterator();
		while (it.hasNext()){
			if(searchForIt(centers, it.next(), rx, ry, rz)) {
				it.remove();
			}
		}*/
		
		
		//swap back to system.out!
		System.setOut(originalStream);		
		
		System.out.println("total detected positive: "+total);
		System.out.println("total detected positive after mean-shifting: "+centers.size());
		System.out.println("total Classified voxels: "+totalPixelsClassified);
		//PAUSE
		//Scanner keyboard = new Scanner(System.in);
		//keyboard.nextLine();
		
		return centers;
	}
	
	//write out to filename as a IJ marker file formatted text file.
	private static void writeMarkerFile(HashSet<Point3D> centers, String fileName ) throws Exception
    {
		try{
			//add escape slashes?
			
			//append extension and label
			fileName+= "_IJ.marker";
			File file = new File(fileName);
			//file.getParentFile().mkdirs();
			BufferedWriter marker = new BufferedWriter(new FileWriter(file));
			
		    //write each to file
			System.out.println("general marker file output format:");
			System.out.println("x,y,z,");//.marker csv format
		    for(Point3D p : centers)
			{
				System.out.println(p.x + " " + p.y + " " + p.z); //echo to console
				marker.write(p.x + " " + p.y + " " + p.z);
				marker.newLine();
	
			}
		    
		    System.out.println("written to file: " + fileName);
		    
			marker.close();
		
		} catch(IOException e){
			System.out.println("error writing to file.");
			throw new Exception("error writing to file.", e);
		} 
		
	}
	
	//write out to file as a Vaa3D formatted text file.
	private static void writeVaa3DFile(HashSet<Point3D> centers, String fileName, int totalHeight ) throws Exception
	{
		
		// output result to a file as  V3D marker file
		try{
			//append extension and label
			fileName+="_v3d.marker";
			File file = new File(fileName);
			BufferedWriter marker = new BufferedWriter(new FileWriter(file));
			
		    //write each to file
			System.out.println("Vaa3D file output format:");
			System.out.println("x,y,z,radius,shape,name,comment,color_r,color_g,color_b");//v3d marker .csv format
			int i=1;
			for(Point3D p : centers)
			{
		    	int tmpy=totalHeight-p.y;
				System.out.println((p.x+1) + ", " + tmpy + ", " + (p.z+1) + ", 0,1,detected center " + i + "," + 0 + ",255,50,25"); //echo to console
				marker.write((p.x+1) + ", " + tmpy + ", " + (p.z+1) + ", 0,1,detected center " + i + "," + 0 + ",255,50,25");
				marker.newLine();
				i++;
	
			}
		    
		    System.out.println("written to file: " + fileName);
		    
			marker.close();
			
		} catch(IOException e){
			System.out.println("error writing to file.");
			throw new Exception("error writing to file.", e); 	
		} 
	}
	
	//calculate the correct diameters from roi dimensions for use in annotate().
	private int calcHalfDim(int x) 
    {
		if (x == 1) return x;//dont return a 0 value, just use 1 instead
		
		if((x & 1) == 0){//then even
			return x/2;
		} else { //odd then offset down
			return (x-1)/2;
		}
	}

	//parse ROI files and construct a DataInput object to encapsulate data
	private static DataInput buildDataFromROI(ImagePlus imp, String[] roiPaths, int d, int w, int h) throws Exception
	{
		HashMap classMap = new HashMap<String, String>();
		HashMap roiList= new HashMap<String, Roi>();
		
		
		for(String path : roiPaths) {
			
			
			if(path.endsWith("marker"))
			{
			 String className = Utils.removeExtension(path.substring(path.lastIndexOf(File.separator) + 1));
			 Utils.openRoiMarker(path, roiList, classMap, className);
			}
			else //ROI .zip file
			{
			 String className = Utils.removeExtension(path.substring(path.lastIndexOf(File.separator) + 1));
			 Utils.openRoiZip(path, roiList, classMap, className);
			}
		}
		
		
		if(roiList.size() < 1){
			System.out.println("ERROR: No rois specified.");
		}
		
		return new DataInput(imp, roiList, classMap, Annotator.channel, d, w, h);
		
	}
	
	
	
	// 3D mean shift algorithm to get to center of mass
	//start from ix, iy, iz, until converge to a stable center of mass
	// rx, ry, rz is the searching radium
	// If in valley, will not move to high lands. 
	//
	private static Point3D getMassCenter(ImagePlus imp, int sx, int sy, int sz, int ix, int iy, int iz, int rx, int ry, int rz)
	{
		 double rx_ms = rx, ry_ms = ry, rz_ms = rz;
	     double scx=0,scy=0,scz=0,si=0;
		 double ocx, ocy, ocz; //old center position
		 double ncx, ncy, ncz; //new center position
	     int k1, j1, i1;

	     ncx=ix;
	     ncy=iy;
	     ncz=iz; //new center position
		 
	     ImageProcessor ip;
	     int cv;
		 while (true) //mean shift to estimate the true center
		 {
				ocx=ncx; ocy=ncy; ocz=ncz;

				for (k1=(int) (ocz-rz_ms) ;k1<= (int) (ocz+rz_ms);k1++)
				{
	 			   if (k1<0 || k1>=sz)  continue;
	 			   ip = imp.getStack().getProcessor(k1+1);
				   for (j1= (int)(ocy-ry_ms);j1<= (int) (ocy+ry_ms);j1++)
				   {
							if (j1<0 || j1>=sy)	continue;
							for (i1= (int) (ocx-rx_ms);i1<= (int) (ocx+rx_ms);i1++)
							{
								if (i1<0 || i1>=sx)		continue;
								
								cv = ip.getPixel(i1,j1);

	                        	scz += k1*cv;
								scy += j1*cv;
								scx += i1*cv;
								si += cv;
							}
					}
				}
				if (si>0)
					{ncx = scx/si; ncy = scy/si; ncz = scz/si;}
				else
					{ncx = ocx; ncy = ocy; ncz = ocz;}

				if (ncx<rx || ncx>=sx-1-rx || ncy<ry || ncy>=sy-1-ry || ncz<rz || ncz>=sz-1-rz) //move out of boundary
				{
					ncx = ocx; ncy = ocy; ncz = ocz; //use the last valid center
					break;
				}
				
				//stop when the difference between old center and new center is small enough
				if (Math.sqrt((ncx-ocx)*(ncx-ocx)+(ncy-ocy)*(ncy-ocy)+(ncz-ocz)*(ncz-ocz))<=1)
				{
					System.out.println("cell mass center found: "+ (int) (ncx+0.5)+ " " + (int) (ncy+0.5) +  " " + (int) (ncz+0.5));
					System.out.println("(moved from " + ix + " " + iy + " " + iz +  ")" );
					break;
				}
			}//end of search for mass center

		 int movedx = (int)(ncx+0.5);
		 int movedy = (int)(ncy+0.5);
		 int movedz = (int)(ncz+0.5);
		 return new Point3D(movedx,movedy, movedz);
	}

	//return true only if ther is a point nearby that is NOT at the same exact location.
	//if there is a point at the same location, will return false.
	private static boolean searchForIt(HashSet<Point3D> detectedCenters, Point3D input, int rx, int ry, int rz)
	{
	  for(Point3D p : detectedCenters)
		//if (p.equals(input)) return true;
	
		if ( (!p.equals(input)) && (p.closesTo(input, rx, ry, rz) )) return true;
	  return false;
	}

	//old logic,
	//still need for annotate, messy parallel code now
	private static float[][] get3DFeaViaExtrator(ArrayList alldata, int imageType, int h, int w, int d, ArrayList<Chain> chainList  ) throws Exception
	{
		float[][] extractedFea = null;
		Annotator a = new Annotator();
		
			//sort of hack using logic from Annotator, to load from file AND use arraylist allData style, 
		    //this is not supported in modern Annotator logic, but we need it here.
			ArrayList<Extractor> extractors = chainList.get(0).getExtractors(); //get the list of extractors
			Extractor e = extractors.get(0); //
			FeatureExtractor extractor = a.getExtractorGivenName(e.getClassName(), e.getExternalPath(), e.getParams());
			
					
					
			annotool.ImgDimension dim = new annotool.ImgDimension(); //set up dimensions
			dim.height = h;
			dim.width = w;
			dim.depth = d;
			
			//extractedFea = extractor.calcFeatures(alldata, imageType, dim);
			extractedFea = extractor.calcFeatures(alldata, DataInput.GRAY32, dim); //force image tyoe 
		    //System.out.println("Extracted features:");//debug
			for (int m = 0; m < extractedFea.length; m++) {
				for (int n = 0; n < extractedFea[0].length; n++)
					System.out.print(extractedFea[m][n] + " ");
					System.out.println();
			}
					

		
		
		/*
		annotool.ImgDimension dim = new annotool.ImgDimension();
		dim.height = 2 * ry + 1;
		dim.width = 2 * rx + 1;
		dim.depth = 2 * rz + 1;
		
		try {
			// trick the HaarFeatureExtractor to take float
			// this is a bit weird. because calFeatures usually takes raw data
			// Object
			int changedImageType = DataInput.GRAY32;
			extractedFea = ex.calcFeatures(alldata, changedImageType, dim);
			
			//extractedFea = ex.calcFeatures(alldata, imageType, dim);
			
			
		} catch (Exception e) {
			e.getStackTrace();
		}
		 */
		
		return extractedFea;
	}
	
	// new logic using DataInput 1/27/14
	// compatible with multiple extractors, but this is useless because annotate() logic is not. 
	private static float[][] get3DFeaViaExtrator(DataInput problem, ArrayList<Chain> chainList) throws Exception
	{
		
		float[][] extractedFea = null;
		Annotator a = new Annotator();
		
		try {	
			//load from only first specified chain, others ignored
			ArrayList<Extractor> extractors = chainList.get(0).getExtractors();
			extractedFea = a.extractWithMultipleExtractors(problem, extractors);
		    System.out.println("Extracted features:");//debugDload
		    
			for (int m = 0; m < extractedFea.length; m++) {
				for (int n = 0; n < extractedFea[0].length; n++)
					System.out.print(extractedFea[m][n] + " ");
					System.out.println();
			}
					
			
		} catch (Exception e) {
			throw new Exception("Error extracting features with dataInput.", e);
			
		}

		return extractedFea;
		
		
	}
	
	//wrapper method to safelty get the classifier parsed from the chain file
	private static SavableClassifier getSavableClassifier(ArrayList<Chain> chainList) throws Exception
	{
		   Annotator a = new Annotator();
           
           /*String classi = chainList.get(0).getClassifierClassName();
           HashMap<String,String> params = chainList.get(0).getClassParams();
           Classifier c;
           c = a.getClassifierGivenName(classi, chainList.get(0).getClassifierExternalPath(), params);
           */
		   
           /*ArrayList<ClassifierChain> cc =  chainList.get(0).getClassifier();
           String classi = cc.get(0).getClassName();
           HashMap<String,String> params = cc.get(0).getParams();
           Classifier c;
           c = a.getClassifierGivenName(classi, cc.get(0).getExternalPath(), params);
		   */
		   
		   return chainList.get(0).getSavableClassifier();
           
          
           /*if(c instanceof SavableClassifier){
                   classifier = (SavableClassifier) c;
                   //classifier.trainingOnly(extractedFea, target);
           }
           else throw new Exception("classifier not savable");
          
           return classifier;
			*/
	}

	
	//still used in annotate, but highly parallel and redundant 1/29/14
	private static boolean checkBound(int xi, int yi, int zi, int totalwidth, int totalheight, int totaldepth) 
	{
		if (xi < 0 || xi >= totalwidth)
			return false;
		if (yi < 0 || yi >= totalheight)
			return false;
		if (zi < 0 || zi >= totaldepth)
			return false;
		return true;
	}

	// return an imagePlus of the localmaxima mask.
	private static ImagePlus draw(boolean[] isMaxima, ImagePlus imp, int totaldepth, int totalheight, int totalwidth)
	{
    
	//draw a new one
	ImageProcessor ip;
    ImagePlus centers= ij.gui.NewImage.createShortImage("Synapse Centers",totalwidth,totalheight,totaldepth,0);
    ImageStack stackParticles= centers.getStack();
    int index= -1;
    for (int z= 0; z< totaldepth; z++){
        ip =stackParticles.getProcessor(z+1);
        for (int y=0; y< totalheight; y++){
            for (int x=0; x< totalwidth; x++){
            	index++;
                if (isMaxima[index]) {
    				  ip.moveTo(x, y);						
        			  Color c = new Color(1f, 0f, 0f);
        			  ip.setColor(c);
        			  ip.fillOval(x - 1, y - 1, 2, 2);
                    //    ip.setValue(col);
                    //    ip.drawPixel(x, y);
        			 
                    }
                }
            }
        }
    centers.show();
    
	//for file saving and naming convention
	Date date = new Date();
	SimpleDateFormat fmat =new SimpleDateFormat("_MM_dd_yyyy_hhmmss" );
    
    //save local maxima image for inspection.
    System.out.println("saving local maxima image...");
    String imgFileName = new String("local_maxima" + fmat.format(date) +".tiff" );
    //IJ.save(centers, imgFileName);
    
    return centers;
    }
	
	
	// return the number of ROIS in this instance
	private int getLength() throws Exception
	{
		return problem.getLength();		 
	}
	
	//command line version of code. currently only for testing
	//takes args: image file path, positive roi, negative roi, roi depth, width, height, chain file 
	public static void main(String[] args)
	{
		
		ThreeDROISynapseDriver anno;
		
		String[] roiPaths;
		String chainPath;
		ImagePlus imp, annotateImp;
		int depth=3, width=9, height=9; 
		
		
		//check for null args
		if( args == null || args.length == 0 || args.length > 8){
			System.out.println("Correct usage: java ThreeDROISynapseDriver imagePath, pos ROI, neg ROI, ROI depth, width, height, chain file, OPTIONAL annotateImagePath ");
		}
		
		roiPaths = new String[2];
		//set vars from args
		try{
			imp = new ImagePlus( args[0]);     		System.out.println("image: " + args[0]); //show original image
			roiPaths[0] = args[1];					System.out.println(roiPaths[0]);
			roiPaths[1] = args[2];					System.out.println(roiPaths[1]);
			depth = Integer.parseInt(args[3]);		System.out.println(depth);
			width = Integer.parseInt(args[4]);		System.out.println(width);
			height = Integer.parseInt(args[5]);		System.out.println(height);
			chainPath = args[6];					System.out.println(chainPath);
			if(args.length == 8)
			{				
				annotateImp = new ImagePlus(args[7]);System.out.println(chainPath);
			}
		} catch(Exception argsE){
			System.out.println("Error parsing args");
			return;
		}
		
		System.out.println("success parsing args...");
	
		try{
			//construct
			anno = new ThreeDROISynapseDriver(imp, roiPaths, depth, width, height, chainPath);		
			System.out.println("success building data...\n");
			
			//train test model
			anno.trainAndTest();		
			System.out.println("Finished model ... \nEnter a new image path to annotate, leave blank to use same image.");
			
			//pause execution if no annotate image supplied
			if(args.length <= 7){
				BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in)); 
				String newImagePath = "";
				try {
					newImagePath = stdin.readLine();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				//annotate with model, use new image if provided
				if(!newImagePath.isEmpty())
				{
					annotateImp = new ImagePlus(newImagePath);		
				}
			}
			//anno.annotate(imp, "C:\\Users\\LukeC\\Desktop\\ROI\\test\\file" , ThreeDROISynapseDriver.MARKER_VAA3D_BOTH);
			anno.annotate(imp, "C:\\Users\\Sandahz\\Desktop\\ROIAnnotator\\testfile" , ThreeDROISynapseDriver.MARKER_VAA3D_BOTH, 0, 5,3,8);
			
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		
		
	}//end main
	
}//endclass ThreeDROISynapseDriver
