    /-------------------------------------------------------------------\
   /*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*\
 //                3D SYNAPSE QUANTIFIER                                  \\
||                             JONATHAN SANDERS                            ||
 \\                                       NIU ILAAL 2016                   //
  \-----------------------------------------------------------------------/
   \*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*/

   This folder is for materials related to Three Dimensional Synapse Quantification
	
  *NOTE: The 3D synapse quantifier is an ImageJ plugin called Three_D_ROI_Annotator_Plugin. 
  
   If you are a developer, please check “readme_long.txt” 
  
  
   ----------------------------------------------------------------------------------------------------------
   ---- HOW TO USE Three_D_ROI_Annotator_Plugin -------------------------------------------------------------
   ----------------------------------------------------------------------------------------------------------

	1) Create a subfolder for an ImageJ plugin.

	2) Place these files and jars in the folder:

		AnnotatorUtility.java 			(contains maxima detection and other utils)
		Point3D.java				(helper class for storing voxel locations)
		Three_D_ROI_Annotator_Plugin.java	(IJ dependent GUI for annotator tool)
		ThreeDSynapseDriver.java		(main annotator tool and logic. can be command line)
		RatsSliceProcessor.java			(wrapper for RATSForAxon for 3D slice processing)
		RATSForAxon.java			(main RATS code for 2D Image handling)
		RATSQuadTree.java			(RATS dependency)


		biocat.jar				(BioCAT resources, chain reading, extraction/classification)
		imagescience.jar			(FeatureJ resources, extractors)
		libsvm.jar				(SVM resources, classifier)
		weka.jar				(Weka resources, classifiers)

	3) Load a gray scale image into ImageJ.
		NOTE: splitting image

	4) Click Plugins -> compile and run -> (subfolder for plugin) -> Three_D_ROI_Annotator_Plugin.java
		*NOTE: if the ImageJ compiler is acting up, you can compile manually from the ImageJ plugins subfolder.
		*This should just be a simple 'javac Three_D_Annotator_Plugin.java' to complete.
		*If your version of JAVA is older than 1.8, it will likely fail due to biocat.jar being 1.8.

	5) Specify locations for the required files using the text fields or browsers.
		The critical required files are:
			-The image (already supplied by running the plugin on it)
			-The Positive ROIs in IJ .zip file format (created by ROI manager).
			-The Negative ROIs in IJ .zip file format.
			-The BIOCAT chain file to use.
	
	6) Set desired ROI dimensions. 9x9x3 is generally sufficient for synapse detection. 

	7a)Set the desired threshold. leave slider at 0 to auto threshold.

	7b)Otherwise, check the RATS option and supply rats parameters to use adaptive thresholding.
		Noise level = estimated threshold level of background (low for mostly black images)
		lambda      = scaling factor 
		min leaf    = minimum quad tree elaf size (dynamically determined by plugin)

	8) Select desired save options and destination folder.
		Check any desired file types.
			IJ is space separated X Y Z
			v3d is CSV            x,y,z,radius,shape,name,comment,red,green,blue    

	9) If training is to be done on one image, and testing on another, check "different annotation image" box. 
		After clicking OK, a file browser will open to select image.

	10) Click "OK" to run the annotator.
		The results files will be written to the selected directory with the chosen name + a timestamp,
		and the RATS mask for the image will be displayed. to be safe, it is probably a good idea 
		to save a copy of the mask as well!
			

	11) Note that the output of step 10 when viewed in Vaa3D is marker clusters and not single points.
		We employ the Object_Counter3D plugin to get the centers. This involves some work to get the data in the correct form.
		
		A) Create a new image in ImageJ that is the exact same size as the original.
			set this image to 8-bin and fill with black.
		
		B) Compile and run the Results_Overlay.java ImageJ Plugin.
			This can be in any plugins subfolder, I tend to keep all of my synapse detection plugins together.
			Select the ij format results file from step 10.
		    Leave the visual option unchecked and leave radius set to 0.
			This will in effect draw the results as a binary image that can be processed by Object_Counter.
		
		C) Compile and run Object_Counter_3D with all default settings on the created image.
			
		D) Save the .xls file produced by the Object_Couneter plugin.
		    The output of the object counter plugin is a tab-separated ".xls" file containing a variety of statistics.
			
		E) We only want the centroids for our marker locations.
			compile and run MarkerConverter.java on the command line, passing it the .xls file from step D.
			This will extract the centroid X Y Z locations and save them to an IJ marker file automatically.
			
		
			
		*TIP: execute IJ.jar from the command line to add more than IJ supported ram as well as see biocat output 
				for diagnosing chain behavior. 'java -Xms4g -jar IJ.jar'	
				
				
