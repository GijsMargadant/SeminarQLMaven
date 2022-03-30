import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

public class Simulation {
	
	public static void main(String args[]) {
		
		// Define all the size groups
		String[] sizes = {"XXXS", "XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL"};
				
		//read data for year 2018
		ArrayList<Integer> years = new ArrayList<Integer>(Arrays.asList(2018, 2019,2020));
		ArrayList<HashMap<String, HashMap<String, Product>>> dt = Solver.readData(years);
		
	}
	
	public void simulationMain(int[] T, String[] sizes,
			ForecastDemand forecastDemand, SimulateDemand simulateDemand,
			ArrayList<HashMap<String, HashMap<String, Product>>> data) throws IloException {
		
		
		
		
		//Get ordering level
		
		//Get simulated demand
		
		//Calculate the products remaining
		
		//Calculate the stats of week
		
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
