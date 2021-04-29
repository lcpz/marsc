package problems;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;

import locations.Location;
import model.Agent;
import model.MARSC;
import model.Task;

public class CongestedNDCSProblem extends MARSC {

	protected static final long serialVersionUID = 1L;

	public final boolean perturbed;

	protected Map<Integer, Float> intValueMap;

	public CongestedNDCSProblem(Task[] tasks, Map<Task, Task> order, Agent[] agents, boolean perturbed) {
		super(tasks, order, agents);

		this.perturbed = perturbed;

		intValueMap = new HashMap<>();

		/* pre-compute coalition values to ensure consistency */
		NormalDistribution distr;
		UniformRealDistribution unif;
	    double value;
	    int prob, j;
		for (int i = 1; i <= agents.length; i++) {
			distr = new NormalDistribution(i, Math.pow(i, 0.25));
			value = Math.abs(distr.sample());
			if (perturbed) {
				j = i - 1 > 0 ? i - 1 : i;
				prob = (int) Math.ceil(agents.length/j);
				if (ThreadLocalRandom.current().nextInt(prob) == 0) {
					unif = new UniformRealDistribution(value/10, value);
					value -= Math.abs(unif.sample());
				}
			}
			intValueMap.put(i, (float) value);
		}
	}

	@Override
	public float getValue(Task task, Location location, Agent[] coalition) {
		try {
			if (task == null || location == null || coalition.length == 0)
				throw new Exception("invalid input");
			} catch (Exception e) {
			e.printStackTrace();
		}

		return intValueMap.get(coalition.length);
	}

}