/* SynapseValidator.java
*       a Class used to validate the results of synapse annotation 
*		There is an included main() that can be run on the command line to perform the test.
*       ARGUMENTS:  a folder containing the RDL ground truth regions subfolders with IJ (converted) marker and image files, 
*					the IJ format result file from a whole image annotation with the plugin.
*    	USAGE: java SynapseValidator [ground truth dir] [anno Result File] [orig image width] [height]");			          
*    
*		CHANGELOG
*		11/08/14 - added Fstat calculation
*		11/10/14 - added rough file writing from main 
*		11/22/14 - added averages
*		11/24/14 - cleaned up decimals
*        1/23/15 - fixed averaging error
*        1/23/16 - consolidated extra marker output to another file
*				 - added more marker output, especially markers that fall inside each region for visualization. 
*       TODO:
*		-add marker output of results for each region so v3d can compare the two.
*       -codify 'close enough' calculation.
*		
*/

///////////////////////////////////////////////////////////////
// NOTES:
// -currently using file walker utilities available in jdk 7+
// - 3d to 1d index = X + Y*width + Z*width*depth
// 
//	map unique 1D value into hashmap
// for(detected synapse)
//  	go through real
//      	if(fall into neighborhood)
//          	count it as +1
//
//
// for fraction: go through entire detected list
//		for each, see if in region.
//			if yes, add to an array for processing
//			
// for Precision:
//			index detected (in region) against truth synapses. 
//				if( truth nearby)
//					numerator +1
//
//		denominator == total detected
//
//   since their are few truths, check them against the neighborhood.
//
// for recall: go through region ground truth and check for detectedRegionList
//			if(detected in neighborhood)
//				numerator +1 
//	
//		denominator == total truths
//
// 		for total count, just compare truth to list of detected within region.
//
// for Fstat: combine precision and recall values.
//
//		Fstat = 2 * (Precision * recall) / (Precision + recall)		  
//
//
//

/*
C:\Users\Jonathan\Desktop\validator>java SynapseValidator c:\Users\Jonathan\Desktop\rdlGroundTruth C:\Users\Jonathan\Desktop\rdlGroundTruth\Results_rdlFull_HaarSVM_thr28_4-15-14_IJ.marker 2333 1296 59
*/



import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.text.DecimalFormat;

public class SynapseValidator {
	//****************************************
	//CONSTANTS AND MEMBERS
	//
	//****************************************
	
	//constants
	static final int TEST_NUMBER = 4; //number of statistics to report
	
	//members
	private File groundTruthDir;
	private File annoResultFile;
	
	//private HashMap gtMarkers;
	private HashMap<Integer, Point3D> arMarkers;						//all markers in full image
	private ArrayList<ArrayList<Point3D>> gtRegionMarkers;//each region's markers
	private ArrayList<RegionData> gtRegionData; 					//the location and dimension for each region
	private double[][] valResults;					//validation results for each region
		/*
		* val results organization:
		*	head row = region
		*		0 = fraction 
		*		1 = precision
		*		2 = recall
		*/

	
	//orig image dims
	private int width;
	private int height;
	
	//****************************************
	//CONSTRUCTORS
	//
	//****************************************
	
	//constructor that takes File objects, one for groud truth Dir, and one for Annotation results
	public SynapseValidator( File gtDir, File arFile, int x, int y ) throws FileNotFoundException, IOException {
		if(gtDir.exists()) {
			groundTruthDir = gtDir;
			
			if(arFile.exists()) {
				annoResultFile = arFile;
			}
		} else {
			throw new IOException("files not valid.");
		}
				
		arMarkers = new HashMap<Integer, Point3D>();
		gtRegionMarkers = new ArrayList<ArrayList<Point3D>>();
		gtRegionData = new ArrayList<RegionData>();
		
		width  = x;
		height = y;
	
	}
	
	
	//constructor that takes file paths as strings instead
	public SynapseValidator(String groundPath, String annoResultPath, int x, int y) throws FileNotFoundException, IOException{	
		this(createFile(groundPath), createFile(annoResultPath), x, y);
	}
	
