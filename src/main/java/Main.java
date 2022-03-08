import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

public class Main {

	public static void main(String[] args) {
		// Since I've created a dataFile folder in the project containing all xlsx files, you can access them
		// in this way. This probably only works if you have eclipse and GitHub linked. Otherwise, you should
		// use the file paths from your own PC.
		File data = new File(".\\dataFiles\\dataset.xlsx");
		File relevanceScore = new File(".\\\\dataFiles\\EUR_BusinessCase_Chunk_RelevanceScore_V2.xlsx");
		File warehouseCost = new File(".\\\\dataFiles\\EUR_BusinessCase_Sizegroup_Costs.xlsx");
		
		try {
			CustomDataReader cdm = new CustomDataReader(data, relevanceScore, warehouseCost);
			ArrayList<Integer> years = new ArrayList<Integer>();
			years.add(2018);
			cdm.readData(years);
			
			
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

}
