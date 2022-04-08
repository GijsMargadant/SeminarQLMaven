import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

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
	private double z = 3.5; // z statistic for 99% confidence level
	
	private int nWeeks;
	private double[] seasonalIndices;
	private double[] cleanedSales;
	private final int nSeasons = 52;
	private double cleanedMean;
	private double cleanedStdev;
	private double level;
	private double trend;

	
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
		dataPresent = new boolean[52];
		// Since we assume missing data to be zero, we fill the arrays with zeros.
		// If data is available, we will just override these values later.
		Arrays.fill(dataPresent, false);
		Arrays.fill(weeklySales, 0);
		Arrays.fill(weeklyAverageM3, 0.0);
		Arrays.fill(weeklyAveragePrice, 0.0);
	}

	
	/**
	 * This constructor makes it possible to store more than one year in the product class.
	 * @param shop
	 * @param productGroup
	 * @param chunk
	 * @param sizeGroup
	 * @param unitStorageCost
	 * @param relevanceScore
	 * @param nWeeks
	 */
	public Product(String shop, String productGroup, String chunk, String sizeGroup,
			double unitStorageCost, double relevanceScore, int nWeeks) {
		this.chunk = chunk;
		this.sizeGroup = sizeGroup;
		this.shop = shop;
		this.productGroup = productGroup;
		this.unitStorageCost = unitStorageCost;
		this.relevanceScore = relevanceScore;
		this.nWeeks = nWeeks;

		weeklySales = new int[nWeeks];
		weeklyAverageM3 = new double[nWeeks];
		weeklyAveragePrice = new double[nWeeks];
		dataPresent = new boolean[nWeeks];
		// Since we assume missing data to be zero, we fill the arrays with zeros.
		// If data is available, we will just override these values later.
		Arrays.fill(dataPresent, false);
		Arrays.fill(weeklySales, 0);
		Arrays.fill(weeklyAverageM3, 0.0);
		Arrays.fill(weeklyAveragePrice, 0.0);
	}
	
	//-------------------------------------------------------------------
	// This part contains the code to create a distribution for the data
	//-------------------------------------------------------------------
	
	/**
	 * This method deseasonalizes and detrends the data and finds the parameters
	 * of the Poisson distribution for the cleaned data
	 */
	public void calculateDistributionProperties() {
		// Find the seasonal indices for each season
		findSeasonalIndices();
		// Deseasonalize the data
		double[] deseasonalizedData = deseasonalizeData();
		// Find level and trend by means of ols on deseasonalized data
		double[] beta = findLevelAndTrend(deseasonalizedData);
		level = beta[0];
		trend = beta[1];
		// Final cleaning
		delevelAndDetrendData(deseasonalizedData);
		// calculate the mean of the cleaned data
		cleanedMean = mean(cleanedSales);
		cleanedStdev = stdev(cleanedSales, cleanedMean);
	}
	
	
	/**
	 * This method finds the seasonal indices for each period. It does so in the following way:
	 * - first calculate a moving average for each week. This average is composed of the nSeasons
	 * amount of data points around a given week. So for week 0, that is week 0 till week nSeasons.
	 * For week 26, this is week 26-nSeasons/2 till week 26+nSeasons/2
	 * - Secondly, take the average of the different seasonal indices found for the same periods. So
	 * if both 2019 and 2018 data is present, there are two weeks 0. The final seasonal index for week
	 * 0 is the average of them.
	 */
	private void findSeasonalIndices() {
		seasonalIndices = new double[nSeasons];
		// Initialize an array in which to safe the temporarily seasonal indices.
		double[] SI = new double[nWeeks];
		for(int i = 0; i < nWeeks; i++) {
			// Calculate the moving average mean for each week
			int lb = Math.max(i - (nSeasons/2), 0);
			int ub = (int) Math.min(i + Math.ceil((double) nSeasons/2), nWeeks);
			if(ub - lb < nSeasons) {
				if(lb == 0) {
					ub = nSeasons;
				} else {
					lb = nWeeks - nSeasons;
				}
			}
			double mean = mean(weeklySales, lb, ub);
			// Calculate seasonal index
			SI[i] = weeklySales[i] / mean;
		}
		// Average the seasonal indices for the same periods
		double sum2 = 0.0;
		for(int i = 0; i < nSeasons; i++) {
			int n = 0;
			double sum = 0.0;
			for(int j = i; j < nWeeks; j+=nSeasons) {
				sum += SI[j];
				n++;
			}
			seasonalIndices[i] = sum / n;
			sum2 += seasonalIndices[i];
		}
		// Normalize the indices
		double c = nSeasons / sum2;
		for(int i = 0; i < nSeasons; i++) {
			seasonalIndices[i] *= c;
		}
	}
	
	
	/**
	 * This method deseasonalizes the data. It does so by dividing the weekly data by the
	 * corresponding seasonal index.
	 * @return
	 */
	private double[] deseasonalizeData() {
		double[] result = new double[nWeeks];
		for(int i = 0; i < nSeasons; i++) {
			for(int j = i; j < nWeeks; j+=nSeasons) {
				result[j] = weeklySales[j] / seasonalIndices[i];
			}
		}
		return result;
	}
	
	
	/**
	 * This method performs OLS on the deseasonalized data in order to find the level and the trend.
	 * @param deseasonalizedData
	 * @return
	 */
	private double[] findLevelAndTrend(double[] deseasonalizedData) {
		double[] result = new double[2];
		// x will in our case be the time
		double[] x = IntStream.range(0, nWeeks).mapToDouble(i -> (double) i).toArray();

        // first calculate the mean of x and y
        double sumx = 0.0;
        double sumy = 0.0;
        for (int i = 0; i < nWeeks; i++) {
            sumx  += x[i];
            sumy  += deseasonalizedData[i];
        }
        double xbar = sumx / nWeeks;
        double ybar = sumy / nWeeks;

        // now calculate the slope and level cov(x,x) and cov(x,y)
        double xxbar = 0.0;
        double xybar = 0.0;
        for (int i = 0; i < nWeeks; i++) {
            xxbar += (x[i] - xbar) * (x[i] - xbar);
            xybar += (x[i] - xbar) * (deseasonalizedData[i] - ybar);
        }
        double slope  = xybar / xxbar;
        double intercept = ybar - slope * xbar;
        
        result[0] = intercept;
        result[1] = slope;
		
		return result;
	}
	
	
	/**
	 * This method removes the level and the trend from the data.
	 * @param level
	 * @param trend
	 * @param deseasonalizedData
	 */
	private void delevelAndDetrendData(double[] deseasonalizedData) {
		cleanedSales = new double[nWeeks];
		for(int i = 0; i < nWeeks; i++) {
			cleanedSales[i] = deseasonalizedData[i] / (level + i * trend);
		}
	}
	
	
	/**
	 * This method 
	 * @param week, the week for which a sales value needs to be estimated after week 52 of 2019.
	 * So week 0 means week 1 of 2020
	 * @param r the random object used to generate normally distributed value with.
	 * @return a sales value as integer
	 */
	public int pullRandomSales(int week, Random r) {
		int season = week % nSeasons;
		double levelAndTrend = level + (nWeeks + week) * trend;
		
		// Pull from normal distribution
		double x = r.nextGaussian() * cleanedStdev +  cleanedMean;
		if(x < 0) {
			x = 0;
		}
		
		int sales = (int) Math.round(levelAndTrend * seasonalIndices[season] * x);
		
		return sales;
	}
	
	
	public int getPredictedDemand(int week) {
		int season = week % nSeasons;
		double levelAndTrend = level + (nWeeks + week) * trend;
		
		int sales = (int) Math.round(levelAndTrend * seasonalIndices[season]);
		if (sales < 0) {
			sales = 0;
		}
		
		return sales;
	}
	
	
	
	
	/**
	 * This method calculates the mean of a partial array without the need of
	 * Copying the partial array to a new array.
	 * @param arr
	 * @param lb
	 * @param ub
	 * @return
	 */
	private double mean(int[] arr, int lb, int ub) {
		double mean = 0.0;
		int n = 0;
		for(int i = lb; i < ub; i++) {
			if(dataPresent[i]) {
				mean += arr[i];
				n++;
			}
		}
		return mean/n;
	}
	
	
	//-----------------------------------------------------------
	// This part contains the code to clean the data
	//-----------------------------------------------------------

	/**
	 * This method cleans al the time series data. It does so by first calculating upper and lower bounds
	 * based on mu +/- 3.3*sigma. If a value falls outside this interval, it is removed. This procedure is
	 * repeated until no more data points fall outside the 99% confidence interval. Finally, all removed
	 * values are set equal to the mean of the time series.
	 */
	public int[] cleanTimeSeriesData() {
		int[] modCount = new int[3];
		modCount[0] = cleanSales();
		modCount[1] = cleanVolume();
		modCount[2] = cleanPrices();
		return modCount;
	}

	
	private int cleanSales() {
		int modCount = 0;
		for(int i = 0; i < dataPresent.length; i++) {
			if(dataPresent[i] && weeklySales[i] == 0) {
				weeklySales[i] = Integer.MAX_VALUE;
				modCount++;
			}
		}

		double mean = mean(weeklySales);

		for(int i = 0; i < dataPresent.length; i++) {
			if(dataPresent[i] && weeklySales[i] == Integer.MAX_VALUE) {
				weeklySales[i] = (int) mean;
			}
		}
		return modCount;
	}


	/**
	 * The way the cleaning works is as follows:
	 * First a interval is calculated
	 * Than all values are checked. If a value falls outside the interval, it is set to infinity
	 * These two steps are repeated until not values fall outside the interval
	 * Finally, all values that were set to infinity, are now set to the mean
	 * 
	 * When calculating new mean and stdev, all infinity values are excluded
	 */
	private int cleanVolume() {
		int modCount = 0;
		double mean = mean(weeklyAverageM3);
		boolean clean = false;
		while(!clean) {
			// Calculate mean and stdev
			double stdev = stdev(weeklyAverageM3, mean);
			double LB = Math.max(mean - z*stdev, 0.0);
			double UB = mean + z*stdev;
			
//			if(chunk.substring(0, 2).equals("Sp")) {
//				System.out.println(productGroup + " " + chunk + " " + sizeGroup + " LB,UB: " + LB + "," + UB);
//			}
//			if(chunk.equals("Speelgoedbarbecue")) {
//				System.out.println(productGroup + " " + chunk + " " + sizeGroup + " LB,UB: " + LB + "," + UB);
//			}
			
			// Remove values outside bounds
			// Use open bounds, so (LB, UB) instead of [LB, UB]

			boolean modified = false;
			for(int i = 0; i < dataPresent.length; i++) {
				if(dataPresent[i] && (!(weeklyAverageM3[i] > LB && weeklyAverageM3[i] < UB) && weeklyAverageM3[i] != Double.MAX_VALUE)) {
					weeklyAverageM3[i] = Double.MAX_VALUE;
					modified = true;
					modCount++;
				}
			}
			// If the list was not modified, it is now clean
			// Else, do another iteration after calculating the new mean
			if(!modified) {
				clean = true;
			} else {
				mean = mean(weeklyAverageM3);
			}
		}
		// If data has been removed, set all removed data equal to the mean
		if(modCount > 0) {
			for(int i = 0; i < dataPresent.length; i++) {
				if(dataPresent[i] && weeklyAverageM3[i] == Double.MAX_VALUE) {
					weeklyAverageM3[i] = mean;
				}
			}
			clean = true;
		}
		return modCount;
	}


	private int cleanPrices() {
		int modCount = 0;
		double mean = mean(weeklyAveragePrice);
		boolean clean = false;
		while(!clean) {
			// Calculate mean and stdev
			double stdev = stdev(weeklyAveragePrice, mean);
			double LB = Math.max(mean - z*stdev, 0.0);
			double UB = mean + z*stdev;

			// Remove values outside bounds
			// Use open bounds, so (LB, UB) instead of [LB, UB]
//			
//			if(chunk.equals("Educatief spel")) {
//				System.out.println(productGroup + " " + chunk + " " + sizeGroup + " LB,UB: " + LB + "," + UB);
//			}

			boolean modified = false;
			for(int i = 0; i < dataPresent.length; i++) {
				if(dataPresent[i] && (!(weeklyAveragePrice[i] > LB && weeklyAveragePrice[i] < UB) && weeklyAveragePrice[i] != Double.MAX_VALUE)) {
//					System.out.print("Removing: " + weeklyAveragePrice[i] + " ");
//					System.out.println(productGroup + " " + chunk + " " + sizeGroup + " LB,UB: " + LB + "," + UB + " stdev: " + stdev);
					weeklyAveragePrice[i] = Double.MAX_VALUE;
					modified = true;
					modCount++;
				}
			}
			// If the list was not modified, it is now clean
			// Else, do another iteration after calculating the new mean
			if(!modified) {
				clean = true;
			} else {
				mean = mean(weeklyAveragePrice);
			}
		}
		// If data has been removed, set all removed data equal to the mean
		if(modCount > 0) {
			for(int i = 0; i < dataPresent.length; i++) {
				if(dataPresent[i] && weeklyAveragePrice[i] == Double.MAX_VALUE) {
					weeklyAveragePrice[i] = mean;
				}
			}
			clean = true;
		}
		return modCount;
	}


	/**
	 * This method calculate the mean of an integer array
	 * @param arr
	 * @return
	 */
	private double mean(int[] arr) {
		double mean = 0.0;
		int n = 0;
		// Problem is that the given arr is not the same length as dataPresent
		for(int i = 0; i < dataPresent.length; i++) {
			if(dataPresent[i] && arr[i] > 0 && arr[i] < Integer.MAX_VALUE) {
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
			if(dataPresent[i] && arr[i] > 0 && arr[i] < Double.MAX_VALUE) {
				mean += arr[i];
				n++;
			}
		}
		mean = mean / (double) n;
		return mean;
	}

//	/**
//	 * This method calculates the standard deviation for an integer array
//	 * @param arr
//	 * @param mean
//	 * @return
//	 */
//	private double stdev(int[] arr, double mean) {
//		double stdev = 0.0;
//		int n = 0;
//		for(int i = 0; i < dataPresent.length; i++) {
//			if(dataPresent[i] && arr[i] > 0 && arr[i] < Integer.MAX_VALUE) {
//				stdev += Math.pow((double)(arr[i] - mean), 2.0);
//				n++;
//			}
//		}
//		stdev = Math.sqrt(stdev / (double) (n - 1));
//		return stdev;
//	}

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
			if(dataPresent[i] && arr[i] > 0 && arr[i] < Double.MAX_VALUE) {
				stdev += Math.pow((double)(arr[i] - mean), 2.0);
				n++;
			}
		}
		if(n > 1) {
			stdev = Math.sqrt(stdev / (double) (n - 1));
		} else {
			// If only one observation is present, don't modify it by setting stdev equal to 1.
			// In this way, the one observation will always lay in the interval
			stdev = 1;
		}
		// This is needed in case there is no variance, because the interval is open
		if(stdev == 0) {
			stdev = 1;
		}
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
	 * This method returns the average averageM3
	 * This is used for 2020
	 * @param week goes from 0 to 51
	 * @return averageM3 as double
	 */
	public double getAverageAverageM3() {
		int count = 0; 
		double sum = 0; 
		for (double d : weeklyAverageM3) {
			if (d > 0.0000000000) {
				count ++;
				sum += d;
			}
		}
		
		if (count == 0) {
			return 0;
		}
		
		return sum/count;
	}
	
	/**
	 * This method returns the average average price
	 * This is used for 2020
	 * 	 * @param week goes from 1 to 52
	 * @return average price as double
	 */
	public double getAverageAveragePrice() {
		int count = 0; 
		double sum = 0; 
		for (double d : weeklyAveragePrice) {
			if (d > 0.0000) {
				count ++;
				sum += d;
			}
		}
		if (count == 0) {
			return 0;
		}
		return sum/count;
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


	public double[] getSeasonalIndices() {
		return seasonalIndices;
	}


	public void setSeasonalIndices(double[] seasonalIndices) {
		this.seasonalIndices = seasonalIndices;
	}


	public double[] getCleanedSales() {
		return cleanedSales;
	}


	public void setCleanedSales(double[] cleanedSales) {
		this.cleanedSales = cleanedSales;
	}


	public double getCleanedMean() {
		return cleanedMean;
	}


	public void setCleanedMean(double cleanedMean) {
		this.cleanedMean = cleanedMean;
	}


	public double getCleanedStdev() {
		return cleanedStdev;
	}


	public void setCleanedStdev(double cleanedStdev) {
		this.cleanedStdev = cleanedStdev;
	}


	public double getLevel() {
		return level;
	}


	public void setLevel(double level) {
		this.level = level;
	}


	public double getTrend() {
		return trend;
	}


	public void setTrend(double trend) {
		this.trend = trend;
	}
	
	
}
