package solvers;

import ilog.concert.*;
import ilog.cplex.IloCplex;
import locations.Location;
import model.*;
import org.apache.commons.lang3.StringUtils;
import toolkit.Utils;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Reducing the MARSC to a Binary Integer Program (BIP) and solving it with CPLEX.
 *
 * @author lcpz
 */
public class CPLEX extends Solver {

    private boolean verbose;

	/* Variables for calculating the anytime quality index. */
	public float anytimeQualityIndex;
	protected ArrayList<Long> anytimeSolutionTimes;
	protected ArrayList<Float> anytimeSolutionScores;
	protected long lastSolutionTime;

	/**
	 * Log new incumbents if they are at better than the old.
	 */
	static class LogCallback extends IloCplex.MIPInfoCallback {
		CPLEX solver;
		double lastIncumbent, timeStart, detTimeStart;

		LogCallback(CPLEX solver, double lastIncumbent, double timeStart, double detTimeStart) {
		    this.solver = solver;
			this.lastIncumbent = lastIncumbent;
			this.timeStart = timeStart;
			this.detTimeStart = detTimeStart;
		}

		public void main() throws IloException {
			if (hasIncumbent() && Math.abs(lastIncumbent) < Math.abs(getIncumbentObjValue())) {
				lastIncumbent = Math.abs(getIncumbentObjValue());
			    float newScore = getCurrentScore();
				solver.updateScore((long) (getCplexTime() - timeStart), newScore);

				if (solver.verbose)
					System.out.println(String.format("Time: %.4f s~ ticks: %.4f~ incumbent solution score: %.4f",
						getCplexTime() - timeStart, getDetTime() - detTimeStart, newScore)
						.replace(",", ".").replace("~", ","));
			}
		}

		public float getCurrentScore() throws IloException {
			int h, i, j, k;
			float currentScore = 0;

			for (h = 0; h < solver.caVars.length; h++) { // task
				for (i = 0; i < solver.caVars[h].length; i++) // location
					for (j = 0; j < solver.caVars[h][i].length; j++) { // coalition
						for (k = 0; k < solver.caVars[h][i][j].length; k++) // time
							if (solver.caVars[h][i][j][k] != null && getIncumbentValue(solver.caVars[h][i][j][k]) > 0) {
								if (solver.tasks[h].demand.timeWindow.earliestTime + k <=
									solver.tasks[h].demand.timeWindow.softLatestTime)
									currentScore += solver.tasks[h].demand.profit;
								else
									currentScore += (float) 1 / k * solver.tasks[h].demand.profit;
							}
					}
			}

			return currentScore;
		}

	}

	private synchronized void updateScore(long lastTime, float lastScore) {
		lastSolutionTime = lastTime == 0 ? 1 : lastTime; // when the problem is too small or the computer is too fast
		anytimeSolutionTimes.add(lastSolutionTime);
		anytimeSolutionScores.add(lastScore);
	}

	private IloCplex lp; /* The Linear Program */

	/* Statistics */
	private String status = "Unsolved", cplexStatus = "N/A";
	private double value = Double.NaN;
	private long formulationTime = -1;
	private long solvingTime = -1;

	/*
	 * Coalition Allocation binary variables. The indexes are:
	 *
	 * 1. task
	 * 2. location
	 * 3. coalition (expressed as a number between 1 and 2^|A|)
	 * 4. time
	 */
	private IloIntVar[][][][] caVars;

	private final Map<Integer, Agent[]> caMap;

	private final Map<Integer, Float> caValueMap;

	public final int coalitionsNr;

	public CPLEX(MARSC problem) {
		super(problem);

		coalitionsNr = (int) Math.pow(2, agents.length);
		caMap = new HashMap<>();
		caValueMap = new HashMap<>();

		anytimeSolutionTimes = new ArrayList<>();
		anytimeSolutionTimes.add(0L);
		anytimeSolutionScores = new ArrayList<>();
		anytimeSolutionScores.add(0f);
	}

