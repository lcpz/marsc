package problems;

import java.util.Map;

import org.apache.commons.math3.distribution.UniformRealDistribution;

import locations.Location;
import model.Agent;
import model.MARSC;
import model.Task;

public class LinearPerturbedProblem extends MARSC {

	protected static final long serialVersionUID = 1L;

	public final boolean perturbed;

	/* Taken from Ramchurn's 2010 CFSTP paper. */
	protected UniformRealDistribution unif = new UniformRealDistribution(1, 2);

	float[] cValues;

	public LinearPerturbedProblem(Task[] tasks, Map<Task, Task> order, Agent[] agents, boolean perturbed) {
		super(tasks, order, agents);
		this.perturbed = perturbed;
		if (perturbed) {
			cValues = new float[agents.length];
			for (int i = 0; i < agents.length; i++)
				cValues[i] = (float) ((i+1) * Math.abs(unif.sample()));
		}
	}

	@Override
	public float getValue(Task task, Location location, Agent[] coalition) {
		if (!perturbed) // superadditive
			return (float) coalition.length;
		else {
			int i = coalition.length - 1;
			if (i < 0) i = 0;
			return cValues[i];
		}
	}

}