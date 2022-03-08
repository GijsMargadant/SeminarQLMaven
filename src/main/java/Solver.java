import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

/**
 * 
 * @author Minh Ngoc Pham
 *
 */
public class Solver {
	
	public static void main(String[] args) throws FileNotFoundException
	{		
		File data = new File(".\\dataFiles\\dataset.xlsx");
		File relevanceScore = new File(".\\\\dataFiles\\EUR_BusinessCase_Chunk_RelevanceScore_V2.xlsx");
		File warehouseCost = new File(".\\\\dataFiles\\EUR_BusinessCase_Sizegroup_Costs.xlsx");
		
		ArrayList<HashMap<String, HashMap<String, Product>>> dt = new ArrayList<HashMap<String, HashMap<String, Product>>>();
		
		try {
			CustomDataReader cdm = new CustomDataReader(data, relevanceScore, warehouseCost);
			ArrayList<Integer> years = new ArrayList<Integer>();
			years.add(2018);
			dt = cdm.readData(years);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		HashMap<String, HashMap<String, Product>> trial = new HashMap<String, HashMap<String, Product>>();
		trial.put("Poppenverzorgingsproduct", dt.get(0).get("Poppenverzorgingsproduct"));
		trial.put("Speelgoedwatersproeier", dt.get(0).get("Speelgoedwatersproeier"));
		
		// Optimize the problem.
		String[] sizes = {"XXXS", "XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL"};
				
		try
		{
			solve(52, sizes, trial);
//			solve(52, sizes, dt.get(0));
		}
		catch (IloException e)
		{
			System.out.println("A Cplex exception occured: " + e.getMessage());
			e.printStackTrace();
		}	
	}
	
	/**
	 * TODO
	 * @param coordinates
	 * @param distances
	 * @throws IloException
	 */
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
		IloNumVar [][][] u = new IloNumVar[T][n][size];
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
						z[t][i][s] = cplex.intVar(0, prod.getSales(t), "z(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + ")");
						u[t][i][s] = cplex.intVar(0, Integer.MAX_VALUE, "z(" + (t+1) + "," + chunkNames.get(i) + "," + sizes[s] + ")");
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
					cplex.addEq(u[0][i][s], 0, "Initial storage level");
					for (int t = 0; t < T; t++) {
						objExpr = cplex.sum(objExpr, cplex.prod(prod.getAveragePrice(t), z[t][i][s]));
						cplex.addGe(cplex.sum(x[t][i][s][0], x[t][i][s][1]), cplex.sum(u[t][i][s], z[t][i][s]), "Constraints on goods in warehouse");
						cplex.addLe(x[t][i][s][0], cplex.prod(cap0, cplex.sum(1, cplex.negative(y[i]))), "Constraints on goods allocation");
						cplex.addLe(x[t][i][s][1], cplex.prod(cap1, y[i]), "Constraints on goods allocation");
						if (t + 1 != T) {
							cplex.addEq(cplex.sum(u[t + 1][i][s], z[t][i][s]), cplex.sum(x[t][i][s][0], x[t][i][s][1]), "Inventory at the beginning of the period");
						}
					}
				}
			}
		}
		cplex.addMaximize(objExpr);
		
		// Export model
		cplex.exportModel("Model.lp");
		
		// Solve the model.

		cplex.solve();

		// Query the solution.
		if (cplex.getStatus() == IloCplex.Status.Optimal)
		{
			System.out.println("Found optimal solution!");
			System.out.println("Objective = " + cplex.getObjValue() + " kilometers.");
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
								System.out.println("We store " + chunkNames.get(i) + " in big warehouse with amount " + Math.round(cplex.getValue(x[t][i][s][0])) + " of size " + sizes[s] + " in week " + t);	
							}
						}
					}
				}
			}
		}
		else
		{
			System.out.println("No optimal solution found");
		}
	}
		
}