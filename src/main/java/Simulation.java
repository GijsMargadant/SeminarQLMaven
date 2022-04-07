import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

public class Simulation {
	
	public static void main(String args[]) throws IloException {
		
		// Define all the size groups
		String[] sizes = {"XXXS", "XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL"};
				
		//read data for year 2018
//		ArrayList<Integer> years = new ArrayList<Integer>(Arrays.asList(2018, 2019,2020));
//		ArrayList<Integer> years = new ArrayList<Integer>(Arrays.asList(2020));
//		ArrayList<HashMap<String, HashMap<String, Product>>> dt = Solver.readData(years);
		HashMap<String, HashMap<String, Product>> dt = Simulation.readData();
		
		
		System.out.println("De data is geladen");
		
		
		/*
		int size = sizes.length;
		ArrayList<String> chunkNames = new ArrayList<String>(dt.keySet());
		int n = chunkNames.size();
		
		for (int i = 0; i < n; i ++) {
			for (int t = 0; t < 1; t++) {
				for (int s = 0; s < size; s++) {
					HashMap<String, Product> chunk = dt.get(chunkNames.get(i));
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						if (prod.getAverageAverageM3() == 1) {
							System.out.println(prod.getAverageAverageM3());
							for (int j =0; j < 104; j ++) {
								System.out.print(prod.getAverageM3(j));
								
							}
							System.out.println();

						}
						
						if (Double.isNaN(prod.getAverageAverageM3())) {
							System.out.println(prod.getAverageAverageM3());
							System.out.println(prod.getAverageM3(t));
						}
					}
				}
			}
		}
		*/
		
		solve2020(new int[]{0,52}, sizes, dt);

		
		
		/*
		int[] T = new int[] {0,2};
		
		int size = sizes.length;
		ArrayList<String> chunkNames = new ArrayList<String>(dt.keySet());
		int n = chunkNames.size();
		int[][][] z = new int[T[1]][n][size];
		int[][][] demand = new int[T[1]][n][size];
		
		System.out.println(chunkNames.get(0));
		System.out.println(dt.get(0).get(chunkNames.get(0)).toString());
		
		z[0][0][3] = 40;
		demand[0][0][3] = 60;
		
//		simulationMain(T, sizes, dt.get(0), z, demand);
		
        Random ran = new Random(1234);

		//Adding a bit of randomness to the demand
		for (int i = 0; i < n; i ++) {
			HashMap<String, Product> chunk = dt.get(chunkNames.get(i));
			for (int s = 0; s < size; s++) {
				Product prod = chunk.get(sizes[s]);
				if (prod != null) {
					demand[0][i][s] = (int) (prod.getSales(0) + Math.round( Math.sqrt(prod.getSales(0)) * ran.nextGaussian()));
					if (demand[0][i][s] < 0 ) {
						demand[0][i][s] = 0;
					}
					z[0][i][s] = prod.getSales(0);
				}
			}
		}
		simulationMain(T, sizes, dt, z, demand);
		*/
		
	}
	