	public CPLEX(MARSC problem, boolean verbose) {
		this(problem);
		this.verbose = verbose;
	}

	/**
	 * Creates a BIP of the input problem.
	 */
	private void createFormulation() {
		try {
			lp = new IloCplex();

			caVars = new IloIntVar[tasks.length][][][];

			/*
			 * In a solution, the coalition allocations related to a task must all have the same location.
			 * This is expressed with binary variables, where the indexes are:
			 *
			 * 1. task
			 * 2. location
			 */
			IloIntVar[][] locVars = new IloIntVar[tasks.length][];

			IloNumExpr objective = lp.intExpr();

			int h, i, j, k;
			Location[] possibleLocations;
			TimeWindow tw;
			IloNumExpr workloadDone, locVarsSum;

			Agent[] coalition;
			int coalitionStartTime;
			Task precedence;

			for (h = 0; h < tasks.length; h++) { // task
				possibleLocations = tasks[h].demand.possibleLocations;
				tw = tasks[h].demand.timeWindow;
				caVars[h] = new IloIntVar[possibleLocations.length][][];
				locVars[h] = new IloIntVar[possibleLocations.length];
				workloadDone = lp.linearNumExpr();
				locVarsSum = lp.linearIntExpr();

				for (i = 0; i < possibleLocations.length; i++) { // location
					caVars[h][i] = new IloIntVar[coalitionsNr][tw.hardLatestTime - tw.earliestTime];
					locVars[h][i] = lp.boolVar();
					locVarsSum = lp.sum(locVarsSum, locVars[h][i]);

					for (j = 1; j < coalitionsNr; j++) { // coalition
						coalition = getCoalition(j);

						if (coalition == null || coalition.length == 0)
							continue;

						/*
						 * Spatial constraints (1).
						 *
						 * If exists an agent in this coalition that cannot
						 * reach the task location within the time window,
						 * then do not add the decision variable.
						 */
						coalitionStartTime = startingTimeFromInitialLocation(tasks[h], possibleLocations[i], coalition);
						if (coalitionStartTime > -1) {
							if (coalitionStartTime > tw.earliestTime)
								coalitionStartTime -= tw.earliestTime;
							else
								coalitionStartTime = 0;

							for (k = coalitionStartTime; k < tw.hardLatestTime - tw.earliestTime; k++) { // time
								caVars[h][i][j][k] = lp.boolVar(); /* Binary (0-1) decision variables */
								//objective = lp.sum(objective, lp.prod(getPhiPsi(h, tw.earliestTime + k), caVars[h][i][j][k]));
								//workloadDone = lp.sum(workloadDone, lp.prod(Math.ceil(getCoalitionValue(tasks[h], possibleLocations[i], coalition, j)), caVars[h][i][j][k]));
								objective = lp.sum(objective, lp.prod(tasks[h].demand.profit * (tw.earliestTime + k), caVars[h][i][j][k]));
								workloadDone = lp.sum(workloadDone, lp.prod(getCoalitionValue(tasks[h], possibleLocations[i], coalition, j), caVars[h][i][j][k]));
							}
						}
					}
				}

				/* Temporal constraints (1). */
				lp.addLe(locVarsSum, 1);
				lp.addGe(workloadDone, tasks[h].demand.workload);
				//lp.addEq(workloadDone, Math.ceil(tasks[h].demand.workload));
			}

			/*
			 * Spatial constraints (2).
			 *
			 * If an agent can't work on two tasks consecutively,
			 * then it can work on at most one between them.
			 */
			TreeSet<Task> orderedTasks = new TreeSet<>((v1, v2) -> {
				if (order != null) { // satisfy ordering constraints
					Task prec1 = order.get(v1);
					Task prec2 = order.get(v2);
					if (prec1 != null && prec1.equals(v2))
						return 1;
					if (prec2 != null && prec2.equals(v1))
						return -1;
				}

				return v1.demand.timeWindow.earliestTime - v2.demand.timeWindow.earliestTime;
			});

			Collections.addAll(orderedTasks, tasks);

			Task currentTask = orderedTasks.pollFirst();
			for (Task v : orderedTasks) {
				// intersecting time windows, or v's starts before currentTask's
				if (currentTask != null && v.demand.timeWindow.earliestTime <= currentTask.demand.timeWindow.hardLatestTime) {
					tw = currentTask.demand.timeWindow;
					possibleLocations = currentTask.demand.possibleLocations;
					for (i = 0; i < possibleLocations.length; i++)
						for (j = 1; j < coalitionsNr; j++)
							for (k = 0; k < tw.hardLatestTime - tw.earliestTime; k++)
								if (caVars[currentTask.id][i][j][k] != null)
									addInterTaskSpatialConstraints(currentTask.id, i, j, k, v);
				}
				currentTask = v;
			}

			/* Structural constraints. */
			IloIntExpr structuralConstraintsExpr;
			for (h = 0; h < tasks.length; h++) { // task
				tw = tasks[h].demand.timeWindow;
				for (i = 0; i < tasks[h].demand.possibleLocations.length; i++) // location
					for (k = 0; k < tw.hardLatestTime - tw.earliestTime; k++) { // time
						structuralConstraintsExpr = lp.intExpr();
						for (j = 1; j < coalitionsNr; j++) // coalition
							if (caVars[h][i][j][k] != null) {
								structuralConstraintsExpr = lp.sum(structuralConstraintsExpr, caVars[h][i][j][k]);
								// Temporal constraints (2) - all coalition allocations must have the same location
								lp.addLe(caVars[h][i][j][k], locVars[h][i]);
							}
						lp.addLe(structuralConstraintsExpr, 1); // at most 1 CA per task, location and time
					}
			}

			/* Ordering constraints. */
			if (order != null && order.size() > 0)
				for (h = 0; h < tasks.length; h++) {
					precedence = order.get(tasks[h]);
					tw = tasks[h].demand.timeWindow;

					if (precedence != null &&
						tw.earliestTime <= precedence.demand.timeWindow.hardLatestTime) {
						for (i = 0; i < tasks[h].demand.possibleLocations.length; i++)
							for (j = 1; j < coalitionsNr; j++)
							    addOrderingConstraints(h, i, j, tw.earliestTime, precedence.demand.timeWindow.hardLatestTime);
					}
				}

			/* Objective function */
			lp.addMinimize(objective);
		} catch (IloException e) {
			e.printStackTrace();
		}
	}

