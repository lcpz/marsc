# MARSC

[![DOI](https://zenodo.org/badge/362866832.svg)](https://zenodo.org/badge/latestdoi/362866832)

| **Author** | **License** |
|---|---|
| [Luca Capezzuto](https://lcpz.gitlab.io) | [MIT](https://opensource.org/licenses/MIT) |

Algorithms for solving the *Multi-Agent Routing and Scheduling through Coalition formation problem* (MARSC).

## Usage

```shell
java -jar benchmark.jar -h
```

Requires Java 11+. To specify multiple entries for an option, separate them with spaces. Example:

```shell
java -jar benchmark.jar -n <node-dataset> -s <station-dataset> -a EDF BNT ANT -r 1 2 3 -p SUPERADDITIVE NDCS
```

## Building

```shell
mvn install
```

## Using CPLEX

Assuming a GNU/Linux distro and CPLEX version 20.1, add the following option to the Java VM:

```shell
-Djava.library.path=/opt/ibm/ILOG/CPLEX_Studio201/cplex/bin/x86-64_linux/
```

## To-do list

- [ ] Visualisation layer - initial problem status and solution found.
- [ ] Check the feasibility of [MASiRe](https://github.com/smart-pucrs/MASiRe) as a testbed.