	public static void solve2020(int[] T, String[] sizes, HashMap<String, HashMap<String, Product>> data) throws IloException
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
						// TODO (done): Change .getSales() to get prediction. 
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
						//TODO: maybe this constraint can be relaxed
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
		cplex = Solver.addCapacityConstraint2020(T, sizes, cplex, x, data, 0.15, 0.15);
		
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
							zSolution[t][i][s] = (int) cplex.getValue(z[t][i][s]);
							xSolution[t][i][s][0] = (int) cplex.getValue(x[t][i][s][0]);
							xSolution[t][i][s][1] = (int) cplex.getValue(x[t][i][s][1]);
						}
					}
				}
			}
			
			
			/** Set output parameters here */
			boolean showWeeklyCapasity = true;
			boolean showWeeklyServiceLevel = true;
			
			
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
				if (showWeeklyCapasity) {
				System.out.println("The capacity used in week " + t + "  is: "+ capasityUsed );
				System.out.println("The percentage of capacity used in week " + t + "is: "+ capasityUsed / 2700  );
				}
				
				if (showWeeklyServiceLevel) {
					System.out.println("The amount of goods sold in week " + t + "is: "+fulfilledDemandWeekly );
					System.out.println("The amount of goods demanded in week " + t + " is: "+totDemandWeekly );
					System.out.println("The servicelevel in week " + t + " is: "+ ((double) fulfilledDemandWeekly /totDemandWeekly) );
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


			
			
			
			//OLD FUNCTIONS
			//	View the capacity per week
			//capacityCheck(T, sizes, cplex, x, data);
			
			// View the service level per category over the full years
//			getRevenue(T, sizes, cplex, z, data);
			
			// View the service level per category over the full years
//			Solver.serviceLevel(T, sizes, cplex, z, data);
			
			
			// View the relavance score for each week and overall the weeks
//			relevanceScore(T, sizes, cplex, z, data);

			
			//	View the service level per category per week. 
			//  The last parameter is printAll. If true it prints for all weeks is false it only prints the weeks with service level <100.
//			Solver.serviceLevelWeekly(T, sizes, cplex, z, data, false);
			
			
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
	
	
	public static void simulationMain(int[] T, String[] sizes,
			HashMap<String, HashMap<String, Product>> data,
			int[][][] z, int[][][] demand) throws IloException {
		
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
		
		
		for (int t = T[0]; t < T[1]; t++) {
			
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
							System.out.println("we ordered "+ ( -storage[t][i][s] + z[t][i][s])+" at time  "+ t+" for chunk "+ chunkNames.get(i)+"of size "+ sizes[s]);		
							totalOrdered += z[t][i][s] - storage[t][i][s];
							storage[t][i][s]  = z[t][i][s];
						}else if(storage[t][i][s] > z[t][i][s]) {
							System.out.println("we threw away  "+ ( storage[t][i][s] - z[t][i][s])+" at time  "+ t+" for chunk "+ chunkNames.get(i)+"of size "+ sizes[s]);		
							totalThrewAway += storage[t][i][s] -z[t][i][s] ;
							storage[t][i][s]  = z[t][i][s];
						}
					}
				}
				//Check if an order is placed if so add to the total amount of orders for ordering cost.
				if (orderForChunk) {
					orders ++;
				}
			}
			
			/** Selling products */
			for (int i = 0; i < n; i ++) {
				HashMap<String, Product> chunk = data.get(chunkNames.get(i));
				for (int s = 0; s < size; s++) {
					Product prod = chunk.get(sizes[s]);
					if (prod != null) {
						if (demand[t][i][s] <= storage[t][i][s]) {
							//Update revenue
							revenue += prod.getAveragePrice(t) * demand[t][i][s];
							revenueTheoretical += prod.getAveragePrice(t) * demand[t][i][s];
							//Update relevance score
							relevanceSoldProducts += prod.getRelevanceScore() * demand[t][i][s];
							relevanceAllProducts += prod.getRelevanceScore() * demand[t][i][s];
							// update amount of products
							productsSold += demand[t][i][s];
							totalDemand += demand[t][i][s];
							
							//Update the amount in storage
							storage[t][i][s] -= demand[t][i][s];
						}else {
							//Update revenue
							revenue += prod.getAveragePrice(t) * storage[t][i][s];
							revenueTheoretical += prod.getAveragePrice(t) * demand[t][i][s];
							//Update relevance score
							relevanceSoldProducts += prod.getRelevanceScore() * storage[t][i][s];
							relevanceAllProducts += prod.getRelevanceScore() * demand[t][i][s];
							// update amount of products
							productsSold += storage[t][i][s];
							totalDemand += demand[t][i][s];
							
							//Update the amount in storage
							storage[t][i][s] = 0;
						}
					}
				}
			}
			
			
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
		
		
		//Print interesting information on the simulation
		System.out.println("The amount of orders is: " + orders + " so ordering cost is: " + orders * 10 );	
		System.out.println("The revenue for this period is: " + revenue);	
		System.out.println("The holding cost for this period is: " + holdingCost);	
		System.out.println("The profit for this period is: " + (revenue - holdingCost));
		System.out.println();

		System.out.println("There are " + totalOrdered + " products ordered and " + totalThrewAway+ " products thown away");	
		System.out.println("The amount of products sold is: " + productsSold);	
		System.out.println("The amount of products demanded is: " + totalDemand);	
		System.out.println("The service level is: " + ((double)productsSold / totalDemand ));
		System.out.println();


		System.out.println("This is the end of the simulation main method");	
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
	
}
