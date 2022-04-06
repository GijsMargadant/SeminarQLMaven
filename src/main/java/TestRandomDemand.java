import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * This class is to test the random demand pulling function of the product class.
 * @author gijsm
 *
 */
public class TestRandomDemand {
	private static HashMap<String,HashMap<String,Product>> data;
	
	public static void main(String[] args) {
		getData();
		
		String name = "data";
		String file = "C:\\Users\\gijsm\\Documents\\DOCUMENTEN\\School\\SeminarCaseStudy\\SolutionFiles\\" + name + ".xlsx";
		try {
			writeData(file);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	//--------------------------------------------------------------------
	// This part is for writing data
	//--------------------------------------------------------------------
	
	private static void writeData(String fileLocation) throws IOException {
		// Initialize a workbook and a sheet. Data is written on the sheet and the sheet is contained in the workbook.
		Workbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Data");
		
		int rowNum = 0;
		int celNum = 0;
		
		int nWeeks = 104;
		int nSeasons = 52;
		// Create a header
		Row header = sheet.createRow(rowNum);
		boolean ready = false;
		while(!ready) {
			Cell cell = header.createCell(celNum);
			switch(celNum) {
			case 0: cell.setCellValue("productGroup");
				break;
			case 1: cell.setCellValue("chunk");
				break;
			case 2: cell.setCellValue("sizeGroup");
				break;
			case 3: cell.setCellValue("storageCost");
				break;
			case 4: cell.setCellValue("relevanceScore");
				break;
			case 5: cell.setCellValue("cleanMean");
			break;
			case 6: cell.setCellValue("cleanStdev");
			break;
			case 7: cell.setCellValue("level");
			break;
			case 8: cell.setCellValue("trend");
			break;
			default:
				int startCel = celNum;
				while(celNum < startCel + nSeasons) {
					Cell c = header.createCell(celNum);
					c.setCellValue("SI" + celNum);
					celNum++;
				}
				while(celNum < startCel + nWeeks) {
					Cell c = header.createCell(celNum);
					c.setCellValue("cleaned" + celNum);
					celNum++;
				}
				while(celNum < startCel + nWeeks) {
					Cell c = header.createCell(celNum);
					c.setCellValue("sales" + celNum);
					celNum++;
				}

				ready = true;
			}
			celNum++;
		}
		int rowLength = celNum + 1;
		rowNum++;
		
		for(HashMap<String,Product> chunk : data.values()) {
			for(Product p : chunk.values()) {
				Row row = sheet.createRow(rowNum);
				celNum = 0;
				ready = false;
				while(!ready) {
					Cell cell = row.createCell(celNum);
					switch(celNum) {
					case 0: cell.setCellValue(p.getProductGroup());
						break;
					case 1: cell.setCellValue(p.getChunk());
						break;
					case 2: cell.setCellValue(p.getSizeGroup());
						break;
					case 3: cell.setCellValue(p.getUnitStorageCost());
						break;
					case 4: cell.setCellValue(p.getRelevanceScore());
						break;
					case 5: cell.setCellValue(p.getCleanedMean());
					break;
					case 6: cell.setCellValue(p.getCleanedStdev());
					break;
					case 7: cell.setCellValue(p.getLevel());
					break;
					case 8: cell.setCellValue(p.getTrend());
					break;
					default:
						for(double SI : p.getSeasonalIndices()) {
							Cell c = row.createCell(celNum);
							c.setCellValue(SI);
							celNum++;
						}
						for(double cleaned : p.getCleanedSales()) {
							Cell c = row.createCell(celNum);
							c.setCellValue(cleaned);
							celNum++;
						}
						for(int sales : p.getWeeklySales()) {
							Cell c = row.createCell(celNum);
							c.setCellValue(sales);
							celNum++;
						}
						ready = true;
					}
					celNum++;
				}
				rowNum++;
			}
		}
		
		// Create a file path
		FileOutputStream outputStream = new FileOutputStream(fileLocation);

		workbook.write(outputStream);
		workbook.close();
	}
	
	
	//--------------------------------------------------------------------
	// This part is for reading data
	//--------------------------------------------------------------------	
	
	/**
	 * This function exists to avoid a lot of try catch blocks in the main method
	 */
	private static void getData() {
		try {
			importData();
		} catch (EncryptedDocumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static void importData() throws EncryptedDocumentException, FileNotFoundException, IOException {
		File dataSet;
		File relevanceScore;
		File warehouseCost;
		// Check your operating system in order to correctly specify file paths
		String os = System.getProperty("os.name").toLowerCase();
		if (os.indexOf("win") >= 0) {
			dataSet = new File(".\\dataFiles\\dataset.xlsx");
			relevanceScore = new File(".\\dataFiles\\EUR_BusinessCase_Chunk_RelevanceScore_V2.xlsx");
			warehouseCost = new File(".\\dataFiles\\EUR_BusinessCase_Sizegroup_Costs.xlsx");
		} else {
			dataSet = new File("./dataFiles/dataset.xlsx");
			relevanceScore = new File("./dataFiles/EUR_BusinessCase_Chunk_RelevanceScore_V2.xlsx");
			warehouseCost = new File("./dataFiles/EUR_BusinessCase_Sizegroup_Costs.xlsx");
		}
	
		// Open excel sheets
		long tic = System.currentTimeMillis();
		Workbook dataSetWb = WorkbookFactory.create(new FileInputStream(dataSet));
		Workbook relevanceFactorWb = WorkbookFactory.create(new FileInputStream(relevanceScore));
		Workbook sizeGroupCostsWb = WorkbookFactory.create(new FileInputStream(warehouseCost));
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
	}
	
	
	/**
	 * Since not all values in the data set are of the expected type, this method converts them all to String
	 * @param cell
	 * @return String representation of the cell
	 */
	private static String getCellValueAsString(Cell cell) {
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
	
	
	/**
	 * Since sometimes the size is denoted as 2XS and sometimes as XXS, we convert everything to XXS format
	 * @param size
	 * @return
	 */
	private static String convertSizeFormat(String size) {
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
	
}
