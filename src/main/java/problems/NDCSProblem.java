package problems;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;

import locations.Location;
import model.Agent;
import model.MARSC;
import model.Task;

public class NDCSProblem extends MARSC {

	protected static final long serialVersionUID = 1L;

	public final boolean urgent, congested;

	protected float[] preValues;

	protected Map<String, Float> coalitionValueMap;

	public NDCSProblem(Task[] tasks, Map<Task, Task> order, Agent[] agents, boolean urgent, boolean congested) {
		super(tasks, order, agents);

		this.urgent = urgent;
		this.congested = congested;

		// pre-computing
		preValues = new float[agents.length];
		for (int i = 0; i < agents.length; i++)
			preValues[i] = (float) Math.abs(new NormalDistribution(i+1, Math.pow(i+1, 0.25)).sample());

		coalitionValueMap = new HashMap<>();
	}

	@Override
	public float getValue(Task task, Location location, Agent[] coalition) {
		String s = String.format("%d%s", task.id, Arrays.toString(coalition));
		Float fmap = coalitionValueMap.get(String.format("%d%s", task.id, Arrays.toString(coalition)));

		if (fmap != null)
			return fmap;

		float f = preValues[coalition.length-1];

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