	//helper for constructor
	private static File createFile(String s) throws FileNotFoundException, IOException {
		File f = new File(s);
		return f;
	}
	
	//****************************************
	//PUBLIC METHODS
	//
	//****************************************
	
	//public method for validation. takes an (currently unused) option param and returns an array of test results
	public double[][] validate(int options) throws IOException{
		buildMarkers();
		
		validate();

		return valResults;
	
	}

	//accessor for results
	public double[][] getResults(){
		return valResults;
	}
	
	
	//****************************************
	//PRIVATE METHODS
	//
	//****************************************
	
	//private validator. does actual validation
	private void validate(){
		//get results for each region.
		// use offsets based on efficiency?
		// if possible is small, index each into the list of detected.
		// see notes at head

		
		System.out.println("\n*** Validation ***");
		
		
		valResults= new double[gtRegionMarkers.size()][TEST_NUMBER];
		int hashIndex;
		ArrayList<Point3D> arMarkersInRegion = new ArrayList<Point3D>();
		
		//process each region.
		int regionIndex = 0;
		Point3D p3d;
		
		try {
				
			//extra marker output files
			Date d = new Date();
			SimpleDateFormat df =new SimpleDateFormat("MM_dd_yyyy_hhmmss" );
				//extra file 1
			String fName = new String("Extra_Output_markers_from_test_regions" + df.format(d) + ".txt" );
			File fileExtra = new File(fName);
			BufferedWriter extraOut = new BufferedWriter(new FileWriter(fileExtra));
				//extra file 2
			String fName2 = new String("Extra_Output2_markers_from_manual_fullscale" + df.format(d) + ".txt" );
			File fileExtra2 = new File(fName2);
			BufferedWriter extraOut2 = new BufferedWriter(new FileWriter(fileExtra2));
				//extra file 3
			String fName3 = new String("Extra_Output3_precise&recall_markers" + df.format(d) + ".txt" );
			File fileExtra3 = new File(fName3);
			BufferedWriter extraOut3 = new BufferedWriter(new FileWriter(fileExtra3));
			
			//scan each region for markers from arMarkers that exist here.
			for(RegionData rData : gtRegionData){
				System.out.println("calculating fraction for region "+(regionIndex+1));
				arMarkersInRegion.clear();
				
				//do total count. need to determine what results synapses fall in regions.
				for(int z = rData.cornerZ; z< rData.cornerZ+rData.depth; z++){
					System.out.print("\nz = " +z+" ");
					for(int y = rData.cornerY; y<=rData.cornerY+rData.height; y++){
						for(int x = rData.cornerX; x<=rData.cornerX+rData.width; x++){
							hashIndex = calcIndex(x, y, z, width, height);
						
							p3d = arMarkers.get(hashIndex);
							if(p3d != null){
								arMarkersInRegion.add(p3d);
								System.out.print('x');
							} 
						}
					} 
				}
				
				
				//NEW TEST output the markers in region and markers in region.
				//////////////////
				/*extraOut.write("\n#MARKERS IN REGION from Test File - Native coordinates "+(regionIndex+1)); 
				extraOut.write(System.getProperty("line.separator"));
				for( Point3D p : arMarkersInRegion) {
					extraOut.write(p.x + " " + p.y + " " + p.z);
					extraOut.write(System.getProperty("line.separator"));
				}
				*/
				extraOut.write("\n#MARKERS IN REGION from test File - Adjusted to region "+(regionIndex+1));
				extraOut.write(System.getProperty("line.separator"));
				RegionData r = gtRegionData.get(regionIndex);
				for( Point3D p : arMarkersInRegion) {
					extraOut.write((p.x-r.cornerX) + " " + (p.y-r.cornerY) + " " + (p.z-r.cornerZ));
					extraOut.write(System.getProperty("line.separator"));
				}
				
				extraOut2.write("\n#MARKERS IN REGION manual annotation File "+(regionIndex+1));
				extraOut2.write(System.getProperty("line.separator"));
				for( Point3D p : gtRegionMarkers.get(regionIndex)) {
					extraOut2.write(p.x + " " + p.y + " " + p.z);
					extraOut2.write(System.getProperty("line.separator"));
				}
				///////////////////
				
				//fraction
				// results for detected over real
				double fraction =  (double)arMarkersInRegion.size() / gtRegionMarkers.get(regionIndex).size();
				valResults[regionIndex][0] = fraction;
				System.out.println("\nfraction = "+ arMarkersInRegion.size() + " / " + gtRegionMarkers.get(regionIndex).size() + " = " + fraction );
			
			
				// precision
				// use current arraylist of markers in region
				int preciseMarkers = 0;
				double precision = 0;
				
				//TEST EXTRA FILE OUTPUT
				extraOut3.write("\n#PRECISE MARKERS "+(regionIndex+1));
				extraOut3.write(System.getProperty("line.separator"));
				for(Point3D p : arMarkersInRegion) {
					if(markerNear(p, rData, gtRegionMarkers.get(regionIndex), 7,7,5)) {
						preciseMarkers++; //actual counter!
						extraOut3.write(p.x + " " + p.y + " " + p.z);
						extraOut3.write(System.getProperty("line.separator"));
						System.out.println("preciseMarker " + preciseMarkers);
					}
				
				}
				
				
				precision = (double) preciseMarkers / arMarkersInRegion.size();
				valResults[regionIndex][1] = precision;
			
			
				// recall
				// use current arrayList of markers in region
				int recallMarkers =0;
				double recall =0;
				
				//TEST EXTRA FILE OUTPUT
				extraOut3.write("\n#RECALL MARKERS "+(regionIndex+1));
				extraOut3.write(System.getProperty("line.separator"));
				for (Point3D p : gtRegionMarkers.get(regionIndex)) {
					if(markerNear(p, rData, arMarkersInRegion, 7,7,5)){
						recallMarkers++; //actual counter!
						extraOut3.write(p.x + " " + p.y + " " + p.z);
						extraOut3.write(System.getProperty("line.separator"));
						System.out.println("recallMarker "+recallMarkers);
					}
				
				
				}
				
				
				recall = (double)recallMarkers / gtRegionMarkers.get(regionIndex).size();
				valResults[regionIndex][2] = recall;
			
				
				//fStat
				//as F= 2*(p*r)/(p+r)
				double fStat;
				fStat = 2 * (precision * recall ) / (precision + recall);
				valResults[regionIndex][3] = fStat;
		
				//last in region loop
				regionIndex++;
				System.out.println("...done");
				
				//TEST EXTRA FILE OUTPUT
				extraOut.write(System.getProperty("line.separator"));
				extraOut2.write(System.getProperty("line.separator"));
				extraOut3.write(System.getProperty("line.separator"));
			}
			
			//clean up extra marker output file handle
			extraOut.close();
			extraOut2.close();
			extraOut3.close();

			} catch(Exception extra_write_except) {
				System.out.println("error writing extra file");
				extra_write_except.printStackTrace();
		}
		
	
	}//end validate

	
	//find if there are any points in list nearby to p. if yes, return true
	private Boolean markerNear(Point3D p, RegionData rd, ArrayList<Point3D> list, int w, int h, int d) {
		
		int xmin, xmax, ymin, ymax, zmin, zmax;
			xmin = p.x - calcHalfDim(w);
			xmax = p.x + calcHalfDim(w);
			ymin = p.y - calcHalfDim(h);
			ymax = p.y + calcHalfDim(h);
			zmin = p.z - calcHalfDim(d);
			zmax = p.z + calcHalfDim(d);
			
		
		//if( insidebounds) return true;
		for( Point3D p3d: list ){
			if(p3d.x>=xmin && p3d.x<=xmax && p3d.y>=ymin && p3d.y<=ymax && p3d.z>=zmin && p3d.z<=zmax ){
				System.out.println(p.convertToString() + " is near " + p3d.convertToString());
				
				return true;
			}
		
		}
		
		return false;
	
	}
	
