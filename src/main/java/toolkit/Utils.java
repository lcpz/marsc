package toolkit;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.collect.Sets;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;

import model.Agent;
import model.Task;

public class Utils {

	/**
	 * Constructs a subarray of agents.
	 *
	 * @param agents The initial array of agents.
	 * @param which  An array of boolean, where <code>which[i]</code> means that
	 *               <code>agents[i]</code> must be included in the subarray.
	 * @param size   the number of true values in <code>which</code>.
	 * @return The related subarray of <code>agents</code>.
	 */
	public static Agent[] getSubArray(Agent[] agents, boolean[] which, int size) {
		try {
			if (agents.length != which.length)
				throw new Exception("both arrays must have the same length");
			if (size < 1)
				throw new Exception("subarray size must be positive");
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (size == agents.length)
			return agents;

		Agent[] s = new Agent[size];
		int j = 0;

		for (int i = 0; i < agents.length; i++) {
			if (which[i])
				s[j++] = agents[i];
			if (j == size)
				break;
		}

		return s;
	}

	/**
	 * Verifies if a task ordering contains a cycle.
	 *
	 * The task ordering is expressed as a Task-Task map, where each entry is such
	 * that the value precedes the key.
	 *
	 * @param order A Task-Task map.
	 * @return A boolean.
	 */
	public static boolean hasCycle(Map<Task, Task> order) {
		MutableGraph<Task> graph = GraphBuilder.undirected().build();

		Task to, from;

		for (Entry<Task, Task> entry : order.entrySet()) {
			to = entry.getKey();

			if (to != null) {
				from = entry.getValue();

				if (from != null) {
					graph.addNode(to);
					graph.addNode(from);
					graph.putEdge(from, to);
				}
			}
		}

		return Graphs.hasCycle(graph);
	}

	/**
	 * Get an integer array with numbers from 0 to n-1.
	 *
	 * @param n The length of the array.
	 * @return The array
	 */
	public static int[] getRangeArray(int n) {
		if (n < 0)
			return null;

		if (n == 0)
			return new int[0];

		int[] arr = new int[n];

		for (int i = 0; i < n; i++)
			arr[i] = i;

		return arr;
	}

	public static Set<Agent> intersection(Agent[] coalition, Agent[] coalition2) {
		if (coalition == null || coalition.length == 0 ||
			coalition2 == null || coalition2.length == 0)
			return null;

		Set<Agent> c1 = Set.of(coalition);
		Set<Agent> c2 = Set.of(coalition2);

		Set<Agent> s;

		// Guava optimisation
		if (c2.size() < c1.size())
			s = Sets.intersection(c2, c1);
		else
			s = Sets.intersection(c1, c2);

		if (s.size() > 0)
			return s;

		return null;
	}

    public static int[] deepClone(int[] a) {
		//return Arrays.stream(a).map(IntUnaryOperator.identity()).toArray();
		int[] b = new int[a.length];
		for (int i = 0; i < a.length; i++)
			b[i] = a[i];
		return b;
    }

	public static Agent[] deepClone(Agent[] a) {
		//return Arrays.stream(a).map(IntUnaryOperator.identity()).toArray();
		Agent[] b = new Agent[a.length];
		for (int i = 0; i < a.length; i++)
			b[i] = new Agent(a[i].id, a[i].initialLocation, a[i].speed);
		return b;
	}

	public static double[] toPrimitiveArray(List<Double> r) {
		if (r == null)
			return null;

		double[] arr = new double[r.size()];

		int i = 0;
		for (Double d : r)
		    if (d != null)
				arr[i++] = d;

		return arr;
	}
}