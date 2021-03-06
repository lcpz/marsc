package solvers;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import model.CoalitionAllocation;
import model.MARSC;
import model.Solution;
import model.Task;

/**
 * Earliest Deadline First (EDF) algorithm.
 *
 * It satisfies the spatio-temporal constraints in the same way as BNT, but it
 * prioritises nodes with the earliest time windows.
 *
 * @author lcpz
 */
public class EDF extends BNT {

	protected Set<Task> taskSet;

	public EDF(MARSC problem) {
		super(problem);
	}

	@Override
	public void solve() {
		taskSet = new TreeSet<>(comparator);
		Collections.addAll(taskSet, tasks);

		HashSet<Task> completedTasks = new HashSet<>();
		HashSet<CoalitionAllocation> l = new HashSet<>();
		Solution currentSolution;

		for (Task v : taskSet) {
			currentSolution = getSingletonSolution(v, agents);

			/* No solution to node i, but it may exist a solution to node j > i. */
			if (currentSolution == null) continue;

			updateAgentStatus(currentSolution);
			completedTasks.addAll(currentSolution.tasks);

			singletonScores.put(currentSolution.tasks.iterator().next().id, currentSolution.getScore(false));

			for (CoalitionAllocation ca : currentSolution.coalitionAllocations)
				l.add(ca);
		}

		solution = new Solution(completedTasks, l.toArray(new CoalitionAllocation[0]));
	}

}