import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.UnknownObjectException;

public class StochasticSolver {

	public static void main(String[] args) throws FileNotFoundException
	{		
		// Define all the size groups
		String[] sizes = {"XXXS", "XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL"};
		
		//read data for year 2018
		ArrayList<Integer> years = new ArrayList<Integer>();
		years.add(2018);
		years.add(2019);
//		years.add(2020);
		ArrayList<HashMap<String, HashMap<String, Product>>> dt = readData("dataset - Copy.xlsx", years);
		dt.add(readData("Forecast 2020.xlsx", new ArrayList<Integer>(Arrays.asList(2020))).get(0));
		variance(52, sizes, dt);

		//Try to build and solve the model.
		
		try
		{
			solveStochastic(52, sizes, dt.get(2));
		}
		catch (IloException e)
		{
			System.out.println("A Cplex exception occured: " + e.getMessage());
			e.printStackTrace();
		}	
	}
	
	/**
	 * This method reads the data for the years given and returns them in format implemented by Gijs
	 * @param years An arrayList with the years for which the data needs to be retrieved. 
	 * @return The data
	 */
	public static ArrayList<HashMap<String, HashMap<String, Product>>> readData(String filename, ArrayList<Integer> years){
		File data;
		File relevanceScore;
		File warehouseCost;
		// Check your operating system in order to correctly specify file paths
		String os = System.getProperty("os.name").toLowerCase();
		if (os.indexOf("win") >= 0) {
			data = new File(".\\dataFiles\\" + filename);
			relevanceScore = new File(".\\dataFiles\\EUR_BusinessCase_Chunk_RelevanceScore_V2.xlsx");
			warehouseCost = new File(".\\dataFiles\\EUR_BusinessCase_Sizegroup_Costs.xlsx");
		}else {
			data = new File("./dataFiles/" + filename);
			relevanceScore = new File("./dataFiles/EUR_BusinessCase_Chunk_RelevanceScore_V2.xlsx");
			warehouseCost = new File("./dataFiles/EUR_BusinessCase_Sizegroup_Costs.xlsx");
		}
		
		ArrayList<HashMap<String, HashMap<String, Product>>> dt = new ArrayList<HashMap<String, HashMap<String, Product>>>();
				
		try {
			CustomDataReader cdm = new CustomDataReader(data, relevanceScore, warehouseCost);
			 return cdm.readData(years);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static void variance(int T, String[] sizes, ArrayList<HashMap<String, HashMap<String, Product>>> dt) {
		HashMap<String, HashMap<String, Product>> dt2018 = dt.get(0);
		HashMap<String, HashMap<String, Product>> dt2019 = dt.get(1);
		HashMap<String, HashMap<String, Product>> dt2020 = dt.get(2);
		
		int size = sizes.length;
		ArrayList<String> chunkNames = new ArrayList<String>(dt2018.keySet());
		int n = chunkNames.size();
		
		for (int i = 0; i < n; i ++) {
			for (int s = 0; s < size; s++) {
				HashMap<String, Product> chunk18 = dt2018.get(chunkNames.get(i));
				HashMap<String, Product> chunk19 = dt2019.get(chunkNames.get(i));
				HashMap<String, Product> chunk20 = dt2020.get(chunkNames.get(i));
				Product prod18 = chunk18 != null ? chunk18.get(sizes[s]) : null;
				Product prod19 = chunk19 != null ? chunk19.get(sizes[s]) : null;
				Product prod20 = chunk20 != null ? chunk20.get(sizes[s]) : null;
				double sum = 0;
				double squared = 0;
				if (prod18 != null && prod19 != null && prod20 != null) {
					for (int t = 0; t < T; t++) {
						if (t < 45 || t == 52) {
							sum += prod18.getSales(t);
							squared += prod18.getSales(t) * prod18.getSales(t);
							sum += prod19.getSales(t);
							squared += prod19.getSales(t) * prod19.getSales(t);
						}
						if (t == 1) {
							sum += prod20.getSales(t);
							squared += prod20.getSales(t) * prod20.getSales(t);
						}
					}
//					System.out.println((squared - sum * sum / T)/T);
					prod20.setSalesVarianceOfWeek((squared - sum * sum / T)/T);
				}
			}
		}
	}
	
	public static void solveStochastic(int T, String[] sizes, HashMap<String, HashMap<String, Product>> data) throws IloException
	{
		// Create the model.
		IloCplex cplex = new IloCplex ();
		
		double criticalValue95 = 1.96;
		double criticalValue98 = 2.3263;
		
		int maxDemandProduct = 100000 ;
		
		double cap0 = 3000*15/100;
		double cap1 = 15000*15/100;
		
		int size = sizes.length;
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		
		// Create the variables and their domain restrictions.
		IloNumVar [][][][] x = new IloNumVar[T][n][size][2];
		IloNumVar [][][] z = new IloNumVar[T][n][size];
//		IloNumVar [][][] u = new IloNumVar[T][n][size];
		IloNumVar [] y = new IloNumVar[n];
		
		for (int i = 0; i < n; i ++) {
			y[i] = cplex.boolVar("y(" + chunkNames.get(i) + ")");
			for (int t = 0; t < T; t++) {
				for (int s = 0; s < size; s++) {
					HashMap<String, Product> chunk = data.get(chunkNames.get(i));
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						x[t][i][s][0] = cplex.intVar(0, Integer.MAX_VALUE, "x(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + "," + 0 + ")");
						x[t][i][s][1] = cplex.intVar(0, Integer.MAX_VALUE, "x(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + "," + 1 + ")");
						z[t][i][s] = cplex.intVar(0, Math.max(prod.getSales(t), 0), "z(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + ")");
//						u[t][i][s] = cplex.intVar(0, Integer.MAX_VALUE, "z(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + ")");
					}
				}
			}
		}
		
		// Create the objective.
		IloNumExpr objExpr = cplex.constant(0);
		
		for (int i = 0; i < n; i ++) {
			HashMap<String, Product> chunk = data.get(chunkNames.get(i));
			for (int s = 0; s < size; s++) {
				Product prod = chunk.get(sizes[s]);
				if (prod != null) {
//					cplex.addEq(u[0][i][s], 0, "Initial storage level");
					for (int t = 0; t < T; t++) {
						objExpr = cplex.sum(objExpr, cplex.prod(prod.getAveragePrice(t), z[t][i][s]));
						objExpr = cplex.sum(objExpr, cplex.negative(cplex.prod(prod.getUnitStorageCost(), x[t][i][s][0])));
						objExpr = cplex.sum(objExpr, cplex.negative(cplex.prod(prod.getUnitStorageCost(), x[t][i][s][1])));
						// Use one of the two
//						cplex.addGe(cplex.sum(x[t][i][s][0], x[t][i][s][1]), cplex.sum(u[t][i][s], z[t][i][s]), "Constraints on goods in warehouse");
//						cplex.addEq(cplex.sum(x[t][i][s][0], x[t][i][s][1]), z[t][i][s], "Constraints on goods in warehouse");
						
						cplex.addLe(x[t][i][s][0], cplex.prod(maxDemandProduct, cplex.sum(1, cplex.negative(y[i]))), "Constraints on warehouse goods allocation");
						cplex.addLe(x[t][i][s][1], cplex.prod(maxDemandProduct, y[i]), "Constraints on warehouse goods allocation");
//						if (t + 1 != T) {
//							cplex.addEq(cplex.sum(u[t + 1][i][s], z[t][i][s]), cplex.sum(x[t][i][s][0], x[t][i][s][1]), "Inventory at the beginning of the period");
//						}
						
					}
				}
			}
		}
		cplex.addMaximize(objExpr);
		
		for (int t = 0; t < T; t++) {
			IloNumExpr capacity0 = cplex.constant(0);
			IloNumExpr capacity1 = cplex.constant(0);
			IloNumExpr serviceGT = cplex.constant(0);
			IloNumExpr serviceROT = cplex.constant(0);
			double demandGT = 0;
			double demandROT = 0;
			double stdGT = 0;
			double stdROT = 0;
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						capacity0 = cplex.sum(capacity0, cplex.prod(prod.getAverageM3(t), x[t][i][s][0]));
						capacity1 = cplex.sum(capacity1, cplex.prod(prod.getAverageM3(t), x[t][i][s][1]));
					
						//					double criticalValue = prod.getProductGroup().equals("General Toys") ? criticalValue98 : criticalValue95;
						//					cplex.addGe(cplex.sum(x[t][i][s][0], x[t][i][s][1]), prod.getSales(t) + prod.getSalesVarianceOfWeek(t)*criticalValue);
						if (prod.getProductGroup().equals("General Toys")) {
							serviceGT = cplex.sum(serviceGT, cplex.sum(x[t][i][s][0], x[t][i][s][1]));
							demandGT += prod.getSales(t);
							stdGT += prod.getSalesVarianceOfWeek(t);
						}
						else {
							serviceROT = cplex.sum(serviceROT, cplex.sum(x[t][i][s][0], x[t][i][s][1]));
							demandROT += prod.getSales(t);
							stdROT += prod.getSalesVarianceOfWeek(t);
						}
					}
				}
			}
			cplex.addLe(capacity0, cap0, "Capacity constraint for small warehouse");
			cplex.addLe(capacity1, cap1, "Capacity constraint for big warehouse");
			cplex.addGe(serviceGT, demandGT + stdGT*criticalValue98);
			cplex.addGe(serviceROT, demandROT + stdROT*criticalValue95);
		}
		
		//Add the service level constraints
