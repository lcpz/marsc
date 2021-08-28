package solvers;

import java.util.*;

import locations.Location;
import model.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import toolkit.Utils;

/**
 * Bounded Node Traversal (BNT) algorithm.
 *
 * @author lcpz
 */
public class BNT extends Solver {

	protected float[] optimalSingletonScores;
	protected Map<Integer, Float> singletonScores;

	public BNT(MARSC problem) {
		super(problem);
		singletonScores = new HashMap<>();
	}

	protected Solution getSingletonSolution(Task task, Location location, Set<Agent> assignableAgents) {
		/*
		 * We assume that task has no uncompleted precedence, and that assignableAgents
		 * satisfies the spatial constraints. Since we are only defining a solution to
		 * just one task, the structural constraints are already satisfied. Hence, we
		 * only have to verify the temporal constraints.
		 */

		float workload = task.demand.workload;
		int earliestTime = task.demand.timeWindow.earliestTime;
		int hardLatestTime = task.demand.timeWindow.hardLatestTime;

		/*
		 * Not all agents might arrive at the same time, hence there might be some
		 * agents working on the task before others arrive. We have to capture their
		 * contribution.
		 */

		List<CoalitionAllocation> l = new LinkedList<>();
		Agent[] coalition = assignableAgents.toArray(new Agent[0]);
		Agent[] subCoalition;
		float workloadDone = 0, score = 0, subCoalitionValue, contribution;
		int endTime, startTime;

		for (int i = 0; i < coalition.length; i++) { // already sorted
			subCoalition = ArrayUtils.subarray(coalition, 0, i + 1);
			subCoalitionValue = problem.getValue(task, location, subCoalition);

			if (i + 1 <  coalition.length)
				contribution = (coalition[i + 1].arrivalTime - coalition[i].arrivalTime) * subCoalitionValue;
			else
				contribution = (hardLatestTime - coalition[i].arrivalTime) * subCoalitionValue;
			workloadDone += contribution;
			endTime = (int) Math.ceil(coalition[i].arrivalTime + ((workload - (workloadDone - contribution)) / subCoalitionValue)) - 1;
			startTime = coalition[i].arrivalTime;
			if (startTime < earliestTime)
				startTime = earliestTime;
			if (endTime < startTime)
				endTime = startTime;
			if (workloadDone < workload) {
				if (i + 1 < coalition.length) {
					l.add(new CoalitionAllocation(task, location, subCoalition, subCoalitionValue, startTime, coalition[i + 1].arrivalTime));
					score += getMarginalScore(task, startTime, coalition[i + 1].arrivalTime);
				}
			} else {
				l.add(new CoalitionAllocation(task, location, subCoalition, subCoalitionValue, startTime, endTime));
				score += getMarginalScore(task, startTime, endTime);

				if (endTime <= hardLatestTime) {
					Solution newSolution = new Solution(Set.of(task), l.toArray(new CoalitionAllocation[0]), score);
					newSolution.taskCompletionTime = new HashMap<>();
					newSolution.taskCompletionTime.put(task, endTime);
					return newSolution;
				}

				break;
			}
		}

		return null;
	}

	public static Comparator<Agent> byArrivalTime = Comparator.comparingInt(a -> a.arrivalTime);

	public TreeSet<Agent> getAssignable(Task v, Location location, Agent[] agents) {
		int earliestTime = v.demand.timeWindow.earliestTime;
		int hardLatestTime = v.demand.timeWindow.hardLatestTime;
		int arrivalTime;

		TreeSet<Agent> assignableAgents = new TreeSet<>(byArrivalTime);

		for (Agent a : agents) {
			arrivalTime = a.endTime + a.getTravelTimeTo(location);
			if (arrivalTime <= hardLatestTime) { // a satisfies the spatial constraints of (v, location)
				a.arrivalTime = Math.max(arrivalTime, earliestTime);
				assignableAgents.add(a);
			}
		}

		return assignableAgents;
	}

	protected Solution getSingletonSolution(Task v, Agent[] agents) {
		Solution currentSolution, bestSolution = null;
		TreeSet<Agent> assignableAgents;

		for (Location location : v.demand.possibleLocations) {
			assignableAgents = getAssignable(v, location, agents);

			if (assignableAgents != null && assignableAgents.size() > 0) { // satisfy the temporal constraints
				currentSolution = getSingletonSolution(v, location, assignableAgents);
				if (currentSolution != null)
					if (bestSolution == null || currentSolution.getScore(true) > bestSolution.getScore(true))
						bestSolution = currentSolution;
			}
		}

		return bestSolution;
	}

	protected void updateAgentStatus(Solution solution) {
	    if (solution != null && solution.coalitionAllocations.length > 0) {
			CoalitionAllocation lastCA = solution.coalitionAllocations[solution.coalitionAllocations.length - 1];
			for (Agent a : lastCA.coalition) {
				a.endTime = lastCA.endTime;
				a.location = lastCA.location;
			}
		}
	}

	@Override
	public void solve() {
		Set<Task> taskSet = new TreeSet<>(comparator);

		Collections.addAll(taskSet, problem.getTasks());

		HashSet<Task> completedTasks = new HashSet<>();
		HashSet<CoalitionAllocation> l = new HashSet<>();
		Solution currentSolution, bestSolution;

		for (int i = 0; i < tasks.length; i++) { // define i-th singleton solution
			bestSolution = null;
			for (Task v : taskSet) {
				if (v.status.equals(Task.Status.COMPLETED))
					continue;

				currentSolution = getSingletonSolution(v, agents);
				if (currentSolution != null && (bestSolution == null || currentSolution.getScore(false) > bestSolution.getScore(false)))
					bestSolution = currentSolution;
			}

			if (bestSolution == null)
				break; // no singleton solution for j >= i

			singletonScores.put(bestSolution.tasks.iterator().next().id, bestSolution.getScore(false));
			updateAgentStatus(bestSolution);
			completedTasks.addAll(bestSolution.tasks);
			for (Task v : bestSolution.tasks)
				v.status = Task.Status.COMPLETED;
			Collections.addAll(l, bestSolution.coalitionAllocations);
		}

		solution = new Solution(completedTasks, l.toArray(new CoalitionAllocation[0]));
	}

	@Override
	public float getAnytimeQualityIndex() {
		return 0;
	}

	public float[] getOptimalSingletonScores() {
		if (optimalSingletonScores == null) {
			optimalSingletonScores = new float[tasks.length];

			 // For each task v, compute the optimal score of the sub-problem where there is only v
			Agent[] agentsCopy = Utils.deepClone(agents);
			Solution s;
			for (int i = 0; i < tasks.length; i++) {
				s = getSingletonSolution(tasks[i], agentsCopy);
				if (s != null)
					optimalSingletonScores[i] = s.getScore(false);
				for (Agent a : agentsCopy)
					a.arrivalTime = 0;
			}
		}

		return optimalSingletonScores;
	}

	public Double getMedianApproximationScoreRatio(float[] optimalSingletonScores) {
		double[] r = new double[tasks.length];
		Float singletonScore;

		for (int i = 0; i < tasks.length; i++) {
		    singletonScore = singletonScores.get(i);
			if (singletonScore != null && Math.abs(optimalSingletonScores[i]) > 0)
				r[i] = Math.abs(singletonScore / optimalSingletonScores[i]);
		}

		//ArrayUtils.removeAllOccurrences(r, 0);

		return new Median().evaluate(r);
	}

	public Double getMedianApproximationScoreRatio() {
		return getMedianApproximationScoreRatio(getOptimalSingletonScores());
	}
}
