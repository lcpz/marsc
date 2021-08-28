#!/usr/bin/env python3

# improvement ratios of Solver 1 over Solver 2

import re, sys
from statistics import median as med

semilogy = False # plot Y in log10 scale

# hard-coded, but quicker
labels = {
    0 : 'time',
    1 : 'tasks',
    2 : 'score',
    3 : 'singleton score'
}

solvers = ['BNT', 'EDF'] # Solver 1 and 2

if semilogy is True:
    from math import log10 as log

    def to_log(arr):
        max_value = max(arr)
        magnitude = int(log(max_value))
        if magnitude == 0:
            magnitude = 1
        return [(max_value * log(v+0.1) / magnitude) for v in arr]

def main():
    summary_file = 'summary.txt'

    if len(sys.argv) > 1:
        summary_file = sys.argv[1] # the input summary file

    d = {}

    with open(summary_file) as f:
        for line in f:
            cr = re.match(r'^.*-\d+', line).group(0).split('-') # class-ratio

            if cr[0] not in d:
                d[cr[0]] = {}

            solver = re.findall('(\w+) \[', line)[0]

            i = 0
            for entry in re.findall('\d+.?\d* \+\- \[\d+.?\d* \d+.?\d*\]', line):
                e = entry.split(' +- ')

                median = float(e[0])
                ci = e[1].replace('[', '').replace(']', '')
                key = labels[i]

                if key not in d[cr[0]]:
                    d[cr[0]][key] = {}

                if solver not in d[cr[0]][key]:
                    d[cr[0]][key][solver] = {}

                # problem-type, metric, solver, ratio
                d[cr[0]][key][solver][float(cr[1])] = '{} {}'.format(median, ci)

                i += 1

    for problem_class, keys in d.items():
        print(problem_class)

        median = {}
        median_plus = {}
        median_minus = {}

        for key, value in keys.items(): # metric
            median[key] = {}
            median_plus[key] = {}
            median_minus[key] = {}

            for k, v in value.items(): # solver
                xarr = list(v.keys())
                yarr = []
                yarr_plus = []
                yarr_minus = []

                for x, s in v.items(): # values
                    arr = s.split(' ') # [y, CI_plus, CI_minus]
                    yarr.append(float(arr[0]))
                    yarr_plus.append(float(arr[1]))
                    yarr_minus.append(float(arr[2]))

                if semilogy is True:
                    yarr = to_log(yarr)

                median[key][k] = {}
                median_plus[key][k] = {}
                median_minus[key][k] = {}

                for i,_ in enumerate(yarr):
                    median[key][k][int(xarr[i])] = yarr[i]
                    median_plus[key][k][int(xarr[i])] = yarr_plus[i]
                    median_minus[key][k][int(xarr[i])] = yarr_minus[i]

            d1 = median[key]
            d2 = median_plus[key]
            d3 = median_minus[key]

            for i in d1[solvers[0]].keys():
                if d1[solvers[1]][i] != 0:
                    d1[solvers[0]][i] /= d1[solvers[1]][i]
                if d2[solvers[1]][i] != 0:
                    d2[solvers[0]][i] /= d2[solvers[1]][i]
                if d3[solvers[1]][i] != 0:
                    d3[solvers[0]][i] /= d3[solvers[1]][i]

            med_value = med(d1[solvers[0]].values())
            lm = list(d1[solvers[0]].values()) + list(d2[solvers[0]].values()) + list(d3[solvers[0]].values())

            print('{} {:.2f} +- [{:.2f} {:.2f}]'.format(key, med_value, max(lm) - med_value, med_value - min(lm)))

if __name__ == "__main__":
    main()
