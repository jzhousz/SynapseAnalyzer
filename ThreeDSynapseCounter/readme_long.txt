    /-------------------------------------------------------------------\
   /*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*\
 //                3D SYNAPSE DETECTION                                    \\
||                             JONATHAN SANDERS                             ||
 \\                                       NIU ILAAL 2016                   //
  \-----------------------------------------------------------------------/
   \*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*_*/

   This folder is for materials related to Three Dimensional Synapse Quantification
including:
	-annotator code
	-imageJ plugin elements
	-test data sets
	
  *NOTE: The 3D synapse detector is an ImageJ plugin. If you are an user instead of a developer, please jump to Section 3 in CONTENTS -
 "HOW TO USE Three_D_ROI_Annotator_Plugin"

  *NOTE: This location overrides the old folder "3DSynapse_Tracing"  or any duplicate file 
  in "SynapseCounter_topomap/Axon Topo Map"

  
   ----------------------------------------------------------------------------------------------------------
   ---- CONTENTS --------------------------------------------------------------------------------------------
   ----------------------------------------------------------------------------------------------------------  
  
	1    -    File listing and explanation
	
	2    -    Preprocessing Images for use in detection
	
	3    -    HOW TO USE Three_D_ROI_Annotator_Plugin
	
	4    -    Validating Output From 3D Annotation

(1)----------------------------------------------------------------------------------------------------------
   ---- WHAT FILES ARE IN THIS FOLDER -----------------------------------------------------------------------
   ----------------------------------------------------------------------------------------------------------
      It is the home of the following:
	
	
	(1). ThreeDSynapseDriver.java
	
		The class "ThreeDSynapseDriver", which takes images and outputs marker files. 
		It is the core synapse detection code called by an IJ plugin GUI.



	(2). ThreeD_ROI_Annotator_Plugin.java
		
		The ImageJ plugin GUI "Three_D_ROI_Annotator_Plugin": This is the GUI wrapper that 
		calls the 3DROI Annotator (takes images, chain files .. and output marker file).
		This code requires:
			-ThreeDSynapseDriver.java
			-AnnotatorUtility.java
			-Point3D.java
			-biocat.jar
			-libsvm.jar
			-weka.jar
			-imagescience.jar
	   	to be in the same subfolder in the ImageJ plugins folder.
		libsvm, weka, and imagescience jars are biocat's machine learning dependencies.
	

	(3). Object_Counter3D_2Channel.java 
		
		The ImageJ plugin for 3D connected component analysis and splitting  
		It is the one that outputs synapse count and other statistics. 
		This code is also used to do Centrioid detection for the final step of marker detection.
		
		A) Object_Counter3D.java, the original plugin, is included as well for other uses.
		B) Object_COunter3D_Rats.java is a variant that uses RATS thresholding.
		c) Object_COunter3D_Rats_splitting.java is another variant that does both RATS and splitting of clusters
	

	(4). Synapse_Validator_v2.java
		
		The validator for the Three_D_ROI_Annotator_Plugin. 
		It runs on the command line and requires a specific folder structure. 
		This is detailed in its own readme, readme_validator.txt"
		The V2 version should be used, it fixes many errors in calculation and provides more detailed output.
	

	(5). RATS_Pseudo3D.java 
	
		The pseudo-3D RATS slice by slice processor. This wrapper runs as an ImageJ plugin, 
		and can be invoked in other programs to handle RATS thresholding on a 3D image volume. 
		It DOES NOT do the RATS algorithm in full 3D, it is slice by slice 2D. 
		For laser confocal images, this is generally acceptable due to the isotropic Z-direction resolution.

		This code wraps the RATS core classes:
			-RATSForAxon,java
			-RATSQuadtree.java

		and the pseudo3D processing class:
			-RatsSliceProcessor.java


	(6). Results_Overlay.java 
	
		The simple visualizer plugin for ImageJ. This allows quick drawing of 
		results files (results_XXXX.ij) in ImageJ. Currently this is used as a kludge for creating binary 
		images of the results from marker detection so that centriod detection can be 
		done in Object_Counter3D_2channel.java.


	(7). MarkerConverter.java
        
		A program to convert between IJ marker format and Vaa3D marker formats
        and from .xls ObjectCounter3D results to IJ marker format.
        ARGUMENTS: a marker file of either IJ (including Tester.java results), v3d, or results.xls type and image height.
                call from command line as: java MarkerConverter [file] [image height]


	(8). Colocal_Filter.java 
	
		An ImageJ plugin filter for colocalizing marker data.  
		This will compare a results file (IJ format) to an image and output only the markers that are 
		proximal to the mask of the image based on the plugin options.
		
		this is used for presynapse colocalization.
	

	(9). Zscale_util.java
		
		A small utility to modify the scale of v3d.marker and .swc files.
		USAGE: Java Zscale_util [file] [x scale] [y scale] [z scale]
		
	(10). Other related datasets
		-ROIS
		-Chains
		-Results







