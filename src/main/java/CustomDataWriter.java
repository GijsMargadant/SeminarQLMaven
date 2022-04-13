import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import ilog.concert.IloException;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.UnknownObjectException;

public class CustomDataWriter {
	private File file;

	/**
	 * Constructor for customDataWriter
	 * @param file the file path to the folder that you want to safe the result in
	 */
	public CustomDataWriter(File file) {
		this.file = file;
	}

	/**
	 * 
	 * @param y the y variable from the solver as IloNumVar[]
	 * @param z the z variable from the solver as IloNumVar[][][]
	 * @param data the trial as defined and named in the solver. This is one element from the ArrayList
	 * returned from the CustomDataReader
	 * @param fileName the name you want to give to the xlsx file, without .xlsx at the end.
	 * @throws IloException 
	 * @throws UnknownObjectException 
	 * @throws IOException 
	 */
	public void writeSolutionToExcelFile(IloCplex cplex, IloNumVar[] y, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data, String fileName) throws UnknownObjectException, IloException, IOException {
		System.out.println("Starting to write data to Excel file: " + fileName);
		//		IloCplex cplex = new IloCplex();
		String[] sizes = {"XXXS", "XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL"};
		int nChunks = data.size();
		int nWeeks = 52;
		int nSizes = sizes.length;

		// Initialize a workbook and a sheet. Data is written on the sheet and the sheet is contained in the workbook.
		Workbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Solution");

		// The variables will be displayed in a 2D matrix with T on the vertical axis and the groupSizes (products) categorized
		// by chunk on the horizontal axis

		// Set the width for each column in the file. One file 
		int nColumns = nChunks * nSizes + 1;
		for(int column = 0; column < nColumns; column++) {
			sheet.setColumnWidth(column, 6000);
		}

		// Create a row for productGroups, chunk names, groupSizes, y and rows for the weeks
		int nRows = nWeeks + 4;
		for(int row = 0; row < nRows; row++) {
			sheet.createRow(row);
		}

		// Fill all columns one by one. This is done by first iterating over all chunks and their corresponding groupSizes.
		// We then fill in the weekly inventory levels for each groupSize
		int column = 0;
		for(int chunk = 0; chunk < nChunks; chunk++) {
			// Retrieve the binary value for which warehouse is used
			int warehouseVar;
			if(cplex.getValue(y[chunk]) > 0.5) {
				warehouseVar = 1;
			} else {
				warehouseVar = 0;
			}
			// Find the chunk name as a substring within the y variable name
			String yName = y[chunk].getName();
			int a = yName.indexOf("(") + 1;
			int b = yName.indexOf(")");
			String chunkName = yName.substring(a, b);
			// Find the productGroup name.
			String randomSizeName = new ArrayList<String>(data.get(chunkName).keySet()).get(0);
			String productGroupName = data.get(chunkName).get(randomSizeName).getProductGroup();
			// Go over all possible groupSizes, also the ones that don't exist for the given chunk
			for(int size = 0; size < nSizes; size++) {
				// Get the names for sizeGroup and ProductGroup and the 
				String sizeName = sizes[size];
				for(int row = 0; row < nRows; row++) {
					// Set the corresponding values to the cells
					Cell cell = sheet.getRow(row).createCell(column);
					if(row == 0) {
						cell.setCellValue(productGroupName);
					} else if(row == 1) {
						cell.setCellValue(chunkName);
					} else if(row == 2) {
						cell.setCellValue(sizeName);
					} else if(row == 3) {
						cell.setCellValue(warehouseVar);
					} else {
						if(data.get(chunkName).containsKey(sizeName)) {
							cell.setCellValue(cplex.getValue(z[row-4][chunk][size]));
//							cell.setCellValue(cplex.getValue(z[row-4][chunk][size]) / (double) data.get(chunkName).get(sizeName).getSales(row-4));
						} else {
							cell.setCellValue("NA");
						}
					}
				}
				column++;
			}
		}
		cplex.close();

		// Create a file path
		String path = file.getAbsolutePath();
		String fileLocation = path.substring(0, path.length()) + fileName + ".xlsx";
		FileOutputStream outputStream = new FileOutputStream(fileLocation);

		workbook.write(outputStream);
		workbook.close();
	}
	
	public void writeHeuristicsSolutionToExcelFile(IloCplex cplex, IloNumVar[] y, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data, String fileName) throws UnknownObjectException, IloException, IOException {
		System.out.println("Starting to write data to Excel file: " + fileName);
		//		IloCplex cplex = new IloCplex();
		String[] sizes = {"XXXS", "XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL"};
		int nChunks = data.size();
		int nWeeks = 52;
		int nSizes = sizes.length;

		// Initialize a workbook and a sheet. Data is written on the sheet and the sheet is contained in the workbook.
		Workbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Solution");

		// The variables will be displayed in a 2D matrix with T on the vertical axis and the groupSizes (products) categorized
		// by chunk on the horizontal axis

		// Set the width for each column in the file. One file 
		int nColumns = nChunks * nSizes + 1;
		for(int column = 0; column < nColumns; column++) {
			sheet.setColumnWidth(column, 6000);
		}

		// Create a row for productGroups, chunk names, groupSizes, y and rows for the weeks
		int nRows = nWeeks + 4;
		for(int row = 0; row < nRows; row++) {
			sheet.createRow(row);
		}

		// Fill all columns one by one. This is done by first iterating over all chunks and their corresponding groupSizes.
		// We then fill in the weekly inventory levels for each groupSize
		int column = 0;
		for(int chunk = 0; chunk < nChunks; chunk++) {
			// Find the chunk name as a substring within the y variable name
			String yName = y[chunk].getName();
			int a = yName.indexOf("(") + 1;
			int b = yName.indexOf(")");
			String chunkName = yName.substring(a, b);
			// Find the productGroup name.
			String randomSizeName = new ArrayList<String>(data.get(chunkName).keySet()).get(0);
			String productGroupName = data.get(chunkName).get(randomSizeName).getProductGroup();
			// Go over all possible groupSizes, also the ones that don't exist for the given chunk
			for(int size = 0; size < nSizes; size++) {
				// Get the names for sizeGroup and ProductGroup and the 
				String sizeName = sizes[size];
				for(int row = 0; row < nRows; row++) {
					// Set the corresponding values to the cells
					Cell cell = sheet.getRow(row).createCell(column);
					if(row == 0) {
						cell.setCellValue(productGroupName);
					} else if(row == 1) {
						cell.setCellValue(chunkName);
					} else if(row == 2) {
						cell.setCellValue(sizeName);
					} else if(row == 3) {
						cell.setCellValue(0);
					} else {
						if(data.get(chunkName).containsKey(sizeName)) {
							cell.setCellValue(cplex.getValue(z[row-4][chunk][size]));
//							cell.setCellValue(cplex.getValue(z[row-4][chunk][size]) / (double) data.get(chunkName).get(sizeName).getSales(row-4));
						} else {
							cell.setCellValue("NA");
						}
					}
				}
				column++;
			}
		}
		cplex.close();

		// Create a file path
		String path = file.getAbsolutePath();
		String fileLocation = path.substring(0, path.length()) + fileName + ".xlsx";
		FileOutputStream outputStream = new FileOutputStream(fileLocation);

		workbook.write(outputStream);
		workbook.close();
	}

}
