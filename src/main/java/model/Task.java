package model;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import locations.Location;

/**
 * A basic task.
 *
 * @author lcpz
 */
public class Task implements Serializable {

	private static final long serialVersionUID = 1L;

	public enum Status {
		UNCOMPLETED, // the workload of this task is > 0
		ALLOCABLE,   // at least 1 agent can reach and work on this task before its hard latest time
		ALLOCATED,   // at least 1 agent is reaching or working on it
		COMPLETED    // the workload of this task is <= 0
	}

	public final int id;
	public final String idStr;

	public final Demand demand;

	/* Current task status. */
	public Status status = Status.UNCOMPLETED;

	/*
	 * At each possible location, defines which agents this task can be currently assigned to.
	 * This structure is only used by CTS algorithms.
	 */
	public Map<Location, Set<Agent>> allocableMap;

	public Task(int id, Demand demand) {
		try {
			if (demand == null)
				throw new Exception(String.format("task %d has a null demand", id));
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.id = id;
		this.idStr = String.format("T%d", id);
		this.demand = demand;
	}

	@Override
	public String toString() {
		return idStr;
	}

	public boolean equals(Task task) {
		return id == task.id;
	}

}