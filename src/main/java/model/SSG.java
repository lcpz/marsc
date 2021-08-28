package model;

import java.util.Set;

/**
 * A Solution Scheduling Graph record.
 *
 * @author lcpz
 */
public class SSG {

	/* The subset of tasks on which the subproblem is specified. */
	public final Set<Task> tasks;

	/*
	 * The graph paths. A path is a solution; an incomplete path is an expandable
	 * solution, while a complete path is a not expandable solution.
	 */
	public Set<Solution> paths;

	/**
	 * A highest-degree solution.
	 *
	 * Note that having the highest degree does not imply to complete the highest
	 * number of tasks.
	 *
	 * That is, there might be solutions with lower degree, but which complete more
	 * tasks.
	 */
	public Solution bestSolution;

	/* The maximum number of completed tasks among the solutions of this SSG. */
	public int maxCompletedTasks;

	public SSG(Set<Task> tasks, Set<Solution> paths, Solution bestSolution, int maxCompletedTasks) {
		try {
			if (tasks == null || tasks.size() == 0)
				throw new Exception("undefined subset of tasks");
			if (paths == null)
				throw new Exception("undefined paths");
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.tasks = tasks;
		this.paths = paths;
		this.bestSolution = bestSolution;
		this.maxCompletedTasks = maxCompletedTasks;
	}

	public SSG(Set<Task> tasks) {
		try {
			if (tasks == null || tasks.size() == 0)
				throw new Exception("undefined subset of tasks");
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.tasks = tasks;
		maxCompletedTasks = 0;
	}

	public String toString() {
		StringBuilder s = new StringBuilder(String.format("SSG %s (completed: %d)\n", tasks, maxCompletedTasks));
		for (Solution sol : paths) {
			if (sol == bestSolution)
				s.append("*BEST* ");
			s.append(sol);
			s.append("\n");
		}

		return s.toString();
	}

}