	private float getCoalitionValue(Task task, Location location, Agent[] coalition, int coalitionIdx) {
		Float f = caValueMap.get(coalitionIdx);

		if (f != null)
			return f;

		f = problem.getValue(task, location, coalition);
		caValueMap.put(coalitionIdx, f);

		return f;
	}

	@SuppressWarnings("unused")
	private float getPhiPsi(int taskIndex, int t) {
		TimeWindow tw = tasks[taskIndex].demand.timeWindow;
		float d = tasks[taskIndex].demand.profit;
		if (t > tw.softLatestTime)
			d *= (float) 1/t;
		return d;
	}

	private int startingTimeFromInitialLocation(Task task, Location location, Agent[] coalition) {
		int gamma = task.demand.timeWindow.hardLatestTime;
		int max = 0, temp;

		for (Agent a : coalition) {
			temp = a.getTravelTimeTo(location);
			if (temp >= gamma)
				return -1; // if some agents in this coalition cannot reach location, then this coalition is not valid
			if (temp > max)
				max = temp;
		}

		/* If an agent reaches a task location at time t, it starts to work at time t+1. */
		return max + 1;
	}

	private void addInterTaskSpatialConstraints(int h, int i, int j, int k, Task v) throws IloException {
		int i2, j2, k2, lambda;

		int alpha = tasks[h].demand.timeWindow.earliestTime;
		Location location = tasks[h].demand.possibleLocations[i];
		Agent[] coalition = getCoalition(j);
		Set<Agent> commonAgents;

		Location[] possibleLocations = v.demand.possibleLocations;
		TimeWindow tw = v.demand.timeWindow;

		if (coalition != null)
			for (i2 = 0; i2 < possibleLocations.length; i2++) {
				for (j2 = 1; j2 < coalitionsNr; j2++) {
					if (j == j2)
						commonAgents = Set.of(coalition);
					else
						commonAgents = Utils.intersection(coalition, getCoalition(j2));

					if (commonAgents != null && commonAgents.size() > 0) {
						lambda = 1 + alpha + k + getMaxRho(commonAgents, location, possibleLocations[i2]);

						if (tw.earliestTime <= lambda && lambda < tw.hardLatestTime)
							for (k2 = 0; k2 <= lambda - tw.earliestTime; k2++)
								if (caVars[v.id][i2][j2][k2] != null)
									lp.addLe(lp.sum(caVars[h][i][j][k], caVars[v.id][i2][j2][k2]), 1);
					}
				}
			}
	}

