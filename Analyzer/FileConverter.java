

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.Scanner;

public class FileConverter {
	private File inputFile = null;
	private File outputFile = null;
	private int height;
	private AnnOutputPanel pnlStatus = null;
	
	public FileConverter(File inputFile, File outputFile, int height, AnnOutputPanel pnlStatus) {
		this.inputFile = inputFile;
		this.outputFile = outputFile;
		this.height = height;
		this.pnlStatus = pnlStatus;
	}
	
	/**
	 * Converts traced neuron file from Amira format to Vaa3D format
	 */
	public void convertNeuronFile() {
		Scanner scanner = null;
		try {
			scanner = new Scanner(inputFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			pnlStatus.setOutput("Cannot open input file.");
			return;
		}
		String  line = null;
		double x, y, z;
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
			
			while(scanner.hasNextLine() || line.isEmpty()) {
				line = scanner.nextLine();
				line.trim();
				if(line.startsWith("#")) {
					writer.write(line);
					writer.newLine();
					continue;
				}
				
				String[] parts = line.split(" ");
				
				//Throw exception for invalid format
				if(parts.length < 7) {
					pnlStatus.setOutput("Invalid neruon node file format. Each data line must have seven parts.");
					return;
				}
				
				x = Double.parseDouble(parts[2]);
				y = Double.parseDouble(parts[3]);
				z = Double.parseDouble(parts[4]);
				
				//V3dX = AmiraX +1;
				//V3dY = totalheight - AmiraY;
				//V3dZ = AmiraZ +1;
				
				x++;
				y = height - y;
				z++;
				
				parts[2] = Double.toString(x);
				parts[3] = Double.toString(y);
				parts[4] = Double.toString(z);
				
				int i = 0;
				for(String part : parts) {
					if(i == 6)
						writer.write(part);
					else
						writer.write(part + " ");
					i++;
				}
				writer.newLine();
				writer.flush();
			}
		} catch (Exception e) {
			e.printStackTrace();
			pnlStatus.setOutput("Conversion failed. " + e.getMessage());
			return;
		}
		pnlStatus.setOutput("Successfully converted!");
	}
	
	/**
	 * Converts synapse file from csv format exported from ImageJ to Vaa3D format
	 */
	public void convertSynapseFile() {
		Scanner scanner = null;
		try {
			scanner = new Scanner(inputFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			pnlStatus.setOutput("Cannot open input file.");
			return;
		}
		String  line = null;
		double x, y, z;
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
			
			while(scanner.hasNextLine() || line.isEmpty()) {
				line = scanner.nextLine();
				line.trim();
				if(line.startsWith("#")) {
					writer.write(line);
					writer.newLine();
					continue;
				}
				
				String[] parts = line.split(",");
				
				//Throw exception for invalid format
				if(parts.length != 3) {
					pnlStatus.setOutput("Invalid synapse file format. Each data line must have 3 parts.");
					return;
				}
				
				x = Double.parseDouble(parts[0]);
				y = Double.parseDouble(parts[1]);
				z = Double.parseDouble(parts[2]);
				
				//V3dX = AmiraX +1;
				//V3dY = totalheight - AmiraY;
				//V3dZ = AmiraZ +1;
				
				x++;
				y = height - y;
				z++;
				
				parts[0] = Double.toString(x);
				parts[1] = Double.toString(y);
				parts[2] = Double.toString(z);
				
				int i = 0;
				for(String part : parts) {
					if(i == 2)
						writer.write(part);
					else
						writer.write(part + ",");
					i++;
				}
				writer.newLine();
				writer.flush();
			}
		} catch (Exception e) {
			e.printStackTrace();
			pnlStatus.setOutput("Conversion failed. " + e.getMessage());
			return;
		}
		pnlStatus.setOutput("Successfully converted!");
	}
}