	//read the file data and build the hashmaps
	private void buildMarkers() throws IOException{
		System.out.println("**BuildMarkers**");
		
		//read the anno results markers.
		arMarkers = hashMarkers(parseMarkerFile(annoResultFile), width, height);
		
		
		
		//read the gt directory for markers and offset info
		FileVisitor<Path> fv = new FileWalker();
	
		Files.walkFileTree(groundTruthDir.toPath(), fv);
		
		//gtRegionMarkers data are set by walker
		/*
		int i = 0;
		for(ArrayList<Point3D> ar : gtRegionMarkers) {
			System.out.println("region " + (i+1) + "++++++++++");
			for(Point3D p3d : ar) {
				System.out.println(p3d.convertToString());
			}
		
			System.out.println(gtRegionMarkers.get(i).size()+" markers");
		
			i++;
		}
		*/
		
		System.out.println("annoResultFile = " + annoResultFile.getName());

		
		
		System.out.println("**END BuildMarkers**\n");
	}//end buildMarkers

	
	//read a file and build an arraylist of markers
	private static ArrayList<Point3D> parseMarkerFile(File markerFile) throws IOException{
	
		ArrayList<Point3D> markers = new ArrayList<Point3D>();
		System.out.println("**reading line 1 **");
		
		//open file stream
		FileInputStream fstream = new FileInputStream(markerFile);
		BufferedReader in = new BufferedReader(new InputStreamReader(fstream));

		//read
		String line;
		//Point3D p3d = null;
		int count = 1;
		while((line = in.readLine()) != null) {

				//discard comments and blank lines 
				if(line.contains(""+"#") || line.length()<=1) {
						line = in.readLine();//ignore comments and space
				} else {
					//store point in markers
					markers.add(lineToPoint3D(line));
					count++;
				}
				
		}

		System.out.println("parsed " + count + " markers from file.");
		
		
		fstream.close();
		return markers;
	
	}//end parseMarkerFile
	

