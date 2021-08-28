# MARSC

[![DOI](https://zenodo.org/badge/362866832.svg)](https://zenodo.org/badge/latestdoi/362866832)

| **Author** | **License** |
|---|---|
| [Luca Capezzuto](https://lcpz.gitlab.io) | [MIT](https://opensource.org/licenses/MIT) |

Algorithms for solving the *Multi-Agent Routing and Scheduling through Coalition
formation* problem (MARSC).

## Usage

```shell
java -jar benchmark.jar -h
```

Requires Java 11+. If you want to specify multiple entries for an option, separate them with spaces. Example:

```shell
java -jar benchmark.jar -a EDF BNT ANT -r 1 2 3 -p SUPERADDITIVE NDCS
```

## Building

```shell
mvn install
```

## Installing CPLEX

**Note:** we refer to CPLEX version 20.1; if you are using a different version, modify the commands below accordingly.

Add the following dependency to `pom.xml`:

```
<dependency>
<groupId>cplex</groupId>
<artifactId>cplex</artifactId>
<version>20.1</version>
</dependency>
```

And install the `cplex.jar` in your maven repository:

```shell
mvn install:install-file -DgroupId=cplex -DartifactId=cplex -Dversion=20.1 -Dpackaging=jar -Dfile=/opt/ibm/ILOG/CPLEX_Studio201/cplex/lib/cplex.jar
```

To use CPLEX, add the following option to the Java VM:

```shell
-Djava.library.path=/opt/ibm/ILOG/CPLEX_Studio201/cplex/bin/x86-64_linux/
```

## To-do list

- [ ] Visualisation layer - initial problem status and solution found.
- [ ] Check the feasibility of [MASiRe](https://github.com/smart-pucrs/MASiRe) as a testbed.
