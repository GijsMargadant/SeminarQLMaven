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

/**
 * 
 * @author Minh Ngoc Pham, Floris Haverman
 * 
 * General Comments:
 * Solving time has increased significantly due to the service level constraint being binding. 
 * On my (Floris) computer it takes about 3-5 minutes to run the whole program. 
 *
 */
public class Solver {
	
	public static void main(String[] args) throws FileNotFoundException
	{		
		// Define all the size groups
		String[] sizes = {"XXXS", "XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL"};
		
		//read data for year 2018
		ArrayList<Integer> years = new ArrayList<Integer>(Arrays.asList(2020));
		ArrayList<HashMap<String, HashMap<String, Product>>> dt = readData(new ArrayList<Integer>(Arrays.asList(2020)));

		//Try to build and solve the model.
		
		try
		{
			solve(52, sizes, dt.get(0));
//			solveForSubperiod(new int[]{0,52}, sizes, dt.get(0));
//			solveForSubperiod(new int[]{45,48}, sizes, dt.get(0));
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
	
	public static void solve(int T, String[] sizes, HashMap<String, HashMap<String, Product>> data) throws IloException
	{
		// Create the model.
		IloCplex cplex = new IloCplex ();
		
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
						// Use one of the two
//						cplex.addGe(cplex.sum(x[t][i][s][0], x[t][i][s][1]), cplex.sum(u[t][i][s], z[t][i][s]), "Constraints on goods in warehouse");
						cplex.addEq(cplex.sum(x[t][i][s][0], x[t][i][s][1]), z[t][i][s], "Constraints on goods in warehouse");
						
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
		cplex = serviceLevelConstraintPerCategorie(T, sizes, cplex, z, data);
		
		
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
			ArrayList<ArrayList<Double>> leftoverCapacity = capacityCheck(T, sizes, cplex, x, data);
			
			// View the service level per category over the full years
			serviceLevel(T, sizes, cplex, z, data);
			
			//	View the service level per category per week. 
			//  The last parameter is printAll. If true it prints for all weeks is false it only prints the weeks with service level <100.
			serviceLevelWeekly(T, sizes, cplex, z, data, false);
			
			
			//	View the results of 2018 projected on 2019
			//ArrayList<HashMap<String, HashMap<String, Product>>> dt2019 = readData(new ArrayList<Integer>(Arrays.asList(2019)));
			//projectedOn2019(T, sizes, cplex, z, data, dt2019.get(0));
			
			int[] yResult = new int[n];
			for (int i = 0; i < n; i ++) {
				yResult[i] = (int) cplex.getValue(y[i]);
			}
			
			//	Write the excel file to a excel file
			writeSolutionToDucument(cplex, z, y, data, "Solution_Baseline_2020");
			
//			ArrayList<ArrayList<Double>> leftoverCapacity = leftoverCapacity(T, sizes, cplex, y, x, data);
			
			heuristic(T, sizes, yResult, leftoverCapacity.get(0), leftoverCapacity.get(0), data);
		}
		else
		{
			System.out.println("No optimal solution found");
		}
		cplex.close();
	}
	
	public static void heuristic(int T, String[] sizes, int[] y, ArrayList<Double> capacity0, ArrayList<Double> capacity1, HashMap<String, HashMap<String, Product>> data) throws IloException {
		IloCplex cplex = new IloCplex ();

		double criticalValue95 = 1.96;
		double criticalValue98 = 2.3263;
		
		int size = sizes.length;
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		
		// Create the variables and their domain restrictions.
		IloNumVar [][][] r = new IloNumVar[T][n][size];
		IloNumVar [] y_var = new IloNumVar[n];
		
		for (int i = 0; i < n; i ++) {
			y_var[i] = cplex.boolVar("y(" + chunkNames.get(i) + ")");
			for (int t = 0; t < T; t++) {
				for (int s = 0; s < size; s++) {
					HashMap<String, Product> chunk = data.get(chunkNames.get(i));
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						r[t][i][s]= cplex.intVar(0, Integer.MAX_VALUE, "r(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + ")");
//						r[t][i][s][1] = cplex.intVar(0, Integer.MAX_VALUE, "r(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + "," + 1 + ")");
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
					for (int t = 0; t < T; t++) {
						objExpr = cplex.sum(objExpr, cplex.prod(prod.getRelevanceScore(), r[t][i][s]));
						double criticalValue = prod.getProductGroup().equals("General Toys") ? criticalValue98 : criticalValue95;
						cplex.addLe(r[t][i][s], prod.getSalesVarianceOfWeek(t)*criticalValue);
					}
				}
			}
		}
		cplex.addMaximize(objExpr);
		
		for (int t = 0; t < T; t++) {
			IloNumExpr cap0 = cplex.constant(0);
			IloNumExpr cap1 = cplex.constant(0);
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
//						int warehouse0 = (int) (1 - cplexOld.getValue(y[i]));
//						int warehouse1 = (int) (cplexOld.getValue(y[i]));
						cap0 = cplex.sum(cap0, cplex.prod(prod.getAverageM3(t), cplex.prod(1 - y[i], r[t][i][s])));
						cap1 = cplex.sum(cap1, cplex.prod(prod.getAverageM3(t), cplex.prod(y[i], r[t][i][s])));
					}
				}
			}
			cplex.addLe(cap0, capacity0.get(t), "Constraints on warehouse goods allocation");
			cplex.addLe(cap1, capacity1.get(t), "Constraints on warehouse goods allocation");
		}
		
		// Solve the model.
		cplex.solve();
		
		if (cplex.getStatus() == IloCplex.Status.Optimal)
		{
			//Below are some different options to analyze the solution
			
			System.out.println("Found optimal solution!");
			System.out.println("Objective = " + cplex.getObjValue());
			
			//	Write the excel file to a excel file
			writeSolutionToDucumentHeuristics(cplex, r, y_var, data, "Solution_Heuristics_2020");
			
			cplex.close();
			
		}
		else
		{
			System.out.println("No optimal solution found");
		}
		
		cplex.close();
	}
	
	public static ArrayList<ArrayList<Double>> leftoverCapacity(int T, String[] sizes, IloCplex cplex, IloNumVar[] y, IloNumVar[][][][] x, HashMap<String, HashMap<String, Product>> data) throws IloException {
		ArrayList<Double> capacity0 = new ArrayList<Double>();
		ArrayList<Double> capacity1 = new ArrayList<Double>();
		
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		double maxcap0 = 3000*15/100;
		double maxcap1 = 15000*15/100;
		
		for (int t = 0; t < T; t++)	{
			IloNumExpr cap0 = cplex.constant(0);
			IloNumExpr cap1 = cplex.constant(0);
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						cap0 = cplex.sum(cap0, cplex.prod(prod.getAverageM3(t), x[t][i][s][0]));
						cap1 = cplex.sum(cap1, cplex.prod(prod.getAverageM3(t), x[t][i][s][1]));
					}
				}
			}
			capacity0.add(maxcap0 - cplex.getValue(cap0));
			capacity0.add(maxcap1 - cplex.getValue(cap1));
		}
		
		ArrayList<ArrayList<Double>> result = new ArrayList<ArrayList<Double>>();
		result.add(capacity0);
		result.add(capacity1);
		return result;
	}
	
	public static void solveForSubperiod(int[] T, String[] sizes, HashMap<String, HashMap<String, Product>> data) throws IloException
	{
		// Create the model.
		IloCplex cplex = new IloCplex ();
		cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0.00001);

		int maxDemandProduct = 60012;

		//double cap0 = 3000*15/100;
		//double cap1 = 15000*15/100;
		
		int size = sizes.length;
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		
		// Create the variables and their domain restrictions.
		IloNumVar [][][][] x = new IloNumVar[T[1]][n][size][2];
		IloNumVar [][][] z = new IloNumVar[T[1]][n][size];