	//hash the markers for later access
	private static HashMap<Integer, Point3D> hashMarkers(ArrayList<Point3D> markers, int w, int h) {
		HashMap<Integer, Point3D> hashedMarkers = new HashMap<Integer, Point3D>();
		
		int rawID =0;
		for(Point3D p3d : markers) {
					rawID = calcIndex(p3d.x, p3d.y, p3d.z, w, h);
					hashedMarkers.put(new Integer(rawID),p3d);
		}
		
		
		//System.out.println('\n'+"Number of markers = "+markers.size()); 
		//System.out.println('\n'+"Number of hashes = "+hashedMarkers.size()); 
		
		
		return hashedMarkers;
	}

	
	
	//tokenize the raw file line and return a Point3D
	private static Point3D lineToPoint3D(String l) {
		int x,y,z;
			
		String[] t = l.split(" ");
		x=Integer.parseInt(t[0].trim());
		y=Integer.parseInt(t[1].trim());
		z=Integer.parseInt(t[2].trim());

		
		return new Point3D(x,y,z);
	}
	
	//convert the p3d values into a hashable unique index
	private static int calcIndex(int x, int y, int z, int w, int h) {
		return x + y*w + z*w*h;
	}
	
	//claculate the whole pixel value for half of a dimension
	private int calcHalfDim(int x) {
                if (x == 1) return x;//dont return a 0 value, just use 1 instead
                
                if((x & 1) == 0){//then even
                        return x/2;
                } else { //odd then offset down
                        return (x-1)/2;
                }
        }
	
	//****************************************
	//MAIN()
	// 
	//****************************************
	
