package problems;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.distribution.UniformRealDistribution;

import locations.Location;
import model.Agent;
import model.MARSC;
import model.Task;

public class AgentBasedProblem extends MARSC {

	protected static final long serialVersionUID = 1L;

	protected Map<Integer, Float> agentPerformance;
	protected Map<String, Float> coalitionValueMap;

	public AgentBasedProblem(Task[] tasks, Map<Task, Task> order, Agent[] agents) {
		super(tasks, order, agents);

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
			return fmap.floatValue();

		float f = 0f;

		for (Agent a : coalition)
			f += agentPerformance.get(a.id);

		coalitionValueMap.put(s, f);

		return f;
	}

}