# MARSC

| **Author** | **License** |
|---|---|
| [Luca Capezzuto](https://lcpz.gitlab.io) | [MIT](https://opensource.org/licenses/MIT) |

Algorithms for solving the *Multi-Agent Routing and Scheduling through Coalition
formation* (MARSC) problem.

## Usage

With Java 11 or above, start the LFB benchmark with:

```shell
java -jar lfb-benchmark.jar -n node-dataset.csv -s station-dataset.csv [-c [space-separated problem types]]
```

Valid values for `-c` are:

```shell
SUPERADDITIVE
LINEAR_PERTURBED
UNIFORM
NORMAL
MODIFIED_UNIFORM
MODIFIED_NORMAL
AGENT_BASED
NDCS
CONGESTED_NDCS
```

Except `LINEAR_PERTURBED` and `CONGESTED_NDCS`, these correspond to the
coalition value distributionss described in the following
[paper](https://eprints.soton.ac.uk/337164/1/Paper_524.pdf):

> Rahwan, Talal, Michalak, Tomasz and Jennings, Nicholas R. (2012). A hybrid
> algorithm for coalition structure generation. AAAI-12.

If `-c` is not set, the benchmark is run on all problem types.

## Build instructions

1. Import the project in Eclipse as a Maven project.
2. Update it to download the Maven dependencies.
3. In Eclipse's Package Explorer (left bar), right click on the project and select `Run As -> Maven install`.
