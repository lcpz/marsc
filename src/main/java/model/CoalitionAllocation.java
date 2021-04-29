package model;

import java.util.Arrays;

import org.apache.commons.lang3.ArrayUtils;

import locations.Location;

public class CoalitionAllocation {

	public final Task task;
	public final Location location;
	public final Agent[] coalition;

	/**
	 * The times at which this coalition respectively starts and ends to work on the
	 * task.
	 */
	public final int startTime, endTime;

	public final float value;

	/* String representation. */
	private String str;

	public CoalitionAllocation(Task task, Location location, Agent[] coalition, float value, int startTime, int endTime) {
		try {
			if (task == null)
				throw new Exception("null task");
			if (location == null || !ArrayUtils.contains(task.demand.possibleLocations, location))
				throw new Exception("null or invalid task location");
			if (coalition == null || coalition.length == 0)
				throw new Exception("invalid agent array");
			if (startTime < 0)
				throw new Exception("negative start time");
			if (endTime < 0)
				throw new Exception("negative end time");
			if (startTime > endTime)
				throw new Exception(String.format("start time is greater than end time [%s, %s]", startTime, endTime));
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.task = task;
		this.location = location;
		this.coalition = coalition;
		this.value = value;
		this.startTime = startTime;
		this.endTime = endTime;
	}

	@Override
	public String toString() {
		if (str != null)
			return str;

		StringBuilder s = new StringBuilder();

		if (startTime < endTime)
			s.append(String.format("%d - %d: ", startTime, endTime));
		else
			s.append(String.format("%d: ", startTime));

		int interval = endTime - startTime + 1;
		s.append(String.format("%s <w = %.2f, loc = %s, %s> -> %s, work done: %.2f x %d = %.2f", task,
				task.demand.workload, location, task.demand.timeWindow, Arrays.toString(coalition), value,
				interval, interval * value));

		str = s.toString();

		return str;
	}

	public boolean equals(CoalitionAllocation ca) {
		if (this == ca)
			return true;

		if (task.equals(ca.task))
			if (location.equals(ca.location))
				if (startTime == ca.startTime)
					if (endTime == ca.endTime)
						if (Arrays.equals(coalition, ca.coalition))
							return true;
		return false;
	}

}