	//main for execution on command line.
	//
	public static void main(String[] args) {
		if (args.length == 0 ) {
			System.out.println("USAGE: java SynapseValidator [ground truth dir] [anno Result File] [orig image width] [height]");
			return;
		}
		
		//declare!
		String extraFile = "";
		SynapseValidator validator;
		File file;
		BufferedWriter out;
		DecimalFormat fmatDecimal = new DecimalFormat("##.00%"); //% formatting
		double [] totals = new double[TEST_NUMBER];
		
		for(double i : totals) { //make sure 0 totals
			i = 0.00;
		}
		
		//get the extra file name info from either path or args
		if (args.length == 5) {
			extraFile = args[4];
		}

		try{
			//file saving mechanics
			Date date = new Date();
			SimpleDateFormat fmatDate =new SimpleDateFormat("MM_dd_yyyy_hhmmss" );
			String fileName = new String("validation_result_" + fmatDate.format(date) + "_" + extraFile + ".txt" );
			file = new File(fileName);
			out = new BufferedWriter(new FileWriter(file));

			//do validation
			validator = new SynapseValidator(args[0], args[1], Integer.parseInt(args[2]), Integer.parseInt(args[3]));
			
			validator.validate(1);
		
			//print to console *******************************
			//print to file	
			
			//headings and labels for stats
			System.out.println("\nResults by each region:");
			out.write("ground Truth Dir: " + args[0] + System.getProperty("line.separator") +
					  "anno Result File: " + args[1] + System.getProperty("line.separator") +
					  "Image Width:      " + args[2] + System.getProperty("line.separator") +
					  "Image Height:     " + args[3] + System.getProperty("line.separator") + System.getProperty("line.separator") );
			out.write("Results by each region:");
			out.write(System.getProperty("line.separator"));
			
			String[] labels = new String[TEST_NUMBER];
			labels[0] = "Fraction:  "; 
			labels[1] = "Precision: ";
			labels[2] = "Recall:    ";
			labels[3] = "F-measure: ";
			
			//loop through all regions
			int regionCount = 0;
			for(double[] region : validator.getResults() ) {
				System.out.println("region: "+(regionCount+1));
				out.write("region: "+(regionCount+1));
				out.write(System.getProperty("line.separator"));
				
				//loop through every statistic, track the totals
				int i=0;
				for(double result : region) {
					System.out.println("    " + labels[i] + fmatDecimal.format(result));
					out.write("    " + labels[i] + fmatDecimal.format(result));
					out.write(System.getProperty("line.separator"));
					totals[i] += result; // sum totals
					i++;
				}
				
				regionCount++;
			}
			
			//run average of totals
			System.out.println("Averages for all regions:");
			out.write("Averages for all regions:");
			out.write(System.getProperty("line.separator"));
			int i=0;
			for(double total : totals) {
				System.out.println("    Average " + labels[i] + fmatDecimal.format(total/regionCount));
				out.write("    Average " + labels[i] + fmatDecimal.format(total/regionCount));
				out.write(System.getProperty("line.separator"));				
				i++;
			}
			
			
			
			System.out.println("\nWritten to file: " + fileName);
			
			out.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		
	}//end main

	
	
	
	//****************************************
	//File Walker inner class
	// 	NOTE: only jdk 7+
	//
	// -upon entry into a region dir, data is read in and recorded.
	// -upon region dir exit, that data is converted into whole image index 
	// and put into array.
	//****************************************
	private class FileWalker extends SimpleFileVisitor<Path> {
		
		//constants
		static final String DATA_DIR_NAME = "region";
		static final String ROOT_DIR_NAME = "truth";
		static final String MARKER_EXT    = ".marker";
		static final String IMG_EXT       = ".tif";
		
		//members
		private int regionCounter = 0;
		private Boolean regionFlag = false;
		private ArrayList<Point3D> tempMarkers = null;
		private RegionData tempRegionData = null;
		
		
		
		
		//****************************************
		//public methods
		// 
		//****************************************
		
		//define file visit behavior
		@Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
			System.out.println("	Processing file:" + file);
			
			String fileName = file.getFileName().toString();
			
			if(regionFlag.equals(true)) { //ignore if in a dir that is not a region
				if(fileName.contains(MARKER_EXT)) {//file is a marker 
					System.out.println("		"+fileName+" file is MARKER.");
					//process the file data if markers, and read location if picture. TODO
					tempMarkers = parseMarkerFile(file.toFile());
					
				} else if(fileName.contains(IMG_EXT)) {//file is a region image
					System.out.println("		"+fileName+" is IMAGE.");
					//just parse the useful info from the file name
					fileName = fileName.substring(0,fileName.lastIndexOf('.'));
					String[] tempRegionDataStrings = fileName.split("_");
					
					/*
					System.out.println();
					for(String s : tempRegionDataStrings) {
						System.out.print(s+", ");
					}
					System.out.println();
					*/
				
					//store region data				//x										//y										//z										//w									//h										//d
					tempRegionData = new RegionData(Integer.parseInt(tempRegionDataStrings[1]), Integer.parseInt(tempRegionDataStrings[2]), Integer.parseInt(tempRegionDataStrings[3]), 
												    Integer.parseInt(tempRegionDataStrings[4]), Integer.parseInt(tempRegionDataStrings[5]), Integer.parseInt(tempRegionDataStrings[6]));
					System.out.println("		"+tempRegionData.toString());
				
				}
			}
			
			return FileVisitResult.CONTINUE;
			
		}
		
		//define directory pre visit behavior
		@Override  public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
			System.out.println("Processing directory:" + dir);
			
			//if dir is a region then process
			if(dir.getFileName().toString().toLowerCase().contains(DATA_DIR_NAME.toLowerCase())) {
				System.out.println(dir.getFileName() + " contains \"region.\"");
				regionFound();
				return FileVisitResult.CONTINUE;
			//if dir is the root then continue	
			} else if(dir.getFileName().toString().toLowerCase().contains(ROOT_DIR_NAME.toLowerCase())) {
				return FileVisitResult.CONTINUE;
			//if dir is not relevant, skip it
			} else {
				System.out.println(dir.getFileName() + " does not contain \"region\"");
				return FileVisitResult.SKIP_SUBTREE;
			}
			
			//return FileVisitResult.CONTINUE;
		}
	
