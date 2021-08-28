package model;

import java.io.Serializable;
import java.util.Map;

import org.apache.commons.lang3.SerializationUtils;

import locations.Location;
import toolkit.Utils;

/**
 * A Multi-Agent Task Scheduling and Routing through Coalition formation (MARSC) problem.
 *
 * @author lcpz
 */
public abstract class MARSC implements Serializable {

	private static final long serialVersionUID = 1L;

	protected Task[] tasks;

	/* Task ordering, defined as a map: value precedes key. */
	protected Map<Task, Task> order;

	protected Agent[] agents;

	public final int maximumProblemCompletionTime;

	protected String stringRepresentation;

	public MARSC(Task[] tasks, Map<Task, Task> order, Agent[] agents) {
		try {
			if (tasks == null || tasks.length == 0)
				throw new Exception("invalid task array");
			if (agents == null || agents.length == 0)
				throw new Exception("invalid agent array");
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.tasks = tasks;
		this.agents = agents;

		// 'order' can be null or empty (implying no task ordering)
		if (order != null && order.size() > 0) {
			try {
				if (order.entrySet().contains(null) || order.containsValue(null))
					throw new Exception("order map contains null keys or values");
				if (Utils.hasCycle(order))
					throw new Exception("order map contains cycles");
			} catch (Exception e) {
				e.printStackTrace();
			}

			this.order = order;
		}

		int dmax = 0;
		for (Task v : tasks)
			if (v.demand.timeWindow.hardLatestTime > dmax)
				dmax = v.demand.timeWindow.hardLatestTime;
		maximumProblemCompletionTime = dmax;
	}

	public Task[] getTasks() {
		return tasks;
	}

	public Map<Task, Task> getTaskOrdering() {
		return order;
	}

	public Agent[] getAgents() {
		return agents;
	}

	/**
	 * The amount of work that coalition <code>agents</code> does on task
	 * <code>task</code> at location <code>taskLocation</code> in one time unit.
	 *
	 * Equivalent to the characteristic function in cooperative game theory.
	 *
	 * <b>Important:</b> use a hash map to store and retrieve the value of each
	 * input. This avoids inconsistent coalition values when this function is
	 * non deterministic and gets called more than once on the same input by the
	 * same solver.
	 */
	public abstract float getValue(Task task, Location location, Agent[] coalition);

	public float getValue(CoalitionAllocation ca) {
		return getValue(ca.task, ca.location, ca.coalition);
	}

	/**
	 * Return a deep copy of this problem instance.
	 */
	public MARSC clone() {
		return (MARSC) SerializationUtils.clone(this);
	}

	@Override
	public String toString() {
		if (stringRepresentation == null) {
			StringBuilder s = new StringBuilder(String.format("Tasks: %d", tasks.length));

			if (order != null && order.keySet().size() > 0) {
				s.append("\nTask ordering:\n");
				for (Map.Entry<Task, Task> entry : order.entrySet())
					s.append(String.format("%s -> %s\n", entry.getValue(), entry.getKey()));
			} else
				s.append("\nNo task order\n");

			s.append("Agent locations:\n");
			for (Agent a : agents)
				s.append(String.format("%s at %s\n", a, a.location));

			stringRepresentation = s.toString();
		}

		return stringRepresentation;
	}

}
