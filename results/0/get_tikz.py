#!/usr/bin/env python3

# plot results in a TikZ-friendly format

import re, sys

semilogy = False # plot Y in log10 scale
median_filter = False
median_filter_window = 5

labels = { # hard-coded, but quicker
    0 : 'time',
    1 : 'tasks',
    2 : 'score',
    3 : 'singleton score'
}

if semilogy is True:
    from math import log10 as log

    def to_log(arr):
        max_value = max(arr)
        magnitude = int(log(max_value))
        if magnitude == 0:
            magnitude = 1
        return [(max_value * log(v+0.1) / magnitude) for v in arr]

if median_filter is True:
    from scipy.signal import medfilt

def my_print(metric, ratio, value):
    if metric == 'tasks':
        print('({}, {:.2f})'.format(ratio, value))
    else:
        print('({}, {})'.format(ratio, int(value)))

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

                median = float(e[0].replace(',', '.'))
                ci = e[1].replace('[', '').replace(']', '')
                key = labels[i]

                if key not in d[cr[0]]:
                    d[cr[0]][key] = {}

                if solver not in d[cr[0]][key]:
                    d[cr[0]][key][solver] = {}

                # problem-type, metric, solver, ratio
                d[cr[0]][key][solver][float(cr[1].replace(',', '.'))] = '{} {}'.format(median, ci)

                i += 1

    for problem_class, keys in d.items():
        print(problem_class)

        median = {}
        ci_plus = {}
        ci_minus = {}

        for key, value in keys.items(): # metric
            median[key] = {}
            ci_plus[key] = {}
            ci_minus[key] = {}

            for k, v in value.items(): # solver
                xarr = list(v.keys())
                yarr = []
                yarr_minus = []
                yarr_plus = []

                for x, s in v.items(): # values
                    arr = s.split(' ') # [y, CI_plus, CI_minus]
                    yarr.append(float(arr[0].replace(',', '.')))
                    yarr_plus.append(float(arr[0].replace(',', '.')) + float(arr[1].replace(',', '.')))
                    yarr_minus.append(float(arr[0].replace(',', '.')) - float(arr[2].replace(',', '.')))

                if semilogy is True:
                    yarr = to_log(yarr)
                    yarr_minus = to_log(yarr_minus)
                    yarr_plus = to_log(yarr_plus)

                median[key][k] = {}
                ci_plus[key][k] = {}
                ci_minus[key][k] = {}

                for i,_ in enumerate(yarr):
                    median[key][k][int(xarr[i])] = yarr[i]
                    ci_plus[key][k][int(xarr[i])] = yarr_plus[i]
                    ci_minus[key][k][int(xarr[i])] = yarr_minus[i]

                if median_filter is True:
                    median_m = medfilt(list(median[key][k].values()), kernel_size=[median_filter_window])
                    median_cp = medfilt(list(ci_plus[key][k].values()), kernel_size=[median_filter_window])
                    median_cm = medfilt(list(ci_minus[key][k].values()), kernel_size=[median_filter_window])

                    for i,_ in enumerate(yarr):
                        median[key][k][int(xarr[i])] = median_m[i]
                        ci_plus[key][k][int(xarr[i])] = median_cp[i]
                        ci_minus[key][k][int(xarr[i])] = median_cm[i]

        for metric, d1 in median.items():
            print(metric)
            for solver, d2 in d1.items():
                print(solver)
                print('median')
                for ratio, value in d2.items():
                    my_print(metric, ratio, value)
                print('confidence interval upper bound')
                for ratio, value in ci_plus[metric][solver].items():
                    my_print(metric, ratio, value)
                print('confidence interval lower bound')
                for ratio, value in ci_minus[metric][solver].items():
                    my_print(metric, ratio, value)

if __name__ == "__main__":
    main()