		//define directory exit behavior
		@Override public FileVisitResult postVisitDirectory( Path dir, IOException e ) {
			System.out.println("dirCounter = " + regionCounter);
			
			if(regionFlag){
				//convert the markers to global coordinates
				System.out.println("converting markers to full scale image.");
				for( Point3D p3d : tempMarkers) {
					
					p3d.offsetPoint(tempRegionData.cornerX, tempRegionData.cornerY, tempRegionData.cornerZ);
					
					System.out.println("new point: " + p3d.convertToString());
					
				}
				
				
				//record the markers
				gtRegionMarkers.add(tempMarkers);
				
				//record regionData
				gtRegionData.add(tempRegionData);
			
			}
			
			//reset data for a new region.
			
			reset();
			
			
			return FileVisitResult.CONTINUE;
		}
	
	
		//****************************************
		//private methods
		// 
		//****************************************
		private void regionFound() {
			regionCounter++;
			regionFlag=true;
			
		}
		
		private void reset() {
			regionFlag=false;
			tempMarkers = null;
			//offset = null;
			tempRegionData = null;
			//tempRegionDataStrings = null;
		
		}
		
	
	}//end FileWalker
	
	
}//end class



//helper region class
class RegionData {

	int cornerX, cornerY, cornerZ, width, height, depth;
	
	public RegionData(int x, int y, int z, int w, int h, int d) {
		cornerX = x;
		cornerY = y;
		cornerZ = z;
		width   = w;
		height  = h;
		depth	= d;
	
	}

	public @Override String toString() {
		return "<"+cornerX+", "+cornerY+", "+cornerZ+", "+width+", "+height+", "+depth+">";
	}

}