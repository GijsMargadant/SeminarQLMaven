
/**
 * @author Gijs
 * The Product class stores all data that associated with a product (also called groupSize)
 * over the course of one year. It stores the following data:
 * @totalWeeklySales is a positive integer for total weekly units of product sold
 * @averageM3 is a positive double for the volume in m3 of one unit of product
 * @averagePrice is a positive double for the average selling price of one unit of product
 * @chunk is a string representing the name of the chunk
 * @name is a string representing the name of the product (groupSize)
 * @unitStorageCost is a double representing the warehousing cost of one unit of product
 * @relevanceScore is a positive double between zero and one representing the relevance of the
 * product
 */
public class Product {
	private int[] weeklySales;
	private double[] averageM3;
	private double[] averagePrice;
	private String chunck;
	private String name;
	private double unitStorageCost;
	private double relevanceScore;

	public Product() {
		// TODO Auto-generated constructor stub
		// Test
	}

}
