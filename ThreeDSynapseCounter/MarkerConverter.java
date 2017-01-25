/* MarkerConverter.java
*       a program to convert between IJ marker format and Vaa3D marker format
        and from .xls IJ results to IJ marker format.
*       ARGUMENTS: a marker file of either IJ (including Tester.java results), Vaa3d, or results.xls type and image height.
*               call from command line as: java MarkerConverter [file] [image height]
*
*       additionally,
*               convert([filename], [height]) //will open file and write out results to new file
*               v3dToIJ([file line], [height])              //returns converted string as IJ
*               IJTov3d([file line], [height], [marker ID]) //returns converted string as V3D
                xlsToIJ([file line])                        //returnd converted string as IJ
*       can be called as utilities.
*               
*/


import java.io.*;
import java.util.ArrayList;
import java.lang.Math.*;


//main function to oversee the file conversion and display usage
public class MarkerConverter {

        //file type constants
        static int IJ = 0;
        static int V3D = 1;
        static int XLS = 2;
    
        //do the conversion. requires image height to work.
        public static void convert(String f, int h) throws IOException, Exception{
				ArrayList<String> outputArray =  new ArrayList<String>();

                //open file stream
                FileInputStream fstream = new FileInputStream(f);
                BufferedReader in = new BufferedReader(new InputStreamReader(fstream));

                //check file type
                String line;
                line = in.readLine();
                while(line.contains(""+"#") || line.length()<=1 || line.startsWith(" ") ){ //scrub header comments and blanks
                                if ( line.startsWith(" ") ) { //.xls comment
                                    line = "#x   y   z";
                                }
                                outputArray.add(line);//preserve blanks and comments
                                //System.out.println(line);//debug echo to console
                                line = in.readLine();
                        }
                        
                int fileType; // 0 = IJ, 1 = V3D, 2 = XLS
                fileType = getFileType(line); //only check after comments
                
                //alert user to file type and conversion being made
                System.out.println();
                if(fileType == V3D) {
                        System.out.println("File is v3d type. Converting to IJ type.");
                        
                } else if(fileType == IJ) {
                        System.out.println("File is IJ type. Converting to v3d type.");
                } else if(fileType == XLS) {
                        System.out.println("File is XLS type. Converting to IJ type.");
                }
        
                //read and write correct conversion
                String outputLine = "";
                int ID = 0;
                while(true) {
                        //System.out.println(line);//debug
                        
                        //discard comments and blank lines 
                        if(line.contains(""+"#") || line.length()<=1) {
                                outputLine = line;//preserve blanks and comments
                        } 
                        //use correct converter
                        else if(fileType == V3D) {//for v3d->IJ
                                outputLine = v3dToIJ(line, h);
                        } else if(fileType == IJ) {//for IJ->v3D
                                ID++;
                                outputLine = IJTov3d(line, h, ID);
                        } else if(fileType == XLS) {
                                outputLine = xlsToIJ(line);
                        }
                        
                        //System.out.println(outputLine);//debug
                        
                        //store outputs to write after all are finished
                        outputArray.add(outputLine);
                        
                        
                        //bottom drive for loop
                        if((line = in.readLine()) == null) break;
                }

                //set up new file
                String fileName = createName( fileType, f);
                File file = new File(fileName);
                BufferedWriter out = new BufferedWriter(new FileWriter(file));
                
                //actually write to file
                int counter = 0;
                for( String s:outputArray) {
                        out.write(s);
                        out.write(System.getProperty("line.separator"));
                        System.out.println(s);//echo to console
                        counter++;
                }
                System.out.println(System.getProperty("line.separator")+"Written to file: " + fileName);
                System.out.println(counter + " lines written.");
                
                //close streams
                in.close();
                out.close();
                
        }//end convert
        
        //convert a string from 'results' xls output to IJ format
        static public String xlsToIJ(String l){
            int x,y,z;
            double xCent, yCent, zCent;
            
            String[] t = l.split("\t");

            xCent = Double.parseDouble(t[18].trim());
            yCent = Double.parseDouble(t[19].trim());
            zCent = Double.parseDouble(t[20].trim());
            x = (int) Math.round(xCent);
            y = (int) Math.round(yCent);
            z = (int) Math.round(zCent);
            
            String newLine = ("" + x + " " + y + " " + (z+1));
            return newLine;
            
        }
        
