import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import javax.swing.plaf.synth.SynthOptionPaneUI;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public class CustomDataReader {
	private Workbook dataSetWb;
	private Workbook relevanceFactorWb;
	private Workbook sizeGroupCostsWb;
	

	public static void main(String[] args) {
		long tic = System.currentTimeMillis();
				
		// Since I've created a dataFile folder in the project containing all xlsx files, you can access them
		// in this way. This probably only works if you have eclipse and GitHub linked. Otherwise, you should
		// use the file paths from your own PC.
		File data;
		File relevanceScore;
		File warehouseCost;
		// Check your operating system in order to correctly specify file paths
		String os = System.getProperty("os.name").toLowerCase();
		if (os.indexOf("win") >= 0) {
			data = new File(".\\dataFiles\\dataset.xlsx");
			relevanceScore = new File(".\\dataFiles\\EUR_BusinessCase_Chunk_RelevanceScore_V2.xlsx");
			warehouseCost = new File(".\\dataFiles\\EUR_BusinessCase_Sizegroup_Costs.xlsx");
		}else {
			data = new File("./dataFiles/dataset.xlsx");
			relevanceScore = new File("./dataFiles/EUR_BusinessCase_Chunk_RelevanceScore_V2.xlsx");
			warehouseCost = new File("./dataFiles/EUR_BusinessCase_Sizegroup_Costs.xlsx");
		}
		
		
		try {
			CustomDataReader cdm = new CustomDataReader(data, relevanceScore, warehouseCost);
			ArrayList<Integer> years = new ArrayList<Integer>();
			years.add(2018);
			years.add(2019);
			ArrayList<HashMap<String, HashMap<String, Product>>> result = cdm.readData(years);
			
			int year = 0;
			for(HashMap<String, HashMap<String, Product>> yearlyData : result) {
				int totalProducts = 0;
				for(String chunk : yearlyData.keySet()) {
					totalProducts += yearlyData.get(chunk).keySet().size();
				}
				System.out.println("Total products in year " + years.get(year) + ": " + totalProducts);
				year++;
			}
			
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		long toc = System.currentTimeMillis();
		System.out.println("Total bussy time: " + (toc - tic) / 1000 + " s");
	}
	
	
	/**
	 * This is a constructor for the CustomDataReader. Since the data is divided among 3 excel files, the input
	 * is three excel files
	 * @param dataSet this File contains the majority of the data
	 * @param relevanceFactor this File contains the relevance Factors for each chunk
	 * @param sizeGroupCosts this File contains the warehouseCosts for each groupSize
	 * @throws FileNotFoundException whenever one of the files can not be found
	 */
	public CustomDataReader(File dataSet, File relevanceFactor, File sizeGroupCosts) throws FileNotFoundException {
		// Try to safe workbooks for each file. A workbook is a collection of the different sheets in an excel.
		try {
			long tic = System.currentTimeMillis();
			dataSetWb = WorkbookFactory.create(new FileInputStream(dataSet));
			relevanceFactorWb = WorkbookFactory.create(new FileInputStream(relevanceFactor));
			sizeGroupCostsWb = WorkbookFactory.create(new FileInputStream(sizeGroupCosts));
			long tac = System.currentTimeMillis();
			System.out.println("Time spent creating workbooks: " + (tac - tic)/1000 + " s");
		} catch (IOException e) {
			throw new FileNotFoundException();
		}
	}
	
	/**
	 * This method reads the data from the files provided to the object. The data is stored in the following structure:
	 * First, for each chunk a separate HashMap is created containing groupSize as a key and a Product object as its
	 * value. Call these the 'chunk HashMaps' in which data is stored for each groupSize that is present in a given chunk.
	 * A second HashMap stores all these 'chunk HashMaps' with the chunk name as a key and the chunk HashMaps as values.
	 * Since a Product object only holds data for one year, this big HashMap only represents all products sold in one year.
	 * As more than one year may be provided in the method, these big HashMaps are stored in an arrayList in the same order
	 * as the years were provided.
	 * 
	 * By using HashMaps, values can be obtained or changed in constant time and using Iterators we can easily go over all
	 * Products when we want to obtain parameters for constraints or objective functions.
	 * 
	 * @param years an integer array list with all the years that you want the data of.
	 * @return an ArrayList with HashMaps containing all the Products with data for one year. Each HashMap represents
	 * its own year. HashMaps are sorted by their natural order by year.
	 * @throws IllegalStateException if a cell holds a data type that is not expected
	 */
	public ArrayList<HashMap<String, HashMap<String, Product>>> readData(ArrayList<Integer> years) throws IllegalStateException {
		// Measure the time it takes for data reading for evaluation purposes
		long tic = System.currentTimeMillis();
		
		// Initialize the result object
		ArrayList<HashMap<String, HashMap<String, Product>>>  result = new ArrayList<HashMap<String, HashMap<String, Product>>>(years.size());
		for(int i = 0; i < years.size(); i++) {
			result.add(new HashMap<String, HashMap<String,Product>>());
		}
		// Retrieve the data sheets
		Sheet dataSheet = dataSetWb.getSheetAt(0);
		Sheet relevanceSheet = relevanceFactorWb.getSheetAt(0);
		Sheet sizeGroupCostSheet = sizeGroupCostsWb.getSheetAt(0);
		
		// First import the relevance score data and sizeGroup cost data, because the records in the big data set point
		// to values in these data sets
		HashMap<String, Double> relevanceData = new HashMap<String, Double>();
		int i = 0;
		for(Row row : relevanceSheet) {
			// Skip the header of the table
			// At the and of the excel file a chunk name disappeared, so don't read this row
			if(i != 0 && !(row.getCell(0) == null || row.getCell(0).getCellType() == CellType.BLANK)) {
				try {
					String chunk = row.getCell(0).getStringCellValue();
					Double score = row.getCell(1).getNumericCellValue();
					relevanceData.put(chunk, score);
				} catch (Exception e) {
					throw new IllegalStateException("Cell values are not as expected");
				}
			}
			i++;
		}
		System.out.println("relevanceData size: " + relevanceData.size());
		
		// Second import the sizeGroup costs
		HashMap<String, Double> sizeGroupCost = new HashMap<String, Double>();
		i = 0;
		for(Row row : sizeGroupCostSheet) {
			// Skip the header of the table
			if(i != 0) {
				try {
					String sizeGroup = row.getCell(0).getStringCellValue();
					sizeGroup = convertSizeFormat(sizeGroup);
					Double cost = row.getCell(1).getNumericCellValue();
					sizeGroupCost.put(sizeGroup, cost);
				} catch (Exception e) {
					throw new IllegalStateException("Cell values are not as expected");
				}
			}
			i++;
		}
		System.out.println("sizeGroupData size: " + sizeGroupCost.size());
		
		// Now start creating all the products and save them in results
		
		// ------------------------------------
		// HEADER NAMES WITH COLUMN INDEX
		// YEAR 0
		// WEEK 1
		// QTY_SALES 2
		// PRODUCT_GROUP 3
		// SHOP 4
		// CHUNK_NAME 5
		// SIZE_GROUP 6
		// AVERAGE_M3 7
		// AVERAGE_PRICE 8
		// ------------------------------------
		
		i = 0;
		for(Row row : dataSheet) {
			// Skip the header of the table
			
			if(i != 0) {
				try {
					
					// TODO Check whether the data from the excel is valid, e.g. no negative demand
					
					// Find all primary necessary data to find out whether we need to add a new
					// product or that we just need to add time series data.
					// Since all values in the xlsx file are of type text, we need to convert them
					int year = Integer.parseInt(getCellValueAsString(row.getCell(0)));
					int week = Integer.parseInt(getCellValueAsString(row.getCell(1)));
					int qtySales = (int) Math.max(Double.parseDouble(getCellValueAsString(row.getCell(2))),0);
					String productGroup = getCellValueAsString(row.getCell(3));
					String chunk = getCellValueAsString(row.getCell(5));
					String sizeGroup = getCellValueAsString(row.getCell(6));
					sizeGroup = convertSizeFormat(sizeGroup);
					double averageM3 = Math.max(Double.parseDouble(getCellValueAsString(row.getCell(7))),0);
					double averagePrice;
					if (row.getCell(8) != null) {
						averagePrice = Math.max(Double.parseDouble(getCellValueAsString(row.getCell(8))),0);
					}else {
						averagePrice = 0; 
					}
					
					// Since there are chunks that are named the same, but belong to different product groups,
					// we differentiate between all chunks based on a chunk_productGroup key
					String chunkKey = chunk + "_" + productGroup;
					
					// Check whether or not the Product already exists in the result data structure.
					// If it does, just add the time series data to the product.
					// If it does not, create a new Product object and add it to results
					int index = years.indexOf(year);
					if(index >= 0) {
						// In this case, a year is found that is specified when this method was called, so we read
						// the data and safe it.
						if(result.get(index).containsKey(chunkKey)) {
							if(result.get(index).get(chunkKey).containsKey(sizeGroup)) {
								// In this case, we only need to add time series data to an already existing
								// product
								result.get(index).get(chunkKey).get(sizeGroup).addSale(week, qtySales);
								result.get(index).get(chunkKey).get(sizeGroup).addAverageM3(week, averageM3);
								result.get(index).get(chunkKey).get(sizeGroup).addAveragePrice(week, averagePrice);
							} else {
								// In this case, we only need to add the product to the chunk HashMap
								// Find secondary data in order to create a new product
								String shop = getCellValueAsString(row.getCell(4));
								double warehouseCost = sizeGroupCost.get(sizeGroup);
								double relevance = relevanceData.get(chunk);
								Product product = new Product(shop, productGroup, chunk, sizeGroup, year, warehouseCost, relevance);
								// Add time series data
								product.addSale(week, qtySales);
								product.addAverageM3(week, averageM3);
								product.addAveragePrice(week, averagePrice);
								// Add the new object to the result
								result.get(index).get(chunkKey).put(sizeGroup, product);
							}
						} else {
							// In this case, we need to add the chunk to the big HashMap and the product
							// to a new chunk HashMap
							// Find secondary data in order to create a new product
							String shop = getCellValueAsString(row.getCell(4));
							double warehouseCost = sizeGroupCost.get(sizeGroup);
							double relevance = relevanceData.get(chunk);
							Product product = new Product(shop, productGroup, chunk, sizeGroup, year, warehouseCost, relevance);
							// Add time series data
							product.addSale(week, qtySales);
							product.addAverageM3(week, averageM3);
							product.addAveragePrice(week, averagePrice);
							// Add the product to a new chunk HashMap and that add it to the big HashMap
							HashMap<String, Product> chunkMap = new HashMap<String, Product>();
							chunkMap.put(sizeGroup, product);
							result.get(index).put(chunkKey, chunkMap);
						}
					}
				} catch (NullPointerException n) {
					if(n.getMessage().contentEquals("Cannot invoke \"org.apache.poi.ss.usermodel.Cell.getCellType()\" because \"cell\" is null")) {
						// In this case, probably a cell is empty. We just skip the line in the database
						System.err.println("In line " + (i + 1) + " is a missing value. Did not insert this line in the data set.");
					} else {
						// In this case something else is wrong
						n.printStackTrace();
					}
				} catch (Exception e) {
					e.printStackTrace();
					throw new IllegalStateException("Something went wrong reading the big data file in line " + (i + 1));
				}
			}
			i++;
		}
		
		// This part cleans the data in a more sophisticated way. Can be disabled without any problems
//		int[] cleaningResults = new int[3];
//		Arrays.fill(cleaningResults, 0);
//		for(HashMap<String, HashMap<String, Product>> year : result) {
//			for(HashMap<String, Product> chunk : year.values()) {
//				for(Product product : chunk.values()) {
//					int[] modCount = product.cleanTimeSeriesData();
//					cleaningResults[0] += modCount[0];
//					cleaningResults[1] += modCount[1];
//					cleaningResults[2] += modCount[2];
//				}
//			}
//		}
//		System.out.println("Cleaned the folowing amount datapoints:");
//		System.out.println("Sales: " + cleaningResults[0]);
//		System.out.println("Volume: " + cleaningResults[1]);
//		System.out.println("Prices: " + cleaningResults[2]);
		
		
		for(int j = 0; j < years.size(); j++) {
			System.out.println("Data size for year " + years.get(j) + ": " + result.get(j).size() + " chunks");
		}
		
		long toc = System.currentTimeMillis();
		System.out.println("Total reading time: " + (toc - tic) / 1000 + " s");
		return result;
	}
	
	
	
	public HashMap<String, HashMap<String, Product>> readDataCombined() {
		
		HashMap<String, HashMap<String, Product>> data = new HashMap<String, HashMap<String, Product>>();
		
		// Open excel sheets
		long tic = System.currentTimeMillis();
		Sheet dataSheet = dataSetWb.getSheetAt(0);
		Sheet relevanceSheet = relevanceFactorWb.getSheetAt(0);
		Sheet sizeGroupCostSheet = sizeGroupCostsWb.getSheetAt(0);
		long toc = System.currentTimeMillis();
		System.out.println("Opened sheets in " + (toc - tic)/1000 + " s");
		
		
		// First import the relevance score data and sizeGroup cost data, because the records in the big data set point
		// to values in these data sets
		HashMap<String, Double> relevanceData = new HashMap<String, Double>();
		int i = 0;
		for(Row row : relevanceSheet) {
			// Skip the header of the table
			// At the and of the excel file a chunk name disappeared, so don't read this row
			if(i != 0 && !(row.getCell(0) == null || row.getCell(0).getCellType() == CellType.BLANK)) {
				try {
					String chunk = row.getCell(0).getStringCellValue();
					Double score = row.getCell(1).getNumericCellValue();
					relevanceData.put(chunk, score);
				} catch (Exception e) {
					throw new IllegalStateException("Cell values are not as expected");
				}
			}
			i++;
		}
		System.out.println("Relevance data size: " + relevanceData.size());
		
		// Second import the sizeGroup costs
		HashMap<String, Double> sizeGroupCost = new HashMap<String, Double>();
		i = 0;
		for(Row row : sizeGroupCostSheet) {
			// Skip the header of the table
			if(i != 0) {
				try {
					String sizeGroup = row.getCell(0).getStringCellValue();
					sizeGroup = convertSizeFormat(sizeGroup);
					Double cost = row.getCell(1).getNumericCellValue();
					sizeGroupCost.put(sizeGroup, cost);
				} catch (Exception e) {
					throw new IllegalStateException("Cell values are not as expected");
				}
			}
			i++;
		}
		System.out.println("Storagecost data size: " + sizeGroupCost.size());
		
		
		tic = System.currentTimeMillis();
		int baseYear = 2018;
		int nWeeks = 104;
		data = new HashMap<String,HashMap<String,Product>>();
		i = 0;
		for(Row row : dataSheet) {
			if(i != 0) {
				try {
					// Find all primary necessary data to find out whether we need to add a new
					// product or that we just need to add time series data.
					// Since all values in the xlsx file are of type text, we need to convert them
					int year = Integer.parseInt(getCellValueAsString(row.getCell(0)));
					int week = Integer.parseInt(getCellValueAsString(row.getCell(1)));
					week = (year - baseYear) * 52 + week;
					int qtySales = (int) Math.max(Double.parseDouble(getCellValueAsString(row.getCell(2))),0);
					String productGroup = getCellValueAsString(row.getCell(3));
					String chunk = getCellValueAsString(row.getCell(5));
					String sizeGroup = getCellValueAsString(row.getCell(6));
					sizeGroup = convertSizeFormat(sizeGroup);
					double averageM3 = Math.max(Double.parseDouble(getCellValueAsString(row.getCell(7))),0);
					double averagePrice;
					if (row.getCell(8) != null) {
						averagePrice = Math.max(Double.parseDouble(getCellValueAsString(row.getCell(8))),0);
					}else {
						averagePrice = 0; 
					}

					// Since there are chunks that are named the same, but belong to different product groups,
					// we differentiate between all chunks based on a chunk_productGroup key
					String chunkKey = chunk + "_" + productGroup;

					// Check whether or not the Product already exists in the result data structure.
					// If it does, just add the time series data to the product.
					// If it does not, create a new Product object and add it to results

					if(year != 2020) {
						if(data.containsKey(chunkKey)) {
							if(data.get(chunkKey).containsKey(sizeGroup)) {
								// In this case, we only need to add time series data to an already existing
								// product
								data.get(chunkKey).get(sizeGroup).addSale(week, qtySales);
								data.get(chunkKey).get(sizeGroup).addAverageM3(week, averageM3);
								data.get(chunkKey).get(sizeGroup).addAveragePrice(week, averagePrice);
							} else {
								// In this case, we only need to add the product to the chunk HashMap
								// Find secondary data in order to create a new product
								String shop = getCellValueAsString(row.getCell(4));
								double storageCost = sizeGroupCost.get(sizeGroup);
								double relevance = relevanceData.get(chunk);
								Product product = new Product(shop, productGroup, chunk, sizeGroup, storageCost, relevance, nWeeks);
								// Add time series data
								product.addSale(week, qtySales);
								product.addAverageM3(week, averageM3);
								product.addAveragePrice(week, averagePrice);
								// Add the new object to the result
								data.get(chunkKey).put(sizeGroup, product);
							}
						} else {
							// In this case, we need to add the chunk to the big HashMap and the product
							// to a new chunk HashMap
							// Find secondary data in order to create a new product
							String shop = getCellValueAsString(row.getCell(4));
							double storageCost = sizeGroupCost.get(sizeGroup);
							double relevance = relevanceData.get(chunk);
							Product product = new Product(shop, productGroup, chunk, sizeGroup, storageCost, relevance, nWeeks);
							// Add time series data
							product.addSale(week, qtySales);
							product.addAverageM3(week, averageM3);
							product.addAveragePrice(week, averagePrice);
							// Add the product to a new chunk HashMap and that add it to the big HashMap
							HashMap<String, Product> chunkMap = new HashMap<String, Product>();
							chunkMap.put(sizeGroup, product);
							data.put(chunkKey, chunkMap);
						}
					}

				} catch (NullPointerException n) {
					if(n.getMessage().contentEquals("Cannot invoke \"org.apache.poi.ss.usermodel.Cell.getCellType()\" because \"cell\" is null")) {
						// In this case, probably a cell is empty. We just skip the line in the database
						System.err.println("In line " + (i + 1) + " is a missing value. Did not insert this line in the data set.");
					} else {
						// In this case something else is wrong
						n.printStackTrace();
					}
				} catch (Exception e) {
					e.printStackTrace();
					throw new IllegalStateException("Something went wrong reading the big data file in line " + (i + 1));
				}
			}
			i++;
		}
		toc = System.currentTimeMillis();
		System.out.println("Saved data in " + (toc - tic)/1000 + " s");
		
		
		tic = System.currentTimeMillis();
		int[] cleaningResults = new int[3];
		for(HashMap<String,Product> chunk : data.values()) {
			for(Product product : chunk.values()) {
				int[] modCount = product.cleanTimeSeriesData();
				cleaningResults[0] += modCount[0];
				cleaningResults[1] += modCount[1];
				cleaningResults[2] += modCount[2];
			}
		}
		toc = System.currentTimeMillis();
		System.out.println("Data cleaned in " + (toc - tic)/1000 + " s");
		System.out.println("Sales: " + cleaningResults[0]);
		System.out.println("Volume: " + cleaningResults[1]);
		System.out.println("Prices: " + cleaningResults[2]);
		
		
		//-------------------------------------
		// This code is used to calculate the distribution properties
		//-------------------------------------
		
		int count = 0;
		tic = System.currentTimeMillis();
		for(HashMap<String,Product> chunk : data.values()) {
			for(Product product : chunk.values()) {
				count++;
				product.calculateDistributionProperties();
			}
		}
		toc = System.currentTimeMillis();
		System.out.println("Calculated distribution properties of " + count +" products in " + (toc - tic) + " ms");
		
		return data;
	}
		

	
	/**
	 * Since sometimes the size is denoted as 2XS and sometimes as XXS, we convert everything to XXS format
	 * @param size
	 * @return
	 */
	private String convertSizeFormat(String size) {
		String result = "";
		// First check if the size begins with a digit
		if(Character.isDigit(size.charAt(0))) {
			// Save that digit and remove it
			int digit = Character.getNumericValue(size.charAt(0));
			// This is in case there is a space at the beginning of the size
			result = size.substring(1);
			// Now add as many X's in front of the size as the digit is big.
			// Keep in mind that for 3Xs, this means that 2 X's need to be added.
			for(int i = 0; i < digit - 1; i++) {
				result = "X" + result;
			}
		} else {
			result = size;
		}
		return result;
	}
	
	
	
	/**
	 * Since not all values in the data set are of the expected type, this method converts them all to String
	 * @param cell
	 * @return String representation of the cell
	 */
	private String getCellValueAsString(Cell cell) {
		String result;
		switch (cell.getCellType()) {
		case STRING:
			result = cell.getStringCellValue();
			break;
		case NUMERIC:
			result = String.valueOf(cell.getNumericCellValue());
			break;
		default:
			result = cell.getStringCellValue();
			break;
		}
		return result;
	}
	
}
