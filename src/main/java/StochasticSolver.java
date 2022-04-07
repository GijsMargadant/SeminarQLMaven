import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

public class StochasticSolver {

	public static void main(String[] args) throws FileNotFoundException
	{		
		// Define all the size groups
		String[] sizes = {"XXXS", "XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL"};
		
		//read data for year 2018
		ArrayList<Integer> years = new ArrayList<Integer>(Arrays.asList(2020));
		ArrayList<HashMap<String, HashMap<String, Product>>> dt = readData(new ArrayList<Integer>(Arrays.asList(2020)));
		System.out.println(dt);

		//Try to build and solve the model.
		
		try
		{
			solveStochastic(52, sizes, dt.get(0));
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
	public static ArrayList<HashMap<String, HashMap<String, Product>>> readData(ArrayList<Integer> years){
		File data;
		File relevanceScore;
		File warehouseCost;
		// Check your operating system in order to correctly specify file paths
		String os = System.getProperty("os.name").toLowerCase();
		if (os.indexOf("win") >= 0) {
			data = new File(".\\dataFiles\\Forecast with dummy 45 - 51 - Copy.xlsx");
			relevanceScore = new File(".\\dataFiles\\EUR_BusinessCase_Chunk_RelevanceScore_V2.xlsx");
			warehouseCost = new File(".\\dataFiles\\EUR_BusinessCase_Sizegroup_Costs.xlsx");
		}else {
			data = new File("./dataFiles/Forecast with dummy 45 - 51 - Copy.xlsx");
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
						double criticalValue = prod.getProductGroup().equals("General Toys") ? criticalValue98 : criticalValue95;
						cplex.addGe(cplex.sum(x[t][i][s][0], x[t][i][s][1]), prod.getSales(t) + prod.getSalesVarianceOfWeek(t)*criticalValue);
					}
				}
			}
		}
		cplex.addMaximize(objExpr);
		
		for (int t = 0; t < T; t++) {
			IloNumExpr capacity0 = cplex.constant(0);
			IloNumExpr capacity1 = cplex.constant(0);
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						capacity0 = cplex.sum(capacity0, cplex.prod(prod.getAverageM3(t), x[t][i][s][0]));
						capacity1 = cplex.sum(capacity1, cplex.prod(prod.getAverageM3(t), x[t][i][s][1]));
					}
				}
			}
			cplex.addLe(capacity0, cap0, "Capacity constraint for small warehouse");
			cplex.addLe(capacity1, cap1, "Capacity constraint for big warehouse");
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
//			writeSolutionToDucument(cplex, z, y, data);
			
		}
		else
		{
			System.out.println("No optimal solution found");
		}
	}

}