        // convert a string from v3d fromat to IJ format
        static public String v3dToIJ(String l, int h) throws Exception{
                String[] t = l.split(",");
                
                //look for invalid height
                int tempH = h - Integer.parseInt(t[1].trim());
                if( tempH < 0) {
                        throw new Exception("Invalid height: " + tempH);
                }
                
                //v3d format: "x,y,z,radius,shape,name,comment,color_r,color_g,color_b"
                //IJ  format: "x y z"  IJWidth+1 
                //                    v3dWidth-1                              v3dHeight inverted                       v3dDepth-1
                String newLine = (""+(Integer.parseInt(t[0].trim()) - 1)+" "+(tempH)+" " +(Integer.parseInt(t[2].trim()) - 1));
                
                return newLine;
        }//end

        
        //convert a string from IJ format to v3d format, properly naming the markers and filling in default v3d params
        static public String IJTov3d(String l, int h, int ID) throws Exception{
                int r=255,b=50,g=25;
				String[] t = l.split(" ");
                
                int tempH = h - Integer.parseInt(t[1].trim());
                if( tempH < 0) {
                        throw new Exception("Invalid height: " + tempH);
                }
                
				//set color for Results files from Synapse Distribution Tester output.
				if(t.length > 3) {
					int binNum = Integer.parseInt(t[3].trim());
					switch (binNum) {
						/*red/blue gradient
						case 1: r=229; g=0; b=30; //red
							break;
						case 2: r=221; g=0; b=121; //red-pink
							break;
						case 3: r=213; g=0; b=205; //pink
							break;
						case 4: r=128; g=0; b=206; //purple
							break;
						case 5: r=40; g=0; b=198; //lavender
							break;
						case 6: r=0; g=39; b=191; //blue
							break;
						default: r=255; g=255; b=255; //white
							break;
						*/
						
						/* red/blue HSV gradient
						case 1: r=229; g=0; b=30; //red
							break;
						case 2: r=221; g=144; b=0; //orange
							break;
						case 3: r=120; g=213; b=0; //lime
							break;
						case 4: r=0; g=206; b=45; //green
							break;
						case 5: r=0; g=197; b=198; //teal
							break;
						case 6: r=0; g=39; b=191; //blue
							break;
						default: r=255; g=255; b=255; //white
							break;
						*/
						
						//red/blue HSV high saturation
						case 1: r=229; g=0; b=30; //red
							break;
						case 2: r=241; g=114; b=0; //orange
							break;
						case 3: r=140; g=233; b=0; //lime
							break;
						case 4: r=0; g=226; b=65; //green
							break;
						case 5: r=0; g=185; b=218; //teal
							break;
						case 6: r=0; g=59; b=211; //blue
							break;
						default: r=255; g=255; b=255; //white
							break;
					}
					
				}
				
                //IJ format: "x y z"  IJWidth+1   
                //v3d format: "x,y,z,radius,shape,name,comment,color_r,color_g,color_b"
                //                                        IJHeight inverted                    IJDepth+1                               v3d additional params
                String newLine = (""+(Integer.parseInt(t[0].trim()) + 1)+", "+(tempH)+", "+(Integer.parseInt(t[2].trim()) + 1)+", "+"0,1,detected center "+ID+",0,"+r+","+g+","+b);
                
                
                return newLine;
        }//end
        
        //read first line, if string split(",") == v3d format (10 tokens), return true.
        //if == IJ format (1 token), return false
        static public int getFileType(String line)  throws Exception{
                String[] tokens = line.split("\t"); //TAB
                
                System.out.println( "tokens split by tab: " + tokens.length);
                
                //for object counter output
                if(tokens.length > 1) {
                 return XLS;
                }
                
                else {
                    tokens = line.split(",");

                    //for vaa3d 
                    if(tokens.length > 1) {
                     return V3D;
                    }
                    //for IJ 
                    else if(tokens.length == 1) {
                     return IJ;
                    }
                } 
                
                //if no match
                throw new Exception("file does not match possible type.");
        
        }//end
        
        
        // create a new file name based on the old one.
        static private String createName(int fileType, String f) {
                File tempFile = new File(f);
                String origFileName = tempFile.getName();
                
                String[] filePrefix = origFileName.split("\\.");
                
                String fileName = "";
                if((fileType == 1) || (fileType == 2) ) {
                        //fileName = filePrefix[0] + "_ConvertTo_IJ.marker";
                        fileName = "Converted_to_IJ_"+filePrefix[0]+".marker";
                } else if(fileType == 0) {
                        //fileName = filePrefix[0] + "_ConvertTo_v3d.marker";
                        fileName = "Converted_to_v3d_"+filePrefix[0]+".marker";
                } else {
                        fileName = "impossible_to_reach.file";
                }
                
                return fileName;
        }//end
        
        
        //MAIN function to run from command line.
        // simply calls convert() on the command line args
        public static void main(String[] args) {
        
        if(args.length != 2) {
                System.out.println("USAGE: java MarkerConverter [file] [image height]");
                System.out.println("makes the following conversions:");
                System.out.println("    IJ.marker   -> V3D.marker");
                System.out.println("    V3D.marker  -> IJ.marker");
                System.out.println("    results.xls -> IJ.marker");
                return;
        }
        
        try{
                convert(args[0], Integer.parseInt(args[1]));
        }catch(Exception e) {
                e.printStackTrace();
        }
        
        
        return;
        
        
        }//endmain
}//endclass