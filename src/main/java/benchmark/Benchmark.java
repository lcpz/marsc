package benchmark;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Stopwatch;
import com.javadocmd.simplelatlng.LatLng;
import locations.Location;
import locations.LocationLatLng;
import model.Agent;
import model.MARSC;
import model.Solution;
import model.Task;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.json.JSONArray;
import org.json.JSONObject;
import solvers.*;
import toolkit.RandomProblemGenerator;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * LFB benchmark developed for the MARSC paper.
 *
 * @author lcpz
 */
public class Benchmark {

	protected Setup setup;
	protected Median median = new Median();

	// algorithms - metrics (plus solving time) - replicates
	protected double[][][] batchResults;

	private int testNr = 1;

	private static final Stopwatch stopwatch = Stopwatch.createUnstarted();

	protected void run(String type, float ratio, int batchIdx, MARSC problem) {
		if (problem == null)
			problem = setup.generate(type, agents, ratio);

		int i = 0;
		for (String algorithm : algorithms)
			switch (algorithm) {
				case "ANT": // Anytime and exact Node Traversal
					getAndPrintResults(new ANT(problem.clone(), timeoutMs, maxPermutations, maxConsecutiveInvestigated, verbose), i++, batchIdx);
					break;
				case "BNT": // Bounded Node Traversal
					getAndPrintResults(new BNT(problem.clone()), i++, batchIdx);
					break;
				case "CTS": // Cluster-based Task Scheduling
					getAndPrintResults(new CTS(problem.clone()), i++, batchIdx);
					break;
				case "EDF":
					getAndPrintResults(new EDF(problem.clone()), i++, batchIdx);
					break;
				case "CPLEX":
					getAndPrintResults(new CPLEX(problem.clone(), verbose), i++, batchIdx);
					break;
				default:
					System.err.format("%s is not a valid algorithm identifier", algorithm);
					break;
			}

		testNr++;
	}

	protected void getAndPrintResults(Solver solver, int i, int batchIdx) {
		stopwatch.reset();
		stopwatch.start();
		solver.solve(stopwatch);
		stopwatch.stop();

		StringBuilder s = new StringBuilder(String.format("[%5d] %s [", testNr, solver.getClass().getSimpleName()));

		Solution solution = solver.getSolution();

		if (solution != null) {
			if (solution.getScore(false) <= 0)
				solution.getScore(); // be sure to compute score

			if (solution.coalitionAllocations.length > 0) {
				batchResults[i][0][batchIdx] = solution.getScore(false);
				batchResults[i][1][batchIdx] = solution.getMedianSingletonScore();
				//batchResults[i][2][batchIdx] = solver.getAnytimeQualityIndex();
				batchResults[i][3][batchIdx] = 100 * (solution.tasks.size() / (float) solver.getProblem().getTasks().length);
			}

			s.append(String.format("score: %s, ", batchResults[i][0][batchIdx]));
			s.append(String.format("median singleton score: %s, ", batchResults[i][1][batchIdx]));
			//s.append(String.format("anytime quality index: %s, ", batchResults[i][2][batchIdx]));
			s.append(String.format("visited nodes (%%): %s, ", batchResults[i][3][batchIdx]));
		}

		if (solver instanceof CPLEX) {
			batchResults[i][4][batchIdx] = ((CPLEX) solver).getLastSolutionTime();
			batchResults[i][5][batchIdx] = ((CPLEX) solver).getFormulationTime();
			batchResults[i][6][batchIdx] = (float) stopwatch.elapsed(TimeUnit.MILLISECONDS);
			if (batchResults[i][6][batchIdx] == 0) batchResults[i][6][batchIdx] = 1;
			s.append(String.format("solving time (ms): %s, ", batchResults[i][4][batchIdx]));
			s.append(String.format("formulation time (ms): %s, ", batchResults[i][5][batchIdx]));
			s.append(String.format("total execution time (ms): %s]", batchResults[i][6][batchIdx]));
		} else {
			batchResults[i][4][batchIdx] = (float) stopwatch.elapsed(TimeUnit.MILLISECONDS);
			if (batchResults[i][4][batchIdx] == 0) batchResults[i][6][batchIdx] = 1;
			if (solver instanceof BNT || solver.getClass().equals(CTS.class)) {
				s.append(String.format("solving time (ms): %s, ", batchResults[i][4][batchIdx]));
				if (solver instanceof BNT && !solver.getClass().equals(ANT.class))
					batchResults[i][5][batchIdx] = ((BNT) solver).getMedianApproximationScoreRatio();
				else if (solver.getClass().equals(CTS.class))
					batchResults[i][5][batchIdx] = ((CTS) solver).getMedianApproximationScoreRatio();
				s.append(String.format("median approximation score: %s]", batchResults[i][5][batchIdx]));
			} else
				s.append(String.format("solving time (ms): %s]", batchResults[i][4][batchIdx]));
		}

		System.out.println(s);
	}

