package solvers;

import java.util.*;

import locations.Location;
import model.Agent;
import model.CoalitionAllocation;
import model.MARSC;
import model.Solution;
import model.Task;
import org.apache.commons.math3.stat.descriptive.rank.Median;

/**
 * Cluster-based Task Scheduling (CTS) algorithm.
 *
 * Augmented to solve MARSC instances.
 *
 * @author lcpz
 */
public class CTS extends Solver {

	/* Current problem time, starting at 0. */
	protected int currentTime;

	protected Solution[] singletonSolutions;

	public CTS(MARSC problem) {
		super(problem);

		for (Task v : tasks)
			if (v != null)
				resetAllocableMap(v);
	}

	public CTS(MARSC problem, int[] taskOrder) {
		this(problem);

		if (taskOrder.length == tasks.length) {
			Task[] orderedTasks = new Task[tasks.length];
			for (int i = 0; i < tasks.length; i++)
				orderedTasks[i] = tasks[taskOrder[i]];
			tasks = orderedTasks;
		} else
			System.err.println("Input task order array does not have the right length");
	}

	/**
	 * Given agent <code>a</code>, set as allocable to <code>a</code> the current
	 * closest and uncompleted/allocated task v reachable by <code>a</code>.
	 *
	 * @param i An agent index.
	 */
	protected void setPotentialAllocationsTo(int i) {
		// 0: allocable, 1: allocated (but not completed)
		int[] taskIdx = new int[] { -1, -1 };
		int[] arrivalTimes = new int[] {
		    problem.maximumProblemCompletionTime + 1,
			problem.maximumProblemCompletionTime + 1
		};
		Location[] locations = new Location[2];

		int idx, t, gamma;
		Task precedence;

		for (int j = 0; j < tasks.length; j++) {
			if (tasks[j] == null)
				continue;

			if (order != null) { // satisfy ordering constraints
				precedence = order.get(tasks[j]);
				if (precedence != null && precedence.status != Task.Status.COMPLETED)
					continue;
			}

			if (tasks[j].status == Task.Status.UNCOMPLETED || tasks[j].status == Task.Status.ALLOCATED) {
				idx = 0;
				if (tasks[j].status == Task.Status.ALLOCATED)
					idx = 1;

				gamma = tasks[j].demand.timeWindow.hardLatestTime;

				for (Location location : tasks[j].demand.possibleLocations) {
					t = currentTime + agents[i].getTravelTimeTo(location);
					if (taskIdx[idx] == -1 || (t < gamma && t < arrivalTimes[idx]
						&& gamma < tasks[taskIdx[idx]].demand.timeWindow.hardLatestTime)
						&& (singletonSolutions[taskIdx[idx]] == null || singletonSolutions[taskIdx[idx]].getLastWorkingTime() >= t)) {
						taskIdx[idx] = j;
						arrivalTimes[idx] = t;
						locations[idx] = location;
					}
				}
			}
		}

		if (taskIdx[0] > -1 || taskIdx[1] > -1) {
			// prioritise not yet allocated tasks
			idx = taskIdx[0] > -1 ? 0 : 1;

			tasks[taskIdx[idx]].status = Task.Status.ALLOCABLE;
			tasks[taskIdx[idx]].allocableMap.get(locations[idx]).add(agents[i]);

			agents[i].target = tasks[taskIdx[idx]];
			agents[i].targetLocation = locations[idx];
			agents[i].arrivalTime = arrivalTimes[idx];
		} // else, no task is allocable to agents[i]
	}

