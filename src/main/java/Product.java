
/**
 * @author Gijs
 * The Product class stores all data that associated with a product (also called groupSize)
 * over the course of one year. It stores the following data:
 * @totalWeeklySales is a positive integer for total weekly units of product sold
 * @averageM3 is a positive double for the volume in m3 of one unit of product
 * @averagePrice is a positive double for the average selling price of one unit of product
 * @shop is a string representing the shop to which the product group belongs
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
	private String shop;
	private String productGroup;
	private String chunk;
	private String sizeGroup;
	private double unitStorageCost;
	private double relevanceScore;
	private int year;
	
	private boolean[] dataPresent;
	private double z = 3.3; // z statistic for 99% confidence level

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
	public Product(String shop, String productGroup, String chunk, String sizeGroup, int year,
			double unitStorageCost, double relevanceScore) {
		// Initialize all variables and arrays
		this.chunk = chunk;
		this.sizeGroup = sizeGroup;
		this.shop = shop;
		this.productGroup = productGroup;
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
			dataPresent[i] = false;
		}
	}
	
	/**
	 * This method cleans al the time series data. It does so by first calculating upper and lower bounds
	 * based on mu +/- 3.3*sigma. If a value falls outside this interval, it is removed. This procedure is
	 * repeated until no more data points fall outside the 99% confidence interval. Finally, all removed
	 * values are set equal to the mean of the time series.
	 */
	public void cleanTimeSeriesData() {
		cleanSales();
		cleanVolume();
		cleanPrices();
	}
	
	private void cleanSales() {
		for(int i = 0; i < dataPresent.length; i++) {
			if(dataPresent[i] && weeklySales[i] < 0) {
				weeklySales[i] = 0;
			}
		}
		
		double mean = mean(weeklySales);
		for(int i = 0; i < dataPresent.length; i++) {
			if(dataPresent[i] && weeklySales[i] == 0) {
				weeklySales[i] = (int) mean;
			}
		}
	}
	
	
	private void cleanVolume() {
		boolean clean = false;
		while(!clean) {
			// Calculate mean and stdev
			double mean = mean(weeklyAverageM3);
			double stdev = stdev(weeklyAverageM3, mean);
			double LB = Math.max(mean - z*stdev, 0.0);
			double UB = mean + z*stdev;
			
			// Remove values outside bounds
			// Use open bounds, so (LB, UB) instead of [LB, UB]
			boolean modified = false;
			for(int i = 0; i < dataPresent.length; i++) {
				if(dataPresent[i] && !(weeklyAverageM3[i] > LB || weeklyAverageM3[i] < UB)) {
					weeklyAverageM3[i] = 0;
					modified = true;
				}
			}
			
			// If no data has been removed, set all removed data equal to the mean
			if(!modified) {
				for(int i = 0; i < dataPresent.length; i++) {
					if(dataPresent[i] && weeklyAverageM3[i] == 0) {
						weeklyAverageM3[i] = mean;
						modified = true;
					}
				}
				clean = true;
			}
			
		}
	}
	
	
	private void cleanPrices() {
		boolean clean = false;
		while(!clean) {
			// Calculate mean and stdev
			double mean = mean(weeklyAveragePrice);
			double stdev = stdev(weeklyAveragePrice, mean);
			double LB = Math.max(mean - z*stdev, 0.0);
			double UB = mean + z*stdev;
			
			// Remove values outside bounds
			// Use open bounds, so (LB, UB) instead of [LB, UB]
			boolean modified = false;
			for(int i = 0; i < dataPresent.length; i++) {
				if(dataPresent[i] && !(weeklyAveragePrice[i] > LB || weeklyAveragePrice[i] < UB)) {
					weeklyAveragePrice[i] = 0;
					modified = true;
				}
			}
			
			// If no data has been removed, set all removed data equal to the mean
			if(!modified) {
				for(int i = 0; i < dataPresent.length; i++) {
					if(dataPresent[i] && weeklyAveragePrice[i] == 0) {
						weeklyAveragePrice[i] = mean;
						modified = true;
					}
				}
				clean = true;
			}
			
		}
	}
	
	
	/**
	 * This method calculate the mean of an integer array
	 * @param arr
	 * @return
	 */
	private double mean(int[] arr) {
		double mean = 0.0;
		int n = 0;
		for(int i = 0; i < dataPresent.length; i++) {
			if(dataPresent[i] && arr[i] > 0) {
				mean += arr[i];
				n++;
			}
		}
		mean = mean / (double) n;
		return mean;
	}
	
	
	/**
	 * This method calculate the mean of a double array
	 * @param arr
	 * @return
	 */
	private double mean(double[] arr) {
		double mean = 0.0;
		int n = 0;
		for(int i = 0; i < dataPresent.length; i++) {
			if(dataPresent[i] && arr[i] > 0) {
				mean += arr[i];
				n++;
			}
		}
		mean = mean / (double) n;
		return mean;
	}
	
	/**
	 * This method calculates the standard deviation for an integer array
	 * @param arr
	 * @param mean
	 * @return
	 */
	private double stdev(int[] arr, double mean) {
		double stdev = 0.0;
		int n = 0;
		for(int i = 0; i < dataPresent.length; i++) {
			if(dataPresent[i] && arr[i] > 0) {
				stdev += Math.pow((double)(arr[i] - mean), 2.0);
				n++;
			}
		}
		stdev = Math.sqrt(stdev / (double) (n - 1));
		return stdev;
	}
	
	/**
	 * This method calculates the standard deviation for a double array
	 * @param arr
	 * @param mean
	 * @return
	 */
	private double stdev(double[] arr, double mean) {
		double stdev = 0.0;
		int n = 0;
		for(int i = 0; i < dataPresent.length; i++) {
			if(dataPresent[i] && arr[i] > 0) {
				stdev += Math.pow((double)(arr[i] - mean), 2.0);
				n++;
			}
		}
		stdev = Math.sqrt(stdev / (double) (n - 1));
		return stdev;
	}

	
	
	/**
	 * This method sets the qty of sales for a certain week
	 * @param week goes from 1 to 52
	 * @param qty
	 */
	public void addSale(int week, int qty) {
		weeklySales[week - 1] = qty;
		dataPresent[week - 1] = true;
	}
	
	/**
	 * This method sets the averageM3 for a given week
	 * @param week goes from 1 to 52
	 * @param volume
	 */
	public void addAverageM3(int week, double volume) {
		weeklyAverageM3[week - 1] = volume; 
		dataPresent[week - 1] = true;
	}
	
	/**
	 * This method sets the price for a given week
	 * @param week goes from 1 to 52
	 * @param price
	 */
	public void addAveragePrice(int week, double price) {
		weeklyAveragePrice[week - 1] = price;
		dataPresent[week - 1] = true;
	}
	
	/**
	 * This method returns the sales for a given week
	 * @param week goes from 0 to 51
	 * @return sales as integer
	 */
	public int getSales(int week) {
		return weeklySales[week];
	}
	
	/**
	 * This method returns the averageM3 for a given week
	 * @param week goes from 0 to 51
	 * @return averageM3 as double
	 */
	public double getAverageM3(int week) {
		return weeklyAverageM3[week];
	}
	
	/**
	 * This method gives the average price for a given week
	 * @param week goes from 1 to 52
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
	public boolean belongsToGeneralToys() {
		return productGroup.equals("General Toys");
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

	public String getShop() {
		return shop;
	}

	public void setShop(String shop) {
		this.shop = shop;
	}

	public String getProductGroup() {
		return productGroup;
	}

	public void setProductGroup(String productGroup) {
		this.productGroup = productGroup;
	}
}
