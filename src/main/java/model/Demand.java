package model;

import java.io.Serializable;

import locations.Location;

/**
 * A task demand record.
 *
 * @author lcpz
 */
public class Demand implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * The set of possible locations at which the task can be executed.
	 *
	 * For example, a rescued person can be taken to a number of different hospitals.
	 *
	 * This array never changes.
	 *
	*/
	public final Location[] possibleLocations;

	public float workload, profit;

	public final TimeWindow timeWindow;

	public Demand(Location[] possibleLocations, float workload, float profit, TimeWindow timeWindow) {
		try {
			if (possibleLocations == null || possibleLocations.length == 0)
				throw new Exception("null or empty task location array");
			if (workload < 0)
				throw new Exception(String.format("negative workload: %f", workload));
			if (profit < 0)
				throw new Exception(String.format("negative profit: %f", profit));
			if (timeWindow == null)
				throw new Exception("null or empty time window");
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.possibleLocations = possibleLocations;
		this.workload = workload;
		this.profit = profit;
		this.timeWindow = timeWindow;
	}

}