	private int getMaxRho(Set<Agent> agents, Location location, Location location2) {
		int rho, maxRho = 0;

		for (Agent a : agents) {
			rho = a.getTravelTime(location, location2);
			if (rho > maxRho)
				maxRho = rho;
		}

		return maxRho;
	}

	private void addOrderingConstraints(int v2, int l, int C, int alpha2, int gamma1) throws IloException {
		//IloIntExpr expr = lp.intExpr();

        try {
			for (int t = alpha2; t <= gamma1; t++)
				if (caVars[v2].length > l && caVars[v2][l].length > C && caVars[v2][l][C].length > t &&
						caVars[v2][l][C][t] != null) {
					lp.remove(caVars[v2][l][C][t]);
					caVars[v2][l][C][t] = null;
					//expr = lp.sum(expr, caVars[v2][l][C][t]);
				}
		} catch (ArrayIndexOutOfBoundsException e) {
        	e.printStackTrace();
		}

		//lp.addEq(expr, 0);
	}

	@Override
	public void solve() {
		try {
			createFormulation();
			if (stopwatch != null)
				formulationTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);
		} catch (OutOfMemoryError e) {
			e.printStackTrace();
			return;
		}


		lp.setOut(null); // turn all logging off

		try { /* Solve the linear program and show the solution found (if any) */
			//lp.setParam(IloCplex.Param.Preprocessing.Presolve, false); // no pre-solve, because ANT does not have it
			lp.setParam(IloCplex.Param.MIP.Display, 0); // turn logging off (but print version and parameters)
			lp.setParam(IloCplex.Param.TimeLimit, 3600); // max solving time: 1 hour

			lp.exportModel("last_bip.lp"); // not only this exports the BIP, it also optimises the formulation

			// set the maximum number of nodes solved before the algorithm terminates without reaching optimality
			//lp.setParam(IloCplex.Param.MIP.Limits.Nodes, 5000); // taken from MIPex4 example

			double lastObjVal = (lp.getObjective().getSense() == IloObjectiveSense.Minimize) ? Double.MAX_VALUE : -Double.MAX_VALUE;
			lp.use(new LogCallback(this, lastObjVal, lp.getCplexTime(), lp.getDetTime()));

			boolean solutionFound = lp.solve();
			if (stopwatch != null)
				solvingTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);

