package benchmark;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.math3.distribution.UniformIntegerDistribution;

import locations.Location;
import locations.LocationLatLng;
import model.Agent;
import model.Demand;
import model.MARSC;
import model.Task;
import model.TimeWindow;
import problems.Problem;

/**
 * A set of problems generated from the London Fire Brigade records.
 *
 * @author lcpz
 */
public class Setup {

	private String charSplit;

	/* The first line contains the headers. */
	public final String[][] nodeRecords;

	public final Map<String, ArrayList<String>> stationsMap;

	public final Map<String, Integer> nodeLabel;

	protected int currIdx; // current record index

	private ThreadLocalRandom rnd = ThreadLocalRandom.current();

	private UniformIntegerDistribution unifW = new UniformIntegerDistribution(10, 300);

	public Setup(Path taskDatasetPath, Path stationLocationDatasetPath, String charSplit) {
		this.charSplit = charSplit;
		String label = String.format("[%s]", this.getClass().getSimpleName());
		System.out.print(String.format("%s extracting task records... ", label));
		nodeRecords = extract(taskDatasetPath);

		// label -> row_index map to avoid hard-coding
		nodeLabel = new HashMap<>();
		for (int i = 0; i < nodeRecords[0].length; i++)
			nodeLabel.put(nodeRecords[0][i], i);

		//currIdx = ThreadLocalRandom.current().nextInt(1, nodeRecords.length); // random initial record
		currIdx = 1;
		System.out.print(String.format("done.\n%s extracting station records... ", label));
		stationsMap = extractMap(stationLocationDatasetPath);
		System.out.println(String.format("done.\n%s initialisation completed.", label));
	}

	public Setup(String nodeP, String stationP) {
		this(Paths.get(nodeP), Paths.get(stationP), ",");
	}

	private String[][] extract(Path path) {
		BufferedReader br = null;

		try {
			br = new BufferedReader(new FileReader(path.toFile()));

			String[][] s = new String[(int) Files.lines(path).count()][];
			String line;

			int i = 0;
			while ((line = br.readLine()) != null)
				s[i++] = line.split(charSplit);

			return s;
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null)
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}

		return null;
	}

	private Map<String, ArrayList<String>> extractMap(Path path) {
		BufferedReader br = null;

		try {
			br = new BufferedReader(new FileReader(path.toFile()));

			Map<String, ArrayList<String>> m = new HashMap<>();
			String line;
			String[] arr;
			ArrayList<String> al;
			String value;

			while ((line = br.readLine()) != null) {
				arr = line.split(charSplit);
				al = new ArrayList<>(arr.length);
				for (String col : arr)
					al.add(col);
				value = al.remove(0);
				m.put(value, al);
			}

			return m;
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null)
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}

		return null;
	}

	/**
	 * Generates a MARSC instance.
	 *
     * As of December 2020, the active LFB pumping appliances are 150 Mercedes
     * Benz Atego 1325F. Hence, this benchmark has the following limitations:
	 *
	 * - Max 150 homogeneous agents.
	 * - Each node has exactly 1 location.
	 * - Homogeneous rewards.
	 *
	 * @param type    The MARSC type.
	 * @param agentNr The number of agents in the instance.
	 * @param ratio   The tasks/agents ratio, that is, how many tasks per agent the
	 *                instance must contain.
	 * @return A MARSC instance.
	 * @throws Exception
	 */
	public MARSC generate(String type, int agentNr, float ratio) {
		if (currIdx >= nodeRecords.length)
			currIdx = 1;

		Task[] tasks = new Task[(int) Math.ceil(agentNr * ratio)];
		Map<Task, Task> order = new HashMap<>();
		int i, attendance, earliestTime, softLatestTime, hardLatestTime;
		ArrayList<LocationLatLng> initialAgentLocations = new ArrayList<>();
		ArrayList<String> station;
		LocationLatLng location;
		Location[] possibleLocations;
		TimeWindow timeWindow, twPrev, tw;
		int workload;

		int latIdx = nodeLabel.get("Latitude");
		int lngIdx = nodeLabel.get("Longitude");
		int stationIdx = nodeLabel.get("DeployedFromStation_Name");
		int stationCodeIdx = nodeLabel.get("DeployedFromStation_Code");
		int attendanceTimeIdx = nodeLabel.get("AttendanceTimeSeconds");

		for (i = 0; i < tasks.length; i++) {
			possibleLocations = new LocationLatLng[] {
				new LocationLatLng(Double.parseDouble(nodeRecords[currIdx][latIdx]),
				Double.parseDouble(nodeRecords[currIdx][lngIdx])) // 1 location per node
			};

			location = null;

			station = stationsMap.get(nodeRecords[currIdx][stationIdx]);
			if (station == null)
				stationsMap.get(nodeRecords[currIdx][stationCodeIdx]);

			if (station == null) // if no corresponding station name or code
				System.err.println(String.format("Input station dataset does not contain %s (%s), skipping it",
						nodeRecords[currIdx][stationIdx], nodeRecords[currIdx][stationCodeIdx]));
			else {
				location = new LocationLatLng(Double.parseDouble(station.get(0)), Double.parseDouble(station.get(1)));
				initialAgentLocations.add(location);
			}

			attendance = (int) Math.abs(Integer.parseInt(nodeRecords[currIdx][attendanceTimeIdx]));

			earliestTime = 0;
			if (location != null)
				earliestTime = possibleLocations[0].getTravelTimeTo(location, 1f);
			hardLatestTime = earliestTime + attendance;
			softLatestTime = rnd.nextInt(earliestTime, hardLatestTime + 1);
			timeWindow = new TimeWindow(earliestTime, softLatestTime, hardLatestTime);

			//workload = (int) Math.ceil(new UniformRealDistribution(attendance * 0.5f, attendance).sample());
			workload = unifW.sample();

			tasks[i] = new Task(i, new Demand(possibleLocations, workload, 1, timeWindow));

			// task ordering is a partial chain decided by the flip of a coin
			if (i > 0 && ThreadLocalRandom.current().nextInt(2) == 0) {
				twPrev = tasks[i-1].demand.timeWindow;
				tw = tasks[i].demand.timeWindow;
				if (twPrev.earliestTime <= tw.earliestTime
					&& twPrev.hardLatestTime < tw.hardLatestTime) // iff feasible
					order.put(tasks[i], tasks[i - 1]);
			}

			if (++currIdx >= nodeRecords.length)
				currIdx = 1; // we start from 1 since row 0 contains the headers
		}

		Agent[] agents = new Agent[agentNr];
		for (i = 0; i < agentNr; i++)
			// all agents have the same speed
		    agents[i] = new Agent(i, initialAgentLocations.get(rnd.nextInt(initialAgentLocations.size())), 1);

		return Problem.getInstance(type, tasks, order, agents);
	}

}
