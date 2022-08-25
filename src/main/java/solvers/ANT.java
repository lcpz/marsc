package solvers;

import model.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.util.CombinatoricsUtils;
import toolkit.Utils;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ANT extends BNT {

    protected long timeoutMs, maxPermutations, maxConsecutiveInvestigatedLimit;

    protected boolean verbose;

    /* Variables for implementing the stopping criterion. */
    protected boolean stoppingCondition;
    protected long skipped; // number of permutations that have been safely skipped so far
    protected long investigated; // number of permutations that have been investigated
    protected long maxConsecutiveInvestigated; // number of consecutive permutations investigated (i.e., without improving the incumbent solution)
    protected long[] permutationsLeft;

    protected boolean timeout; // solving time limit condition

    /* Variables for calculating the anytime quality index. */
    public float anytimeQualityIndex;
    protected ArrayList<Long> anytimeSolutionTimes;
    protected ArrayList<Float> anytimeSolutionScores;
    protected long lastSolutionTime;

    public ANT(MARSC problem) {
        super(problem);
        anytimeSolutionTimes = new ArrayList<>();
        anytimeSolutionTimes.add(0L);
        anytimeSolutionScores = new ArrayList<>();
        anytimeSolutionScores.add(0f);
    }

    public ANT(MARSC problem, long timeoutMs, long maxPermutations, long maxConsecutiveInvestigatedLimit, boolean verbose) {
        this(problem);
        this.timeoutMs = timeoutMs;
        this.maxPermutations = maxPermutations;
        this.maxConsecutiveInvestigatedLimit = maxConsecutiveInvestigatedLimit;
        this.verbose = verbose;
    }

    protected void updateAgentStatus(Solution solution, Agent[] agents) {
        if (solution != null && solution.coalitionAllocations.length > 0) {
            CoalitionAllocation lastCA = solution.coalitionAllocations[solution.coalitionAllocations.length - 1];
            HashMap<Integer, Agent> m = new HashMap<>();
            for (Agent agent : agents)
                m.put(agent.id, agent);

            Agent agent;
            for (Agent a : lastCA.coalition) {
                agent = m.get(a.id);
                agent.endTime = lastCA.endTime;
                agent.location = lastCA.location;
            }
        }
    }

    /**
     * Get an optimal set of routes for the input task schedule.
     *
     * @param a An ordered array of task IDs.
     */
    private void getSolutionForSchedule(int[] a, Agent[] agents) {
        HashSet<Task> completedTasks = new HashSet<>();
        HashSet<CoalitionAllocation> l = new HashSet<>();
        Solution singletonSolution;
        float score = 0;

        for (int idx : a) {
            singletonSolution = getSingletonSolution(tasks[idx], agents);

            if (singletonSolution != null) {
                updateAgentStatus(solution, agents);
                completedTasks.addAll(singletonSolution.tasks);
                Collections.addAll(l, singletonSolution.coalitionAllocations);
                score += singletonSolution.getScore(false);
            }
        }

        // update incumbent solution
        Solution s = new Solution(completedTasks, l.toArray(new CoalitionAllocation[0]), score);
        updateSolution(s, score);

        updateStoppingCondition(completedTasks.size() - 1);
    }

    /**
     * Update stopping condition:
     * If there are no solutions for any permutation of k tasks, then the same holds
     * for any permutation of l > k tasks, hence stop.
     */
    private synchronized void updateStoppingCondition(int k) {
        if (k >= 0 && this.solution != null && this.solution.tasks.size() > 0 && this.solution.tasks.size() - 1 <= k) {
            if (permutationsLeft[k] == -1) // lazy initialisation
            	try {
            		if (tasks.length > 0 && k + 1 <= tasks.length)
            			permutationsLeft[k] = CombinatoricsUtils.binomialCoefficient(tasks.length, k + 1);
            	} catch (MathArithmeticException e) {
            		// the result is too large to be represented by a long integer, skip assignment
            	}
            stoppingCondition = --permutationsLeft[k] - skipped <= 0;
        }
    }

    private synchronized void updateSolution(Solution solution, float newScore) {
        if (this.solution == null || newScore > this.solution.getScore(false)) {
            this.solution = solution;
            if (stopwatch != null && solution != null) {
                lastSolutionTime = stopwatch.elapsed().toMillis();
                float lastScore = solution.getScore(false);

                if (anytimeSolutionScores.isEmpty() || anytimeSolutionScores.get(anytimeSolutionScores.size() - 1) < lastScore) {
                    // when the problem is too small or the computer is too fast
                    anytimeSolutionTimes.add(lastSolutionTime == 0 ? 1 : lastSolutionTime);
                    anytimeSolutionScores.add(lastScore);

                    if (verbose)
                        System.out.println(String.format("Time: %.4f s~ permutations %s~ incumbent solution score: %s",
                                lastSolutionTime / 10e2, skipped + investigated, newScore)
                                .replace(",", ".").replace("~", ","));
                }

                maxConsecutiveInvestigated = 0;
            }
        }

        if (timeoutMs > 0 && stopwatch != null && stopwatch.isRunning() && stopwatch.elapsed().toMillis() > timeoutMs)
            timeout = true;
    }

    @Override
    public void solve() { // using Heap's algorithm for creating permutations
        stoppingCondition = false;
        skipped = 0; // number of permutations that have been safely skipped
        permutationsLeft = new long[tasks.length];

        maxConsecutiveInvestigated = 0;
        int i, j;
        int[] c = new int[tasks.length], a = new int[tasks.length];

        Set<Task> taskSet = new TreeSet<>(comparator);
        Collections.addAll(taskSet, problem.getTasks());
        i = 0;
        for (Task v : taskSet) {
            permutationsLeft[i] = -1;
            a[i++] = v.id;
        }
        getSolutionForSchedule(a, Utils.deepClone(agents)); // first solution, synchronous

        // multi-thread search for remaining solutions
        //ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newCachedThreadPool();
        i = 0;
        while (i < tasks.length && !stoppingCondition && !timeout) {
            if (c[i] < i) {
                j = i % 2 == 0 ? 0 : c[i];

                ArrayUtils.swap(a, j, i);

                /*
                 * If swap satisfies ordering constraints, and at least one of the following holds:
                 *     1. Task i precedes task j
                 *     2. alpha_j >= alpha_i
                 *     3. gamma_j >= gamma_j
                 * then investigate this permutation of tasks.
                 * Conditions 1-3 excludes permutations that do not have optimal solutions, because they schedule less
                 * urgent tasks before more urgent ones.
                */
                //if (comparator.compare(tasks[a[j]], tasks[a[i]]) >= 0 &&
                if (order == null || (order.size() > 0 && satisfiesOrder(a))) {
                    executor.submit((Callable<Void>) () -> {
                        getSolutionForSchedule(Utils.deepClone(a), Utils.deepClone(agents));
                        return null;
                    });
                    investigated++;

                    // convergence criterion: stop if the incumbent solution does not improve after X permutations
                    if (maxPermutations <= 0 && maxConsecutiveInvestigatedLimit > 0)
                        stoppingCondition = ++maxConsecutiveInvestigated >= maxConsecutiveInvestigatedLimit;
                } else {
                    skipped++;
                    maxConsecutiveInvestigated = 0;
                }

                c[i]++;
                i = 0;

                if (maxPermutations > 0 && investigated + skipped >= maxPermutations) {
                    if (verbose)
                        System.out.format("Maximum number of permutations reached (%s)\n", maxPermutations);
                    break;
                }
            } else
                c[i++] = 0;
        }

        if (verbose && stoppingCondition)
            System.out.println("Stopping condition met");

        if (verbose && timeout)
            System.out.format("Timeout reached (%s ms)\n", timeoutMs);

        executor.shutdownNow();
    }

    private boolean satisfiesOrder(int[] a) {
        if (order == null || order.size() == 0)
            return true;

        Map<Task, Task> order = problem.getTaskOrdering();
        Map<Integer, Integer> m = new HashMap<>();
        int i;
        for (i = 0; i < a.length; i++)
            m.put(tasks[a[i]].id, i);

        Task prec;
        Integer precId;
        for (i = 0; i < a.length; i++) {
            prec = order.get(tasks[a[i]]);
            if (prec != null) {
                precId = m.get(prec.id);
                if (precId == null || precId > i) // precedence missing from task permutation, or violating order
                    return false;
            }
        }

        return true;
    }

    @Override
    public float getAnytimeQualityIndex() {
        if (anytimeQualityIndex == 0) {
            float optimalScore = solution.getScore(false);
            float t = lastSolutionTime == 0 ? 1 : lastSolutionTime;

            // Trapezoidal rule
            for (int i = 1; i < anytimeSolutionTimes.size(); i++)
                anytimeQualityIndex += Math.abs(
                    ((anytimeSolutionTimes.get(i) - anytimeSolutionTimes.get(i - 1)) / t) *
                    ((anytimeSolutionScores.get(i) + anytimeSolutionScores.get(i - 1)) / (2 * optimalScore))
                );
        }

        return anytimeQualityIndex;
    }

}
