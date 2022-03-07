import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;

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
			e.printStackTrace();
		}
	}
	
	/**
	 * This method reads the data from the files provided in the constructor. It creates a separate Product for
	 * each groupSize of each chunk. These products are than saved in a HashMap with their chunk names as keys.
	 * Therefore, there are as many products in a bucket as there are groupSizes defined within a chunk. Furthermore,
	 * since a Product only holds data for a single year, a separate HashMap is created for each year. These different
	 * HashMaps are stored in an ArrayList.
	 * @return an ArrayList with HashMaps containing all the Products with data for one year. Each HashMap represents
	 * its own year. HashMaps are sorted by their natural order by year.
	 */
	public ArrayList<HashMap<String, Product>> readData() {
		// Initialize the result object
		ArrayList<HashMap<String, Product>> result = new ArrayList<HashMap<String, Product>>();
		// Retrieve the data sheets
		Sheet dataSheet = dataSetWb.getSheetAt(0);
		Sheet relevanceSheet = relevanceFactorWb.getSheetAt(0);
		Sheet sizeGroupCostSheet = sizeGroupCostsWb.getSheetAt(0);
		
		// First import the relevance score data and sizeGroup cost data, because the records in the big data set point
		// to values in these data sets
		HashMap<String, Double> relevanceData = new HashMap<String, Double>();
		return result;
	}
	
	

}
