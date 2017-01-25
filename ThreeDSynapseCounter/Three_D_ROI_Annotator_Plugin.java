/* ThreeDRoiAnnotator - Jon Sanders 2/14/2014
 * Gui for running the ThreeDROIAnnotator out of BIOCat as an ImageJ plugin
 *
 */
 
 
 /* NOTES and TODO
  * 02/16/14 looking into awt file choosers from venkat
  * 02/18/14 implement rest of GUI, for now, add logic for annotation
  * 04/14/14 added default paths for testing ease
  *          save options added to gui
  *          uses new AnnotatorUtility file
  *          supports user threshold, complete with preview autothreshold  
  *   
  *          supports RATS 
  * 02/10/15 timer for analyze()         
  *
  * TODO
  * add support to train first. report success to user, 
  * report detected centers via an IJ results table
  */
  
  //09/06/14 NOTE: threshold dev hack, enter -1 in threshold field to 
  //			invoke rats thresholding, even though the slider wont go below 0.
  
 import ij.*;
 import ij.io.*;
 import ij.IJ;
 import ij.plugin.filter.PlugInFilter;
 import ij.process.*;
 import ij.gui.*;
 import java.awt.*;
 import java.awt.event.*;
 import java.awt.Button;
 import java.util.HashSet;
 
 public class Three_D_ROI_Annotator_Plugin implements PlugInFilter {
   
    int width;
    int depth;
    int height;
    int saveOption = 0;
    int threshold = 0;
	int noise = 4;
	int lambda = 3;
	int minLeaf = 8; //dynamically set in run()
    String posRoiPath, negRoiPath, chainPath, savePath;
    HashSet<Point3D> centers;
    ThreeDROISynapseDriver anno;
	boolean ratsOption = false;
	
	//timing
	long startTime;
	long endTime;
    
    //static and default vairables
    public static int min_roi_height = 1;
    public static int min_roi_width  = 1;
    public static int min_roi_depth  = 1;
    public static int max_roi_height = 20;
    public static int max_roi_width  = 20;
    public static int max_roi_depth  = 10;
    public static int def_roi_height = 9;
    public static int def_roi_width  = 9;
    public static int def_roi_depth  = 3;
    
    
    
    //gui elements
    GenericDialog gd;
    Panel posChooser, negChooser, chainChooser, thresholdP, saveChooser, saveDirP, SaveNameP, optionsP, ratsP;
    Button posRoiB, negRoiB, chainB, saveB, trainB, thresholdB;
    TextField posRoiField, negRoiField, chainField, saveLocField, saveNameField, thresholdField, ratsNoiseField, ratsLambdaField, ratsMinLeafField;
    Checkbox saveIjCB, saveV3dCB, imageCB, ratsCB;
    Label saveL, formatL, ratsL;
 
    public int setup(String arg, ImagePlus imp) {
    
        return DOES_8G;
    }
    
    public void run(ImageProcessor ip) {
    	
    	//dynamically get RATS minleaf size
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
		
        //setup gui
        if (! makeGUI()) return;
        //store data from gui fields
        if (! getDataFromFields()) {
            IJ.log("Missing or incorrect field data.");
            return;
        } 
        
		IJ.log("Annotation Start at:     "+produceTimeStamp(tick()));
        annotate();
        IJ.log("Annotation time elapsed: "+produceTimeStamp(tock()));       
        
    }
 
    //create the gui, if completed returns true to run method
    private boolean makeGUI() {
        
        //setup gui main panel
        gd =  new GenericDialog("3D ROI Annotator");
           // gd.setLayout(new GridLayout(10, 1));
       
        MyListener listener = new MyListener(); 
        
        
        //browser for posRoiB file
        posChooser = new Panel();
            posChooser.setLayout(new FlowLayout(FlowLayout.CENTER,5,0));       
        posRoiB = new Button("Positive");
            posRoiB.addActionListener(listener);
        posRoiField = new TextField(Prefs.get("batch.posRoiB",""), 40);      
        posChooser.add(posRoiB);
        posChooser.add(posRoiField);
        
        //browser for negRoiB file
        negChooser = new Panel();
            negChooser.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));       
        negRoiB = new Button("Negative");
            negRoiB.addActionListener(listener);
        negRoiField = new TextField(Prefs.get("batch.negRoiB",""), 40);       
        negChooser.add(negRoiB);
        negChooser.add(negRoiField);
 
        //browser for chainB file
        
        chainChooser = new Panel();
            chainChooser.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
        chainB = new Button("Chain file");
            chainB.addActionListener(listener);
        chainField = new TextField(Prefs.get("batch.chainB", ""), 40);
        chainChooser.add(chainB);
        chainChooser.add(chainField);
        
        //sliders are handled below, by IJ's GeneralDialog
        
		
		//thresholding (global) panel
        thresholdP = new Panel();
            thresholdP.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0)); 
        thresholdB = new Button("Auto Threshold");
            thresholdB.addActionListener(listener);
        thresholdField = new TextField(Prefs.get("batch.thresholdB", ""), 5);
		
        thresholdP.add(thresholdB);
        thresholdP.add(thresholdField);
		
		//thresholding (rats) panel
		ratsP = new Panel();
			ratsP.setLayout(new FlowLayout(FlowLayout.CENTER, 5,0));
		
		ratsCB = saveIjCB = new Checkbox("Use Rats");
			ratsCB.setState(true);
		
		ratsNoiseField = new TextField("" + noise, 4);
		ratsLambdaField= new TextField("" + lambda, 4);
		ratsMinLeafField=new TextField("" + minLeaf, 4);
		
		ratsP.add(ratsCB);
		ratsP.add(ratsNoiseField);
		ratsP.add(ratsLambdaField);
		ratsP.add(ratsMinLeafField);
        
        
        //browser and options for save location 
        saveChooser = new Panel();
            saveChooser.setLayout(new GridLayout(0,1));
        
        //formatL = new Label("Select save formats:");
        saveIjCB = new Checkbox("IJ ");
            saveIjCB.setState(true);
        saveV3dCB = new Checkbox("V3D");
            saveV3dCB.setState(true);
        saveB = new Button("Save Folder");
            saveB.addActionListener(listener);
        saveLocField = new TextField(Prefs.get("batch.saveB",""), 40);
        saveNameField = new TextField("Results", 20);
        
        saveDirP = new Panel();
            saveDirP.setLayout(new FlowLayout(FlowLayout.LEFT,5,0));
        saveDirP.add(saveB);
        saveDirP.add(saveLocField);
        
        SaveNameP = new Panel();
            SaveNameP.setLayout(new FlowLayout(FlowLayout.LEFT,5,0));
        saveL = new Label("File name:  ");
        SaveNameP.add(saveL);
        SaveNameP.add(saveNameField);
        
        
            //add to saveChooser panel
        saveChooser.add(saveIjCB);
        saveChooser.add(saveV3dCB);
        saveChooser.add(saveDirP);
        saveChooser.add(SaveNameP);
        
        //checkbox options setup
        optionsP = new Panel();
            optionsP.setLayout(new FlowLayout(FlowLayout.LEFT,5,0));
        imageCB = new Checkbox("different annotation image");
        
        optionsP.add(imageCB);
        
        
        //put it all in the dialog
        gd.addMessage("Positive ROI file:");
        gd.addPanel(posChooser);
        gd.addMessage("Negative ROI file:");
        gd.addPanel(negChooser);
        gd.addMessage("BioCAT chain file:");
        gd.addPanel(chainChooser);
        
        gd.addMessage("\nROI dimensions:");
        gd.addSlider("Roi height", min_roi_height, max_roi_height, def_roi_height);
        gd.addSlider("Roi width ", min_roi_width, max_roi_width, def_roi_width);
        gd.addSlider("Roi depth ", min_roi_depth, max_roi_depth, def_roi_depth);
        
        gd.addMessage("\n Global Threshold (leave 0 to use auto threshold");
        gd.addSlider("Threshold", 0, 255, 0);
        gd.addPanel(thresholdP);
        
		gd.addMessage("Rats Threshold \nOptions: noise, lambda, min-leaf");
		gd.addPanel(ratsP);
		
        gd.addMessage("\n\nSave formats, location and file name:");
        gd.addPanel(saveChooser);
        
        gd.addMessage("\n\nOther options:");
        gd.addPanel(optionsP);
        
        
        
        //fillDefaults(); //for testing
        
        
        //show it after completion
        gd.showDialog();
        
        if(gd.wasCanceled())
            return false;
    
        return true;
        

    }//end makeGUI
    
    //fill in some default strings for ease of testing.
    private void fillDefaults() {
        posRoiField.setText("F:\\URA\\Work\\roiAnnotator\\cropped_AxonTerminalSynapse_3DRoiSet_positive.zip");
        negRoiField.setText("F:\\URA\\Work\\roiAnnotator\\cropped_AxonTerminalSynapse_3DRoiSet_negative.zip");
        chainField.setText("F:\\URA\\Work\\roiAnnotator\\Synapse_Haar_SVM.ichn");
        saveLocField.setText("");
    

    }    
    //get all the data from the gui fields and put in vars
    //check for empty fields
    private boolean getDataFromFields() {
    
            posRoiPath = posRoiField.getText();
            negRoiPath = negRoiField.getText();
            chainPath  = chainField.getText();
            
            height = (int) gd.getNextNumber();
            width  = (int) gd.getNextNumber();
            depth  = (int) gd.getNextNumber();
            
            threshold = (int) gd.getNextNumber();
            
            savePath = (saveLocField.getText() + saveNameField.getText());
            
            //get rats options
			ratsOption= false;
			if( ratsCB.getState() ) ratsOption = true;
			
			noise   = Integer.parseInt(ratsNoiseField.getText());
			lambda  = Integer.parseInt(ratsLambdaField.getText());
			minLeaf = Integer.parseInt(ratsMinLeafField.getText());
			
			
            //decide on save options based on checkboxes
            //0 for none, 1 for IJ, 2 for V3D, 3 for both
            saveOption = 0;
            if(saveIjCB.getState()) saveOption+=anno.MARKER_ONLY;
            if(saveV3dCB.getState()) saveOption+=anno.VAA3D_ONLY;
    
            //break if file paths not specified.
            if(posRoiPath.length()==0 || negRoiPath.length()==0 || chainPath.length()==0 || savePath.length()==0) 
               return false;
    
            //print param list to log
            String s = ", ", e = " ";
            IJ.log("PARAMS: " + height + s + width + s + depth + s + posRoiPath + s + negRoiPath + s + chainPath + s + saveOption 
			       + " RATS: " + noise + e + lambda + e + minLeaf);
    
           return true;
    }//end getDataFromFields
    
    
    //annotate the same image after training. todo fix for new image
    private void annotate() {
        try{
        
            ImagePlus imp = WindowManager.getCurrentImage();
            
            //if new image option, use it for annotation
            ImagePlus annoImp = imp;
            
            if(imageCB.getState()) {
                 OpenDialog od = new OpenDialog("Select an image to be annotated");
                 annoImp = IJ.openImage(od.getPath());
                
            }
            
            IJ.showStatus("ROI annotation starting.");
        
            //pack roi path strings
            String[] roiPaths = new String[2];
            roiPaths[0] = posRoiPath;
            roiPaths[1] = negRoiPath;
            
			//construct
            IJ.showStatus("Building data...test");
			anno = new ThreeDROISynapseDriver(imp, roiPaths, depth, width, height, chainPath);		
			IJ.log("sucessful data build");
			
			
			//train test model
            IJ.showStatus("Training and testing...");
            IJ.log("Training and testing...");
            float rate = anno.trainAndTest();	
            IJ.log("recognition rate: " + rate);
            
            
            //annotate
            IJ.showStatus("Annotating...");
            IJ.log("Annotating...");
			if(ratsOption) threshold = -1; //set to ratsmode
			anno.annotate(annoImp, savePath, saveOption, threshold, noise, lambda, minLeaf);

            IJ.log("Finished!");
            
            //print centers to log
            centers = anno.getDetectedCenters();
            IJ.log("\nDetected Centers: " + centers.size());
			IJ.log("x, y, z");//.marker csv format
		    for(Point3D p : centers)
			{
				IJ.log(p.x + " " + p.y + " " + p.z); //echo to console
			}
          
            
		}
		catch(Exception e) {
			e.printStackTrace();
            IJ.log(e.toString());
        }
	}//end annotate
	
	//timing functions adapted from RATSForAxon 
    private long tick(){
		this.startTime = System.currentTimeMillis();
		return startTime;
	}
    private long tock(){
	    return (System.currentTimeMillis() - this.startTime);
	} 
    private long tock(String message){
		long elapsedTime = tock();
		IJ.log(message + " " + elapsedTime + " ms"); 
		return elapsedTime;
    }  
	
	private String produceTimeStamp( long millis ) {
		long second = (millis / 1000) % 60;
		long minute = (millis / (1000 * 60)) % 60;
		long hour = (millis / (1000 * 60 * 60)) % 24;

		String time = String.format("%02d:%02d:%02d:%d", hour, minute, second, (millis/1000)) ;
		return time;
	}
    
    //crude inner class listener for custom buttons
    private class MyListener implements ActionListener {
      public void actionPerformed(ActionEvent e) {
      
        Object source = e.getSource();
        String s;
        String path ="default";
        OpenDialog od;
        DirectoryChooser dc;
        int num;
        
        if(source.equals(posRoiB)) {
            //IJ.log("posRoiB button press");
            od = new OpenDialog("select positive ROI file", "");
            path = od.getPath();
            if( path.equals(null)) return;
            posRoiField.setText(path);
        } else if(source.equals(negRoiB)) {
            //IJ.log("negRoiB button press");
            od = new OpenDialog("select negative ROI file", "");
            path = od.getPath();
            if( path.equals(null)) return;
            negRoiField.setText(path);
        } else if(source.equals(chainB)) {
            od = new OpenDialog("select BIOCAT chain file", "");
            path = od.getPath();
            if( path.equals(null)) return;
            chainField.setText(path);
            
        } else if(source.equals(saveB)) {
            dc = new DirectoryChooser("select directory to save files to");
            path = dc.getDirectory();
            if( path.equals(null)) return;
            saveLocField.setText(path);
            
        } else if(source.equals(thresholdB)) {         
            num = (int) AnnotatorUtility.calcThreshold(WindowManager.getCurrentImage(), 1);
            thresholdField.setText(Integer.toString(num));
        }
        
        
      }
    }//end inner class MyListener
  
 }//endclass ThreeDRoiAnnotator_