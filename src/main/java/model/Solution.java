package model;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
	public static final Comparator<CoalitionAllocation> SortByStartTime = new Comparator<>() {
		public int compare(CoalitionAllocation a, CoalitionAllocation b) {
			if (a.startTime == b.startTime) {
				if (a.endTime == b.endTime)
					return -1; // put A first
				return a.endTime - b.endTime;
			}

			return a.startTime - b.startTime;
		}
	};

	/* The tasks completed by this solution. */
	public Set<Task> tasks;

	/* The agents involved in this solution. */
	public Set<Agent> agents;

	/* The coalition allocations that defines this solution. */
	public CoalitionAllocation[] coalitionAllocations;

	/* The completion time of each task. */
	public Map<Task, Integer> taskCompletionTime;

	/* The maximum time unit at which each agent in the solution is free.
	 *
	 * That is, ca.endTime + 1, where ca is the last coalition allocation
	 * in which agent a appears. */
	public Map<Agent, CoalitionAllocation> lacaMap;

	/*
	 * Solution quality: the $\Delta$ score, where 0 means unfeasible and -1 means
	 * not computed.
	 */
	public float degree = -1;

	/*
	 * Like <code>degree</code>, but it also takes into account how early tasks are
	 * completed in the solution.
	 */
	public float earliness = -1;

	public long score = -1, medianSingletonScore = -1;

	public Solution(Set<Task> tasks, CoalitionAllocation[] coalitionAllocations, float degree, float earliness) {
		this.tasks = tasks;
		this.coalitionAllocations = coalitionAllocations;
		this.degree = degree >= -1 ? degree : -1;
		this.earliness = earliness >= -1 ? earliness : -1;

		agents = new HashSet<>();
		for (CoalitionAllocation ca : coalitionAllocations)
			for (Agent a : ca.coalition)
				agents.add(a);
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
				String.format("Solution [tasks = %d, degree = %s, earliness = %s]\n", tasks.size(), degree, earliness));
		for (CoalitionAllocation ca : coalitionAllocations)
			s.append(ca + "\n");
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
	public long getScore() {
		if (score == -1) {
			HashMap<Integer, Double> m = new HashMap<>();
			for (Task v : tasks)
				m.put(v.id, Double.valueOf(0d));

			score = 0;
			int i;
			for (CoalitionAllocation ca : coalitionAllocations)
				for (i = ca.startTime; i <= ca.endTime; i++)
					if (i <= ca.task.demand.timeWindow.softLatestTime) {
						score += i * ca.task.demand.profit;
						m.put(ca.task.id, m.get(ca.task.id) + i * ca.task.demand.profit);
					} else {
						score += ca.task.demand.profit;
						m.put(ca.task.id, m.get(ca.task.id) + ca.task.demand.profit);
					}

			i = 0;
			double[] singletonScores = new double[tasks.size()];
			for (Double d : m.values())
				singletonScores[i++] = d.doubleValue();

			medianSingletonScore = (long) new Median().evaluate(singletonScores);
		}

		return score;
	}

	public long getMedianSingletonScore() {
		if (medianSingletonScore == -1)
			getScore();

		return medianSingletonScore;
	}

}
