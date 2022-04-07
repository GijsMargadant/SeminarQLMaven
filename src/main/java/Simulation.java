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
		ArrayList<Integer> years = new ArrayList<Integer>(Arrays.asList(2020));
		ArrayList<HashMap<String, HashMap<String, Product>>> dt = Solver.readData(years);
		
		int[] T = new int[] {0,2};
		
		int size = sizes.length;
		ArrayList<String> chunkNames = new ArrayList<String>(dt.get(0).keySet());
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
			HashMap<String, Product> chunk = dt.get(0).get(chunkNames.get(i));
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
		simulationMain(T, sizes, dt.get(0), z, demand);

		
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
	
	
	
	public IloCplex buildModelOneWeek(int t, String[] sizes, ArrayList<Integer> fixedY, int[][] inventory,
			ForecastDemand forecastDemand, SimulateDemand simulateDemand,
			ArrayList<HashMap<String, HashMap<String, Product>>> data) throws IloException {
		
		IloCplex cplex = new IloCplex();
		//TODO: Get correct chunk names
		int size = sizes.length;
		ArrayList<String> chunkNames = new ArrayList<String>(data.get(0).keySet());
		int n = chunkNames.size();
		
		IloNumVar [][][] x = new IloNumVar[n][size][2];
		IloNumVar [][] z = new IloNumVar[n][size];
		IloNumVar [][] u = new IloNumVar[n][size];
		IloNumVar [] y = new IloNumVar[n];
		
		//Get the simulated demand for this week
//		data = simulateDemand.simulateDemand();
		//TODO: need to add the demand to data
		//TODO: format the simulated demand output to fit chuck and sizes. 
		
		//Add variables to the model
		for (int i = 0; i < n; i ++) {
			y[i] = cplex.boolVar("y(" + chunkNames.get(i) + ")");
			
			//If the chunk has already be stored somewhere if must be stored at the same place. 
			if (fixedY.get(i) != null) {
				if (fixedY.get(i) == 1) {
					y[i].setLB(0.9);
				}else {
					y[i].setUB(0.1);
				}
			}
			
			for (int s = 0; s < size; s++) {
				//TODO: Get correct chunks
				HashMap<String, Product> chunk = data.get(2).get(chunkNames.get(i));
				Product prod = chunk.get(sizes[s]);
				//TODO: when is a product null? If it has not been sold previously?
				if (prod != null) {
					x[i][s][0] = cplex.intVar(0, Integer.MAX_VALUE, "x(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + "," + 0 + ")");
					x[i][s][1] = cplex.intVar(0, Integer.MAX_VALUE, "x(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + "," + 1 + ")");
					z[i][s] = cplex.intVar(0, prod.getSales(t), "z(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + ")");
					u[i][s] = cplex.intVar(0, Integer.MAX_VALUE, "u(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + ")");
				}	
			}
		}
		
		//create objective function
		IloNumExpr objExpr = cplex.constant(0);
		
		for (int i = 0; i < n; i ++) {
			// TODO: not sure if get(2) is correct here 
			HashMap<String, Product> chunk = data.get(2).get(chunkNames.get(i));
			for (int s = 0; s < size; s++) {
				Product prod = chunk.get(sizes[s]);
				if (prod != null) {
					objExpr =  cplex.sum(objExpr, cplex.prod(z[i][s], prod.getAveragePrice(t)));
				}
			}
		}
		cplex.addMaximize(objExpr);
		
		//Add capacity constraint
		int cap0 = 3000 * 15 / 100;
		int cap1 = 15000 * 15 / 100;
		IloNumExpr capacity0 = cplex.constant(0);
		IloNumExpr capacity1 = cplex.constant(0);
		for (int i = 0; i < n; i ++) {
			HashMap<String, Product> chunk = data.get(2).get(chunkNames.get(i));
			for (int s = 0; s < size; s++) {
				Product prod = chunk.get(sizes[s]);
				if (prod != null) {
					capacity0 = cplex.sum(capacity0, cplex.prod(prod.getAverageM3(t), x[i][s][0]));
					capacity1 = cplex.sum(capacity1, cplex.prod(prod.getAverageM3(t), x[i][s][1]));
				}
			}
		}
		cplex.addLe(capacity0, cap0, "Capacity constraint for small warehouse");
		cplex.addLe(capacity1, cap1, "Capacity constraint for big warehouse");
		
		//Add storage balancing constraint
		for (int i = 0; i < n; i ++) {
			HashMap<String, Product> chunk = data.get(2).get(chunkNames.get(i));
			for (int s = 0; s < size; s++) {
				Product prod = chunk.get(sizes[s]);
				if (prod != null) {
					IloNumExpr storage = cplex.constant(0);
					storage = cplex.sum(storage, inventory[i][s] );
					storage = cplex.sum(storage, x[i][s][0]);
					storage = cplex.sum(storage, x[i][s][1]);
					storage = cplex.diff(storage, z[i][s]);
					cplex.addEq(u[i][s], storage, "Storage balancing constraint for product" +chunkNames.get(i) + "and size "+ sizes[s]);
				}
			}
		}
		
		
		
		// Solve the model.
		cplex.solve();

		// Query the solution.
		if (cplex.getStatus() == IloCplex.Status.Optimal)
		{
			//Below are some different options to analyze the solution
			
			System.out.println("Found optimal solution!");
			System.out.println("Objective = " + cplex.getObjValue());
			
			
		}
		else
		{
			System.out.println("No optimal solution found");
		}		
		
		
		
		return cplex;
	}
	
	
	
	public void simulateOneWeek(ForecastDemand forecastDemand, SimulateDemand simulateDemand) {
		//Get demand forecast for next week
		ArrayList<HashMap<String, HashMap<String, Product>>> forcastTplus1 = forecastDemand.forecastOneAhead();
		
		//Method to add forecasted demand to the data 

		
		//Get simulated demand for next week
		ArrayList<HashMap<String, HashMap<String, Product>>> simulatedDemand = simulateDemand.simulateDemand();
		
		//Method to add simulated demand to the data 
		
		
	}
	
	interface ForecastDemand {
		public ArrayList<HashMap<String, HashMap<String, Product>>> forecastOneAhead();
		public ArrayList<HashMap<String, HashMap<String, Product>>> forecastTwoAhead();
	}
	
	interface SimulateDemand {
		public ArrayList<HashMap<String, HashMap<String, Product>>> simulateDemand();
	}
		

}
