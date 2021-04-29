package solvers;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import locations.Location;
import model.Agent;
import model.CoalitionAllocation;
import model.MARSC;
import model.Solution;
import model.Task;

/**
 * Bounded Node Traversal (BNT) algorithm.
 *
 * @author lcpz
 */
public class BNT extends Solver {

	protected Set<Task> taskSet;

	public BNT(MARSC problem) {
		super(problem);
	}

	protected Solution getSingletonSolution(Task task, Location location, TreeMap<Integer, Set<Agent>> assignablesMap) {
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
		float workloadDone = 0;
		Integer[] arrivalTimes = assignablesMap.keySet().toArray(new Integer[assignablesMap.size()]);
		int j;
		Set<Agent> subCoalition;
		Agent[] subCoalitionArr;

		for (int i = 0; i < arrivalTimes.length; i++) { // already sorted
			subCoalition = assignablesMap.get(arrivalTimes[i]);
			for (j = i - 1; j >= 0; j--)
				subCoalition.addAll(assignablesMap.get(arrivalTimes[j]));
			subCoalitionArr = subCoalition.toArray(new Agent[subCoalition.size()]);
			float subCoalitionValue = problem.getValue(task, location, subCoalitionArr);
			float contribution;
			if (i + 1 < arrivalTimes.length)
				contribution = (arrivalTimes[i + 1] - arrivalTimes[i]) * subCoalitionValue;
			else
				contribution = (hardLatestTime - arrivalTimes[i]) * subCoalitionValue;
			workloadDone += contribution;
			int endTime = arrivalTimes[i]
					+ (int) Math.ceil((workload - (workloadDone - contribution)) / subCoalitionValue);
			int startTime = arrivalTimes[i];
			if (startTime <= earliestTime)
				startTime = earliestTime;
			else
				startTime++;
			if (workloadDone < workload) {
				if (i + 1 < arrivalTimes.length)
					l.add(new CoalitionAllocation(task, location, subCoalitionArr, subCoalitionValue, startTime,
							arrivalTimes[i + 1]));
				else
					l.add(new CoalitionAllocation(task, location, subCoalitionArr, subCoalitionValue, startTime,
							endTime));
			} else { // minimal set of coalition allocations, stop
				if (endTime < startTime)
					endTime = startTime;

				l.add(new CoalitionAllocation(task, location, subCoalitionArr, subCoalitionValue, startTime, endTime));

				if (endTime <= hardLatestTime)
					return new Solution(Set.of(task), l.toArray(new CoalitionAllocation[l.size()]), -1, -1);

				break;
			}
		}

		return null;
	}

	public static Comparator<Integer> intCmp = new Comparator<>() {
		@Override
		public int compare(Integer i1, Integer i2) {
			return i1 - i2;
		}
	};

	public TreeMap<Integer, Set<Agent>> getAssignables(Task v, Location location) {
		TreeMap<Integer, Set<Agent>> assignablesMap = new TreeMap<>(intCmp);

		int earliestTime = v.demand.timeWindow.earliestTime;
		int hardLatestTime = v.demand.timeWindow.hardLatestTime;
		int arrivalTime;

		Set<Agent> s;
		for (Agent a : agents) {
			arrivalTime = a.endTime + a.getTravelTimeTo(location);
			if (arrivalTime < hardLatestTime) {
				if (arrivalTime < earliestTime)
					a.arrivalTime = earliestTime;
				else
					a.arrivalTime = arrivalTime;
				s = assignablesMap.get(a.arrivalTime);
				if (s == null)
					s = new HashSet<>();
				s.add(a); // a satisfies the spatial constraints of (v, location)
				assignablesMap.put(a.arrivalTime, s);
			}
		}

		return assignablesMap;
	}

	protected Solution getSingletonSolution(Task v) {
		Solution currentSolution, bestSolution = null;
		TreeMap<Integer, Set<Agent>> assignablesMap;

		for (Location location : v.demand.possibleLocations) {
			assignablesMap = getAssignables(v, location);

			if (assignablesMap != null && assignablesMap.size() > 0) { // satisfy the temporal constraints
				currentSolution = getSingletonSolution(v, location, assignablesMap);
				if (currentSolution != null)
					/* Maximise the objective function by picking a
					 * singleton solution that completes its task
					 * in the shortest amount of time. */
					if (bestSolution == null || currentSolution.getLastWorkingTime() < bestSolution.getLastWorkingTime())
						bestSolution = currentSolution;
			}
		}

		return bestSolution;
	}

	protected void updateAgentStatus(Solution solution) {
		CoalitionAllocation lastCA = solution.coalitionAllocations[solution.coalitionAllocations.length - 1];
		for (Agent a : lastCA.coalition) {
			a.endTime = lastCA.endTime;
			a.location = lastCA.location;
		}
	}

	public final Comparator<Task> comparator = new Comparator<>() {
		@Override
		public int compare(Task v1, Task v2) {
			if (order != null) { // satisfy ordering constraints
				Task prec1 = order.get(v1);
				Task prec2 = order.get(v2);
				if (prec1 != null && prec1.equals(v2))
					return 1;
				if (prec2 != null && prec2.equals(v1))
					return -1;
			}

			if (v1.demand.timeWindow.earliestTime != v2.demand.timeWindow.earliestTime)
				return v1.demand.timeWindow.earliestTime - v2.demand.timeWindow.earliestTime;

			return v1.demand.timeWindow.hardLatestTime - v2.demand.timeWindow.hardLatestTime;
		}
	};

	@Override
	public void solve() {
		taskSet = new TreeSet<>(comparator);

		for (Task v : problem.getTasks())
			taskSet.add(v);

		HashSet<Task> completedTasks = new HashSet<>();
		HashSet<CoalitionAllocation> l = new HashSet<>();
		Solution currentSolution, bestSolution;

		for (int i = 0; i < tasks.length; i++) { // define i-th singleton solution
			bestSolution = null;
			for (Task v : taskSet) {
				if (v.status.equals(Task.Status.COMPLETED))
					continue;

				currentSolution = getSingletonSolution(v);
				if (currentSolution != null && (bestSolution == null || currentSolution.getLastWorkingTime() < bestSolution.getLastWorkingTime()))
					bestSolution = currentSolution;
			}

			if (bestSolution == null)
				break; // no singleton solution for j >= i

			updateAgentStatus(bestSolution);
			completedTasks.addAll(bestSolution.tasks);
			for (Task v : bestSolution.tasks)
				v.status = Task.Status.COMPLETED;
			for (CoalitionAllocation ca : bestSolution.coalitionAllocations)
				l.add(ca);
		}

		solution = new Solution(completedTasks, l.toArray(new CoalitionAllocation[l.size()]), -1, -1);
	}

}