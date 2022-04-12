import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

public class Simulation {
	
	public static void main(String args[]) throws IloException {
		
		
		// Define all the size groups
		String[] sizes = {"XXXS", "XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL"};
				
		HashMap<String, HashMap<String, Product>> dt = Simulation.readData();
		
		
		System.out.println("De data is geladen");

		
		HashMap<String, Object> parameters = new HashMap<String, Object>(); 
		//Set certain parameters for the model;
		
		//Demand options 
		parameters.put("usePlusXInSales", false); //Should not be put true, old option 
		parameters.put("usePoisson", false); //If true uses Poisson distribution otherwise uses normal
		
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
		parameters.put("exportOnlyAverageValues", false);
		parameters.put("fileName", "results"); //Does not work anymore
		parameters.put("filePath", "/Users/floris/Documents/Studie/Year_3_Block_4/Seminar/results/results_100_sim_testing.xlsx"); //Change this to the file path you want to 
		
		// Simulation options
		parameters.put("nbrSimulations" , 10);
		
		//model options
		parameters.put("addOrderingConstraint", false); //Does not work leave false
		parameters.put("addOrderingVariable", false); //Adds the two weeks ordering constraint weeks 44 -52
		parameters.put("addSmartTwoWeeksConstraint", false); //Adds the two weeks ordering constraint weeks 44 -52

		parameters.put("useModelWithTransfer", false); //Does not terminate


		
		ArrayList<String> chunkNames = new ArrayList<String>(dt.keySet());
		int n = chunkNames.size();
		
		HashMap<String, HashMap<String, Product>> smallData = new HashMap<String, HashMap<String, Product>> ();
		smallData.put(chunkNames.get(0), dt.get(chunkNames.get(3)));
		
		for (int j = 0; j < 10; j ++) {
//			System.out.println();
//			System.out.println("This is "+ j);
			HashMap<String, Product> chunk = dt.get(chunkNames.get(j));
			
			for (int i = 0; i < sizes.length; i ++) {
				Product prod = chunk.get(sizes[i]);
				if (prod != null) {
//					System.out.println("sales of size " + sizes[i]+ " is " + prod.getPredictedDemand(0));
				}
			}
		}
		
		
//		Simulation.solve2020WithTransfer(new int[]{0,52}, sizes, dt, parameters);
		if ((boolean) parameters.get("useModelWithTransfer")) {
			Simulation.solve2020WithTransfer(new int[]{44, 52}, sizes, dt, parameters);
		}else {
			solve2020(new int[]{0,52}, sizes, dt, parameters);
//			solve2020(new int[]{47,50}, sizes, smallData, parameters);

		}
		
		
	}
	
