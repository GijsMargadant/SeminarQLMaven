import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

public class Main {

	public static void main(String[] args) {
		File data = new File("C:\\Users\\gijsm\\Documents\\DOCUMENTEN\\School\\SeminarCaseStudy\\OriginalData\\dataset.xlsx");
		File relevanceScore = new File("C:\\Users\\gijsm\\Documents\\DOCUMENTEN\\School\\SeminarCaseStudy\\OriginalData\\EUR_BusinessCase_Chunk_RelevanceScore_V2.xlsx");
		File warehouseCost = new File("C:\\Users\\gijsm\\Documents\\DOCUMENTEN\\School\\SeminarCaseStudy\\OriginalData\\EUR_BusinessCase_Sizegroup_Costs.xlsx");
		
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
