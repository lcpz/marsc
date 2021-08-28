package problems;

import java.util.Map;

import model.Agent;
import model.MARSC;
import model.Task;

/**
 * A MARSC instantiator.
 *
 * @author lcpz
 */
public class Problem {

	public static MARSC getInstance(String type, Task[] tasks, Map<Task, Task> order, Agent[] agents) {
		switch (type) {
		case "SUPERADDITIVE":
			return new LinearPerturbedProblem(tasks, order, agents, false);
		case "LINEAR_PERTURBED":
			return new LinearPerturbedProblem(tasks, order, agents, true);
		case "UNIFORM":
			return new UniformProblem(tasks, order, agents, false);
		case "NORMAL":
			return new NormalProblem(tasks, order, agents, false);
		case "MODIFIED_UNIFORM":
			return new UniformProblem(tasks, order, agents, true);
		case "MODIFIED_NORMAL":
			return new NormalProblem(tasks, order, agents, true);
		case "AGENT_BASED":
			return new AgentBasedProblem(tasks, order, agents, false, false);
		case "U_AGENT_BASED":
			return new AgentBasedProblem(tasks, order, agents, true, false);
		case "C_AGENT_BASED":
			return new AgentBasedProblem(tasks, order, agents, false, true);
		case "UC_AGENT_BASED":
			return new AgentBasedProblem(tasks, order, agents, true, true);
		case "NDCS":
			return new NDCSProblem(tasks, order, agents, false, false);
		case "U_NDCS":
			return new NDCSProblem(tasks, order, agents, true, false);
		case "C_NDCS":
			return new NDCSProblem(tasks, order, agents, false, true);
		case "UC_NDCS":
			return new NDCSProblem(tasks, order, agents, true, true);
		default:
			return null;
		}
	}

}