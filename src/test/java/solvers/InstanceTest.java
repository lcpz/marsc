package solvers;

import com.google.common.base.Stopwatch;
import locations.Location;
import locations.LocationPoint;
import model.*;
import org.junit.jupiter.api.Test;
import problems.Problem;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Comparison of different solvers on a specific MARSC instance.
 *
 * @author lcpz
 */
class InstanceTest {

	static final int AGENTS = 2;
	static final int TASKS = 3;

	@Test
	void test0() {
		Task[] tasks = new Task[TASKS];
		Map<Task, Task> order = new HashMap<>();

		// T0
		TimeWindow tw0 = new TimeWindow(29, 32, 160);
		Demand d0 = new Demand(new Location[] {
			new LocationPoint(44, 8)
		}, (float) 45.23, 1f, tw0);
		tasks[0] = new Task(0, d0);

		// T1
		TimeWindow tw1 = new TimeWindow(225, 461, 482);
		Demand d1 = new Demand(new Location[] {
			new LocationPoint(14, 33)
		}, (float) 47.8, 1f, tw1);
		tasks[1] = new Task(1, d1);

		// T2
		TimeWindow tw2 = new TimeWindow(309, 463, 464);
		Demand d2 = new Demand(new Location[] {
				new LocationPoint(42, 24)
		}, (float) 42.42, 1f, tw2);
		tasks[2] = new Task(2, d2);

		order.put(tasks[1], tasks[0]); // the precedence of T1 is T0

		Agent[] agents = new Agent[AGENTS];

		agents[0] = new Agent(0, new LocationPoint(42, 29), 1);
		agents[1] = new Agent(1, new LocationPoint(6, 34), 1);

		MARSC problem = Problem.getInstance("SUPERADDITIVE", tasks, order, agents);

		assertNotNull(problem);
		System.out.println(problem);

		ANT ant = new ANT(problem.clone());
		CPLEX cplex = new CPLEX(problem);

		System.out.println(":: ANT");
		Stopwatch stopwatch = Stopwatch.createStarted();
		ant.solve(stopwatch);
		stopwatch.stop();
		System.out.printf("\nSolution score: %s\nTotal time: %s ms\n\n%s%n",
			(int) Math.ceil(ant.solution.getScore(true)), stopwatch.elapsed(TimeUnit.MILLISECONDS), ant.solution.sort());

		System.out.println(":: CPLEX");
		cplex.solve();
		assertNotNull(cplex.solution);
		System.out.printf("\n%s\n\n%s%n", cplex.getStats(), cplex.solution.sort());
	}

}