//		IloNumVar [][][] u = new IloNumVar[T][n][size];
		IloNumVar [] y = new IloNumVar[n];
		
		for (int i = 0; i < n; i ++) {
			y[i] = cplex.boolVar("y(" + chunkNames.get(i) + ")");
			for (int t = T[0]; t < T[1]; t++) {
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
					for (int t = T[0]; t < T[1]; t++) {
						objExpr = cplex.sum(objExpr, cplex.prod(prod.getAveragePrice(t), z[t][i][s]));
						// Use one of the two
//						cplex.addGe(cplex.sum(x[t][i][s][0], x[t][i][s][1]), cplex.sum(u[t][i][s], z[t][i][s]), "Constraints on goods in warehouse");
						cplex.addEq(cplex.sum(x[t][i][s][0], x[t][i][s][1]), z[t][i][s], "Constraints on goods in warehouse");
						
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
		
		
		
		//Add capacity constraint for small and big warehouse
		cplex = Solver.addCapacityConstraint(T, sizes, cplex, x, data, 0.15, 0.15);
		
		//Add relevance score constraint
//		cplex = Solver.addRelevanceScoreConstraint(T, sizes, cplex, z, data, 0.53);
//		cplex = Solver.addRelevanceScoreConstraintSum(T, sizes, cplex, z, data, 0.925);
		
		//Add the service level constraints
//		cplex = Solver.serviceLevelConstraintPerCategorie(T, sizes, cplex, z, data);
		cplex = Solver.serviceLevelConstraintPerCategoriePeekWeeks(T, sizes, cplex, z, data);
//		cplex = Solver.serviceLevelConstraintOverall(T, sizes, cplex, z, data, 0.9856);
		
		
		
		// The last three parameters are nbr of steps, size of the steps, and value of first step. 
		// So: 2, 0.003, 0.98 means it is solved for an overall service level greater then 0.98 and 0.983
//		solveForDifferentServiceLevels(T, sizes, cplex, z, data, 30, 0.001, 0.925, false);
		
		//Use to solve for different relevance scores
//		solveForDifferentServiceLevels(T, sizes, cplex, z, data, 50, 0.001, 0.91, true);
		
		//Solver.solveForDifferentCapcityLevels(T, sizes, cplex, x, data, 40, 0.001, 0.20);

		
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
			System.out.println(Math.round(cplex.getObjValue()));

			
			//	View the capacity per week
			//capacityCheck(T, sizes, cplex, x, data);
			
			// View the service level per category over the full years
//			getRevenue(T, sizes, cplex, z, data);
			
			// View the service level per category over the full years
			serviceLevel(T, sizes, cplex, z, data);
			
			
			// View the relavance score for each week and overall the weeks
//			relevanceScore(T, sizes, cplex, z, data);

			
			//	View the service level per category per week. 
			//  The last parameter is printAll. If true it prints for all weeks is false it only prints the weeks with service level <100.
			serviceLevelWeekly(T, sizes, cplex, z, data, false);
			
			
			//	View the results of 2018 projected on 2019
			//ArrayList<HashMap<String, HashMap<String, Product>>> dt2019 = readData(new ArrayList<Integer>(Arrays.asList(2019)));
			//projectedOn2019(T, sizes, cplex, z, data, dt2019.get(0));
			
			
			//	Write the excel file to a excel file
			//writeSolutionToDucument(cplex, z, y, data);
			
		}
		else
		{
			System.out.println("No optimal solution found");
		}
	}
	
	public static void stochasticConstraint() {
		
	}
	
	
	/**
	 * This method allows you to write the solution to an excel file. 
	 * !! I, Floris, did not yet test the method, I copied the excising code into a method so should work. !!
	 * @param cplex
	 * @param z
	 * @param y
	 * @param data
	 */
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
	
	public static void writeSolutionToDucumentHeuristics(IloCplex cplex, IloNumVar[][][] z, IloNumVar[] y, HashMap<String, HashMap<String, Product>> data, String filename) {
		File file;
		String os = System.getProperty("os.name").toLowerCase();
		if (os.indexOf("win") >= 0) {
			file = new File(".\\dataFiles\\");
		}else {
			file = new File("./dataFiles/");
		}
		
		CustomDataWriter cdw = new CustomDataWriter(file);
		try {
			cdw.writeHeuristicsSolutionToExcelFile(cplex, y, z, data, filename);
		} catch (UnknownObjectException e) {
			e.printStackTrace();
		} catch (IloException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
//	public static void writeSolutionToDucument(IloCplex cplex, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data, String filename) {
//		// This should write the data to an Excel file
//		//File file = new File("C:\\Users\\gijsm\\Documents\\DOCUMENTEN\\School\\SeminarCaseStudy\\SolutionFiles\\");
//		
//		File file;
//		String os = System.getProperty("os.name").toLowerCase();
//		if (os.indexOf("win") >= 0) {
//			file = new File(".\\dataFiles\\");
//		}else {
//			file = new File("./dataFiles/");
//		}
//		
//		CustomDataWriter cdw = new CustomDataWriter(file);
//		try {
//			cdw.writeSolutionToExcelFile(cplex, z, data, filename);
//		} catch (UnknownObjectException e) {
//			e.printStackTrace();
//		} catch (IloException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}
	
	/**
	 * This method solves the problem with adding a stricter overall service level each time.
	 * @param T Parameter passed on from the solve method.
	 * @param sizes Parameter passed on from the solve method.
	 * @param cplex Parameter passed on from the solve method.
	 * @param z Parameter passed on from the solve method.
	 * @param data Parameter passed on from the solve method.
	 * @param nbrSteps amount of times the problem needs to be solved
	 * @param sizeSteps The size of the increase of the overall service level between steps
	 * @param startValue The first value of the overall service level. 
	 * @throws IloException When a problem occurs. 
	 */
	public static void solveForDifferentServiceLevels(int T, String[] sizes, IloCplex cplex, IloNumVar[][][] z, 
			HashMap<String, HashMap<String, Product>> data, 
			int nbrSteps, double sizeSteps, double startValue) throws IloException {

		cplex.setOut(null);
		for (int j = 0; j < nbrSteps; j++) {
			double overallServiceLevel = startValue + j * sizeSteps;
			cplex = Solver.serviceLevelConstraintOverall(T, sizes, cplex, z, data, overallServiceLevel);
			System.out.println("The solution is now solved for overall service level: " + overallServiceLevel);

			cplex.solve();
			if (cplex.getStatus() == IloCplex.Status.Optimal)
			{
				System.out.println("Found optimal solution!");
				System.out.println("Objective = " + cplex.getObjValue());
			
				//capacityCheck(T, sizes, cplex, x, data);
				serviceLevel(T, sizes, cplex, z, data);
				
			}
			else
			{
				System.out.println("No optimal solution found");
			}
		}
	}
	/**
	 * This method adds the service level constraint per category to the model. 
	 * by using a method the building of the model stays nice and tidy.
	 * As stated in the assignment the service level for General toys should be at least 98% and 
	 * for Recreational and outdoor toys is must be at least 95%.
	 * @param T The number of weeks we are solving the problem for
	 * @param sizes An array with the different size groups
	 * @param cplex The cplex object used to formulate the problem 
	 * @param z The constraint is implemented using the z variables of the problem formulation.
	 * @param data The data about the products.
	 * @throws IloException When something goes wrong with the cplex. 
	 */
	public static IloCplex serviceLevelConstraintPerCategorie(int T,String[] sizes, IloCplex cplex, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		IloNumExpr serviceLevelGT = cplex.constant(0);
		IloNumExpr serviceLevelROT = cplex.constant(0);
		

		double totDemandGT = 0;
		double totDemandROT = 0;
		for (int t = 0; t < T; t++)	{
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						if (prod.getProductGroup().equals("General Toys")) { 
							totDemandGT += prod.getSales(t); 
							serviceLevelGT = cplex.sum(serviceLevelGT, z[t][i][s]);

						}else {
							totDemandROT += prod.getSales(t);
							serviceLevelROT = cplex.sum(serviceLevelROT, z[t][i][s]);
						}
					}
				}
			}
		}
		//System.out.println("This is the total for GT: " + totDemandGT);
		//System.out.println("This is the total for ROT: " + totDemandROT);
		//System.out.println("This is the total for all: " + (totDemandGT +totDemandROT));
	
		IloNumExpr minimumGT = cplex.constant(totDemandGT * 0.98); 
		IloNumExpr minimumROT = cplex.constant(totDemandROT * 0.95); 
		cplex.addGe(serviceLevelGT, minimumGT, "Service Level constraint for General Toys");
		cplex.addGe(serviceLevelROT, minimumROT, "Service Level constraint for Recreational and Outdoor Toys");
		return cplex;
	}
	
	/**
	 * This method adds the general service level constraint to the model. 
	 * When an additional parameter overallServiceLevel is passed on it also inserts a constraint regarding the overall service level.
	 * by using a method the building of the model stays nice and tidy. 
	 * @param T The number of weeks we are solving the problem for
	 * @param sizes An array with the different size groups
	 * @param cplex The cplex object used to formulate the problem 
	 * @param z The constraint is implemented using the z variables of the problem formulation.
	 * @param data The data about the products.
	 * @param overallServiceLevel What the minimum overall service level should be. 
	 * @throws IloException When something goes wrong with the cplex. 
	 */
	public static IloCplex serviceLevelConstraintOverall(int T,String[] sizes, IloCplex cplex, IloNumVar[][][] z, 
			HashMap<String, HashMap<String, Product>> data, double overallServiceLevel) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		IloNumExpr serviceLevel = cplex.constant(0);
		double totDemand = 0;
		for (int t = 0; t < T; t++)	{
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						totDemand += prod.getSales(t); 
						serviceLevel = cplex.sum(serviceLevel, z[t][i][s]);
					}
				}
			}
		}
		IloNumExpr minimum = cplex.constant(totDemand * overallServiceLevel); 
		cplex.addGe(serviceLevel, minimum, "Service Level constraint for all products");
		return cplex;
	}
	
	/**
	 * This method prints the capacity each week for a given solution of the solve method
	 * All parameters come from the solve method
	 * @param T  Number of weeks
	 * @param sizes Array this the sizes
	 * @param cplex The cplex object
	 * @param x The IloCplex object with all the variables
	 * @param data The data on the products
	 * @throws IloException 
	 */
	public static ArrayList<ArrayList<Double>> capacityCheck(int T, String[] sizes, IloCplex cplex, IloNumVar[][][][] x, HashMap<String, HashMap<String, Product>> data ) throws IloException {
		ArrayList<Double> leftovercapacity0 = new ArrayList<Double>();
		ArrayList<Double> leftovercapacity1 = new ArrayList<Double>();
		
		double maxcap0 = 3000*15/100;
		double maxcap1 = 15000*15/100;
		
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		for (int t = 0; t < T; t++)	{
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
			
			System.out.println((cplex.getValue(capacity0) < maxcap0) + " Capasity at time " + t+ " for the small warehouse is :" + cplex.getValue(capacity0));
			System.out.println((cplex.getValue(capacity1) < maxcap1)+" Capasity at time " + t+ " for the big warehouse is :"+ cplex.getValue(capacity1));
			leftovercapacity0.add(maxcap0 - cplex.getValue(capacity0));
			leftovercapacity1.add(maxcap1 - cplex.getValue(capacity1));
		}
		
		ArrayList<ArrayList<Double>> result = new ArrayList<ArrayList<Double>>();
		result.add(leftovercapacity0);
		result.add(leftovercapacity1);
		return result;
	}
	
	/**
	 * This method returns the service level of a given solution. 
	 * @param T
	 * @param sizes
	 * @param cplex
	 * @param x
	 * @param data
	 * @throws IloException
	 */
	public static void serviceLevel(int T, String[] sizes, IloCplex cplex, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data ) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		IloNumExpr serviceLevelGT = cplex.constant(0);
		IloNumExpr serviceLevelROT = cplex.constant(0);
		IloNumExpr serviceLevel = cplex.constant(0);
		
		double totDemandGT = 0;
		double totDemandROT = 0;
		for (int t = 0; t < T; t++)	{
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						
						/*
						 * To see which demand is lost. I used for debugging. 
						if (prod.getSales(t) > Math.round(cplex.getValue(x[t][i][s][0])+ cplex.getValue(x[t][i][s][1]))) {
							System.out.println(prod.getChunk() + " " + prod.getSizeGroup() + " demand is: " + prod.getSales(t) + " sold is: " +(cplex.getValue(x[t][i][s][0])+ cplex.getValue(x[t][i][s][1])));
						}
						*/
						
						serviceLevel = cplex.sum(serviceLevel, z[t][i][s]);

						if (prod.getProductGroup().equals("General Toys")) { 
							totDemandGT += prod.getSales(t); 
							serviceLevelGT = cplex.sum(serviceLevelGT, z[t][i][s]);

						}else {
							totDemandROT += prod.getSales(t);
							serviceLevelROT = cplex.sum(serviceLevelROT, z[t][i][s]);
						}
					}
				}
			}
		}
		System.out.println((cplex.getValue(serviceLevel) / (totDemandGT + totDemandROT) >  0.98) +  " for overall the service level is:" + (cplex.getValue(serviceLevel) / (totDemandGT + totDemandROT)));

		System.out.println((cplex.getValue(serviceLevelGT) / totDemandGT >  0.98) +  " for the general Toys the service level is:" + cplex.getValue(serviceLevelGT) / totDemandGT);
		System.out.println((cplex.getValue(serviceLevelROT) / totDemandROT >  0.95)+ " for the Recreational and Outdoor Toys the service level is: :"+ cplex.getValue(serviceLevelROT)/ totDemandROT);
		
		
	}
	
	/**
	 * This method returns the service level of a given solution. 
	 * @param T
	 * @param sizes
	 * @param cplex
	 * @param x
	 * @param data
	 * @throws IloException
	 */
	public static void projectedOn2019(int T,String[] sizes, IloCplex cplex, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data2018, HashMap<String, HashMap<String, Product>> data2019 ) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data2018.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		int nullExceptions = 0;
		
		double totSupplyGT = 0;
		double totSupplyROT = 0;
		
		double totDemandGT = 0;
		double totDemandROT = 0;
		
		double revenue = 0;
		for (int t = 0; t < T; t++)	{
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data2019.get(chunkNames.get(i));
				if (chunk != null) {
					for (int s = 0; s < size; s++) {
						Product prod = chunk.get(sizes[s]);
						if (prod != null ) {
							try {
								revenue += Math.min(prod.getSales(t), cplex.getValue(z[t][i][s])) * prod.getAveragePrice(t);
								if (prod.getProductGroup().equals("General Toys")) { 
									totDemandGT += prod.getSales(t);
									totSupplyGT += Math.min(prod.getSales(t), cplex.getValue(z[t][i][s]));
									
								}else {
									totDemandROT += prod.getSales(t);
									totSupplyROT += Math.min(prod.getSales(t), cplex.getValue(z[t][i][s]));
								}
							}catch(NullPointerException e) {
								nullExceptions ++;
							}
						}
					}
				}
			}
		}
		System.out.println("The amount of null pointer eceptions that occured is: "+ nullExceptions);
		
		double serviceLevelGT = totSupplyGT / totDemandGT;
		double serviceLevelROT = totSupplyROT / totDemandROT;

		System.out.println( "The Revenue is: " + revenue);

		System.out.println( (serviceLevelGT >  0.98) +  " for the general Toys the service level is:" + serviceLevelGT);
		System.out.println( (serviceLevelROT >  0.95) +  " for the Recreational and Outdoor Toys the service level is:" + serviceLevelROT);
		
	}
	
	
	/**
	 * This method prints the service level per week. 
	 * @param T The number of weeks we are solving the problem for
	 * @param sizes An array with the different size groups
	 * @param cplex The cplex object used to formulate the problem 
	 * @param z The constraint is implemented using the z variables of the problem formulation.
	 * @param data The data about the products.
	 * @param printAll A boolean, if true it prints all values if false it prints the weeks where not all demand is met
	 * @throws IloException
	 */
	public static void serviceLevelWeekly(int T,String[] sizes, IloCplex cplex, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data,
			boolean printAll) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		
		
		for (int t = 0; t < T; t++)	{
			IloNumExpr serviceLevelGT = cplex.constant(0);
			IloNumExpr serviceLevelROT = cplex.constant(0);
			IloNumExpr serviceLevel = cplex.constant(0);
			double totDemandGT = 0;
			double totDemandROT = 0;
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						serviceLevel = cplex.sum(serviceLevel, z[t][i][s]);

						if (prod.getProductGroup().equals("General Toys")) { 
							totDemandGT += prod.getSales(t); 
							serviceLevelGT = cplex.sum(serviceLevelGT, z[t][i][s]);

						}else {
							totDemandROT += prod.getSales(t);
							serviceLevelROT = cplex.sum(serviceLevelROT, z[t][i][s]);
						}
					}
				}
			}
			if (printAll || (cplex.getValue(serviceLevel) / (totDemandGT + totDemandROT))  <  1) {
				System.out.println(cplex.getValue(serviceLevelGT) + " " +  totDemandGT );
				System.out.println(cplex.getValue(serviceLevelROT) + " " + totDemandROT );
				
				System.out.println((cplex.getValue(serviceLevelGT) / totDemandGT >  0.98) +  " At time " + t+ " for the general Toys the service level is:" + cplex.getValue(serviceLevelGT) / totDemandGT );
				System.out.println((cplex.getValue(serviceLevelROT) / totDemandROT >  0.95)+ " At time " + t+ " for the Recreational and Outdoor Toys the service level is: :"+ cplex.getValue(serviceLevelROT)/ totDemandROT);
				System.out.println((cplex.getValue(serviceLevel) / (totDemandGT + totDemandROT)  >  0.9999) +  " At time " + t+ " for the overall service level is:" + (cplex.getValue(serviceLevel) / (totDemandGT + totDemandROT)));
				System.out.println();
			}
			
		}
			
	}
	
	/** Methods with start as parameter **/ 
	/**
	 * This method returns the service level of a given solution. 
	 * @param T
	 * @param sizes
	 * @param cplex
	 * @param x
	 * @param data
	 * @throws IloException
	 */
	public static void getRevenue(int[]  T,  String[] sizes, IloCplex cplex, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		IloNumExpr serviceLevel = cplex.constant(0);
		
		double revenue = 0;

		for (int t = T[0]; t < T[1]; t++) {
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						
						/*
						 * To see which demand is lost. I used for debugging. 
						if (prod.getSales(t) > Math.round(cplex.getValue(x[t][i][s][0])+ cplex.getValue(x[t][i][s][1]))) {
							System.out.println(prod.getChunk() + " " + prod.getSizeGroup() + " demand is: " + prod.getSales(t) + " sold is: " +(cplex.getValue(x[t][i][s][0])+ cplex.getValue(x[t][i][s][1])));
						}
						*/

						serviceLevel = cplex.sum(serviceLevel, cplex.prod(z[t][i][s], prod.getAveragePrice(t)));
						revenue += cplex.getValue(z[t][i][s]) * prod.getAveragePrice(t);
					}
				}
			}
		}
		System.out.println(Math.round(cplex.getValue(serviceLevel))+  " The revenue using cplex" );
		System.out.println(Math.round(revenue)+  " The revenue using get value" );

	
	}
	
	/**
	 * This method returns the service level of a given solution. 
	 * @param T
	 * @param sizes
	 * @param cplex
	 * @param x
	 * @param data
	 * @throws IloException
	 */
	public static void serviceLevel(int[]  T,  String[] sizes, IloCplex cplex, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		IloNumExpr serviceLevelGT = cplex.constant(0);
		IloNumExpr serviceLevelROT = cplex.constant(0);
		IloNumExpr serviceLevel = cplex.constant(0);
		
		double totDemandGT = 0;
		double totDemandROT = 0;
		for (int t = T[0]; t < T[1]; t++) {
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						
						/*
						 * To see which demand is lost. I used for debugging. 
						if (prod.getSales(t) > Math.round(cplex.getValue(x[t][i][s][0])+ cplex.getValue(x[t][i][s][1]))) {
							System.out.println(prod.getChunk() + " " + prod.getSizeGroup() + " demand is: " + prod.getSales(t) + " sold is: " +(cplex.getValue(x[t][i][s][0])+ cplex.getValue(x[t][i][s][1])));
						}
						*/

						serviceLevel = cplex.sum(serviceLevel, z[t][i][s]);

						if (prod.getProductGroup().equals("General Toys")) { 
							totDemandGT += prod.getSales(t); 
							serviceLevelGT = cplex.sum(serviceLevelGT, z[t][i][s]);

						}else {
							totDemandROT += prod.getSales(t);
							serviceLevelROT = cplex.sum(serviceLevelROT, z[t][i][s]);
						}
					}
				}
			}
		}
		System.out.println((cplex.getValue(serviceLevel) / (totDemandGT + totDemandROT) >  0.98) +  " for overall the service level is:" + (cplex.getValue(serviceLevel) / (totDemandGT + totDemandROT)));

		System.out.println((cplex.getValue(serviceLevelGT) / totDemandGT >  0.98) +  " for the general Toys the service level is:" + cplex.getValue(serviceLevelGT) / totDemandGT);
		System.out.println((cplex.getValue(serviceLevelROT) / totDemandROT >  0.95)+ " for the Recreational and Outdoor Toys the service level is: :"+ cplex.getValue(serviceLevelROT)/ totDemandROT);
		
		if (T[0] == 45 && T[1] == 48) {
			System.out.println("Statistics for the whole year are:");
			System.out.println((cplex.getValue(serviceLevel) / (totDemandGT + totDemandROT) >  0.98) +  " for overall the service level for the whole year is:" + ((cplex.getValue(serviceLevel) + 5138862 )/ (totDemandGT + totDemandROT+ 5138862)));

			System.out.println((cplex.getValue(serviceLevelGT) / totDemandGT >  0.98) +  " for the general Toys the service level for the whole year is:" + (cplex.getValue(serviceLevelGT)+ 1997548) / (totDemandGT+ 1997548));
			System.out.println((cplex.getValue(serviceLevelROT) / totDemandROT >  0.95)+ " for the Recreational and Outdoor Toys the service level for the whole year is: :"+ (cplex.getValue(serviceLevelROT)+ 3141314)/ (totDemandROT+ 3141314));
			
		}
		
		
	}
	public static IloCplex addRelevanceScoreConstraintSum(int[]  T,  String[] sizes, IloCplex cplex, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data
			,double relevancePercentage) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		
		for (int t = T[0]; t < T[1]; t++) {
			IloNumExpr relevanceLevel = cplex.constant(0);
			double relevanceLevelTot = 0;

			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						
						/*
						 * To see which demand is lost. I used for debugging. 
						if (prod.getSales(t) > Math.round(cplex.getValue(x[t][i][s][0])+ cplex.getValue(x[t][i][s][1]))) {
							System.out.println(prod.getChunk() + " " + prod.getSizeGroup() + " demand is: " + prod.getSales(t) + " sold is: " +(cplex.getValue(x[t][i][s][0])+ cplex.getValue(x[t][i][s][1])));
						}
						*/
						
						relevanceLevel = cplex.sum(relevanceLevel, cplex.prod(z[t][i][s], prod.getRelevanceScore()));
						relevanceLevelTot += prod.getRelevanceScore() * prod.getSales(t);
						
					}
				}
			}
			cplex.addGe(relevanceLevel, relevanceLevelTot * relevancePercentage, "Capacity constraint for small warehouse");
		}

		return cplex;
	}
	
	
	public static IloCplex addRelevanceScoreConstraint(int[]  T,  String[] sizes, IloCplex cplex, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data
			,double relevancePercentage) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		for (int t = T[0]; t < T[1]; t++) {
			
			IloNumExpr relevanceLevel = cplex.constant(0);
			IloNumExpr relevanceLevelTot = cplex.constant(0);


			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						
						/*
						 * To see which demand is lost. I used for debugging. 
						if (prod.getSales(t) > Math.round(cplex.getValue(x[t][i][s][0])+ cplex.getValue(x[t][i][s][1]))) {
							System.out.println(prod.getChunk() + " " + prod.getSizeGroup() + " demand is: " + prod.getSales(t) + " sold is: " +(cplex.getValue(x[t][i][s][0])+ cplex.getValue(x[t][i][s][1])));
						}
						*/
						
						relevanceLevel = cplex.sum(relevanceLevel, cplex.prod(z[t][i][s], prod.getRelevanceScore()));
//						relevanceLevelTot += prod.getRelevanceScore() * prod.getSales(t);
						relevanceLevelTot = cplex.sum(relevanceLevelTot, z[t][i][s]);

					}
				}

			}
			cplex.addGe(relevanceLevel, cplex.prod(relevanceLevelTot, relevancePercentage), "Capacity constraint for small warehouse");

		}
		return cplex;
	}
	
	public static void relevanceScore(int[]  T,  String[] sizes, IloCplex cplex, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		double totRelevance = 0;
		double lostRelevance = 0;

		double totLost = 0;
		double totDemand = 0;
		
		double relevanceChunks = 0;
		double relevanceChunkstot = 0;
		


		for (int t = T[0]; t < T[1]; t++) {
			double totRelevance2 = 0;
			double lostRelevance2 = 0;

			double totLost2 = 0;
			double totDemand2 = 0;
			
			double relevanceChunks2 = 0;
			double relevanceChunkstot2 = 0;
			IloNumExpr relevanceLevel = cplex.constant(0);
			IloNumExpr relevanceLevelTot = cplex.constant(0);

			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						
						/*
						 * To see which demand is lost. I used for debugging. 
						if (prod.getSales(t) > Math.round(cplex.getValue(x[t][i][s][0])+ cplex.getValue(x[t][i][s][1]))) {
							System.out.println(prod.getChunk() + " " + prod.getSizeGroup() + " demand is: " + prod.getSales(t) + " sold is: " +(cplex.getValue(x[t][i][s][0])+ cplex.getValue(x[t][i][s][1])));
						}
						*/
						
						relevanceLevel = cplex.sum(relevanceLevel, cplex.prod(z[t][i][s], prod.getRelevanceScore()));
						relevanceLevelTot = cplex.sum(relevanceLevelTot, z[t][i][s]);

						
						lostRelevance += Math.max(0, prod.getSales(t) - cplex.getValue(z[t][i][s])) * prod.getRelevanceScore();
						totLost += Math.max(0, prod.getSales(t) - cplex.getValue(z[t][i][s]));
						
						totRelevance += cplex.getValue(z[t][i][s]) * prod.getRelevanceScore();
						totDemand += prod.getSales(t) * prod.getRelevanceScore(); 
//						totDemand += prod.getSales(t) ; 
						
						lostRelevance2 += Math.max(0, prod.getSales(t) - cplex.getValue(z[t][i][s])) * prod.getRelevanceScore();
						totLost2 += Math.max(0, prod.getSales(t) - cplex.getValue(z[t][i][s]));
						
						totRelevance2 += cplex.getValue(z[t][i][s]) * prod.getRelevanceScore();
						totDemand2 += prod.getSales(t) * prod.getRelevanceScore(); 
//						totDemand2 += prod.getSales(t); 
						
						relevanceChunkstot += prod.getRelevanceScore();
						relevanceChunkstot2 += prod.getRelevanceScore();

						
						if (cplex.getValue(z[t][i][s]) > 0) {
							relevanceChunks += prod.getRelevanceScore();
							relevanceChunks2 += prod.getRelevanceScore();
						}
					}
				}
			}
			System.out.println("For time: "+ t);
			System.out.println("The total relavance score is: "+ totRelevance2);
			System.out.println("The total relavance score is: "+ totDemand2);
			System.out.println("The avarage relavance score is: "+ (totRelevance2/ totDemand2));
			//System.out.println("The lost relavance score is: "+ lostRelevance2);
			//System.out.println("The lost demand is: "+ totLost2);
			//System.out.println("The relavance score based on 1 minus lost is: "+ (1- (lostRelevance2/totLost2)));
			//System.out.println("The relavanceChunks: "+ relevanceChunks2);
			//System.out.println("The relavanceChunks total: "+ relevanceChunkstot2);
			
			//System.out.println(Math.round(relevanceChunks2));
