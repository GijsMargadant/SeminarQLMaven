
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
 * @year the year in which the data was obtained
 * @isGeneralToy is a boolean that is true if the product belongs to the productGroup 'General Toys'
 * and false if the product belongs to 'Recreational and Outdoor Toys'
 */
public class Product {
	private int[] weeklySales;
	private double[] weeklyAverageM3;
	private double[] weeklyAveragePrice;
	private String chunk;
	private String sizeGroup;
	private double unitStorageCost;
	private double relevanceScore;
	private int year;
	private boolean isGeneralToy;

	/**
	 * This constructor initializes a product with all data that is not time dependent. Time dependent
	 * data must be added manually with separate functions.
	 * @param chunk the chunk name as String
	 * @param sizeGroup the sizeGroup as String
	 * @param isGeneralToy a boolean whether or not the Product belongs to the General Toy group
	 * @param year the year from which the time series data originates as integer
	 * @param unitStorageCost the unit StorageCost of the product as double
	 * @param relevanceScore the relevance score of the product as double
	 */
	public Product(String chunk, String sizeGroup, boolean isGeneralToy, int year,
			double unitStorageCost, double relevanceScore) {
		// Initialize all variables and arrays
		this.chunk = chunk;
		this.sizeGroup = sizeGroup;
		this.isGeneralToy = isGeneralToy;
		this.year = year;
		this.unitStorageCost = unitStorageCost;
		this.relevanceScore = relevanceScore;
		
		weeklySales = new int[52];
		weeklyAverageM3 = new double[52];
		weeklyAveragePrice = new double[52];
		// Since we assume missing data to be zero, we fill the arrays with zeros.
		// If data is available, we will just override these values later.
		for(int i = 0; i < 52; i++) {
			weeklySales[i] = 0;
			weeklyAverageM3[i] = 0.0;
			weeklyAveragePrice[i] = 0.0;
		}
	}
	
	/**
	 * This method sets the qty of sales for a certain week
	 * @param week
	 * @param qty
	 */
	public void addSale(int week, int qty) {
		weeklySales[week] = qty;
	}
	
	/**
	 * This method sets the averageM3 for a given week
	 * @param week
	 * @param volume
	 */
	public void addAverageM3(int week, double volume) {
		weeklyAverageM3[week] = volume; 
	}
	
	/**
	 * This method sets the price for a given week
	 * @param week
	 * @param price
	 */
	public void addAveragePrice(int week, double price) {
		weeklyAveragePrice[week] = price;
	}
	
	/**
	 * This method returns the sales for a given week
	 * @param week
	 * @return sales as integer
	 */
	public int getSales(int week) {
		return weeklySales[week];
	}
	
	/**
	 * This method returns the averageM3 for a given week
	 * @param week
	 * @return averageM3 as double
	 */
	public double getAverageM3(int week) {
		return weeklyAverageM3[week];
	}
	
	/**
	 * This method gives the average price for a given week
	 * @param week
	 * @return average price as double
	 */
	public double getAveragePrice(int week) {
		return weeklyAveragePrice[week];
	}

	/**
	 * Basic getter method
	 * @return
	 */
	public String getChunk() {
		return chunk;
	}

	/**
	 * Basic setter method
	 * @param chunk
	 */
	public void setChunk(String chunk) {
		this.chunk = chunk;
	}

	/**
	 * Basic getter method
	 * @return
	 */
	public String getSizeGroup() {
		return sizeGroup;
	}

	/**
	 * Basic setter method
	 * @param sizeGroup
	 */
	public void setSizeGroup(String sizeGroup) {
		this.sizeGroup = sizeGroup;
	}

	/**
	 * Basic getter method
	 * @return
	 */
	public double getUnitStorageCost() {
		return unitStorageCost;
	}

	/**
	 * Basic setter method
	 * @param unitStorageCost
	 */
	public void setUnitStorageCost(double unitStorageCost) {
		this.unitStorageCost = unitStorageCost;
	}

	/**
	 * Basic getter method
	 * @return
	 */
	public double getRelevanceScore() {
		return relevanceScore;
	}

	/**
	 * Basic setter method
	 * @param relevanceScore
	 */
	public void setRelevanceScore(double relevanceScore) {
		this.relevanceScore = relevanceScore;
	}

	/**
	 * Basic getter method
	 * @return
	 */
	public int getYear() {
		return year;
	}

	/**
	 * Basic setter method
	 * @param year
	 */
	public void setYear(int year) {
		this.year = year;
	}

	/**
	 * Basic getter method
	 * @return
	 */
	public boolean isGeneralToy() {
		return isGeneralToy;
	}

	/**
	 * Basic setter method
	 * @param isGeneralToy
	 */
	public void setGeneralToy(boolean isGeneralToy) {
		this.isGeneralToy = isGeneralToy;
	}

	/**
	 * Basic getter method
	 * @return
	 */
	public int[] getWeeklySales() {
		return weeklySales;
	}

	/**
	 * Basic getter method
	 * @return
	 */
	public double[] getWeeklyAverageM3() {
		return weeklyAverageM3;
	}

	/**
	 * Basic getter method
	 * @return
	 */
	public double[] getWeeklyAveragePrice() {
		return weeklyAveragePrice;
	}
}