			if (solutionFound) {
				status = lp.getStatus().toString().toLowerCase();
				cplexStatus = lp.getCplexStatus().toString().toLowerCase();
				value = lp.getObjValue();

				int h, i, j, k, earliestTime;
				Location[] locations;
				TreeSet<Integer> times;
				List<CoalitionAllocation> lca = new LinkedList<>();
				Set<Task> taskSet = new HashSet<>();

				for (h = 0; h < caVars.length; h++) { // task
					locations = tasks[h].demand.possibleLocations;
					earliestTime = tasks[h].demand.timeWindow.earliestTime;
					for (i = 0; i < caVars[h].length; i++) // location
						for (j = 0; j < caVars[h][i].length; j++) { // coalition
							times = new TreeSet<>();
							for (k = 0; k < caVars[h][i][j].length; k++) // time
								if (caVars[h][i][j][k] != null && lp.getValue(caVars[h][i][j][k]) > 0)
									times.add(earliestTime + k);
							if (times.size() > 0)
								lca.add(new CoalitionAllocation(tasks[h], locations[i], getCoalition(j), caValueMap.get(j), times.first(), times.last()));
						}
					taskSet.add(tasks[h]);
				}

				CoalitionAllocation[] coalitionAllocations = lca.toArray(new CoalitionAllocation[0]);

				/*
				CoalitionAllocation ca;
				for (h = 0; h < coalitionAllocations.length; h++)
					if (h + 1 < coalitionAllocations.length)
						if (coalitionAllocations[h].task.id == coalitionAllocations[h+1].task.id && coalitionAllocations[h].endTime != coalitionAllocations[h+1].startTime - 1) {
							ca = new CoalitionAllocation(coalitionAllocations[h].task, coalitionAllocations[h].location, coalitionAllocations[h].coalition, coalitionAllocations[h].value, coalitionAllocations[h].startTime, coalitionAllocations[h + 1].startTime - 1);
							coalitionAllocations[h] = ca;
						}
				 */

				solution = new Solution(taskSet, coalitionAllocations);
			}
		} catch (IloException | OutOfMemoryError e) {
			e.printStackTrace();
		}
	}

	private Agent[] getCoalition(int n) {
		if (n <= 0) // empty coalition
			return null;

		Agent[] coalition = caMap.get(n);

		if (coalition != null)
			return coalition;

		char[] bin = StringUtils.leftPad(Integer.toBinaryString(n), agents.length, '0').toCharArray();
		HashSet<Agent> coalitionSet = new HashSet<>();

		for (int i = 0; i < bin.length; i++)
			if (bin[i] == '1')
				coalitionSet.add(agents[i]);

		if (coalitionSet.size() > 0) {
			coalition = coalitionSet.toArray(new Agent[0]);
			caMap.put(n, coalition);
			return coalition;
		}

		return null;
	}

	public float getFormulationTime() {
		return formulationTime;
	}

	public String getStats() {
		return String.format("Solution status: %s\nCPLEX status: %s\nSolution value: %d\nTime to formulate: %s s\nTime to solve: %s s\nTotal time: %s s",
			status, cplexStatus, (int) Math.ceil(value), formulationTime / (float) 1000, solvingTime / (float) 1000, (formulationTime + solvingTime) / (float) 1000);
	}

	@Override
	public float getAnytimeQualityIndex() {
		if (anytimeQualityIndex == 0) {
			float optimalScore;
			if (solution.getScore(false) == 0)
				optimalScore = solution.getScore();
			else
				optimalScore = solution.getScore(false);

			float t = lastSolutionTime == 0 ? 1 : lastSolutionTime;

			/* CPLEX does use the logging callback if it immediately reaches an optimal solution,
			   hence we put the final solution in our anytime arrays. */
			if (anytimeSolutionTimes.size() == 1 && anytimeSolutionTimes.get(0) == 0) {
				anytimeSolutionTimes.add((long) t);
				anytimeSolutionScores.add(optimalScore);
			}

			// Trapezoidal rule
			for (int i = 1; i < anytimeSolutionTimes.size(); i++)
				anytimeQualityIndex += Math.abs(((anytimeSolutionTimes.get(i) - anytimeSolutionTimes.get(i - 1)) / t) *
						((anytimeSolutionScores.get(i) + anytimeSolutionScores.get(i - 1)) / (2 * optimalScore)));
		}

		return anytimeQualityIndex;
	}

	public long getLastSolutionTime() {
		return lastSolutionTime * 1000; // we want ms
	}

}