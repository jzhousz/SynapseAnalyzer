# SynapseAnalyzer
Automatic 3D Synapse Analyzer

“Automatic Analysis of 3D Subcellular Distribution of Synapses  in Complex Dendritic Arbors”

Jonathan Sanders, Edward Hottendorf , Gabriella Sterne, Hanchuan Peng, Bing Ye, and Jie Zhou

This project contains codes for a 3D synapse quantifier.   It is to analyze 3D subcellular synapse distribution using multi-channel microscopic images.

The system consists of 3 components: Synapse Counter, Neuron Reconstructor and Distribution Quantifier.  The 3rd component takes the outputs of the first two components to produce the subcellular synapse distribution.

1. Synapse Counter

Code:

./ThreeDSynapseCounter
 
Usage:   

Synapse Counter is an ImageJ Plugin.   Follow the Section (3) of the readme.txt
./ThreeDSynapseCounter/readme.txt
 
Input:¬ 3D image of synapses. Preprocessing steps are described in readme.txt Section (2), if needed.

Output: marker files of detected synapses.  

Note: If the image has a pre-synaptic channel,  run Colocal_filer.java for co-localization of post- and pre- synaptic makers.

2. Neuron Reconstructor 

Code/Usage: 

It is deployed as a Vaa3D plugin. Vaa3D can be downloaded from vaa3d.org
   Vaa3d -> plug-in -> simple axis analyzer 

Input: 3D image of the neurite

Output: a swc file that describes the structure of neurite.

3. Synapse Distribution Quantifier 

Code:

./Analyzer

Usage:

The distribution quantifier is a standalone Java program.  Follow the readme
./Analyzer/readme.txt    

Input: a) a marker file from the Synapse Counter and b) a (sorted) swc file from Neuron Reconstructor.

Output:  Statistics of synapse distribution on screen;  a space-separated file and its bins based on radius of associated branches.

Note: Example synapse marker file (ExampleDetectedSynapse.marker) and example reconstructed neuron file (ExampleReconstructed.swc) are provided in the folder. 



