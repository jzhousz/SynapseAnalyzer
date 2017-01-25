import ij.*;
import ij.IJ;
import ij.io.*;
import ij.plugin.*;
import ij.plugin.filter.*;
import ij.process.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.Button;
import ij.gui.*;
import ij.plugin.Duplicator;
import java.util.*;
import java.io.*;

/** This ImageJ plugin filter colocalized marker data against a 
    neuron backbone channel mask

    REQUIRES:
        Point3D.class
        
    
    
*/

public class Colocal_filter implements PlugIn, DialogListener{

    //VARIABLES ////////////////////////////////////////////////////////
    static final String pluginName = "Colocalization Filter";
    static String DEST_FOLDER = "plugin_dump";
    
    //rats defaults
    public static int RATS_noise_default  = 10;
    public static int RATS_lambda_default  = 3;
    public static int RATS_minLeaf_default = 32;
    public static int colocalX_default     = 9;
    public static int colocalY_default     = 9;
    public static int colocalZ_default     = 5;
    
    Vector sliders;
    Vector value;
    File file;
    ArrayList<Point3D> markers, colocalMarkers;
    Duplicator d;
    boolean debug = true;
    
    //rats values
    int noise;
    int lambda;
    int minLeaf;
    RatsSliceProcessor rats;
    ImageStack ratsStack;
    //image data
    int Width;
    int Height;
    int NbSlices;
    int arrayLength;
    String imgtitle;
    int PixVal;
    int[] pict;
    boolean[] thr; // true if above threshold
    int arrayIndex;
    int thrVal;
    int x, y, z;
    Point3D p3d;
    int colocalX, colocalY, colocalZ;
    int slice;
    
    ImagePlus img;
    ImageProcessor ip;
    Overlay ov;
    
    //marker chooser GUI
    String    markerFile;
    Panel     markerChooser;
    Button    markerB;
    TextField markerField;
    ////////////////////////////////////////////////////////////////////
    
    /************************************************************
    /*Set up the pre-calculated values and the GUI for the plugin
    /**/
    public boolean setupGUI(String arg) {
        img = WindowManager.getCurrentImage();
        if (img==null){
            IJ.noImage();
            return false;
        } else if (img.getType() != ImagePlus.GRAY8 && img.getType() != ImagePlus.GRAY16 ) {
            IJ.error("8 or 16 bit greyscale image required");
            return false;
        }
        
        IJ.showStatus("initializing...");
        //get image data
        Width=img.getWidth();
        Height=img.getHeight();
        NbSlices=img.getStackSize();
        arrayLength=Width*Height*NbSlices;
        thr=new boolean [arrayLength];
        imgtitle = img.getTitle();

        
        ip=img.getProcessor();
        thrVal=ip.getAutoThreshold();
        ip.setThreshold(thrVal,Math.pow(2,16),ImageProcessor.RED_LUT);
        img.setSlice((int)NbSlices/2);
        img.updateAndDraw(); 

        //dynamically get RATS minLeaf size
		//code derived from RATSForAxon run();
		int minW = ip.getWidth();
		int minH = ip.getHeight();
		minW = (int) minW/5;
		minH = (int) minH/5;
		if(minW < minH) {
			minLeaf = minW;
		} else {
			minLeaf = minH;
		}     

        colocalX = colocalX_default;
        colocalY = colocalY_default;
        colocalZ = colocalZ_default;
        
        //create GUI elements
        GenericDialog gd = new GenericDialog("Colocalization filter");
        
        //global threshold
        gd.addSlider("Global Threshold (if used): ",ip.getMin(), ip.getMax(),thrVal);
        gd.addSlider("Slice: ",1, NbSlices,(int) NbSlices/2);
        //RATS options
        gd.addMessage("RATS options: (leave noise 0 to disable)");
        gd.addNumericField("RATS noise:",RATS_noise_default,0);
        gd.addNumericField("RATS lambda:",RATS_lambda_default,0);
        gd.addNumericField("RATS minLeaf:",minLeaf,0);
        
        //marker file
        MyListener listener = new MyListener(); 
        markerChooser = new Panel();
            markerChooser.setLayout(new FlowLayout(FlowLayout.CENTER,5,0));       
        markerB = new Button("marker");
            markerB.addActionListener(listener);
        markerField = new TextField(Prefs.get("batch.markerB",""), 40);      
        markerChooser.add(markerB);
        markerChooser.add(markerField);
        gd.addMessage("marker file:");
        gd.addPanel(markerChooser);
        
        //nearby parameters
        gd.addMessage("scan region size:");
        gd.addSlider("neighborhood scan width", 1, 10 , colocalX_default);
        gd.addSlider("neighborhood scan height", 1, 10 , colocalY_default);
        gd.addSlider("neighborhood scan depth", 1, 10 , colocalZ_default);
        
        gd.addCheckbox("debug? ",debug);
        
        gd.addDialogListener(this);
        gd.showDialog();
        
        if (gd.wasCanceled()){
            ip.resetThreshold();
            img.updateAndDraw();
            return false;
        }
        IJ.register(this.getClass());       //protect static class variables (filter parameters) from garbage collection
        ip.resetThreshold();
        img.updateAndDraw();
        return true;  
    }
 
