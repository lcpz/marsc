package solvers;

import java.util.Comparator;
import java.util.Map;

import com.google.common.base.Stopwatch;
import model.*;

/**
 * An algorithm for solving MARSC problems.
 *
 * @author lcpz
 */
public abstract class Solver {

	protected Stopwatch stopwatch;

	protected MARSC problem;

	protected Task[] tasks;
	protected Map<Task, Task> order;
	protected Agent[] agents;

	protected volatile Solution solution; // volatile for multi-threading purposes

	public Solver(MARSC problem) {
		try {
			if (problem == null)
				throw new Exception("null problem");
			for (Task v : problem.getTasks())
				if (v == null)
					throw new Exception("there are null tasks");
			for (Agent a : problem.getAgents())
				if (a == null)
					throw new Exception("there are null agents");
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.problem = problem;

		// agents and tasks are deep copies
		agents = problem.getAgents().clone();
		tasks = problem.getTasks().clone();

		order = problem.getTaskOrdering();
	}

	public final Comparator<Task> comparator = (v1, v2) -> {
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
	};

	public abstract void solve();

	public void solve(Stopwatch stopwatch) {
		this.stopwatch = stopwatch;
		solve();
	}

	public Solution getSolution() {
		return solution;
	}

	public abstract float getAnytimeQualityIndex();

	public float getMarginalScore(Task task, int startTime, int arrivalTime) {
		float marginalScore = 0, profit = task.demand.profit;
		int beta = task.demand.timeWindow.softLatestTime, gamma = task.demand.timeWindow.hardLatestTime;

		for (int t = startTime; t <= arrivalTime; t++)
			if (t <= beta)
				marginalScore += profit;
			else
			    marginalScore += (float) (1 - ((t - beta)/(gamma - beta + 1))) * profit;

		return marginalScore;
	}

	public MARSC getProblem() {
		return problem;
	}

}