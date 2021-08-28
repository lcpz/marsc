package solvers;

import com.google.common.base.Stopwatch;
import model.MARSC;
import org.junit.jupiter.api.Test;
import toolkit.RandomProblemGenerator;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CPLEXTest {

	@Test
	void test() {
		MARSC problem = RandomProblemGenerator.generate("SUPERADDITIVE", 2, 3, 1, 50);
		assertNotNull(problem);
		System.out.println(problem);

		ANT ant = new ANT(problem.clone());
		System.out.println(":: ANT");
		Stopwatch stopwatch = Stopwatch.createStarted();
		ant.solve();
		stopwatch.stop();
		System.out.printf("Solution score: %s\nTotal time: %s ms\n\n%s%n",
		ant.solution.getScore(), stopwatch.elapsed(TimeUnit.MILLISECONDS), ant.solution.sort());

		CPLEX cplex = new CPLEX(problem);
		System.out.println(":: CPLEX");
		cplex.solve();
		assertNotNull(cplex.solution);
		System.out.printf("\n%s\n\n%s%n", cplex.getStats(), cplex.solution.sort());

		assertEquals(cplex.solution.getScore(true), ant.solution.getScore(true));
	}

}