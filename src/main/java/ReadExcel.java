import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import ilog.concert.IloException;

public class ReadExcel {
	public static void main(String[] args) {
		// Define all the size groups
		String[] sizes = {"XXXS", "XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL"};
		
		HashMap<String, HashMap<String, Product>> dt = Simulation.readData();
		
		System.out.println("De data is geladen");
		
		int[] T = new int[]{0,52}; 
		
		
		
		HashMap<String, Object> parameters = new HashMap<String, Object>(); 
		//Set certain parameters for the model;
		
		//Demand options 
		parameters.put("usePlusXInSales", false); //Should not be put true, old option 
		parameters.put("usePoisson", true); //If true uses Poisson distribution otherwise uses normal
		
		//Printing options 
		parameters.put("printExcelFormatSimulationResults", false);
		parameters.put("printSimulationResults", true);
		
		parameters.put("showWeeklyCapasity", true);
		parameters.put("showWeeklyServiceLevel", true);
		parameters.put("printEveryOrder", false);
		parameters.put("printEveryVariable", false);
		parameters.put("printEveryVariableIfLost", true);
		
		
		//Export to excel options 
		parameters.put("exportSimulationResults", false);
		parameters.put("exportOnlyAverageValues", true);
		parameters.put("fileName", "results"); //Does not work anymore
		parameters.put("filePath", "/Users/floris/Documents/Studie/Year_3_Block_4/Seminar/results/results_10_sim_testing.xlsx"); //Change this to the file path you want to 
		
		
		// Simulation options
		parameters.put("nbrSimulations" , 100);
		
		//model options
		parameters.put("addOrderingConstraint", false); //Does not work leave false
		parameters.put("addOrderingVariable", false); //Adds the two weeks ordering constraint weeks 44 -52
		parameters.put("addSmartTwoWeeksConstraint", true); //Adds the two weeks ordering constraint weeks 44 -52

		parameters.put("useModelWithTransfer", false); //Does not terminate
		
		
		// Added in read excel class
		parameters.put("filePathInput", "/Users/floris/Documents/Studie/Year_3_Block_4/Input/dataFilesSolution_Heuristics_2020_without_ordering time.xlsx");
		
		
		parameters.put("filePathInput", "/Users/floris/Documents/Studie/Year_3_Block_4/Input/dataFilesSolution_Heuristics_2020_without_ordering time.xlsx");
		parameters.put("filePath", "/Users/floris/Documents/Studie/Year_3_Block_4/Seminar/results/results_100_sim_poisson_Heuristics_2020_without_ordering_time_minh.xlsx"); //Change this to the file path you want to 
		ReadExcel.run(T, sizes, dt, parameters);
		
		parameters.put("filePathInput", "/Users/floris/Documents/Studie/Year_3_Block_4/Input/dataFilesSolution_Heuristics_2020.xlsx");
		parameters.put("filePath", "/Users/floris/Documents/Studie/Year_3_Block_4/Seminar/results/results_100_sim_poisson_Heuristics_2020_minh.xlsx"); //Change this to the file path you want to 
		ReadExcel.run(T, sizes, dt, parameters);
		
		parameters.put("filePathInput", "/Users/floris/Documents/Studie/Year_3_Block_4/Input/dataFilesSolution_Baseline_2020.xlsx");
		parameters.put("filePath", "/Users/floris/Documents/Studie/Year_3_Block_4/Seminar/results/results_100_sim_poisson_Baseline_2020_minh.xlsx"); //Change this to the file path you want to 
		ReadExcel.run(T, sizes, dt, parameters);
		
		
		parameters.put("filePathInput", "/Users/floris/Documents/Studie/Year_3_Block_4/Input/dataFilesSolution_Baseline_2020_without_ordering_time.xlsx");
		parameters.put("filePath", "/Users/floris/Documents/Studie/Year_3_Block_4/Seminar/results/results_100_sim_poisson_Baseline_2020__without_ordering_time_minh.xlsx"); //Change this to the file path you want to 
		ReadExcel.run(T, sizes, dt, parameters);
		
		
		/*
		int[][][] z = ReadExcel.getOrderUpToLevel(T,  sizes, dt, parameters);

		try {
			Simulation.getSimulationResults(T, sizes, dt, z, parameters);
		} catch (IloException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		*/		
		 
	}
	
