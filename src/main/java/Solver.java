import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

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
		//read data for year 2018
		ArrayList<Integer> years = new ArrayList<Integer>(Arrays.asList(2018));
		ArrayList<HashMap<String, HashMap<String, Product>>> dt = readData(new ArrayList<Integer>(Arrays.asList(2018)));

		
		// Define all the size groups
		String[] sizes = {"XXXS", "XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL"};
		
		try
		{
			solve(52, sizes, dt.get(0));
		}
		catch (IloException e)
		{
			System.out.println("A Cplex exception occured: " + e.getMessage());
			e.printStackTrace();
		}	
	}
	
	public static ArrayList<HashMap<String, HashMap<String, Product>>> readData(ArrayList<Integer> years){
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
						
						cplex.addLe(x[t][i][s][0], cplex.prod(cap0 / prod.getAverageM3(t), cplex.sum(1, cplex.negative(y[i]))), "Constraints on warehouse goods allocation");
						cplex.addLe(x[t][i][s][1], cplex.prod(cap1 / prod.getAverageM3(t), y[i]), "Constraints on warehouse goods allocation");
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
		cplex = Solver.serviceLevelConstraint(T, sizes, cplex, z, data);
		
		
		// The last three parameters are nbr of steps, size of the steps, and value of first step. 
		// So: 2, 0.003, 0.98 means it is solved for an overall service level greater then 0.98 and 0.983
		//solveForDifferentServiceLevels(T, sizes, cplex, z, data, 10, 0.001, 0.982);
		
		// Export model
		//cplex.exportModel("Model.lp");
		
		
		// Solve the model.

		cplex.solve();

		// Query the solution.
		if (cplex.getStatus() == IloCplex.Status.Optimal)
		{
			System.out.println("Found optimal solution!");
			System.out.println("Objective = " + cplex.getObjValue());
			
			
			//capacityCheck(T, sizes, cplex, x, data);
			serviceLevel(T, sizes, cplex, z, data);
			projectedOn2019(T, sizes, cplex, z, data, data);
			//serviceLevelWeekly(T, sizes, cplex, x, data);
			
			//ArrayList<HashMap<String, HashMap<String, Product>>> dt2019 = readData(new ArrayList<Integer>(Arrays.asList(2019)));
			//sprojectedOn2019(T, sizes, cplex, z, data, dt2019.get(0));
			
			
			boolean writeSolutionToDucument = false;
			if (writeSolutionToDucument) {
				// This should write the data to an Excel file
				File file = new File("C:\\Users\\gijsm\\Documents\\DOCUMENTEN\\School\\SeminarCaseStudy\\SolutionFiles\\");
				
				CustomDataWriter cdw = new CustomDataWriter(file);
				try {
					cdw.writeSolutionToExcelFile(cplex, y, z, data, "Solution_SatisfactionLevels");
				} catch (UnknownObjectException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IloException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			/*		
			for (int t = 0; t < T; t++)	{
				for (int i = 0; i < n; i ++) {
					for (int s = 0; s < size; s++) {
						HashMap<String, Product> chunk = data.get(chunkNames.get(i));
						Product prod = chunk.get(sizes[s]);
						if (prod != null) {
							if (cplex.getValue(y[i]) <= 0.5) {
								System.out.println("We store " + chunkNames.get(i) + " in small warehouse with amount " + Math.round(cplex.getValue(x[t][i][s][0])) + " of size " + sizes[s] + " in week " + t);
							}
							else {
								System.out.println("We store " + chunkNames.get(i) + " in big warehouse with amount " + Math.round(cplex.getValue(x[t][i][s][1])) + " of size " + sizes[s] + " in week " + t);	
							}
						}
					}
				}
			}
			*/
		}
		else
		{
			System.out.println("No optimal solution found");
		}
	}
	
	public static void solveForDifferentServiceLevels(int T,String[] sizes, IloCplex cplex, IloNumVar[][][] z, 
			HashMap<String, HashMap<String, Product>> data, 
			int nbrSteps, double sizeSteps, double startValue) throws IloException {

		cplex.setOut(null);
		for (int j = 0; j < nbrSteps; j++) {
			double overallServiceLevel = startValue + j * sizeSteps;
			cplex = Solver.serviceLevelConstraintWithOverall(T, sizes, cplex, z, data, overallServiceLevel);
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
	 * This method adds the service level constraint to the model. 
	 * by using a method the building of the model stays nice and tidy. 
	 * @param T
	 * @param sizes
	 * @param cplex
	 * @param x
	 * @param data
	 * @throws IloException
	 */
	public static IloCplex serviceLevelConstraint(int T,String[] sizes, IloCplex cplex, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data) throws IloException {
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
	 * This method adds the service level constraint to the model. 
	 * When an additional parameter overallServiceLevel is passed on it also inserts a constraint regarding the overall service level.
	 * by using a method the building of the model stays nice and tidy. 
	 * @param T
	 * @param sizes
	 * @param cplex
	 * @param x
	 * @param data
	 * @throws IloException
	 */
	public static IloCplex serviceLevelConstraintWithOverall(int T,String[] sizes, IloCplex cplex, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data, double overallServiceLevel) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		IloNumExpr serviceLevelGT = cplex.constant(0);
		IloNumExpr serviceLevelROT = cplex.constant(0);
		IloNumExpr serviceLevel = cplex.constant(0);

		double totDemandGT = 0;
		double totDemandROT = 0;
		double totDemand = 0;
		for (int t = 0; t < T; t++)	{
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						totDemand += prod.getSales(t); 
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
		//System.out.println("This is the total for GT: " + totDemandGT);
		//System.out.println("This is the total for ROT: " + totDemandROT);
		//System.out.println("This is the total for all: " + (totDemandGT +totDemandROT));
		IloNumExpr minimumGT = cplex.constant(totDemandGT * 0.98); 
		IloNumExpr minimumROT = cplex.constant(totDemandROT * 0.95); 
		IloNumExpr minimum = cplex.constant(totDemand * overallServiceLevel); 
		cplex.addGe(serviceLevel, minimum, "Service Level constraint for all products");
		cplex.addGe(serviceLevelGT, minimumGT, "Service Level constraint for General Toys");
		cplex.addGe(serviceLevelROT, minimumROT, "Service Level constraint for Recreational and Outdoor Toys");
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
	public static void capacityCheck(int T,String[] sizes, IloCplex cplex, IloNumVar[][][][] x, HashMap<String, HashMap<String, Product>> data ) throws IloException {
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
			
			System.out.println((cplex.getValue(capacity0) < 450) + " Capasity at time " + t+ " for the small warehouse is :" + cplex.getValue(capacity0));
			System.out.println((cplex.getValue(capacity1) < 2250)+" Capasity at time " + t+ " for the big warehouse is :"+ cplex.getValue(capacity1));
		}
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
	public static void serviceLevel(int T,String[] sizes, IloCplex cplex, IloNumVar[][][] z, HashMap<String, HashMap<String, Product>> data ) throws IloException {
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
		//System.out.println(cplex.getValue(serviceLevelGT) + " " +  totDemandGT );
		//System.out.println(cplex.getValue(serviceLevelROT) + " " + totDemandROT );
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
	 * This method shows the week by week service level
	 * @param T
	 * @param sizes
	 * @param cplex
	 * @param x
	 * @param data
	 * @throws IloException
	 */
	public static void serviceLevelWeekly(int T,String[] sizes, IloCplex cplex, IloNumVar[][][][] x, HashMap<String, HashMap<String, Product>> data ) throws IloException {
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		int size = sizes.length;
		
		
		
		for (int t = 0; t < T; t++)	{
			IloNumExpr serviceLevelGT = cplex.constant(0);
			IloNumExpr serviceLevelROT = cplex.constant(0);
			double totDemandGT = 0;
			double totDemandROT = 0;
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {

						if (prod.getProductGroup().equals("General Toys")) { 
							totDemandGT += prod.getSales(t); 
							serviceLevelGT = cplex.sum(serviceLevelGT, x[t][i][s][0]);
							serviceLevelGT = cplex.sum(serviceLevelGT, x[t][i][s][1]);

						}else {
							totDemandROT += prod.getSales(t);
							serviceLevelROT = cplex.sum(serviceLevelROT, x[t][i][s][0]);
							serviceLevelROT = cplex.sum(serviceLevelROT, x[t][i][s][1]);
						}
					}
				}
			}
			System.out.println((cplex.getValue(serviceLevelGT) / totDemandGT >  0.98) +  " At time " + t+ " for the general Toys the service level is:" + cplex.getValue(serviceLevelGT) / totDemandGT );
			System.out.println((cplex.getValue(serviceLevelROT) / totDemandROT >  0.95)+ " At time " + t+ " for the Recreational and Outdoor Toys the service level is: :"+ cplex.getValue(serviceLevelROT)/ totDemandROT);
			
		}

		
		
	}
	
	
		
}
