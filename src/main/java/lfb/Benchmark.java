package lfb;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Median;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Stopwatch;

import model.MARSC;
import model.Solution;
import solvers.BNT;
import solvers.EDF;
import solvers.Solver;

/**
 * LFB benchmark developed for the MARSC paper.
 *
 * @author lcpz
 */
public class Benchmark {

	public static final int METRICS = 4;

	protected Setup lfb;
	protected Mean mean = new Mean();
	protected StandardDeviation std = new StandardDeviation();
	protected Median median = new Median();

	protected double[][] valuesEDF, valuesBNT;

	// algorithms - metrics (plus CPU time) - replicates
	protected double[][][] batchResults;

	private int testNr = 1;

	private static Stopwatch stopwatch = Stopwatch.createUnstarted();

	protected void reset(String type, float ratio) {
		// median and related 95% CI
		System.out.println(String.format("\n%s-%-4s EDF [time: %s (ms), tasks: %s (%%), score: %s, singleton score: %s]",
			type, ratio, getCIMedian(valuesEDF[0]), getCIMedian(valuesEDF[1]), getCIMedian(valuesEDF[2]), getCIMedian(valuesEDF[3])));

		System.out.println(String.format("%s-%-4s BNT [time: %s (ms), tasks: %s (%%), score: %s, singleton score: %s]\n",
			type, ratio, getCIMedian(valuesBNT[0]), getCIMedian(valuesBNT[1]), getCIMedian(valuesBNT[2]), getCIMedian(valuesBNT[3])));

		valuesBNT = new double[4][replicates];
		valuesEDF = new double[4][replicates];

		lfb.currIdx = 1;
	}

	protected void run(String type, float ratio, int batchIdx) {
		/* This leads to performance oscillations, because problems with
		 * lesser node-to-agent ratios may be bigger than problems
		 * with greater node-to-agent ratios. */
		//int agents = ThreadLocalRandom.current().nextInt(1, 151);

		MARSC problem = lfb.generate(type, agents, ratio);

		EDF edf = new EDF(problem.clone());
		BNT bnt = new BNT(problem.clone());

		double[] resultsEDF = getResults(problem.getTasks().length, edf);
		double[] resultsBNT = getResults(problem.getTasks().length, bnt);

		for (int i = 0; i < resultsEDF.length; i++) {
			valuesEDF[i][batchIdx] = resultsEDF[i];
			valuesBNT[i][batchIdx] = resultsBNT[i];
		}

		System.out.println(String.format("[%5d] %s %.2f ratio, |A| = %d (%3d of %d)", testNr,
			type, ratio, problem.getAgents().length, batchIdx + 1, replicates));
		System.out.println(String.format("[%5d] %s", testNr, resultsToString("EDF", resultsEDF)));
		System.out.println(String.format("[%5d] %s", testNr++, resultsToString("BNT", resultsBNT)));
	}

	public double getCI(double[] values) { // 95% Confidence Interval
		return 1.96d * (std.evaluate(values) * ((double) Math.sqrt(values.length)));
	}

	public String getCIMedian(double[] values) { // like above, but for median
		Arrays.sort(values);

		double v = 1.96 * Math.sqrt(values.length * 0.5 * 0.95);
		int j = (int) Math.ceil(values.length * 0.5 - v);
		int k = (int) Math.ceil(values.length * 0.5 + v);

		if (j < 0) j = 0;

		if (k >= values.length)
			k = values.length - 1;

		double m = median.evaluate(values);

		return String.format("%.2f +- [%.2f %.2f]", m, values[k] - m, m - values[j]);
	}

	public static double[] getResults(int totalTasks, Solver solver) {
		stopwatch.reset();
		stopwatch.start();
		solver.solve();
		stopwatch.stop();
		Solution solution = solver.getSolution();
		if (solution == null || solution.coalitionAllocations.length == 0) {
			//System.err.println(String.format("%s did not find solutions", solver.getClass().getSimpleName()));
			return new double[] { stopwatch.elapsed(TimeUnit.MILLISECONDS), 0, 0, 0 };
		}
		return new double[] {
			stopwatch.elapsed(TimeUnit.MILLISECONDS),
			100 * solution.tasks.size() / (double) totalTasks,
			solution.getScore(),
			solution.getMedianSingletonScore()
		};
	}

	public static String resultsToString(String id, double[] results) {
		return String.format("%-4s [time: %s ms, tasks: %.2f%%, score: %d, singleton score: %d]",
			id, (int) results[0], results[1], (int) Math.ceil(results[2]), (int) Math.ceil(results[3]));
	}

	@Parameter(names = { "--node-dataset-path", "-n" }, required = true, description = "Path to a London Fire Brigade data set containing nodes (tasks)")
	private String nodeDatasetPath;

	@Parameter(names = { "--station-dataset-path", "-s" }, required = true, description = "Path to a London Fire Brigade data set containing fire stations")
	private String stationDatasetPath;

	@Parameter(names = { "--problem-types", "-p" }, variableArity = true, description = "The types of problem to test with (See README for the full list)")
	private List<String> problemTypes = Arrays.asList(new String[] {
		"SUPERADDITIVE",
		//"LINEAR_PERTURBED",
		"UNIFORM",
		"NORMAL",
		"MODIFIED_UNIFORM",
		"MODIFIED_NORMAL",
		"AGENT_BASED",
		"NDCS",
		"CONGESTED_NDCS"
	});

	@Parameter(names = { "--agents", "-m" }, description = "Number of agents for each problem")
	private int agents = 150;

	@Parameter(names = { "--replicates", "-i" }, description = "How many replicates per test configuration")
	private int replicates = 100;

	@Parameter(names = { "--node-to-agent-ratios", "-r" }, variableArity = true, description = " The node-to-agent ratio (i.e., how many nodes per each agent)")
	private List<Float> ratios = Arrays.asList(new Float[] {
		1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f,
		11f, 12f, 13f, 14f, 15f, 16f, 17f, 18f, 19f, 20f
	});

    @Parameter(names = { "--help", "-h" }, description = "Print this help text", help = true)
    private boolean help = false;

	public static void main(String[] args) {
		Benchmark benchmark = new Benchmark();
		JCommander jct = JCommander.newBuilder().addObject(benchmark).build();
		jct.parse(args);

		if (benchmark.help) {
			jct.usage();
			return;
		}

        benchmark.valuesEDF = new double[4][benchmark.replicates];
        benchmark.valuesBNT = new double[4][benchmark.replicates];

		benchmark.lfb = new Setup(benchmark.nodeDatasetPath, benchmark.stationDatasetPath);

		System.out.println(String.format("[Benchmark] fixed number of agents: %d", benchmark.agents));
		System.out.println(String.format("[Benchmark] node-to-agent ratios: %s", benchmark.ratios));
		System.out.println(String.format("[Benchmark] replicates: %d", benchmark.replicates));
		System.out.println(String.format("[Benchmark] problem types: %s", benchmark.problemTypes));

		System.out.println("[Benchmark] session started\n");

		int i;
		for (String type : benchmark.problemTypes)
			for (float ratio : benchmark.ratios) {
				for (i = 0; i < benchmark.replicates; i++)
					benchmark.run(type, ratio, i);
				benchmark.reset(type, ratio);
			}

		System.out.println("[Benchmark] session terminated");
	}

}