	public static void run(int[] T, String[] sizes,HashMap<String, HashMap<String, Product>> dt,
			HashMap<String, Object> parameters) {
		int[][][] z = ReadExcel.getOrderUpToLevel(T,  sizes, dt, parameters);

		try {
			Simulation.getSimulationResults(T, sizes, dt, z, parameters);
		} catch (IloException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static int[][][] getOrderUpToLevel(int[] T, String[] sizes,HashMap<String, HashMap<String, Product>> data,
			HashMap<String, Object> parameters){
		int size = sizes.length;
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		
		int[][][] orderLevel =  new int[T[1]][n][size];
		try {
			
		    XSSFWorkbook wb = new XSSFWorkbook(new File((String) parameters.get("filePathInput")));
		    XSSFSheet sheet = wb.getSheetAt(0);
		    XSSFRow row;
		    XSSFCell cell;

		    int rows; // No of rows
		    rows = sheet.getPhysicalNumberOfRows();

		    int cols = 0; // No of columns
		    int tmp = 0;

		    // This trick ensures that we get the data properly even if it doesn't start from first few rows
		    for(int i = 0; i < 10 || i < rows; i++) {
		        row = sheet.getRow(i);
		        if(row != null) {
		            tmp = sheet.getRow(i).getPhysicalNumberOfCells();
		            if(tmp > cols) cols = tmp;
//		            System.out.println("The number of columns is :" + tmp);
		        }
		    }
		    
		    for(int c = 0; c < cols; c++) {
		    	row = sheet.getRow(1);
//		    	cell = row.getCell(c);
		    	String chunkName = row.getCell(c).getStringCellValue();
		    	HashMap<String, Product> chunk = data.get(chunkName);
		    	
		    	row = sheet.getRow(2);
		    	String sizeName = row.getCell(c).getStringCellValue();

		    	Product prod = chunk.get(sizeName);
				if (prod != null) {
//					System.out.println("We found a product!!");
//					System.out.println("We found " + prod.getChunk() + " of size " + prod.getSizeGroup());
					int i = chunkNames.indexOf(chunkName);
					int s = 0;
					switch (sizeName) {
						case "XXXS":
							s= 0;
							break;
						case "XXS":
							s= 1;
							break;
						case "XS":
							s= 2;
							break;
						case "S":
							s= 3;
							break;
						case "M":
							s= 4;
							break;
						case "L":
							s= 5;
							break;
						case "XL":
							s= 6;
							break;
						case "XXL":
							s= 7;
							break;
						case "XXXL":
							s= 8;
							break;
							
					}
					
					for (int t = 0; t < T[1]; t ++) {
						orderLevel[t][i][s] = (int) sheet.getRow(t + 4).getCell(c).getNumericCellValue();
					}
					
				}	
		    }
		    /*
		    for(int r = 0; r < rows; r++) {
		        row = sheet.getRow(r);
		        if(row != null) {
		            for(int c = 0; c < 10; c++) {
		                cell = row.getCell((short)c);
		                if(cell != null) {
		                	switch (cell.getCellType()) {
		                	case NUMERIC:
		                		System.out.println(cell.getNumericCellValue());
	                			break;
		                	case STRING:

		                		System.out.println("This is a string: " + cell.getStringCellValue());
	                			break;
		                	}
		                }
		            }
		        }
		    }
		    */
		} catch(Exception ioe) {
		    ioe.printStackTrace();
		}
		
//		System.out.println(orderLevel[50][chunkNames.indexOf("Speeltunnel_Recreational and Outdoor Toys")][6]);
		return orderLevel;
	}
}