	/**
	 * It finds a solution to a single task.
	 *
	 * It is assumed that the given coalition satisfies the spatial constraints,
	 * that is, it can reach the task location before the hard latest time.
	 *
	 * @param task      The task for which to find a solution.
	 * @param location  The task location.
	 * @param feasibleAgents A set of feasible agents.
	 *
	 * @return A solution to the input task.
	 */
	protected Solution getSingletonSolution(Task task, Location location, Set<Agent> feasibleAgents) {
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

		TreeMap<Integer, Set<Agent>> m =  new TreeMap<>();

		for (Agent a : feasibleAgents) {
			a.arrivalTime = Math.max(a.arrivalTime, earliestTime);
			Set<Agent> s = m.get(a.arrivalTime);
			if (s == null) s = new HashSet<>();
			s.add(a);
			m.put(a.arrivalTime, s);
		}

		List<CoalitionAllocation> l = new LinkedList<>();
		float workloadDone = 0, score = 0;
		Integer[] arrivalTimes = m.keySet().toArray(new Integer[0]);
		int j;

		for (int i = 0; i < arrivalTimes.length; i++) { // already sorted
			Set<Agent> subCoalition = m.get(arrivalTimes[i]);
			for (j = i - 1; j >= 0; j--)
				subCoalition.addAll(m.get(arrivalTimes[j]));
			Agent[] subCoalitionArr = subCoalition.toArray(new Agent[0]);
			float subCoalitionValue = problem.getValue(task, location, subCoalitionArr);
			float contribution;
			if (i + 1 < arrivalTimes.length)
				contribution = (arrivalTimes[i + 1] - arrivalTimes[i]) * subCoalitionValue;
			else
				contribution = (hardLatestTime - arrivalTimes[i]) * subCoalitionValue;
			workloadDone += contribution;
			int endTime = (int) Math.ceil(arrivalTimes[i] + ((workload - (workloadDone - contribution)) / subCoalitionValue)) - 1;
			int startTime = arrivalTimes[i] + 1;
			if (startTime < earliestTime)
				startTime = earliestTime;
			if (endTime < startTime)
				endTime = startTime;
			if (workloadDone < workload) {
				if (i + 1 < arrivalTimes.length) {
					l.add(new CoalitionAllocation(task, location, subCoalitionArr, subCoalitionValue, startTime, arrivalTimes[i + 1]));
					score += getMarginalScore(task, startTime, arrivalTimes[i + 1]);
				}
			} else {
				l.add(new CoalitionAllocation(task, location, subCoalitionArr, subCoalitionValue, startTime, endTime));
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

	protected void resetAllocableMap(Task v) {
		v.allocableMap = new HashMap<>();
		for (Location location : v.demand.possibleLocations)
			v.allocableMap.put(location, new HashSet<>());
	}

	protected boolean someAgentsAreBusy() {
		for (Agent a : agents)
			if (a.status != Agent.Status.IDLE)
				return true;
		return false;
	}

	@Override
	public void solve() {
		int numberOfCompletedTasks = 0, i;
		singletonSolutions = new Solution[tasks.length];
		Solution solution;

		do {
			for (i = 0; i < agents.length; i++) {
				// if idle, define which task can be assigned to this agent
				if (agents[i].status == Agent.Status.IDLE)
					setPotentialAllocationsTo(i);
				// if target reached, update this agent's status and location
				else if (agents[i].target != null && agents[i].status == Agent.Status.TRAVELING) {
					if (agents[i].target.status == Task.Status.COMPLETED) {
						agents[i].status = Agent.Status.IDLE;
						agents[i].target = null;
					} else if (agents[i].arrivalTime < currentTime) {
						agents[i].status = Agent.Status.WORKING;
						agents[i].location = agents[i].targetLocation;
					}
				}
			}

			for (i = 0; i < tasks.length; i++) {
				if (tasks[i].status == Task.Status.ALLOCABLE) {
					// define a solution, or improve an existing one
					for (Location location : tasks[i].demand.possibleLocations) {
						Set<Agent> allocableAgents = tasks[i].allocableMap.get(location);

						if (allocableAgents.size() == 0)
							continue; // tasks[i] is currently not allocable at location

                        if (singletonSolutions[i] != null)
							for (Agent alreadyAllocatedAgent : singletonSolutions[i].agents)
								if (alreadyAllocatedAgent.targetLocation.equals(location))
									allocableAgents.add(alreadyAllocatedAgent);

						solution = getSingletonSolution(tasks[i], location, allocableAgents);

						if (solution != null && (singletonSolutions[i] == null ||
							solution.getScore(false) > singletonSolutions[i].getScore(false)))
							singletonSolutions[i] = solution;
					}

					resetAllocableMap(tasks[i]);

					if (singletonSolutions[i] != null) {
						// update status and eventually location of assigned agents
						for (Agent a : singletonSolutions[i].agents)
							if (a.arrivalTime <= currentTime) { // agent already reached location
								a.status = Agent.Status.WORKING;
								a.location = a.targetLocation;
							} else {
								a.status = Agent.Status.TRAVELING;
							}
						tasks[i].status = Task.Status.ALLOCATED;
					} else
						tasks[i].status = Task.Status.UNCOMPLETED;
				}

				if (tasks[i].status == Task.Status.ALLOCATED && singletonSolutions[i] != null
					&& singletonSolutions[i].taskCompletionTime.get(tasks[i]) <= currentTime) {
					for (Agent a : singletonSolutions[i].agents) {
						a.status = Agent.Status.IDLE;
						a.target = null;
						a.targetLocation = null;
					}
					tasks[i].status = Task.Status.COMPLETED;
					numberOfCompletedTasks++;
				}
			}
		} while (someAgentsAreBusy() && numberOfCompletedTasks < tasks.length && ++currentTime <= problem.maximumProblemCompletionTime);
	}

	protected Solution merge(Solution[] singletonSolutions) {
		Set<Task> tasks = new HashSet<>();
		Set<CoalitionAllocation> l = new HashSet<>();
		float score = 0;

		for (Solution s : singletonSolutions)
			if (s != null) {
				tasks.addAll(s.tasks);
				Collections.addAll(l, s.coalitionAllocations);
				score += s.getScore(false);
			}

		return new Solution(tasks, l.toArray(new CoalitionAllocation[0]), score);
	}


	@Override
	public Solution getSolution() {
		if (solution == null)
			solution = merge(singletonSolutions);
		return solution;
	}

	public float[] getSingletonSolutionScores() {
		float[] f = new float[tasks.length];
		for (int i = 0; i < tasks.length; i++)
			if (singletonSolutions[i] != null)
				f[i] = singletonSolutions[i].getScore(false);
		return f;
	}

	public Double getMedianApproximationScoreRatio() {
		double[] r = new double[tasks.length];
		float[] singletonScores = getSingletonSolutionScores();
		float[] optimalSingletonScores = new BNT(problem).getOptimalSingletonScores(); // a bit ugly, but faaast

		for (int i = 0; i < tasks.length; i++)
			if (Math.abs(optimalSingletonScores[i]) > 0)
				r[i] = Math.abs(singletonScores[i] / optimalSingletonScores[i]);

		//ArrayUtils.removeAllOccurrences(r, 0);

		return new Median().evaluate(r);
	}

	@Override
	public float getAnytimeQualityIndex() {
		return 0;
	}

}