	public static void solve2020(int[] T, String[] sizes, HashMap<String, HashMap<String, Product>> data,
			HashMap<String, Object> parameters) throws IloException
	{
		// Create the model.
		IloCplex cplex = new IloCplex ();
//		cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0.00001);

		int maxDemandProduct = 60012 * 4;

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
		
		IloNumVar [][] order = new IloNumVar[T[1]][n];
		

		
		for (int i = 0; i < n; i ++) {
			y[i] = cplex.boolVar("y(" + chunkNames.get(i) + ")");
			for (int t = T[0]; t < T[1]; t++) {
				if ((boolean) parameters.get("addOrderingVariable") || (boolean) parameters.get("addSmartTwoWeeksConstraint")) { 
					order[t][i] = cplex.boolVar("order(" + chunkNames.get(i) +" , " + (t+ 1) +")");
				
				}
				for (int s = 0; s < size; s++) {
					HashMap<String, Product> chunk = data.get(chunkNames.get(i));
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						x[t][i][s][0] = cplex.intVar(0, Integer.MAX_VALUE, "x(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + "," + 0 + ")");
						x[t][i][s][1] = cplex.intVar(0, Integer.MAX_VALUE, "x(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + "," + 1 + ")");
						z[t][i][s] = cplex.intVar(0, Math.max(prod.getPredictedDemand(t), 0), "z(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + ")");
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
						objExpr = cplex.sum(objExpr, cplex.prod(prod.getAverageAveragePrice(), z[t][i][s]));
						// Use one of the two
//						cplex.addGe(cplex.sum(x[t][i][s][0], x[t][i][s][1]), cplex.sum(u[t][i][s], z[t][i][s]), "Constraints on goods in warehouse");
						if ((boolean) parameters.get("addSmartTwoWeeksConstraint")) {
							cplex.addGe(cplex.sum(x[t][i][s][0], x[t][i][s][1]), z[t][i][s], "Constraints on goods in warehouse");

						}else{
							cplex.addEq(cplex.sum(x[t][i][s][0], x[t][i][s][1]), z[t][i][s], "Constraints on goods in warehouse");
						}
						
						
						if ((boolean) parameters.get("addOrderingVariable")) { 
							cplex.addLe(x[t][i][s][0], cplex.prod(maxDemandProduct, order[t][i]), "Constraints on ordering in allowed weeks");
							cplex.addLe(x[t][i][s][1], cplex.prod(maxDemandProduct, order[t][i]), "Constraints on ordering in allowed weeks");
//													
						}
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
		
		if ((boolean) parameters.get("addSmartTwoWeeksConstraint")) {
			for (int t = T[0]; t < T[1]; t++) {
				if (t >= 44 &&  t + 1 != T[1]) { //Check if we are in November or December
					
					for (int i = 0; i < n; i ++) {
						HashMap<String, Product> chunk = data.get(chunkNames.get(i));
						boolean needToAddConstraint = true;
						for (int s = 0; s < size; s++) {
							Product prod = chunk.get(sizes[s]);
							if (prod != null) {
								IloNumExpr twoWeeksConstraintLeft = cplex.constant(0);
								IloNumExpr twoWeeksConstraintRight = cplex.constant(0);
								
								twoWeeksConstraintLeft = cplex.sum(twoWeeksConstraintLeft, x[t][i][s][0]);
								twoWeeksConstraintLeft = cplex.sum(twoWeeksConstraintLeft, x[t][i][s][1]);
								
								twoWeeksConstraintLeft = cplex.diff(twoWeeksConstraintLeft, x[t + 1][i][s][0]);
								twoWeeksConstraintLeft = cplex.diff(twoWeeksConstraintLeft, x[t + 1][i][s][1]);

								twoWeeksConstraintRight = cplex.sum(twoWeeksConstraintRight, cplex.prod(prod.getPredictedDemand(t), order[t + 1][i]));
								twoWeeksConstraintRight = cplex.diff(twoWeeksConstraintRight, prod.getPredictedDemand(t));
								twoWeeksConstraintRight = cplex.sum(twoWeeksConstraintRight, z[t][i][s]);
								twoWeeksConstraintRight = cplex.diff(twoWeeksConstraintRight, cplex.prod(maxDemandProduct, cplex.diff(1, order[t+1][i])));

								
								cplex.addGe(twoWeeksConstraintLeft,twoWeeksConstraintRight,  "No consecutive weeks ordering");
								
								
								if (needToAddConstraint) {
									needToAddConstraint = false;
									IloNumExpr orderConstraint = cplex.constant(0);
									orderConstraint = cplex.sum(orderConstraint, order[t][i]);
									orderConstraint = cplex.sum(orderConstraint, order[t + 1][i]);
									cplex.addGe(orderConstraint, 1,  "No consecutive weeks ordering");
								}
							}
						}
					}
					
				}
			}
		}
		
		
		
		//Add capacity constraint for small and big warehouse
		cplex = Solver.addCapacityConstraint2020(T, sizes, cplex, x, data, 0.15, 0.15);
		
		if ((boolean) parameters.get("addOrderingVariable")) {
			cplex = Solver.addOrderingConstraint2020(T, sizes, cplex, order, data);
		}

		
		//Add relevance score constraint
//		cplex = Solver.addRelevanceScoreConstraint(T, sizes, cplex, z, data, 0.53);
//		cplex = Solver.addRelevanceScoreConstraintSum(T, sizes, cplex, z, data, 0.925);
		
		//Add the service level constraints
//		cplex = Solver.serviceLevelConstraintPerCategorie(T, sizes, cplex, z, data);
//		cplex = Solver.serviceLevelConstraintPerCategoriePeekWeeks(T, sizes, cplex, z, data);
//		cplex = Solver.serviceLevelConstraintOverall(T, sizes, cplex, z, data, 0.9856);
		
		
		
		// The last three parameters are nbr of steps, size of the steps, and value of first step. 
		// So: 2, 0.003, 0.98 means it is solved for an overall service level greater then 0.98 and 0.983
//		solveForDifferentServiceLevels(T, sizes, cplex, z, data, 30, 0.001, 0.925, false);
		
		//Use to solve for different relevance scores
//		solveForDifferentServiceLevels(T, sizes, cplex, z, data, 50, 0.001, 0.91, true);
		
		//Solver.solveForDifferentCapcityLevels(T, sizes, cplex, x, data, 40, 0.001, 0.20);

		
		/** This is the end of the model building part**/ 
		
		// Export model
//		cplex.exportModel("Model.lp");
		
		// Solve the model.
		cplex.solve();

		// Query the solution.
		if (cplex.getStatus() == IloCplex.Status.Optimal)
		{
			//Below are some different options to analyze the solution
			
			System.out.println("Found optimal solution!");
			System.out.println("Objective = " + cplex.getObjValue());
			System.out.println(Math.round(cplex.getObjValue()));
			
			
			// Copy the solution into a different array
			int [][][] zSolution = new int[T[1]][n][size];
			int [][][][] xSolution = new int[T[1]][n][size][2];
			int [] ySolution = new int[n];
			
			for (int i = 0; i < n; i ++) {
				ySolution[i] = (int) cplex.getValue(y[i]);
				for (int t = T[0]; t < T[1]; t++) {
					for (int s = 0; s < size; s++) {
						HashMap<String, Product> chunk = data.get(chunkNames.get(i));
						Product prod = chunk.get(sizes[s]);
						if (prod != null) {
							zSolution[t][i][s] = (int) Math.round(cplex.getValue(z[t][i][s]));
							xSolution[t][i][s][0] = (int) cplex.getValue(x[t][i][s][0]);
							xSolution[t][i][s][1] = (int) cplex.getValue(x[t][i][s][1]);
						}
					}
				}
			}
			
			
			/** Set output parameters here */
			
			
			//Calculate service level
			int totDemand = 0;
			int fulfilledDemand = 0;
			
			double totCapasityUsed = 0;
			
			
			for (int t = T[0]; t < T[1]; t++) {
				double capasityUsed = 0;
				int totDemandWeekly = 0;
				int fulfilledDemandWeekly = 0;
				
				for (int i = 0; i < n; i ++) {
					for (int s = 0; s < size; s++) {
						HashMap<String, Product> chunk = data.get(chunkNames.get(i));
						Product prod = chunk.get(sizes[s]);
						if (prod != null) {
							totDemand += prod.getPredictedDemand(t);
							fulfilledDemand += zSolution[t][i][s];
							
							totDemandWeekly += prod.getPredictedDemand(t);
							fulfilledDemandWeekly += zSolution[t][i][s];
							
							
							if (prod.getPredictedDemand(t) < zSolution[t][i][s]) {
								System.out.println("Demand small then amount sold for " + chunkNames.get(i) + sizes[s]+ " in week " + t);
								System.out.println("Demand is  " + prod.getPredictedDemand(t) + " and sales is  " + zSolution[t][i][s] );
							}
							
							totCapasityUsed += zSolution[t][i][s] * prod.getAverageAverageM3();
							capasityUsed += zSolution[t][i][s] * prod.getAverageAverageM3();
						}
					}
				}
				if ((boolean) parameters.get("showWeeklyCapasity")) {
					System.out.println("The capacity used in week " + t + "  is: "+ capasityUsed );
					System.out.println("The percentage of capacity used in week " + t + "is: "+ capasityUsed / 2700  );
					System.out.println();
				}
				
				if ((boolean) parameters.get("showWeeklyServiceLevel")) {

					System.out.println("The amount of goods sold in week " + t + "is: "+fulfilledDemandWeekly );
					System.out.println("The amount of goods demanded in week " + t + " is: "+totDemandWeekly );
					System.out.println("The servicelevel in week " + t + " is: "+ ((double) fulfilledDemandWeekly /totDemandWeekly) );
					System.out.println();

				}
			}
			
			System.out.println("The amount of goods sold is: "+fulfilledDemand );
			System.out.println("The amount of goods demanded is: "+totDemand );
			System.out.println("The servicelevel over the whole period is: "+ ((double) fulfilledDemand /totDemand) );
			
			
			System.out.println("The capacity used is: "+totCapasityUsed );
			System.out.println("The percentage of capacity used is: "+ totCapasityUsed / (2700 * T[1]) );

			
			
			/** Running the simulation using the ordering levels from the solution */
			Random r = new Random(1234);
			Simulation.getSimulationResults(T, sizes, data, zSolution, parameters);
			
		}
		else
		{
			System.out.println("No optimal solution found");
		}
	}
	
	public static void getSimulationResults(int[] T, String[] sizes, HashMap<String, HashMap<String, Product>> data,
			int [][][] zSolution, HashMap<String, Object> parameters) throws IloException {
		Random r = new Random(1234);
		int sizeOfResultsSimulation = 4 + (T[1] - T[0]) * 4;
		
		ArrayList<ArrayList<Double>> results = new ArrayList<ArrayList<Double>>();
		
		for (int i = 0; i < (int) parameters.get("nbrSimulations"); i++) {
			results.add(Simulation.simulationMain(T, sizes, data, zSolution, r, parameters));
		
		}
		
		//Print results
		HashMap<String, Double> averageResults = new HashMap<String, Double>(); 
		for (int i = 0; i < sizeOfResultsSimulation; i++) {
			double sum  = 0;
			int count = 0;
			for (int j = 0; j < (int) parameters.get("nbrSimulations"); j++) {
				sum += results.get(j).get(i);
				count ++;
				
			}
			
			double average = sum / count;
			switch(i) {
				case 0: 
					averageResults.put("Revenue", average);
					break;
				case 1: 
					averageResults.put("Products sold", average);
					break;
				case 2: 
					averageResults.put("Products demanded ", average);
					break;
				case 3: 
					averageResults.put("Service level whole year", average);
					break; 
			}
			if ( i >= 4 && i < 4 + T[1] - T[0]) {
				averageResults.put("Service level for week " + (T[0] + i - 3), average);
			}
			
			if ( i >= 4 + (T[1] - T[0]) * 1 && i < 4 + (T[1] - T[0]) * 2) {
				averageResults.put("Revenue for week " + (T[0] + i - 3 - (T[1] - T[0]) * 1), average);
			}
			
			if ( i >= 4 + (T[1] - T[0]) * 2 && i < 4 + (T[1] - T[0]) * 3) {
				averageResults.put("Capacity for week " + (T[0] + i - 3 - (T[1] - T[0]) * 2), average);
			}
			
			int factor = 3;
			if ( i >= 4 + (T[1] - T[0]) * factor && i < 4 + (T[1] - T[0]) * (factor + 1)) {
				averageResults.put("Relevance Score for week " + (T[0] + i - 3 - (T[1] - T[0]) * factor), average);
			}
		
		}

		
		
		if ((boolean) parameters.get("printExcelFormatSimulationResults")) {
			for (int i = 0; i < (int) parameters.get("nbrSimulations"); i++) {
	
				for (int j = 0; j < sizeOfResultsSimulation; j ++) {
					if (j ==0) {
						System.out.print(Math.round(results.get(i).get(j)));
	
					}else{
						System.out.print(results.get(i).get(j));
					}
					if (j != sizeOfResultsSimulation - 1) {
						System.out.print(" ");
					}
				}
				System.out.println();
	
			}
		}

		if ((boolean) parameters.get("exportSimulationResults")) {
			try {
				Simulation.writeToExcel(results, parameters);
				System.out.println("The results are written to an excel sheet");
	
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		if ((boolean) parameters.get("exportOnlyAverageValues")) {
			try {
				Simulation.writeToExcelAverage(averageResults, parameters);
				System.out.println("The results are written to an excel sheet");
	
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}		
		
		
	}
	
	/**
	 * Runs 1 simulation for a given order up to level 
	 * @param T Array with start and end week of the period
	 * @param sizes Array with all the size groups
	 * @param data All the data on the products
	 * @param z The ordering up to levels for each product
	 * @param r A Random object used to generate random sales data
	 * @return 
	 * @throws IloException
	 */
	public static ArrayList<Double> simulationMain(int[] T, String[] sizes,
			HashMap<String, HashMap<String, Product>> data,
			int[][][] z, Random r, HashMap<String, Object> parameters) throws IloException {
		
		int size = sizes.length;
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		
		int[][][] storage = new int[T[1]][n][size];
		//for every week
			//get ordering up to level off products
			//Calculate demand for that week per product
			//update some stats
		
		//Aggregate variables
		int orders = 0; 
		
		int totalOrdered = 0;
		int totalThrewAway = 0;
		
		double holdingCost = 0; //The total holding cost
		double revenue = 0; //Revenue of the time period
		double revenueTheoretical = 0; //The maximum revenue that could have been made
		double relevanceSoldProducts = 0; // The total relevance score of all the products sold
		double relevanceAllProducts = 0;	// the total relevance score of all products demanded.
		int productsSold = 0; //The amount of products sold
		int totalDemand = 0; // the total amount of products demanded.
		
		ArrayList<Double> weeklyServiceLevel = new ArrayList<Double>();
		ArrayList<Double> weeklyRevenue = new ArrayList<Double>();
		ArrayList<Double> weeklyCapacity = new ArrayList<Double>();
		ArrayList<Double> weeklyRelevanceScore = new ArrayList<Double>();
		
		
		for (int t = T[0]; t < T[1]; t++) {
			
			int salesWeek =0;
			int demandWeek =0;
			double revenueWeek = 0;
			double capacityWeek = 0;
			double relevanceScoreWeek = 0;

			
			/** Ordering the new products */
			
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				boolean orderForChunk = false; //Flag to keep track if an order was placed for this chunk
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					
					if (prod != null) {
						if (storage[t][i][s] < z[t][i][s]) {
							//We need to place an order
							orderForChunk = true;
//							System.out.println("we ordered "+ ( -storage[t][i][s] + z[t][i][s])+" at time  "+ t+" for chunk "+ chunkNames.get(i)+"of size "+ sizes[s]);		
							totalOrdered += z[t][i][s] - storage[t][i][s];
							storage[t][i][s]  = z[t][i][s];
						}else if(storage[t][i][s] > z[t][i][s]) {
//							System.out.println("we threw away  "+ ( storage[t][i][s] - z[t][i][s])+" at time  "+ t+" for chunk "+ chunkNames.get(i)+"of size "+ sizes[s]);		
							totalThrewAway += storage[t][i][s] -z[t][i][s] ;
							storage[t][i][s]  = z[t][i][s];
						}
						capacityWeek += storage[t][i][s] * prod.getAverageAverageM3();
						relevanceScoreWeek+= storage[t][i][s] * prod.getRelevanceScore();
						
					}
				}
				//Check if an order is placed if so add to the total amount of orders for ordering cost.
				if (orderForChunk) {
					orders ++;
				}
			}
			
			int demand;
			/** Selling products */
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						//Get the demand for this products
						if ((boolean) parameters.get("usePlusXInSales")) {
							demand = prod.pullRandomSalesFloris(t, r);
						}else if ((boolean) parameters.get("usePoisson") ) {
							demand = prod.pullRandomSalesPoisson(t, r);
						}else {
							demand = prod.pullRandomSales(t, r);
						}
						
						demandWeek += demand;
						
						if (demand <= storage[t][i][s]) {
							//Update revenue
							revenue += prod.getAveragePrice(t) * demand;
							revenueWeek += prod.getAveragePrice(t) * demand;
							revenueTheoretical += prod.getAveragePrice(t) * demand;
							//Update relevance score
							relevanceSoldProducts += prod.getRelevanceScore() * demand;
							relevanceAllProducts += prod.getRelevanceScore() * demand;
							// update amount of products
							productsSold += demand;
							totalDemand += demand;
							
							salesWeek += demand;
							
							//Update the amount in storage
							storage[t][i][s] -= demand;
						}else {
							//Update revenue
							revenueWeek += prod.getAveragePrice(t) * storage[t][i][s];
							revenue += prod.getAveragePrice(t) * storage[t][i][s];
							revenueTheoretical += prod.getAveragePrice(t) * demand;
							//Update relevance score
							relevanceSoldProducts += prod.getRelevanceScore() * storage[t][i][s];
							relevanceAllProducts += prod.getRelevanceScore() * demand;
							// update amount of products
							productsSold += storage[t][i][s];
							totalDemand += demand;
							
							salesWeek += storage[t][i][s];
							//Update the amount in storage
							storage[t][i][s] = 0;
						}
					}
				}
			}
			weeklyServiceLevel.add(((double) salesWeek )/ demandWeek);
			weeklyRevenue.add(revenueWeek);
			weeklyCapacity.add(capacityWeek);
			weeklyRelevanceScore.add(relevanceScoreWeek);
			
			/** Moving on to the next week*/ 
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null && t != T[1] - 1 ) {
						storage[t + 1][i][s] = storage[t][i][s]; //Set remaining products as starting inventory of next week
						holdingCost += storage[t][i][s] * prod.getUnitStorageCost()  * 7; //Add holding cost for the goods that are held for more then a week
					}
				}
			}
		}
		
		if ((boolean) parameters.get("printSimulationResults")) {
			//Print interesting information on the simulation
			//Print interesting information on the simulation
//			System.out.println("The amount of orders is: " + orders + " so ordering cost is: " + orders * 10 );	
			System.out.println("The revenue for this period is: " + revenue);	
//			System.out.println("The holding cost for this period is: " + holdingCost);	
//			System.out.println("The profit for this period is: " + (revenue - holdingCost));
//			System.out.println();

			System.out.println("There are " + totalOrdered + " products ordered and " + totalThrewAway+ " products thown away");	
			System.out.println("The amount of products sold is: " + productsSold);	
			System.out.println("The amount of products demanded is: " + totalDemand);	
			System.out.println("The service level is: " + ((double)productsSold / totalDemand ));
			System.out.println();
		}
		

		ArrayList<Double> results = new ArrayList<Double>();
		results.add(revenue);
		results.add((double) productsSold);
		results.add((double) totalDemand);
		results.add((double)productsSold / totalDemand);
		results.addAll(weeklyServiceLevel);
		results.addAll(weeklyRevenue);
		results.addAll(weeklyCapacity);
		results.addAll(weeklyRelevanceScore);
		
		return results;
	}	
	

	
	/**
	 * This method reads the data for the years given and returns them in format implemented by Gijs
	 * @param years An arrayList with the years for which the data needs to be retrieved. 
	 * @return The data
	 */
	public static HashMap<String, HashMap<String, Product>> readData(){
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
		
		HashMap<String, HashMap<String, Product>> dt = new HashMap<String, HashMap<String, Product>>();
				
		try {
			CustomDataReader cdm = new CustomDataReader(data, relevanceScore, warehouseCost);
			 return cdm.readDataCombined();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void writeToExcel(ArrayList<ArrayList<Double>> results, HashMap<String, Object> parameters) throws IOException {
		// workbook object
        XSSFWorkbook workbook = new XSSFWorkbook();
  
        // spreadsheet object
        XSSFSheet spreadsheet
            = workbook.createSheet("Results Simulation");
  
        // creating a row object
        XSSFRow row;
  
  
        int rowid = 1;
  
        // writing the data into the sheets...
  
        for (ArrayList<Double> result : results) {
        	row = spreadsheet.createRow(rowid++);
        	int cellid = 0;
        	for (double d : result) {
        		Cell cell = row.createCell(cellid++);
                cell.setCellValue(d);
        	}
        }
        
  
        // .xlsx is the format for Excel Sheets...
        // writing the workbook into the file...
        FileOutputStream out = new FileOutputStream(
        		new File((String) parameters.get("filePath")));

        workbook.write(out);
        out.close();
    
	}
	
	public static void writeToExcelAverage(HashMap<String, Double> results, HashMap<String, Object> parameters) throws IOException {
		// workbook object
        XSSFWorkbook workbook = new XSSFWorkbook();
  
        // spreadsheet object
        XSSFSheet spreadsheet
            = workbook.createSheet("Results Simulation");
  
        // creating a row object
        XSSFRow row;
  
  
        int rowid = 1;
        
        /*
        int rowidRev = 5;
        int rowidCap = 5;
        int rowidRel = 5;
        int rowidSer = 5;
  
        int cellid = 0;
        int cellidRev = 0;
        int cellidCap = 0;
        int cellidRel= 0;
        int cellidSer = 0;
        
        */
        
        // writing the data into the sheets...
        row = spreadsheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue("Name");
        cell = row.createCell(1);
        cell.setCellValue("Week number");
        cell = row.createCell(2);
        cell.setCellValue("Value");

        
        for (String s : results.keySet()) {
        	row = spreadsheet.createRow(rowid++);
        	int cellid = 0;
        	
        	 
        	//Add name of variable to excel
        	Cell cellName = row.createCell(cellid++);
            cellName.setCellValue(s);
            
          //Add week number
            if (s.contains("for week")) {
            	cell = row.createCell(cellid++);
            	String[] temp = s.split(" ");
                cell.setCellValue(Integer.parseInt(temp[temp.length - 1]));
            }else {
            	cell = row.createCell(cellid++);
                cell.setCellValue(0);
            }
            
            //Add value to excel 
            Cell cellValue = row.createCell(cellid++);
            cellValue.setCellValue(results.get(s));
            
            
            
            
        }
  
        // .xlsx is the format for Excel Sheets...
        // writing the workbook into the file...
        FileOutputStream out = new FileOutputStream(
        		new File((String) parameters.get("filePath")));

        workbook.write(out);
        out.close();
    
	}
	
	public static void solve2020WithTransfer(int[] T, String[] sizes, HashMap<String, HashMap<String, Product>> data,
			HashMap<String, Object> parameters) throws IloException
	{
		// Create the model.
		IloCplex cplex = new IloCplex ();
//		cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0.00001);

		int maxDemandProduct = 60012;

		//double cap0 = 3000*15/100;
		//double cap1 = 15000*15/100;
		
		int size = sizes.length;
		ArrayList<String> chunkNames = new ArrayList<String>(data.keySet());
		int n = chunkNames.size();
		
		// Create the variables and their domain restrictions.
		IloNumVar [][][][] x = new IloNumVar[T[1]][n][size][2];
		IloNumVar [][][][] z = new IloNumVar[T[1]][n][size][2];
		IloNumVar [][][][] u = new IloNumVar[T[1]][n][size][2];
		IloNumVar [] y = new IloNumVar[n];
		
		IloNumVar [][] order = new IloNumVar[T[1]][n];
		

		
		for (int i = 0; i < n; i ++) {
			y[i] = cplex.boolVar("y(" + chunkNames.get(i) + ")");
			for (int t = T[0]; t < T[1]; t++) {
				if ((boolean) parameters.get("addOrderingVariable")) { 
					order[t][i] = cplex.boolVar("order(" + chunkNames.get(i) + ")");
				
				}
				for (int s = 0; s < size; s++) {
					HashMap<String, Product> chunk = data.get(chunkNames.get(i));
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						for (int j = 0; j < 2; j++) {
							x[t][i][s][j] = cplex.intVar(0, Integer.MAX_VALUE, "x(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + "," + j + ")");
							z[t][i][s][j] = cplex.intVar(0, Math.max(prod.getPredictedDemand(t), 0), "z(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + "," + j+ ")");
							u[t][i][s][j] = cplex.intVar(0, Integer.MAX_VALUE, "u(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + "," + j+ ")");

						}
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
					cplex.addEq(u[T[0]][i][s][0], 0, "Initial storage level 0");
					cplex.addEq(u[T[0]][i][s][1], 0, "Initial storage level 1");
					for (int t = T[0]; t < T[1]; t++) {
						objExpr = cplex.sum(objExpr, cplex.prod(prod.getAverageAveragePrice(), cplex.sum( z[t][i][s][0], z[t][i][s][1])));
						// Use one of the two
						if (t + 1 != T[1]) {
							for (int j = 0; j < 2; j++) {
								IloNumExpr temp = cplex.constant(0);
								temp = cplex.sum(x[t][i][s][j], cplex.diff(u[t][i][s][j], z[t][i][s][j]));
								cplex.addLe(u[t+1][i][s][j],temp, "Constraints on goods in warehouse " + j);

							}

						}else {
							for (int j = 0; j < 2; j++) {
								cplex.addLe(z[t][i][s][j],cplex.sum(x[t][i][s][j],u[t][i][s][j]), "Constraints on goods in warehouse " + j+"Final week");

							}						}
//						cplex.addGe(cplex.sum(x[t][i][s][0], x[t][i][s][1]), cplex.sum(u[t][i][s], z[t][i][s]), "Constraints on goods in warehouse");
//						cplex.addEq(cplex.sum(x[t][i][s][0], x[t][i][s][1]), z[t][i][s], "Constraints on goods in warehouse");
						
						
						if ((boolean) parameters.get("addOrderingVariable")) { 
							cplex.addLe(x[t][i][s][0], cplex.prod(maxDemandProduct, order[t][i]), "Constraints on ordering in allowed weeks");
							cplex.addLe(x[t][i][s][1], cplex.prod(maxDemandProduct, order[t][i]), "Constraints on ordering in allowed weeks");
//													
						}
						cplex.addLe(cplex.sum(x[t][i][s][0], cplex.sum(u[t][i][s][0], z[t][i][s][0])), cplex.prod(3 * maxDemandProduct, cplex.sum(1, cplex.negative(y[i]))), "Constraints on warehouse goods allocation");
						cplex.addLe(cplex.sum(x[t][i][s][1], cplex.sum(u[t][i][s][1], z[t][i][s][1])), cplex.prod(3 * maxDemandProduct, y[i]), "Constraints on warehouse goods allocation");
//						if (t + 1 != T) {
//							cplex.addEq(cplex.sum(u[t + 1][i][s], z[t][i][s]), cplex.sum(x[t][i][s][0], x[t][i][s][1]), "Inventory at the beginning of the period");
//						}
					}
				}
			}
		}
		cplex.addMaximize(objExpr);
		
		
		
		//Add capacity constraint for small and big warehouse
		for (int t = T[0]; t < T[1]; t++) {
			IloNumExpr capacity0 = cplex.constant(0);
			IloNumExpr capacity1 = cplex.constant(0);
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						capacity0 = cplex.sum(capacity0, cplex.prod(prod.getAverageAverageM3(), cplex.sum(x[t][i][s][0],u[t][i][s][0]) ));
						capacity1 = cplex.sum(capacity1, cplex.prod(prod.getAverageAverageM3(), cplex.sum(x[t][i][s][1],u[t][i][s][1]) ));
					}
				}
			}
			cplex.addLe(capacity0, 3000 * 0.15, "Capacity constraint for small warehouse");
			cplex.addLe(capacity1, 15000 * 0.15, "Capacity constraint for big warehouse");
		}
		
		
		if ((boolean) parameters.get("addOrderingVariable")) {
			cplex = Solver.addOrderingConstraint2020(T, sizes, cplex, order, data);
		}

		
		
		/** This is the end of the model building part**/ 
		
		// Export model
		cplex.exportModel("Model.lp");
		
		// Solve the model.
		cplex.solve();

		// Query the solution.
		if (cplex.getStatus() == IloCplex.Status.Optimal)
		{
			//Below are some different options to analyze the solution
			
			System.out.println("Found optimal solution!");
			System.out.println("Objective = " + cplex.getObjValue());
			System.out.println(Math.round(cplex.getObjValue()));
			
			
			// Copy the solution into a different array
			int [][][] zSolution = new int[T[1]][n][size];
			int [][][][] xSolution = new int[T[1]][n][size][2];
			int [] ySolution = new int[n];
			
			for (int i = 0; i < n; i ++) {
				ySolution[i] = (int) cplex.getValue(y[i]);
				for (int t = T[0]; t < T[1]; t++) {
					for (int s = 0; s < size; s++) {
						HashMap<String, Product> chunk = data.get(chunkNames.get(i));
						Product prod = chunk.get(sizes[s]);
						if (prod != null) {
							zSolution[t][i][s] =  (int) Math.round( cplex.getValue(z[t][i][s][0]) + cplex.getValue(z[t][i][s][1]));
							xSolution[t][i][s][0] = (int) Math.round(cplex.getValue(x[t][i][s][0]));
							xSolution[t][i][s][1] = (int) Math.round(cplex.getValue(x[t][i][s][1]));
							
							if ((boolean) parameters.get("printEveryVariable")) {
	
								System.out.println("this is for week "+ t);
								System.out.println("x 0 is" + cplex.getValue(x[t][i][s][0]));
								System.out.println("x 1 is" + cplex.getValue(x[t][i][s][1]));
								System.out.println("z 0 is" + cplex.getValue(z[t][i][s][0]));
								System.out.println("z 1 is" + cplex.getValue(z[t][i][s][1]));
								System.out.println("u 0 is" + cplex.getValue(u[t][i][s][0]));
								System.out.println("u 1 is" + cplex.getValue(u[t][i][s][1]));
							}
							
							if ((boolean) parameters.get("printEveryVariableIfLost") && (int) Math.round( cplex.getValue(z[t][i][s][0]) + cplex.getValue(z[t][i][s][1])) < prod.getPredictedDemand(t) ) {
								
								System.out.println("this is for week "+ t);
								System.out.println("x 0 is" + cplex.getValue(x[t][i][s][0]));
								System.out.println("x 1 is" + cplex.getValue(x[t][i][s][1]));
								System.out.println("z 0 is" + cplex.getValue(z[t][i][s][0]));
								System.out.println("z 1 is" + cplex.getValue(z[t][i][s][1]));
								System.out.println("u 0 is" + cplex.getValue(u[t][i][s][0]));
								System.out.println("u 1 is" + cplex.getValue(u[t][i][s][1]));
								System.out.println("order is" + cplex.getValue(order[t][i]));
								System.out.println("y is" + cplex.getValue(y[i]));
							}
							
							if ((boolean) parameters.get("printEveryOrder")) {
								if (cplex.getValue(x[t][i][s][0])> 0 ) {
									System.out.println("Chunk " + chunkNames.get(i)+ " of size " + sizes[s] +" was ordered " + cplex.getValue(x[t][i][s][0])+ " times at time " + t + "in warehouse 0");
								}
								if (cplex.getValue(x[t][i][s][1])> 0 ) {
									System.out.println("Chunk " + chunkNames.get(i)+ " of size " + sizes[s] +" was ordered " + cplex.getValue(x[t][i][s][1])+ " times at time " + t+ "in warehouse 1");
								}
								
							}
						}
					}
				}
			}
			
			
			/** Set output parameters here */
			boolean showWeeklyCapasity = false;
			boolean showWeeklyServiceLevel = false;
			
			
			//Calculate service level
			int totDemand = 0;
			int fulfilledDemand = 0;
			
			double totCapasityUsed = 0;
			
			
			for (int t = T[0]; t < T[1]; t++) {
				double capasityUsed = 0;
				int totDemandWeekly = 0;
				int fulfilledDemandWeekly = 0;
				
				for (int i = 0; i < n; i ++) {
					for (int s = 0; s < size; s++) {
						HashMap<String, Product> chunk = data.get(chunkNames.get(i));
						Product prod = chunk.get(sizes[s]);
						if (prod != null) {
							totDemand += prod.getPredictedDemand(t);
							fulfilledDemand += zSolution[t][i][s];
							
							totDemandWeekly += prod.getPredictedDemand(t);
							fulfilledDemandWeekly += zSolution[t][i][s];
							
							
							if (prod.getPredictedDemand(t) < zSolution[t][i][s]) {
								System.out.println("Demand small then amount sold for " + chunkNames.get(i) + sizes[s]+ " in week " + t);
								System.out.println("Demand is  " + prod.getPredictedDemand(t) + " and sales is  " + zSolution[t][i][s] );
							}
							
							totCapasityUsed += zSolution[t][i][s] * prod.getAverageAverageM3();
							capasityUsed += zSolution[t][i][s] * prod.getAverageAverageM3();
						}
					}
				}
				if ((boolean) parameters.get("showWeeklyCapasity")) {
					System.out.println("The capacity used in week " + t + "  is: "+ capasityUsed );
					System.out.println("The percentage of capacity used in week " + t + "is: "+ capasityUsed / 2700  );
					System.out.println();
				}
				
				if ((boolean) parameters.get("showWeeklyServiceLevel")) {

					System.out.println("The amount of goods sold in week " + t + "is: "+fulfilledDemandWeekly );
					System.out.println("The amount of goods demanded in week " + t + " is: "+totDemandWeekly );
					System.out.println("The servicelevel in week " + t + " is: "+ ((double) fulfilledDemandWeekly /totDemandWeekly) );
					System.out.println();

				}
				
				if (showWeeklyCapasity || showWeeklyServiceLevel) {
					System.out.println();
				}
			}
			
			System.out.println("The amount of goods sold is: "+fulfilledDemand );
			System.out.println("The amount of goods demanded is: "+totDemand );
			System.out.println("The servicelevel over the whole period is: "+ ((double) fulfilledDemand /totDemand) );
			
			
			System.out.println("The capacity used is: "+totCapasityUsed );
			System.out.println("The percentage of capacity used is: "+ totCapasityUsed / (2700 * T[1]) );

			
			
			/** Running the simulation using the ordering levels from the solution */
			Random r = new Random(1234);
			Simulation.getSimulationResults(T, sizes, data, zSolution, parameters);
			
		}
		else
		{
			System.out.println("No optimal solution found");
		}
	}

}