	public String getCIMedian(double[] values) { // 95% Confidence Interval for median
		Arrays.sort(values);

		//double v = 1.96d * (std.evaluate(values) * Math.sqrt(values.length));
		double v = 1.96d * Math.sqrt(values.length * 0.5 * 0.95);
		int j = (int) Math.ceil(values.length * 0.5 - v);
		int k = (int) Math.ceil(values.length * 0.5 + v);

		if (j < 0) j = 0;

		if (k >= values.length)
			k = values.length - 1;

		double m = median.evaluate(values);

		return String.format("%.4f +- [%.4f %.4f]", m, values[k] - m, m - values[j]);
	}

	protected void reset(String type, float ratio) {
		StringBuilder s;
		int i = 0;

		for (String algorithm : algorithms) {
			s = new StringBuilder(String.format("\n%s-%s %s [", type, ratio, algorithm));

			s.append(String.format("score: %s, ", getCIMedian(batchResults[i][0])));
			s.append(String.format("median singleton score: %s, ", getCIMedian(batchResults[i][1])));
			//s.append(String.format("anytime quality index: %s, ", getCIMedian(batchResults[i][2])));
			s.append(String.format("visited nodes (%%): %s, ", getCIMedian(batchResults[i][3])));

			if (algorithm.contains("CPLEX")) {
				s.append(String.format("solving time (ms): %s, ", getCIMedian(batchResults[i][4])));
				s.append(String.format("formulation time (ms): %s, ", getCIMedian(batchResults[i][5])));
				s.append(String.format("total execution time (ms): %s]", getCIMedian(batchResults[i][6])));
			} else if (algorithm.contains("BNT") || algorithm.contains("EDF") || algorithm.contains("CTS")) {
				s.append(String.format("solving time (ms): %s, ", getCIMedian(batchResults[i][4])));
				s.append(String.format("median approximation score: %s]", getCIMedian(batchResults[i][5])));
			} else
				s.append(String.format("solving time (ms): %s]", getCIMedian(batchResults[i][4])));

			System.out.print(s);

			batchResults[i++] = new double[7][replicates];
		}

		if (!randomProblems)
			setup.currIdx = 1;

		System.out.println();
	}