(2)----------------------------------------------------------------------------------------------------------
   ---- Preprocessing Images for use in detection -----------------------------------------------------------
   ----------------------------------------------------------------------------------------------------------

    Preprocessing can greatly increase detection and eliminate false positives.
	While the exact parameters for preprocessing should be experimented with these were used to process the Kibra image set.
	This is an example of a possible pathway for detecting synapses in the Kibra data set that involves several preprocessing steps.
	1) separate images by channel

	2) Subtract Moprhology from Post and save each channel
		This reduces intracellular noise in the image
		a) split image into  color channels
		b) Process -> Image Calculator -> subtract moprhology from the post-synaspe channel.
		c) recombine the channels, substituting the subtraction result for the post-synapse channel.
		d) save image

	3) process subtracted post synapse 
		a) bg subtract rolling ball radius 5
		b) run ThreeD_ROI_Annotator plugin using RATS 3-3-204, exact center and edge ROIS, HAAR+SVM 
		on 004 image, annotate another series image using trained model.
		c) save results file	

	2) process presynapse channel
		a) background subtract radius 5
		b) despeckle
		c) contrast enhance
		d) despeckle
		e) 3d watershed 27 way with morpholib ImageJ plugin package (http://imagej.net/Morphological_Segmentation)
		f) save mask image for use with colocal filter



(3)----------------------------------------------------------------------------------------------------------
   ---- HOW TO USE Three_D_ROI_Annotator_Plugin -------------------------------------------------------------
   ----------------------------------------------------------------------------------------------------------

	1) Create a subfolder for an ImageJ plugin.

	2) Place these files and jars in the folder:

		AnnotatorUtility.java 			(contains maxima detection and other utils)
		Point3D.java				(helper class for storing voxel locations)
		Three_D_ROI_Annotator_Plugin.java	(IJ dependant GUI for annotator tool)
		ThreeDSynapseDriver.java		(main annotator tool and logic. can be command line)
		RatsSliceProcessor.java			(wrapper for RATSForAxon for 3D slice processing)
		RATSForAxon.java			(main RATS code for 2D Image handling)
		RATSQuadTree.java			(RATS dependancy)


		biocat.jar				(BioCAT resources, chain reading, extraction/classification)
		imagescience.jar			(FeatureJ resources, extractors)
		libsvm.jar				(SVM resources, classifier)
		weka.jar				(weka resources, classifiers)

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
	
	6) Set desired ROI dimensions. 9x9x3 is generally suficient for synapse detection. 

	7a)Set the desired threshold. leave slider at 0 to auto threshold.

	7b)Otherwise, check the RATS option and supply rats parameters to use adaptive thresholding.
		Noise level = estimated threshold level of bachground (low for mostly black images)
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
			

	11) Notice that the output of step 10 when viewed in Vaa3D is actually marker clusters and not single points.
		The output of the first step is currently BEFORE center detection, as there were issues with center calculation 
		in the detector code. instead, Object_Counter3D is used to do center detetcion. 
		This involves a bit of work to get the data in the correct form.
		
		A) Create a new image in ImageJ that is the exact same size as the original.
			set this image to 8-bin and fill with black.
		
		B) Compile and run the Results_Overlay.java ImageJ Plugin.
			This can be in any plugins subfolder, I tend to keep all of my synapse detection plugins together.
			Select the ij format results file from step 10.
		    Leave the visual option unchecked and leave radius set to 0.
			This will in effect draw the results as a binary image that can be processed by Object_Couneter.
		
		C) Compile and run Object_Counter_3D with all default settings on the created image.
			This will default to thresholding the image at 127 intensity which is fine for this purpose, 
			and will perform a connected component analysis on the marker clusters.
			
		D) Save the .xls file produced by the Object_Couneter plugin.
		    The output of the object counter plugin is a tab separated ".xls" file containing a variety of statistics.
			
		E) We only want the cetroids for our marker locations.
			compile and run MarkerConverter.java on the command line, passing it the .xls file from step D.
			This will extract the centroid X Y Z locations and save them to an IJ marker file automatically.
			
		
			
		*TIP: execute IJ.jar from the command line to add more than IJ supported ram as well as see biocat output 
				for diagnosing chain behavior. 'java -Xms4g -jar IJ.jar'	
				
				
