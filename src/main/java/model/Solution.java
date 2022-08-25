package model;

import java.util.*;

import org.apache.commons.math3.stat.descriptive.rank.Median;

/**
 * A set of coalition allocations and related metrics.
 *
 * @author lcpz
 */
public class Solution {

	/**
	 * Sort the coalition allocations of this solution by time, for human
	 * readability.
	 */
	public static final Comparator<CoalitionAllocation> SortByStartTime = (a, b) -> {
		if (a.startTime == b.startTime) {
			if (a.endTime == b.endTime)
				return -1; // put A first
			return a.endTime - b.endTime;
		}

		return a.startTime - b.startTime;
	};

	/* The tasks completed by this solution. */
	public Set<Task> tasks;

	/* The agents involved in this solution. */
	public Set<Agent> agents;

	/* The coalition allocations that defines this solution. */
	public CoalitionAllocation[] coalitionAllocations;

	/* The completion time of each task. */
	public Map<Task, Integer> taskCompletionTime;

	/* Solution score, and median score per task. */
	private float score = -1, medianSingletonScore = -1;

	public Solution(Set<Task> tasks, CoalitionAllocation[] coalitionAllocations) {
		this.tasks = tasks;
		this.coalitionAllocations = coalitionAllocations;

		agents = new HashSet<>();
		for (CoalitionAllocation ca : coalitionAllocations)
			Collections.addAll(agents, ca.coalition);
	}

	public Solution(Set<Task> tasks, CoalitionAllocation[] coalitionAllocations, float score) {
		this(tasks, coalitionAllocations);
		this.score = score;
	}

	/**
	 * Return the end time of the last coalition allocation.
	 *
	 * @return an integer
	 */
	public int getLastWorkingTime() {
		return coalitionAllocations[coalitionAllocations.length - 1].endTime;
	}

	/**
	 * Checks if this solution completes the same tasks completed by
	 * <code>solution</code>.
	 *
	 * <b>Warning:</b> two solutions might complete the same tasks, but be different
	 * sets of coalition allocations. To check if two solutions have the exact same
	 * set of coalition allocations, use {@link #equals(Solution)}.
	 *
	 * @param solution A solution.
	 * @return A boolean.
	 */
	public boolean shallowEquals(Solution solution) {
		if (this == solution)
			return true;
		return tasks.equals(solution.tasks);
	}

	public boolean equals(Solution solution) {
		if (shallowEquals(solution))
			return Arrays.equals(coalitionAllocations, solution.coalitionAllocations);
		return false;
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder(
			String.format("Solution [tasks = %d, score = %s, median singleton score = %s]\n", tasks.size(), score, medianSingletonScore));
		for (CoalitionAllocation ca : coalitionAllocations)
			s.append(ca).append("\n");
		return s.toString();
	}

	public Solution sort() {
		Arrays.sort(coalitionAllocations, SortByStartTime);
		return this;
	}

	/**
	 *
	 * Returns the (non-negative) score (or score) of this solution.
	 *
	 * @return A long integer.
	 */
	public float getScore(boolean compute) {
		if (compute) {
			HashMap<Integer, Double> m = new HashMap<>();
			for (Task v : tasks)
				m.put(v.id, 0d);

			score = 0;
			float marginalScore, profit;
			int i, beta, gamma;
			for (CoalitionAllocation ca : coalitionAllocations)
				for (i = ca.startTime; i <= ca.endTime; i++)
					if (i <= ca.task.demand.timeWindow.softLatestTime) {
						score += ca.task.demand.profit;
						m.put(ca.task.id, m.get(ca.task.id) + ca.task.demand.profit);
					} else {
						beta = ca.task.demand.timeWindow.softLatestTime;
						gamma = ca.task.demand.timeWindow.hardLatestTime;
						profit = ca.task.demand.profit;
						marginalScore = (float) (1 - ((i - beta)/(gamma - beta + 1))) * profit;
						score += marginalScore;
						m.put(ca.task.id, m.get(ca.task.id) + marginalScore);
					}

			i = 0;
			double[] singletonScores = new double[tasks.size()];
			for (Double d : m.values())
				singletonScores[i++] = d;

			medianSingletonScore = (float) new Median().evaluate(singletonScores);
		}

		return score;
	}

	public float getScore() {
		return getScore(true);
	}

	public float getMedianSingletonScore() {
		if (medianSingletonScore == -1)
			getScore(true);

		return medianSingletonScore;
	}

}