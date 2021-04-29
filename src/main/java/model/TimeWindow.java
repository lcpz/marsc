package model;

import java.io.Serializable;

/**
 * A time window record.
 *
 * @author lcpz
 */
public class TimeWindow implements Serializable {

	private static final long serialVersionUID = 1L;

	public final int earliestTime, softLatestTime, hardLatestTime;
	public final String str;

	public TimeWindow(int earliestTime, int softLatestTime, int hardLatestTime) {
		try {
			if (earliestTime < 0)
				throw new Exception(String.format("negative earliest time: %d", earliestTime));
			if (softLatestTime < 0)
				throw new Exception(String.format("negative soft latest time: %d", softLatestTime));
			if (hardLatestTime < 0)
				throw new Exception(String.format("negative hard latest time: %d", hardLatestTime));
			if (earliestTime > softLatestTime)
				throw new Exception(String.format("earliest time %d > soft latest time %d", earliestTime, softLatestTime));
			if (softLatestTime > hardLatestTime)
				throw new Exception(String.format("soft latest time %d > hard latest time %d", softLatestTime, hardLatestTime));
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.earliestTime = earliestTime;
		this.softLatestTime = softLatestTime;
		this.hardLatestTime = hardLatestTime;
		this.str = String.format("[alpha = %d, beta = %d, gamma = %d]", earliestTime, softLatestTime, hardLatestTime);
	}

	@Override
	public String toString() {
		return str;
	}

}