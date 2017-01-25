/* Results_Overlay.java - Jon Sanders 11/5/15
 * A simple ImageJ plugin for viewing the results of Three_D_ROI_Annotator_Plugin 
 * .IJ output files in ImageJ. 
 *
 *	INPUT: - The current selected 8-bit image.
 *		   - A results_XXXX.ij file matching the image.
 *
 *  OUTPUT:- A new image with the points from the .ij file drawn on in pure white 1px points
 */
 
 
 /* NOTES: 
  *		- currently no error checking for image size. This is ok, but behavior is probably
  *			degenerate if a mismatched imageJ file and image are used together. 
  * TODO:
  * 	- possible convert to 3D overlay, instead of destructively drawing on ImageJ (not likely for now)
  *		- possible convert to color and draw in color instead (or adjust LUT for ease)
  *     - add option to remove center dot
  */


import ij.*;
import ij.io.*;
import ij.IJ;
import ij.process.*;
import ij.gui.*;
import ij.plugin.*;
import ij.plugin.filter.PlugInFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.Button;
import java.awt.Color;
import java.util.*;
import java.io.*;

public class Results_Overlay implements PlugInFilter {
	//variables
	ImagePlus imp;
	ImagePlus outImp;
	String resPath;
	int radius;
	boolean zVisOpt;
	
    //gui elements
    GenericDialog gd;
    Panel resChooser;
    Button resB;
    TextField resField;
	
	
	//IJ API setup function
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		radius = 3;
		return DOES_ALL;
}

	//run()
	// "main" for plugins, called by selecting the plugin
	// runs the plugin on the current image after displaying a dialog
	// to get the parameters from the user.
    public void run(ImageProcessor ip) {

        //setup gui
        if (! makeGUI()) return;
        //store data from gui fields
        if (! getDataFromFields()) {
            IJ.log("Missing or incorrect field data.");
            return;
        } 
		
		//copy image
		Duplicator d = new Duplicator();
		ImagePlus copy = new ImagePlus();
		copy = d.run(imp);
		copy.show();

		
		//read file!
		IJ.log("**reading line 1 **");
		//open file stream
		try {
			File resFile = new File(resPath);
			FileInputStream fstream = new FileInputStream(resFile);
			BufferedReader in = new BufferedReader(new InputStreamReader(fstream));


			String line;
			int count = 1;
			int x,y,z;
			
			ImageProcessor copyIp;
			while((line = in.readLine()) != null) {

					//discard comments and blank lines 
					if(line.contains(""+"#") || line.length()<=1) {
						line = in.readLine();//ignore comments and space
					} else { //read and draw point
						
						
						
						String[] t = line.split(" ");
						
						if(t.length != 3) {
							IJ.log( "line: "+ count + ": " + line + " broken."); 
						}
						
						x=Integer.parseInt(t[0].trim());
						y=Integer.parseInt(t[1].trim());
						z=Integer.parseInt(t[2].trim());

						
						
						copyIp = copy.getStack().getProcessor(z);
						
						//draw the oval if needed on main slice
						if(copyIp.getNChannels() > 1) {
							copyIp.setColor(Color.white); 
						} else { 
							copyIp.setColor(255); 
						}
						
						if(radius > 0) {
							copyIp.drawOval((x-radius),(y-radius),radius*2,radius*2);
						}
						
						//draw center dot
						if(copyIp.getNChannels() > 1) {
							copyIp.setColor(Color.white); 
						} else { 
							copyIp.setColor(200); 
						}
						copyIp.drawDot(x,y);
						
						
						//Z direction visualization
						if(zVisOpt){
							try{
								copyIp = copy.getStack().getProcessor(z-1); //slice below
								//draw circle
								if(copyIp.getNChannels() > 1) {
									copyIp.setColor(Color.gray); 
								} else { 
									copyIp.setColor(170); 
								}
								copyIp.drawOval((x-(radius/2)),(y-(radius/2)),radius,radius);
							} catch (Exception e) {
								continue;
							} try {
								copyIp = copy.getStack().getProcessor(z+1); //slice above
								//draw circle
								if(copyIp.getNChannels() > 1) {
									copyIp.setColor(Color.gray); 
								} else { 
									copyIp.setColor(170); 
								}
								copyIp.drawOval((x-(radius/2)),(y-(radius/2)),radius,radius);
							} catch (Exception e) {
								continue;
							}
						}

						count++;
					}
					
			}

			IJ.log("parsed " + count + " markers from file.");
			
			fstream.close();		

		} catch (Exception e) {
			IJ.log(e.getMessage());
		}
		
    }

	
	//makeGUI()
	// set up the simple dialog to get user parameters
	private boolean makeGUI() {

        
        //setup gui main panel
        gd =  new GenericDialog("3D ROI Annotator - Simple Results Visualizer");
       
        MyListener listener = new MyListener(); 
        
        
        //browser for resB file
        resChooser = new Panel();
            resChooser.setLayout(new FlowLayout(FlowLayout.CENTER,5,0));       
        resB = new Button("Result File");
            resB.addActionListener(listener);
        resField = new TextField(Prefs.get("batch.resB",""), 40);      
        resChooser.add(resB);
        resChooser.add(resField);
		
        //put it all in the dialog
        //gd.addMessage("Results file:");
		gd.addMessage("");
        gd.addPanel(resChooser);		
		
        //gd.addMessage("Draw Radius: (0 for dot)");
        gd.addSlider("Raidus (0 for dot)", 0, 20, 0);		
		
		gd.addMessage("\nExtra Options:");
		gd.addCheckbox("extra Z visual", false);
		//show it after completion
        gd.showDialog();
        
        if(gd.wasCanceled())
            return false;
    
        return true;
	}
	
	//getDataFromFields()
	// extract all of the user input from the dialog
	private boolean getDataFromFields() {
		
		resPath = resField.getText();
		radius = (int) gd.getNextNumber();
		zVisOpt = gd.getNextBoolean();
		
		if( resPath.length()==0) {
			return false;
		}
		
		
		return true;
	}
	
	
	//listener class
	private class MyListener implements ActionListener {
      public void actionPerformed(ActionEvent e) {
      
        Object source = e.getSource();
        String s;
        String path ="default";
        OpenDialog od;
        DirectoryChooser dc;
        int num;
        
        if(source.equals(resB)) {
            //IJ.log("resB button press");
            od = new OpenDialog("select positive ROI file", "");
            path = od.getPath();
            if( path.equals(null)) return;
            resField.setText(path);      
        
		}
	  }
    }//end inner class MyListener
	
}//endPlugin
