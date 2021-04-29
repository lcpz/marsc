package toolkit;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;

import locations.Location;
import locations.LocationPoint;
import model.Agent;
import model.Demand;
import model.MARSC;
import model.Task;
import model.TimeWindow;
import problems.Problem;

/**
 * A generator of random MARSC instances, with Point locations.
 *
 * @author lcpz
 */
public class RandomProblemGenerator implements Serializable {

	private static final long serialVersionUID = 1L;

	public MARSC generate(String type, int agentNr, int taskNr, int taskLocNr, int worldDim) {
		int i, j;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();

		Location[] locations = new Location[worldDim * worldDim];
		for (i = 0; i < locations.length; i++)
			locations[i] = new LocationPoint(ThreadLocalRandom.current().nextInt(worldDim),
					ThreadLocalRandom.current().nextInt(worldDim));

		/* Taken from 2010 Ramchurn's CFSTP paper. */
		UniformIntegerDistribution unifT = new UniformIntegerDistribution(5, 600);
		UniformRealDistribution unifW = new UniformRealDistribution(10, 50);

		Task[] tasks = new Task[taskNr];
		Map<Task, Task> order = new HashMap<>();
		for (i = 0; i < taskNr; i++) {
			Location[] possibleLocations = new Location[rnd.nextInt(1, taskLocNr + 1)];
			for (j = 0; j < possibleLocations.length; j++)
				possibleLocations[j] = locations[rnd.nextInt(locations.length)];

			float workload = (float) Math.abs(unifW.sample());

			int softLatestTime = Math.abs(unifT.sample());
			int hardLatestTime = rnd.nextInt(softLatestTime, unifT.getSupportUpperBound() + 1);
			int earliestTime = rnd.nextInt(softLatestTime);
			TimeWindow timeWindow = new TimeWindow(earliestTime, softLatestTime, hardLatestTime);

			float profit = ThreadLocalRandom.current().nextFloat() + 0.1f;

			Demand demand = new Demand(possibleLocations, workload, profit, timeWindow);

			tasks[i] = new Task(i, demand);

			// task ordering is a partial chain decided by the flip of a coin
			if (i > 0 && rnd.nextInt(2) == 0) {
				TimeWindow twPrev = tasks[i-1].demand.timeWindow;
				TimeWindow tw = tasks[i].demand.timeWindow;
				if (twPrev.earliestTime <= tw.earliestTime
					&& twPrev.hardLatestTime < tw.hardLatestTime) // iff feasible
					order.put(tasks[i], tasks[i - 1]);
			}
		}

		Agent[] agents = new Agent[agentNr];
		for (i = 0; i < agentNr; i++)
			// random location position, and all agents have the speed
			agents[i] = new Agent(i, locations[ThreadLocalRandom.current().nextInt(locations.length)], 1);

		return Problem.getInstance(type, tasks, order, agents);
	}

}