    /************************************************************
    /*constantly get recent data from GUI, 
    /*implemented to support threading later
    /**/ 
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
        thrVal          = (int)gd.getNextNumber();  
        slice           = (int)gd.getNextNumber(); //ignore the selected slice.
        noise           = (int)gd.getNextNumber();
        lambda          = (int)gd.getNextNumber();
        minLeaf         = (int)gd.getNextNumber();
        colocalX        = (int)gd.getNextNumber();
        colocalY        = (int)gd.getNextNumber();
        colocalZ        = (int)gd.getNextNumber();    
        debug           = gd.getNextBoolean();
        markerFile      = markerField.getText();
        
        //draw preview
        ip.setThreshold(thrVal,Math.pow(2,16),ImageProcessor.RED_LUT);
        img.setSlice(slice);
        img.updateAndDraw();
        
        return true;
    }
    
    //crude inner class listener for custom GUI buttons
    private class MyListener implements ActionListener {
      public void actionPerformed(ActionEvent e) {
      
        Object source = e.getSource();
        String s;
        String path ="default";
        OpenDialog od;
        DirectoryChooser dc;
        int num;
        
        if(source.equals(markerB)) {
            //IJ.log("markerB button press");
            od = new OpenDialog("select marker file", "");
            path = od.getPath();
            if( path.equals(null)) return;
            markerField.setText(path);
        }
      }
      
    }//end inner listener
 

    /************************************************************
    /*MAIN run method, after setup, do colocalization
    /**/ 
	public void run(String arg) {
        if (! setupGUI(arg)) return;
        
        
        IJ.log("running...");
        IJ.showStatus("running...");
        //check params
        if(debug){
            String sp = "   ";
            IJ.log("PARAMS: ");
            IJ.log(sp + "thr = " + thrVal); 
            IJ.log(sp + "coloc dist = " + colocalX);
            IJ.log(sp + "noise = " + noise);
            IJ.log(sp + "lambda = " + lambda);
            IJ.log(sp + "minLeaf = " + minLeaf);
            IJ.log(sp + "marker = " + markerFile);
        }
        
        IJ.log("loading markers from file...");
        //load markers from file
        try{
            file = new File(markerFile);
            markers = new ArrayList<Point3D>();
            markers = parseMarkerFile(file);
        } catch (Exception e) {
            IJ.log(e.getMessage());
        }
        
        //print loaded markers
        if(debug){
            for(Point3D p3d: markers) {
                IJ.log(p3d.convertToString());
            }
        }
        
        IJ.log("Thresholding...");
        //rats threshold image
        //in a one dimension array
        ImageStack stack = img.getStack();
        if(noise > 0)
            ratsStack = new ImageStack(Width,Height);
        arrayIndex=0;
        d = new Duplicator();
        for (z=1; z<=NbSlices; z++) {
            ip = stack.getProcessor(z);
            if(noise > 0) { //only if rats enabled
                IJ.showStatus("Doing RATS");
                IJ.showProgress(z,NbSlices);
                //IJ.log(" doing rats on slice " +z);
                rats = new RatsSliceProcessor(noise, lambda, minLeaf, new ImagePlus("", ip),0);
                ratsStack.addSlice(rats.getMask().getProcessor());
            }
            for (y=0; y<Height;y++) {
                for (x=0; x<Width;x++) {
                    PixVal=ip.getPixel(x, y);
                    //pict[arrayIndex]=PixVal;
                    if(noise > 0) {//using rats
                        if (rats.getMaskValue(x,y,0) > 0) {
                            thr[arrayIndex]=true; 
                        }
                    } else {
                        if (PixVal>thrVal){
                            thr[arrayIndex]=true;
                        }   
                    }
                    arrayIndex++;
                }
            }
        }
        
        if(noise > 0)
            (new ImagePlus("RATS Mask",ratsStack)).show();

        if(debug)IJ.log("checking markers against mask...");
        //actually do the colocalization
        colocalMarkers = new ArrayList<Point3D>();
        boolean isLocal;
        String message;
        for(Point3D p3d: markers) {
            isLocal = colocalize(p3d, Width, Height, NbSlices, thr, colocalX, colocalY, colocalZ);
            if(isLocal){
                message = " YES "+p3d.convertToString();
                colocalMarkers.add(p3d);
            } else {
                message = " NO "+p3d.convertToString();
            }
            if(debug) IJ.log(message);
        }
        
        
        //print out colocal markers
        IJ.log("\nColocalized markers:");
        IJ.log("markers colocal to image: " + colocalMarkers.size());
        for(Point3D p3d: colocalMarkers) {
            IJ.log(p3d.convertToString());
        }
        
        try{
            writeMarkerFile(colocalMarkers,"colocal_markers");
            writeVaa3DFile(colocalMarkers,"colocal_markers",Height);
        } catch (Exception x) {
            IJ.log(x.getMessage());
        }
        
        
        
        
        IJ.log("Completed\n\n");
        IJ.showStatus("Completed!");
    }//end run
    
    /************************************************************
    /*colocalize a single point to the neuron channel
    /**/
    private boolean colocalize(Point3D p3d, int w, int h, int d, boolean[] t, int cx, int cy, int cz){
        int index = calcIndex(p3d.x, p3d.y, p3d.z, w, h);
        
        if(index >= t.length){
            if(debug) IJ.log("index " + index + " out of bounds. end array: " + t.length);
            return false;            
        }
        
        //1) index is ON the mask
        if (t[index] == true){
            return true;
        } 
        
        //2) index is NEAR the mask
        if(pointNearMask(p3d, w, h, d, t, cx, cy, cz)) {
            return true;
        }
        //3) index is NOT NEAR the mask
        return false;

    }    
    
    // calculate the 1D index value of a voxel
  	private static int calcIndex(int x, int y, int z, int w, int h) {
		return x + y*w + z*w*h;
	}    
    
    // make sure a value is within a range from 0 to [dimension]
    // used to check boundaries
    private int checkAndFixBounds(int value, int dimension){
      if(value < 0) {
          if(debug) IJ.log(value + " out of bounds, setting to 0");//debug
          return 0;
      } else if(value > dimension) {
          if(debug) IJ.log(value + " out of bounds, setting to " + dimension);//debug
          return dimension; 
      } else return value;
    }
	
    private boolean pointNearMask(Point3D p, int w, int h, int d, boolean[] t, int cx, int cy, int cz) {
              
        //get dimensions of area around point
        int xmin, xmax, ymin, ymax, zmin, zmax;
		xmin = checkAndFixBounds(p.x - calcHalfDim(cx), w);
		xmax = checkAndFixBounds(p.x + calcHalfDim(cx), w);
		ymin = checkAndFixBounds(p.y - calcHalfDim(cy), h);
		ymax = checkAndFixBounds(p.y + calcHalfDim(cy), h);
		zmin = checkAndFixBounds(p.z - calcHalfDim(cz), d);
		zmax = checkAndFixBounds(p.z + calcHalfDim(cz), d);
        
        //debug code
        boolean flag = false;
        //if(debug && (p.x == 49)) flag = true;
        if(flag)
            IJ.log("\ndebugging point near mask at " + p.convertToString() + " roi:" + cx +"x" + cy + "x" + cz);

        if(flag){
            IJ.log("x:" + xmin +", "+ xmax);
            IJ.log("y:" + ymin +", "+ ymax);
            IJ.log("z:" + zmin +", "+ zmax);
        }
        
        //check bounds
        
        
        //scan neighborhood for a pixel in mask
        int i;
        for( int z=zmin; z <=zmax; z++){
            for(int y=ymin; y <=ymax; y++){
                for( int x=xmin; x<=xmax; x++){
                    i = calcIndex(x,y,z,w,h);
                    if(flag){
                        IJ.log(x + " " + y + " " + z + " " + i + " " + t[i]);
                    }
                    if(t[i]==true){
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    
    /************************************************************
    /*Borrowed from SynapseValidator.java
    /*parse a marker file and store markers as arraylist of voxels
    /**/
    private static ArrayList<Point3D> parseMarkerFile(File markerFile) throws IOException{
	
		ArrayList<Point3D> markers = new ArrayList<Point3D>();
		IJ.log("**reading line 1 **");
		
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

		IJ.log("parsed " + count + " markers from file.");
		
		fstream.close();
		return markers;
	
	}//end parseMarkerFile
     
	//claculate the whole pixel value for half of a dimension
	private static int calcHalfDim(int x) {
                if (x == 1) return x;//dont return a 0 value, just use 1 instead
                
                if((x & 1) == 0){//then even
                        return x/2;
                } else { //odd then offset down
                        return (x-1)/2;
                }
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

    //borrowed from ThreeDROISynapseDriver
    //write out the markers to IJ directory
	private static void writeMarkerFile(ArrayList<Point3D> centers, String fileName ) throws Exception
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
	
    //borrowed from ThreeDROISynapseDriver
    //write out the markers to IJ directory
	private static void writeVaa3DFile(ArrayList<Point3D> centers, String fileName, int totalHeight ) throws Exception
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

    
}// end plugin	
	


