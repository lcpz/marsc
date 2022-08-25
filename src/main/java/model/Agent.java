package model;

import java.io.Serializable;

import locations.Location;

/**
 * A basic agent.
 *
 * @author lcpz
 */
public class Agent implements Serializable {

	private static final long serialVersionUID = 1L;

	public enum Status {
		IDLE, // not traveling to nor working on a task (i.e., free)
		TRAVELING,
		WORKING
	}

	public final int id;
	public final String idStr;

	/* Current status, initialised to idle. */
	public Status status = Status.IDLE;

	/* Initial and current location. */
	public Location initialLocation, location;

	/* Speed is uniform and defined as the number of cells per time unit. */
	public final float speed;

	/*
	 * The task that is currently:
	 * 1. assignable to this agent if status is IDLE;
	 * 2. being reached by this agent if status is TRAVELING;
	 * 3. being executed by this agent if status is WORKING.
	 */
	public Task target;

	public Location targetLocation;

	/* Time at which the target location is reached. */
	public int arrivalTime;

	/*
	 * Last time in which this agent was working on a task, with 0
	 * meaning that the agent still did not work.
	 */
	public int endTime;

	public Agent(int id, float speed) {
		try {
			if (speed < 0)
				throw new Exception(String.format("agent %d has negative speed (%f)", id, speed));
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.id = id;
		this.idStr = String.format("a%d", id);
		this.speed = speed;
	}

	public Agent(int id, Location initialLocation, float speed) {
		this(id, speed);

		try {
			if (initialLocation == null)
				throw new Exception(String.format("agent %d has a null initial location", id));
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.initialLocation = initialLocation;
		location = initialLocation;
	}

	public int getTravelTime(Location departure, Location destination) {
		return departure.getTravelTimeTo(destination, speed);
	}

	public int getTravelTimeTo(Location destination) {
		return getTravelTime(location, destination);
	}

	@Override
	public String toString() {
		return idStr;
	}

}