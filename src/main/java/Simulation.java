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
				
		//read data for year 2018
//		ArrayList<Integer> years = new ArrayList<Integer>(Arrays.asList(2018, 2019,2020));
//		ArrayList<Integer> years = new ArrayList<Integer>(Arrays.asList(2020));
//		ArrayList<HashMap<String, HashMap<String, Product>>> dt = Solver.readData(years);
		HashMap<String, HashMap<String, Product>> dt = Simulation.readData();
		
		
		System.out.println("De data is geladen");

		
		HashMap<String, Object> parameters = new HashMap<String, Object>(); 
		
		parameters.put("usePlusXInSales", true);
		parameters.put("nbrSimulations" , 1);
		
		solve2020(new int[]{0,1}, sizes, dt, 1, parameters);

		
		
	}
	
	public static void solve2020(int[] T, String[] sizes, HashMap<String, HashMap<String, Product>> data,
			int nbrSimulations, HashMap<String, Object> parameters) throws IloException
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
							zSolution[t][i][s] = (int) cplex.getValue(z[t][i][s]);
							xSolution[t][i][s][0] = (int) cplex.getValue(x[t][i][s][0]);
							xSolution[t][i][s][1] = (int) cplex.getValue(x[t][i][s][1]);
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

			
			
			/** Running the simulation using the ordering levels from the solution */
			Random r = new Random(1234);
			Simulation.getSimulationResults(T, sizes, data, zSolution, nbrSimulations, parameters);
			
		}
		else
		{
			System.out.println("No optimal solution found");
		}
	}
	
	public static void getSimulationResults(int[] T, String[] sizes, HashMap<String, HashMap<String, Product>> data,
			int [][][] zSolution, int nbr, HashMap<String, Object> parameters) throws IloException {
		Random r = new Random(1234);
		int sizeOfResultsSimulation = 4 + T[1] - T[0];
		
		ArrayList<ArrayList<Double>> results = new ArrayList<ArrayList<Double>>();
		
		for (int i = 0; i < nbr; i++) {
			results.add(Simulation.simulationMain(T, sizes, data, zSolution, r, parameters));
		
		}
		
		//Print results
		
		/*
		for (int j = 0; j < sizeOfResultsSimulation; j ++) {
			switch (j) {
			case 0:
				System.out.println("This are the revenues");
				break;
			case 1:
				System.out.println("This are the amount of products sold");
				break;
			case 2:
				System.out.println("This are the amount of products demanded");
				break;
			case 3:
				System.out.println("This are the service levels");
				break;
			default:
				System.out.println("Not defined what this is.");
				break;
			}
			
			for (int i = 0; i < nbr; i++) {
				System.out.println(results.get(i).get(j));
			}
			System.out.println();

		}
		*/
		
		for (int i = 0; i < nbr; i++) {

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
		
		//This only works for Floris at the moment
		try {
			Simulation.writeToExcel(results);
			System.out.println("The results are written to an excel sheet");

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
		
		
		for (int t = T[0]; t < T[1]; t++) {
			
			int salesWeek =0;
			int demandWeek =0;
			
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
						}else{
							demand = prod.pullRandomSales(t, r);
						}
						
						demandWeek += demand;
						
						if (demand <= storage[t][i][s]) {
							//Update revenue
							revenue += prod.getAveragePrice(t) * demand;
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
		//Print interesting information on the simulation
//		System.out.println("The amount of orders is: " + orders + " so ordering cost is: " + orders * 10 );	
		System.out.println("The revenue for this period is: " + revenue);	
//		System.out.println("The holding cost for this period is: " + holdingCost);	
//		System.out.println("The profit for this period is: " + (revenue - holdingCost));
//		System.out.println();

		System.out.println("There are " + totalOrdered + " products ordered and " + totalThrewAway+ " products thown away");	
		System.out.println("The amount of products sold is: " + productsSold);	
		System.out.println("The amount of products demanded is: " + totalDemand);	
		System.out.println("The service level is: " + ((double)productsSold / totalDemand ));
		System.out.println();

		ArrayList<Double> results = new ArrayList<Double>();
		results.add(revenue);
		results.add((double) productsSold);
		results.add((double) totalDemand);
		results.add((double)productsSold / totalDemand);
		results.addAll(weeklyServiceLevel);
		
		return results;
	}	
	

	public static void simulationMain1(int[] T, String[] sizes,
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
//							System.out.println("we ordered "+ ( -storage[t][i][s] + z[t][i][s])+" at time  "+ t+" for chunk "+ chunkNames.get(i)+"of size "+ sizes[s]);		
							totalOrdered += z[t][i][s] - storage[t][i][s];
							storage[t][i][s]  = z[t][i][s];
						}else if(storage[t][i][s] > z[t][i][s]) {
//							System.out.println("we threw away  "+ ( storage[t][i][s] - z[t][i][s])+" at time  "+ t+" for chunk "+ chunkNames.get(i)+"of size "+ sizes[s]);		
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
//		System.out.println("The amount of orders is: " + orders + " so ordering cost is: " + orders * 10 );	
		System.out.println("The revenue for this period is: " + revenue);	
//		System.out.println("The holding cost for this period is: " + holdingCost);	
//		System.out.println("The profit for this period is: " + (revenue - holdingCost));
//		System.out.println();

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

	public static void writeToExcel(ArrayList<ArrayList<Double>> results) throws IOException {
		// workbook object
        XSSFWorkbook workbook = new XSSFWorkbook();
  
        // spreadsheet object
        XSSFSheet spreadsheet
            = workbook.createSheet("Results Simulation version 1.1");
  
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
        		new File("/Users/floris/Documents/Studie/Year_3_Block_4/Seminar/Results.xlsx"));

        workbook.write(out);
        out.close();
    
	}
}
