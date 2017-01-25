//a simple utility to scale the z direction of marker and .swc files by a z factor

import java.io.*;
import java.util.ArrayList;
import java.lang.Math.*;

public class XYZscale_util{
	
	//file type constants
	static final int SWC = 0;
	static final int MARKER = 1;	
	static final int ERROR = -999;
	
	static final int DEC_PRECISION = 4; //float decimal places to use
	
	
	//MAIN********************************************
	public static void main(String[] args) {
		
		//vars
		ArrayList<String> outputArray =  new ArrayList<String>();
		int fileType;
		float scaleX, scaleY, scaleZ;
		FileInputStream fstream;
		BufferedReader in;
		BufferedWriter out;
		File outputFile;
		String line, outputLine;
		
		
		//usage 
		if(args.length < 4) {
			System.out.println("\nCORRECT USAGE:\nJava Zscale_util [file] [x scale] [y scale] [z scale]");		
			return;
		} else if((!getExtenstion(args[0]).equals("swc")) && (!getExtenstion(args[0]).equals("marker"))) {
			System.out.println("Incorrect file type. supports v3d.marker or .swc only");
			return;
		}
		
		
		//set file flag
		if(getExtenstion(args[0]).equals("swc")) fileType = SWC;
		else if(getExtenstion(args[0]).equals("marker")) fileType = MARKER;
		else fileType = ERROR;
		
		
		//set scales
		scaleX = Float.parseFloat(args[1]);
		if(scaleX <= 0 ) {
			System.out.println("scale X is not valid!");
			return;
		}
		scaleY = Float.parseFloat(args[2]);
		if(scaleX <= 0 ) {
			System.out.println("scale Y is not valid!");
			return;
		}
		scaleZ = Float.parseFloat(args[3]);
		if(scaleZ <= 0 ) {
			System.out.println("scale Z is not valid!");
			return;
		}
		
		//MAIN LOGIC ************************************************************     
		try{
			//open file stream
			fstream = new FileInputStream(args[0]);
			in = new BufferedReader(new InputStreamReader(fstream));

		
		//READ FILE 
			
			line = in.readLine();
			
			//scrub header comments and blanks
			while(line.contains(""+"#") || line.length()<=1 || line.startsWith(" ") ){ 
				outputArray.add(line);//preserve blanks and comments
				line = in.readLine();
			} 
			
			//add comment for scaling
			outputArray.add("#scaled by X: " + scaleX + " Y: " + scaleY + " Z: " + scaleZ);
			
			
			//process line by line
			while((line = in.readLine()) != null) {
				if(fileType == SWC) {
					//System.out.println("line is SWC");
					outputLine = scaleSWC(line, scaleX, scaleY, scaleZ);
				} else if(fileType == MARKER) {
					//System.out.println("line is MARKER");
					outputLine = scaleMARKER(line, scaleX, scaleY, scaleZ);
				} else {
					System.out.println("line is ERROR");
					outputLine = line;
				}
				System.out.println(line);
				System.out.println(outputLine);				
			    outputArray.add(outputLine);
				
			}
		


			//set up new file
			String fileName = createName(args[0]);
			outputFile = new File(fileName);
			out = new BufferedWriter(new FileWriter(outputFile));
			
			//actually write to file
			int counter = 0;
			for( String s:outputArray) {
					out.write(s);
					out.write(System.getProperty("line.separator"));
					//System.out.println(s);//echo to console
					counter++;
			}
			System.out.println(System.getProperty("line.separator")+"Written to file: " + fileName);
			System.out.println(counter + " lines written.");
			
			//close streams
			in.close();
			out.close();
		
		} catch (Exception fe) {
			System.out.println("error reading file");
			fe.printStackTrace();
		}	
		
		
		
	} //endmain
	
	
	//**************************************************
	// SCALING METHODS	
	static private String scaleSWC(String l, float sx, float sy, float sz) {
		String scaled = "";
		float x,y,z;
		
		//format: n,type,x,y,z,radius,parent
		String[] t = l.split(" ");
		x = Float.parseFloat(t[2]);
		y = Float.parseFloat(t[3]);
		z = Float.parseFloat(t[4]);
		x*=sx;
		y*=sy;
		z*=sz;
		t[2]= ""+String.format("%."+DEC_PRECISION+"f", x);
		t[3]= ""+String.format("%."+DEC_PRECISION+"f", y);
		t[4]= ""+String.format("%."+DEC_PRECISION+"f", z);
		
		scaled += t[0];
		for(int i = 1; i < t.length; i++) {
			scaled += " " + t[i];
		}
		
		return scaled;
		
	}

	static private String scaleMARKER(String l,float sx, float sy, float sz) {
		String scaled = "";
		float x,y,z;
		
		//format: x, y, z, radius, shape, name, comment,r,g,b
		String[] t = l.split(",");
		
		x = Float.parseFloat(t[0]);
		y = Float.parseFloat(t[1]);
		z = Float.parseFloat(t[2]);
		x*=sx;
		y*=sy;
		z*=sz;
		t[0]= " "+String.format("%."+DEC_PRECISION+"f", x);
		t[1]= " "+String.format("%."+DEC_PRECISION+"f", y);
		t[2]= " "+String.format("%."+DEC_PRECISION+"f", z);
		
		scaled += t[0];
		for(int i = 1; i < t.length; i++) {
			scaled += "," + t[i];
		}
		
		return scaled;
		
	}
	
	
	// create a new filename based on original
	static private String createName(String f) {
		File tempFile = new File(f);
		String origFileName = tempFile.getName();
		String fileName = "";
		fileName += "Scaled_"+origFileName;
		
		return fileName;
	}
	
	// get the file extension of a string
	static private String getExtenstion(String f) {
		String extension = "";

		int i = f.lastIndexOf('.');
		int p = Math.max(f.lastIndexOf('/'), f.lastIndexOf('\\'));

		if (i > p) {
			extension = f.substring(i+1);
		}
		
		return extension;
	}
	
	
} //endclass