//		cplex = Solver.serviceLevelConstraintPerCategorie(T, sizes, cplex, z, data);
		
		
		// The last three parameters are nbr of steps, size of the steps, and value of first step. 
		// So: 2, 0.003, 0.98 means it is solved for an overall service level greater then 0.98 and 0.983
		//solveForDifferentServiceLevels(T, sizes, cplex, z, data, 1, 0.0003, 0.9857);
		
		
		/** This is the end of the model building part**/ 
		
		// Export model
		//cplex.exportModel("Model.lp");
		
		
		// Solve the model.
		cplex.solve();

		// Query the solution.
		if (cplex.getStatus() == IloCplex.Status.Optimal)
		{
			//Below are some different options to analyze the solution
			
			System.out.println("Found optimal solution!");
			System.out.println("Objective = " + cplex.getObjValue());
			
			//	View the capacity per week
//			capacityCheck(T, sizes, cplex, x, data);
			
			// View the service level per category over the full years
//			serviceLevel(T, sizes, cplex, z, data);
			
			//	View the service level per category per week. 
			//  The last parameter is printAll. If true it prints for all weeks is false it only prints the weeks with service level <100.
//			serviceLevelWeekly(T, sizes, cplex, z, data, false);
			
			
			//	View the results of 2018 projected on 2019
			//ArrayList<HashMap<String, HashMap<String, Product>>> dt2019 = readData(new ArrayList<Integer>(Arrays.asList(2019)));
			//projectedOn2019(T, sizes, cplex, z, data, dt2019.get(0));
			
			
			//	Write the excel file to a excel file
			writeSolutionToDucument(cplex, z, y, data, "Solution_Aggregate_2020");
			
		}
		else
		{
			System.out.println("No optimal solution found");
		}
	}
	
	public static void writeSolutionToDucument(IloCplex cplex, IloNumVar[][][] z, IloNumVar[] y, HashMap<String, HashMap<String, Product>> data, String filename) {
		// This should write the data to an Excel file
		//File file = new File("C:\\Users\\gijsm\\Documents\\DOCUMENTEN\\School\\SeminarCaseStudy\\SolutionFiles\\");
		
		File file;
		String os = System.getProperty("os.name").toLowerCase();
		if (os.indexOf("win") >= 0) {
			file = new File(".\\dataFiles\\");
		}else {
			file = new File("./dataFiles/");
		}
		
		CustomDataWriter cdw = new CustomDataWriter(file);
		try {
			cdw.writeSolutionToExcelFile(cplex, y, z, data, filename);
		} catch (UnknownObjectException e) {
			e.printStackTrace();
		} catch (IloException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
