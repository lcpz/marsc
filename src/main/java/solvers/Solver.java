package solvers;

import java.util.Map;

import model.Agent;
import model.MARSC;
import model.Solution;
import model.Task;

/**
 * An algorithm for solving MARSC problems.
 *
 * @author lcpz
 */
public abstract class Solver {

	public static final boolean DEBUG = false;

	protected MARSC problem;

	protected Task[] tasks;
	protected Map<Task, Task> order;
	protected Agent[] agents;

	protected Solution solution;

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
		agents = problem.getAgents();
		tasks = problem.getTasks();
		order = problem.getTaskOrdering();
	}

	public abstract void solve();

	public Solution getSolution() {
		return solution;
	}

}