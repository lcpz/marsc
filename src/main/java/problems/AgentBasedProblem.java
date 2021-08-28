package problems;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.math3.distribution.UniformRealDistribution;

import locations.Location;
import model.Agent;
import model.MARSC;
import model.Task;

public class AgentBasedProblem extends MARSC {

	protected static final long serialVersionUID = 1L;

	protected Map<Integer, Float> agentPerformance;
	protected Map<String, Float> coalitionValueMap;

	public final boolean urgent, congested;

	public AgentBasedProblem(Task[] tasks, Map<Task, Task> order, Agent[] agents, boolean urgent, boolean congested) {
		super(tasks, order, agents);
		this.urgent = urgent;
		this.congested = congested;

		agentPerformance = new HashMap<>();

		// pre-computing
		UniformRealDistribution d = new UniformRealDistribution(0, 10);
		for (Agent a : agents)
			agentPerformance.put(a.id, (float) Math.abs(new UniformRealDistribution(0, 2 * Math.abs(d.sample())).sample()));

		coalitionValueMap = new HashMap<>();
	}

	@Override
	public float getValue(Task task, Location location, Agent[] coalition) {
		String s = String.format("%d%s", task.id, Arrays.toString(coalition));
		Float fmap = coalitionValueMap.get(String.format("%d%s", task.id, Arrays.toString(coalition)));

		if (fmap != null)
			return fmap;

		float f = 0f;

		for (Agent a : coalition)
			f += agentPerformance.get(a.id);

		if (urgent || congested) {
			int probability;
			UniformRealDistribution r = new UniformRealDistribution(f/10, f/5);

			if (urgent) {
				probability = (int) Math.ceil(task.demand.timeWindow.hardLatestTime / (double) (maximumProblemCompletionTime + 1)) * 100;
				if (ThreadLocalRandom.current().nextInt(101) <= probability)
					f -= r.sample();

				probability = (int) Math.ceil(coalition[coalition.length-1].getTravelTimeTo(location) / (double) (maximumProblemCompletionTime + 1)) * 100;
				if (ThreadLocalRandom.current().nextInt(101) <= probability)
					f -= r.sample();
			}

			if (congested) {
				probability = (int) Math.ceil(coalition.length / (double) (agents.length + 1)) * 100;
				if (ThreadLocalRandom.current().nextInt(101) <= probability)
					f -= r.sample();
			}
		}

		coalitionValueMap.put(s, f);

		return f;
	}

}