(4)----------------------------------------------------------------------------------------------------------
   ---- Validating Output From 3D Annotation ----------------------------------------------------------------
   ----------------------------------------------------------------------------------------------------------

    1) Using SynapseValidator_V2.java
	
	- SynapseValidator_V2 can comapare manually annotated regions of an image to the output of 3D marker Detection. 
	
	- It requires a very specific file structure and naming convenrion, but once configured 
	will perform an automatic analysis of the results and provide a report of the Precision, Recall, Fraction, 
	and F-Measure for each test region and overall. 
	
	- It will also output several files filled with markers for use in visually inspecting the results of the validation.
   
		A) Configuring
			
			requires a file structure:

			[root file containing the word "truth" somewhere in it]
				|-(optional) [bin folder for validator code]
				|-Region 01
					|-[cropped region image] name format: [name](region #)_(topleft X)_(topleft y)_(start z)_(width)_(height)_(depth)
					|-[marker file of manual markers] in IJ format
				|-Region 02
				...
				|-Region [N]
		

			The file structure is requried to operate, the validator 'walks' the files looking for keywords to process the regions.
			Any number of regions are supported, as long as they are numbered and contain the correct files.
			you may have other folders and files in the directory, as long as they dont contain the word "region" in the name.
		
		B) Running
		
			java SynapseValidator_v2 [ground truth dir path] [test marker file IJ format] [image height] [image width]
		
		C) Interpreting
   
			The validator will output several files: one report and three acessory files.
				
				(1) validation_result_[date time stamp].txt
					This file contains the statistical results and parameters used to generate the file.
					Each region reports results, and there is a final average.
					This file is tab separated for easy importing into excel.
					
					
					Fraction -  the number of test file synapses in a region / the manual marker count 
					
					Precision - the number of markers from the test file that have a corresponding neaby manual marker / the test marker count
					
					Recall - the number of manual markers that have a corresponding test marker / the manual marker count
						* this search is padded 7 pixels outside the region to catch boundary markers!
					
				(2)	Extra_Output_markers_from_test_regions[date time stamp]
					This file contains the markers from the test output adjusted to the XYZ coordinates of the corresponding region.
					useful for comparing detected vs manual markers.
					
				(3) Extra_Output2_markers_from_manual_fullscale[date time stamp]
					This file contains the manual markers from each region scaled to the fullsize image coordinates.
					useful for overlaying the manual regions on the full image.
					
				(4) Extra_Output3_precise&recall_markers[date time stamp]
					This file contains exactly which markers from the test file were determined to be precise or recalled in each region.
		
   
   
   
   
	2) HOW TO ANNOTATE:
		1) open the region images in Vaa3d
		2) use 1 and 2 click marking to annotate the green synapses in the image.
		3) The rules for a synapse are as follows:
			-Green channel is nearby Red and Blue channel
		4) after completion, saeve the v3d marker file alongside each image in the region subfolder
			with the format: "Kibra_HSN[region number]_[#markers]_[name of user].marker	 

	 -marker files generated in v3d 3d view with "1 click to define marker" operation
	  if 1 click was not accurate, revised with 2 click. 
   
	----Kibra004 HSN color ground truth data----
	--- Version 3 ---
	
	NOTES:
	 -Each region is cropped 100x100x10 size from full size image.
	 -Full size image is 1024x1024x155
	 -Each region image is named according to the convention:
			kimbraHSN(region #)_(topleft X)_(topleft y)_(start z)_(width)_(height)_(depth)
	 -The slice number of the IJ ROI zip files IS NOT the center of the region, 
	  use the region name to determine z slice
	 -Slice marked IS the start slice inclusive. ie: start at 25 = slices 25-34.
	 -For each region image there is an associated marker file containing 
	  the location of each synapse in the region in vaa3d format
	 
	region locations, sizes, markers:
		   x    y    z  w   h   d    
		1  290  696  3  150 150 10  
		2  281  471  26 150 150 10  
		3  567  455  48 150 150 10  
		4  408	356  28 150 150 10  
		5  603	660  29 150 150 10  		    
		6  190  250  41 150 150 10   
		7  236   26  60 150 150 10  
		8   65  448  52 150 150 10  
		9   11  639  64 150 150 10  
		10 442  150  67 150 150 10

		  
	 - EXECUTION: from \bin

	\bin>java SynapseValidator_v2 ..\..\kimbra_04_ground_truth_v3_10regions 3_synapse_tester_output_Kimbra_trueRadius_noMorphC
	olocal_no4thCol_IJ.marker 1024 1024
