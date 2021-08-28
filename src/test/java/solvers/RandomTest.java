package solvers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;

import model.Agent;
import model.MARSC;
import model.Solution;
import model.Task;
import toolkit.RandomProblemGenerator;

/**
 * Comparison of ANT and CTS on multiple random MARSC instances.
 *
 * <b>Make sure</b> that each solver works on its copy of the problem,
 * since both modify the internal data structures.
 *
 * @author lcpz
 */
class RandomTest {

	static final boolean ANT_ENABLED = true;
	static final boolean PRINT_PROBLEM = false;

	static final int TEST_REPETITIONS = 1;

	static final int AGENTS = 2;
	static final int TASKS = 4;
	static final int MAX_TASK_LOCATIONS = 1;
	static final int WORLD_DIM = 50;

	static float rmin = Float.POSITIVE_INFINITY;
	static float rmax = Float.NEGATIVE_INFINITY;
	static float ravg = 0;

	static RandomProblemGenerator pg = new RandomProblemGenerator();

	public static float getRatio(Solution solCTS, Solution solANT) {
		float r = 100; // only second solver found a solution

		if (solCTS == null && solANT == null) // both didn't find a solution
			r = 0;

		if (solCTS != null && solANT == null) // only first solver found a solution
			r = Float.NEGATIVE_INFINITY;

		if (solCTS != null && solANT != null) // both found a solution
			r = 100 - 100 * ((float) solCTS.getScore(true) / solANT.getScore(true));

		/*
		 * The percentage of gain in solution quality when using the second solver
		 * instead of the first.
		 */
		return r;
	}

	@RepeatedTest(TEST_REPETITIONS)
	void test0(RepetitionInfo rep) { // https://stackoverflow.com/a/52074525
		MARSC problem = pg.generate("SUPERADDITIVE", AGENTS, TASKS, MAX_TASK_LOCATIONS, WORLD_DIM);

		if (PRINT_PROBLEM)
			System.out.print(problem);

		String str;

		CTS cts = new CTS(problem.clone());
		cts.solve();
		Solution solCTS = cts.getSolution();
		if (PRINT_PROBLEM) {
			solCTS.sort();
			System.out.printf("CTS %s%n", solCTS);
		} else
			System.out.printf("\nCTS [tasks: %.2f, score: %d, median singleton score: %d]",
				100 * solCTS.tasks.size() / (float) problem.getTasks().length,
				solCTS.getScore(true), solCTS.getMedianSingletonScore());

		BNT bts = new BNT(problem.clone());
		bts.solve();
		Solution solBTS = bts.getSolution();
		if (PRINT_PROBLEM) {
			solBTS.sort();
			System.out.printf("BTS %s%n", solBTS);
		} else
			System.out.printf("\nBTS [tasks: %.2f, score: %d, median singleton score: %d]",
					100 * solBTS.tasks.size() / (float) problem.getTasks().length,
					solBTS.getScore(), solBTS.getMedianSingletonScore());

		if (!ANT_ENABLED)
			return;

		ANT ant = new ANT(problem);
		ant.solve();
		Solution solANT = ant.getSolution();
		if (PRINT_PROBLEM) {
			solANT.sort();
			System.out.printf("\n:: ANT\n%s%n", solANT);
		}

		float ratio = getRatio(solCTS, solANT);
		str = String.format("ANT/CTS gain = %2.2f", ratio);

		if (ratio < 0) {
			Map<Task, Task> order = problem.getTaskOrdering();

			if (order != null && order.keySet().size() > 0) {
				System.out.println("Task ordering:");
				for (Map.Entry<Task, Task> entry : problem.getTaskOrdering().entrySet())
					System.out.printf("%s -> %s%n", entry.getValue(), entry.getKey());
			} else
				System.out.println("No task ordering");

			for (Agent a : problem.getAgents())
				System.out.printf("Agent %s initial location: %s%n", a, a.location);

			System.out.printf("\n:: CTS\n%s%n", solCTS.sort());
			System.out.printf(":: ANT\n%s%n", solANT.sort());
		}

		if (ratio < rmin)
			rmin = ratio;

		if (ratio > rmax)
			rmax = ratio;

		ravg += ratio;

		str = String.format("[%3d] %s", rep.getCurrentRepetition(), str);

		if (ratio >= 0)
			System.out.println(str);
		else
			System.err.println(str);

		assertTrue(ratio >= 0);

		if (rep.getCurrentRepetition() == rep.getTotalRepetitions()) {
			ravg /= rep.getTotalRepetitions();
			System.out.printf("\nmin = %.2f, max = %.2f, avg = %.2f%n", rmin, rmax, ravg);
		}
	}

}