//			System.out.println(Math.round(totRelevance2) +" "+ Math.round(totDemand2) +" "+ Math.round(totRelevance2 * 100/ totDemand2));
//			System.out.println("The relevance Level is: "+ cplex.getValue(relevanceLevel));
//			System.out.println("The relevance Level is: "+ cplex.getValue(relevanceLevelTot));
//			System.out.println("The relevance Level is: "+ cplex.getValue(relevanceLevel)/cplex.getValue(relevanceLevelTot));



		}

//		System.out.println("The total relavance score is: "+ totRelevance);
//		System.out.println("The total relavance score is: "+ totDemand);
//		System.out.println("The avarage relavance score is: "+ (totRelevance/ totDemand));
//		System.out.println("The lost relavance score is: "+ lostRelevance);
//		System.out.println("The lost demand is: "+ totLost);
//		System.out.println("The relavance score based on 1 minus lost is: "+ (1- (lostRelevance/totLost)));
		
		System.out.println("The relavanceChunks: "+ relevanceChunks);
		System.out.println("The relavanceChunks total: "+ relevanceChunkstot);
		System.out.println("The relavanceChunks total per week :"+ relevanceChunkstot /52);


		System.out.println();
		System.out.println();
		System.out.println();
		System.out.println();
		
		
	}
	 /**
	 * 
	 * This method prints the capacity each week for a given solution of the solve method
	 * All parameters come from the solve method
	 * @param T  Number of weeks
	 * @param sizes Array this the sizes
	 * @param cplex The cplex object
	 * @param x The IloCplex object with all the variables
	 * @param data The data on the products
	 * @throws IloException 
	 */
	public static void capacityCheck(int[] T,String[] sizes, IloCplex cplex, IloNumVar[][][][] x, HashMap<String, HashMap<String, Product>> data ) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		for (int t = T[0]; t < T[1]; t++)	{
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
			
			System.out.println((cplex.getValue(capacity0) < 450) + " Capasity at time " + t+ " for the small warehouse is :" + cplex.getValue(capacity0));
			System.out.println((cplex.getValue(capacity1) < 2250)+" Capasity at time " + t+ " for the big warehouse is :"+ cplex.getValue(capacity1));
		}
	}
	
	/**
	 * This method prints the service level per week. 
	 * @param T The number of weeks we are solving the problem for
	 * @param sizes An array with the different size groups
	 * @param cplex The cplex object used to formulate the problem 
	 * @param z The constraint is implemented using the z variables of the problem formulation.
	 * @param data The data about the products.
	 * @param printAll A boolean, if true it prints all values if false it prints the weeks where not all demand is met
	 * @throws IloException
	 */
	public static void serviceLevelWeekly(int[] T,String[] sizes, IloCplex cplex, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data,
			boolean printAll) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		
		
		for (int t = T[0]; t < T[1]; t++) {
			IloNumExpr serviceLevelGT = cplex.constant(0);
			IloNumExpr serviceLevelROT = cplex.constant(0);
			IloNumExpr serviceLevel = cplex.constant(0);
			double totDemandGT = 0;
			double totDemandROT = 0;
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						serviceLevel = cplex.sum(serviceLevel, z[t][i][s]);

						if (prod.getProductGroup().equals("General Toys")) { 
							totDemandGT += prod.getSales(t); 
							serviceLevelGT = cplex.sum(serviceLevelGT, z[t][i][s]);

						}else {
							totDemandROT += prod.getSales(t);
							serviceLevelROT = cplex.sum(serviceLevelROT, z[t][i][s]);
						}
					}
				}
			}
			if (printAll || (cplex.getValue(serviceLevel) / (totDemandGT + totDemandROT))  <  1) {
				System.out.println(cplex.getValue(serviceLevelGT) + " " +  totDemandGT );
				System.out.println(cplex.getValue(serviceLevelROT) + " " + totDemandROT );
				
				System.out.println((cplex.getValue(serviceLevelGT) / totDemandGT >  0.98) +  " At time " + t+ " for the general Toys the service level is:" + cplex.getValue(serviceLevelGT) / totDemandGT );
				System.out.println((cplex.getValue(serviceLevelROT) / totDemandROT >  0.95)+ " At time " + t+ " for the Recreational and Outdoor Toys the service level is: :"+ cplex.getValue(serviceLevelROT)/ totDemandROT);
				System.out.println((cplex.getValue(serviceLevel) / (totDemandGT + totDemandROT)  >  0.9999) +  " At time " + t+ " for the overall service level is:" + (cplex.getValue(serviceLevel) / (totDemandGT + totDemandROT)));
				System.out.println();
			}
			
		}
			
	}
	/**
	 * This method adds the service level constraint per category to the model. 
	 * by using a method the building of the model stays nice and tidy.
	 * As stated in the assignment the service level for General toys should be at least 98% and 
	 * for Recreational and outdoor toys is must be at least 95%.
	 * @param T The number of weeks we are solving the problem for
	 * @param sizes An array with the different size groups
	 * @param cplex The cplex object used to formulate the problem 
	 * @param z The constraint is implemented using the z variables of the problem formulation.
	 * @param data The data about the products.
	 * @throws IloException When something goes wrong with the cplex. 
	 */
	public static IloCplex serviceLevelConstraintPerCategorie(int[] T,String[] sizes, IloCplex cplex, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		IloNumExpr serviceLevelGT = cplex.constant(0);
		IloNumExpr serviceLevelROT = cplex.constant(0);
		

		double totDemandGT = 0;
		double totDemandROT = 0;
		for (int t = T[0]; t < T[1]; t++)	{
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						if (prod.getProductGroup().equals("General Toys")) { 
							totDemandGT += prod.getSales(t); 
							serviceLevelGT = cplex.sum(serviceLevelGT, z[t][i][s]);

						}else {
							totDemandROT += prod.getSales(t);
							serviceLevelROT = cplex.sum(serviceLevelROT, z[t][i][s]);
						}
					}
				}
			}
		}
		//System.out.println("This is the total for GT: " + totDemandGT);
		//System.out.println("This is the total for ROT: " + totDemandROT);
		//System.out.println("This is the total for all: " + (totDemandGT +totDemandROT));
	
		IloNumExpr minimumGT = cplex.constant(totDemandGT * 0.98); 
		IloNumExpr minimumROT = cplex.constant(totDemandROT * 0.95); 
		cplex.addGe(serviceLevelGT, minimumGT, "Service Level constraint for General Toys");
		cplex.addGe(serviceLevelROT, minimumROT, "Service Level constraint for Recreational and Outdoor Toys");
		return cplex;
	}
	
	public static IloCplex serviceLevelConstraintPerCategoriePeekWeeks(int[] T,String[] sizes, IloCplex cplex, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		IloNumExpr serviceLevelGT = cplex.constant(0);
		IloNumExpr serviceLevelROT = cplex.constant(0);
		
		for (int t = T[0]; t < T[1]; t++)	{
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						if (prod.getProductGroup().equals("General Toys")) { 
							serviceLevelGT = cplex.sum(serviceLevelGT, z[t][i][s]);

						}else {
							serviceLevelROT = cplex.sum(serviceLevelROT, z[t][i][s]);
						}
					}
				}
			}
		}
		//System.out.println("This is the total for GT: " + totDemandGT);
		//System.out.println("This is the total for ROT: " + totDemandROT);
		//System.out.println("This is the total for all: " + (totDemandGT +totDemandROT));

		cplex.addGe(serviceLevelGT, 683062, "Service Level constraint for General Toys");
		cplex.addGe(serviceLevelROT, 693035, "Service Level constraint for Recreational and Outdoor Toys");
		return cplex;
	}
	
	/**
	 *This method adds the general service level constraint to the model. 
	 * When an additional parameter overallServiceLevel is passed on it also inserts a constraint regarding the overall service level.
	 * by using a method the building of the model stays nice and tidy. 
	 * @param T The number of weeks we are solving the problem for
	 * @param sizes An array with the different size groups
	 * @param cplex The cplex object used to formulate the problem 
	 * @param z The constraint is implemented using the z variables of the problem formulation.
	 * @param data The data about the products.
	 * @param overallServiceLevel What the minimum overall service level should be. 
	 * @throws IloException When something goes wrong with the cplex. 
	 */
	public static IloCplex serviceLevelConstraintOverall(int[] T,String[] sizes, IloCplex cplex, IloNumVar[][][] z, 
			HashMap<String, HashMap<String, Product>> data, double overallServiceLevel) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		IloNumExpr serviceLevel = cplex.constant(0);
		double totDemand = 0;
		for (int t = T[0]; t < T[1]; t++)	{
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						totDemand += prod.getSales(t); 
						serviceLevel = cplex.sum(serviceLevel, z[t][i][s]);
					}
				}
			}
		}
		IloNumExpr minimum = cplex.constant(totDemand * overallServiceLevel); 
		cplex.addGe(serviceLevel, minimum, "Service Level constraint for all products");
		return cplex;
	}
	/**
	 * This method solves the problem with adding a stricter overall service level each time.
	 * @param T Parameter passed on from the solve method.
	 * @param sizes Parameter passed on from the solve method.
	 * @param cplex Parameter passed on from the solve method.
	 * @param z Parameter passed on from the solve method.
	 * @param data Parameter passed on from the solve method.
	 * @param nbrSteps amount of times the problem needs to be solved
	 * @param sizeSteps The size of the increase of the overall service level between steps
	 * @param startValue The first value of the overall service level. 
	 * @throws IloException When a problem occurs. 
	 */
	public static void solveForDifferentServiceLevels(int[] T, String[] sizes, IloCplex cplex, IloNumVar[][][] z, 
			HashMap<String, HashMap<String, Product>> data, 
			int nbrSteps, double sizeSteps, double startValue,
			boolean useForRelevanceLevel) throws IloException {

		cplex.setOut(null);
		for (int j = 0; j < nbrSteps; j++) {
			double overallServiceLevel = startValue + j * sizeSteps;
			if (useForRelevanceLevel) {
				cplex = Solver.addRelevanceScoreConstraintSum(T, sizes, cplex, z, data, overallServiceLevel);
//				cplex = Solver.addRelevanceScoreConstraint(T, sizes, cplex, z, data, overallServiceLevel);

			}else{
				cplex = Solver.serviceLevelConstraintOverall(T, sizes, cplex, z, data, overallServiceLevel);
			}
			//System.out.println("The solution is now solved for overall service level: " + overallServiceLevel);

			cplex.solve();
			if (cplex.getStatus() == IloCplex.Status.Optimal)
			{
				//System.out.println("Found optimal solution!");
				//System.out.println("Objective = " + Math.round(cplex.getObjValue()));
				System.out.println(Math.round(cplex.getObjValue()));
//				System.out.println(Math.round(overallServiceLevel* 1000) + " "+ Math.round(cplex.getObjValue()));
			
				//capacityCheck(T, sizes, cplex, x, data);
				//serviceLevel(T, sizes, cplex, z, data);
				
			}
			else
			{
				System.out.println("No optimal solution found");
			}
		}
	}
	
	public static void solveForDifferentCapcityLevels(int[] T, String[] sizes, IloCplex cplex, IloNumVar[][][][] x, 
			HashMap<String, HashMap<String, Product>> data, 
			int nbrSteps, double sizeSteps, double startValue) throws IloException {

		cplex.setOut(null);
		for(int j = 0; j < nbrSteps; j++) {
			double capacityPercentage = startValue - j * sizeSteps;
			cplex = Solver.addCapacityConstraint(T, sizes, cplex, x, data, capacityPercentage, capacityPercentage);
			//System.out.println("The solution is now solved for overall capcity level: " + capacityPercentage);

			cplex.solve();
			if (cplex.getStatus() == IloCplex.Status.Optimal)
			{
				//System.out.println("Found optimal solution!");
				//System.out.println("Objective = " + Math.round(cplex.getObjValue()));
				System.out.println(Math.round(cplex.getObjValue()));
			
				//capacityCheck(T, sizes, cplex, x, data);
				//serviceLevel(T, sizes, cplex, x, data);
				
			}
			else
			{
				System.out.println("No optimal solution found");
			}
		}

		
	}
	
	public static void solveForDifferentCapcityLevels(int[] T, String[] sizes, IloCplex cplex, IloNumVar[][][][] x, 
			HashMap<String, HashMap<String, Product>> data, 
			int nbrSteps, double sizeSteps, double startValue,
			int nbrStepsBig, double sizeStepsBig, double startValueBig) throws IloException {

		cplex.setOut(null);
		for (int i = 0; i < nbrStepsBig; i++) {
			double capacityPercentageBig = startValue - i * sizeStepsBig;
			for (int j = 0; j < nbrSteps; j++) {
				double capacityPercentage = startValue - j * sizeSteps;
				cplex = Solver.addCapacityConstraint(T, sizes, cplex, x, data, capacityPercentage, capacityPercentageBig);
				//System.out.println("The solution is now solved for overall capcity level: " + capacityPercentage);
				
				cplex.solve();
				if (cplex.getStatus() == IloCplex.Status.Optimal)
				{
					//System.out.println("Found optimal solution!");
					//System.out.println("Objective = " + Math.round(cplex.getObjValue()));
					System.out.print(Math.round(cplex.getObjValue()));
				
					//capacityCheck(T, sizes, cplex, x, data);
					//serviceLevel(T, sizes, cplex, x, data);
					
				}
				else
				{
					System.out.println("No optimal solution found");
				}
			}
			System.out.println();
		}

		
	}
	
	public static IloCplex addCapacityConstraint(int[] T, String[] sizes, IloCplex cplex, IloNumVar[][][][] x, 
			HashMap<String, HashMap<String, Product>> data,
			double toysPercentageSmall, double toysPercentageBig) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		for (int t = T[0]; t < T[1]; t++) {
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
			cplex.addLe(capacity0, 3000 * toysPercentageSmall, "Capacity constraint for small warehouse");
			cplex.addLe(capacity1, 15000 * toysPercentageBig, "Capacity constraint for big warehouse");
		}
		return cplex;
	}
		
}