	protected static void createJson(MARSC problem, float ratio, int replicate) {
		JSONObject j = new JSONObject();

		Task[] tasks = problem.getTasks();
		JSONArray profit = new JSONArray(tasks.length);
		JSONArray workload = new JSONArray(tasks.length);
		JSONArray possibleNodeLocations = new JSONArray(tasks.length);
		JSONArray earliestTime = new JSONArray(tasks.length);
		JSONArray softLatestTime = new JSONArray(tasks.length);
		JSONArray hardLatestTime = new JSONArray(tasks.length);
		for (Task v : tasks) {
			profit.put(1); // homogeneous profits
            workload.put(v.demand.workload);
			JSONArray possibleLocations = new JSONArray(v.demand.possibleLocations.length);
			for (Location l : v.demand.possibleLocations) {
			    if (l instanceof LocationLatLng) {
					LatLng latLng = ((LocationLatLng) l).location;
					JSONObject objLocation = new JSONObject();
					objLocation.put("lat", latLng.getLatitude());
					objLocation.put("lon", latLng.getLongitude());
					possibleLocations.put(objLocation);
				}
			}
			possibleNodeLocations.put(possibleLocations);
			earliestTime.put(v.demand.timeWindow.earliestTime);
			softLatestTime.put(v.demand.timeWindow.softLatestTime);
			hardLatestTime.put(v.demand.timeWindow.hardLatestTime);
		}
		j.put("profit", profit);

		if (problem.getTaskOrdering() != null) {
			JSONArray order = new JSONArray(problem.getTaskOrdering().size());
			for (Map.Entry<Task, Task> entry : problem.getTaskOrdering().entrySet()) {
				JSONObject p = new JSONObject();
				p.put("preceded", entry.getKey().id);
				p.put("precedence", entry.getValue().id);
				order.put(p);
			}
			j.put("order", order);
		} else
			j.put("order", new JSONArray());

		j.put("workload", workload);

		j.put("possible_node_locations", possibleNodeLocations);

		j.put("earliest_time", earliestTime);
		j.put("soft_latest_time", softLatestTime);
		j.put("hard_latest_time", hardLatestTime);

		Agent[] agents = problem.getAgents();
		JSONArray agentInitialLocation = new JSONArray(agents.length);
		JSONArray agentSpeed = new JSONArray(agents.length);

		for (Agent a : agents) {
			if (a.location instanceof LocationLatLng) {
			    LatLng latLng = ((LocationLatLng) a.location).location;
			    JSONObject objLocation = new JSONObject();
			    objLocation.put("lat", latLng.getLatitude());
				objLocation.put("lon", latLng.getLongitude());
				agentInitialLocation.put(objLocation);
			}
			agentSpeed.put(a.speed);
		}

		j.put("agent_initial_location", agentInitialLocation);
		j.put("agent_speed", agentSpeed);
		j.put("u", 0); // Superadditive coalition values

		try {
			PrintWriter out = new PrintWriter(String.format("marsc_%.2f_%d.json", ratio, replicate).replace(",", "_"));
			out.println(j);
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Parameter(names = { "--node-dataset-path", "-n" }, description = "Path to a London Fire Brigade dataset containing nodes (tasks)")
	private String nodeDatasetPath;

	@Parameter(names = { "--station-dataset-path", "-s" }, description = "Path to a London Fire Brigade dataset containing fire stations")
	private String stationDatasetPath;

	@Parameter(names = { "--problem-types", "-p" }, variableArity = true, description = "The types of problem to test with. Possible entries: " +
		"SUPERADDITIVE, " +
		"LINEAR_PERTURBED, " +
		"UNIFORM, " +
		"NORMAL, " +
		"MODIFIED_UNIFORM, " +
		"MODIFIED_NORMAL, " +
		"AGENT_BASED, " +
		"U_AGENT_BASED, " +
		"C_AGENT_BASED, " +
		"UC_AGENT_BASED, " +
		"NDCS, " +
		"U_NDCS, " +
		"C_NDCS, " +
		"UC_NDCS")
	private List<String> problemClasses = Arrays.asList(
		"SUPERADDITIVE",
		"UNIFORM",
		"NORMAL",
		"UC_AGENT_BASED",
		"UC_NDCS"
	);

	@Parameter(names = { "--replicates", "-i" }, description = "How many replicates per test configuration")
	private int replicates = 100;

	@Parameter(names = { "--agents", "-m" }, description = "Number of agents for each problem")
	private int agents = 150;

	@Parameter(names = { "--generate-only", "-g" }, description = "Only generate problems (in JSON format)")
	private boolean generateOnly = false;

	@Parameter(names = { "--node-to-agent-ratios", "-r" }, variableArity = true, description = " The node-to-agent ratio (i.e., how many nodes per each agent)")
	private List<Float> ratios = Arrays.asList(
		1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f,
		11f, 12f, 13f, 14f, 15f, 16f, 17f, 18f, 19f, 20f
	);

	@Parameter(names = { "--algorithms", "-a" }, variableArity = true, description = "Algorithms to test. Possible entries: EDF, BNT, CTS, ANT, CPLEX")
	private List<String> algorithms = Arrays.asList("EDF", "BNT", "CTS", "ANT");

	@Parameter(names = { "--random", "-x" }, description = "Generate random problems, instead of using datasets")
	private boolean randomProblems = false;

	@Parameter(names = { "--locations-per-task", "-l" }, description = "Locations per task (only used to generate random problems)")
	private int locationsPerTask = 1;

	@Parameter(names = { "--grid-world-dimension", "-w" }, description = "Dimension of the square location space (only used to generate random problems)")
	private int gridWorldDim = 50;

	@Parameter(names = { "--timeout", "-t" }, description = "ANT's execution timeout (ms). Set 0 to disable it")
	private long timeoutMs = 600000; // 10 minutes

	@Parameter(names = { "--max-permutations", "-q" }, description = "ANT's maximum number of permutations investigated. Set 0 to disable it")
	private long maxPermutations = 100000; // 100k

	@Parameter(names = { "--max-consecutive-permutations", "-c" }, description = "ANT's maximum number of consecutive permutations investigated without improving the incumbent solution (i.e., convergence criterion). Set 0 to disable it")
	private long maxConsecutiveInvestigated = 1000000; // 1 million

	@Parameter(names = { "--verbose", "-y" }, description = "Be verbose (only used by ANT and CPLEX)")
	private boolean verbose = false;

    @Parameter(names = { "--help", "-h" }, description = "Print this text", help = true)
    private boolean help = false;

	public static void main(String[] args) {
		Benchmark benchmark = new Benchmark();
		JCommander jct = JCommander.newBuilder().addObject(benchmark).build();
		jct.parse(args);

		String jarName = new File(Benchmark.class.getProtectionDomain()
			.getCodeSource()
			.getLocation()
			.getPath())
			.getName();

		jct.setProgramName(String.format("java -jar %s", jarName));

		if (benchmark.help) {
			jct.usage();
			return;
		}

		if (!benchmark.randomProblems) {
			if (benchmark.nodeDatasetPath == null) {
				System.err.println("Missing node dataset path");
				return;
			}

			if (!new File(benchmark.nodeDatasetPath).isFile()) {
				System.err.format("%s is not a valid node dataset path\n", benchmark.nodeDatasetPath);
				return;
			}

			if (benchmark.stationDatasetPath == null) {
				System.err.println("Missing station dataset path");
				return;
			}

			if (!new File(benchmark.stationDatasetPath).isFile()) {
				System.err.format("%s is not a valid station dataset path\n", benchmark.stationDatasetPath);
				return;
			}
		}

		benchmark.batchResults = new double[benchmark.algorithms.size()][7][benchmark.replicates];

		System.out.printf("[Benchmark] fixed number of agents: %d\n", benchmark.agents);
		System.out.printf("[Benchmark] node-to-agent ratios: %s\n", benchmark.ratios);
		System.out.printf("[Benchmark] algorithms: %s\n", benchmark.algorithms);
		System.out.printf("[Benchmark] replicates: %d\n", benchmark.replicates);
		System.out.printf("[Benchmark] problem classes: %s\n", benchmark.problemClasses);

		System.out.println("[Benchmark] session started\n");

		int i;

		if (benchmark.generateOnly) {
			for (float ratio : benchmark.ratios)
				for (i = 0; i < benchmark.replicates; i++) {
					createJson(benchmark.setup.generate("SUPERADDITIVE", benchmark.agents, ratio), ratio, i); // type is not important, we pick the first
					System.out.printf("Problem %.2f-%d created", ratio, i);
				}
		} else {
			if (benchmark.randomProblems) {
				MARSC problem;
				for (String type : benchmark.problemClasses)
					for (float ratio : benchmark.ratios) {
						for (i = 0; i < benchmark.replicates; i++) {
							problem = RandomProblemGenerator.generate(type,
									benchmark.agents,
									(int) Math.ceil(benchmark.agents * ratio),
									benchmark.locationsPerTask,
									benchmark.gridWorldDim);
							benchmark.run(type, ratio, i, problem);
						}
						benchmark.reset(type, ratio);
						System.out.println();
					}
			} else {
				benchmark.setup = new Setup(benchmark.nodeDatasetPath, benchmark.stationDatasetPath);

				for (String type : benchmark.problemClasses)
					for (float ratio : benchmark.ratios) {
						for (i = 0; i < benchmark.replicates; i++)
							benchmark.run(type, ratio, i, null);
						benchmark.reset(type, ratio);
						System.out.println();
					}
			}

			System.out.println("[Benchmark] session terminated");
		}
	}

}
