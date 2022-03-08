import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class CustomDataReader {
	private Workbook dataSetWb;
	private Workbook relevanceFactorWb;
	private Workbook sizeGroupCostsWb;
	

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
			dataSetWb = new XSSFWorkbook(new FileInputStream(dataSet));
			relevanceFactorWb = new XSSFWorkbook(new FileInputStream(relevanceFactor));
			sizeGroupCostsWb = new XSSFWorkbook(new FileInputStream(sizeGroupCosts));
		} catch (Exception e) {
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
	 * 
	 * @return an ArrayList with HashMaps containing all the Products with data for one year. Each HashMap represents
	 * its own year. HashMaps are sorted by their natural order by year.
	 */
	public ArrayList<TreeMap<String, HashMap<String, Product>>> readData(ArrayList<Integer> years) throws IllegalStateException {
		// Initialize the result object
		ArrayList<TreeMap<String, HashMap<String, Product>>>  result = new ArrayList<TreeMap<String, HashMap<String, Product>>>(years.size());
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
			if(i != 0) {
				try {
					String chunk = row.getCell(0).getStringCellValue();
					Double score = row.getCell(1).getNumericCellValue();
					relevanceData.put(chunk, score);
				} catch (Exception e) {
					throw new IllegalAccessError("Cell values are not as expected");
				}
			}
			i++;
		}
		
		// Second import the sizeGroup costs
		HashMap<String, Double> sizeGroupCost = new HashMap<String, Double>();
		i = 0;
		for(Row row : sizeGroupCostSheet) {
			// Skip the header of the table
			if(i != 0) {
				try {
					String sizeGroup = row.getCell(0).getStringCellValue();
					Double cost = row.getCell(1).getNumericCellValue();
					sizeGroupCost.put(sizeGroup, cost);
				} catch (Exception e) {
					throw new IllegalAccessError("Cell values are not as expected");
				}
			}
			i++;
		}
		
		// Now start creating all the products
		// YEAR 0
		// WEEK 1
		// QTY_SALES 2
		// PRODUCT_GROUP 3
		// CHUNK_NAME 5
		// SIZE_GROUP 6
		// AVERAGE_M3 7
		// AVERAGE_PRICE 8
		i = 0;
		for(Row row : dataSheet) {
			// Skip the header of the table
			if(i != 0) {
				try {
					int year = (int) row.getCell(0).getNumericCellValue();
					int week = (int) row.getCell(1).getNumericCellValue();
					int qtySales = (int) row.getCell(2).getNumericCellValue();
					String productGroup = row.getCell(3).getStringCellValue();
					String chunk = row.getCell(5).getStringCellValue();
					String sizeGroup = row.getCell(6).getStringCellValue();
					double averageM3 = row.getCell(7).getNumericCellValue();
					double averagePrice = row.getCell(8).getNumericCellValue();
					
					// Find warehouse cost and relevance
					double warehouseCost = sizeGroupCost.get(sizeGroup);
					double relevance = relevanceData.get(chunk);
					
					// TODO find out if the data of an instance belongs to a Product
					// object that already exists. If yes, add the data to it. If not,
					// create a new Product object.
					
					
				} catch (Exception e) {
					throw new IllegalAccessError("Cell values are not as expected");
				}
			}
			i++;
		}
		
		
		return result;
	}
}
