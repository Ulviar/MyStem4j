#!/usr/bin/env python3
"""Validate native JMH tail samples, or sample MyStem RSS on macOS/Linux (stdlib only)."""

import argparse
import hashlib
import json
import math
import signal
import subprocess
import time
from pathlib import Path


def summarize(path, minimum):
    rows = []
    for result in json.loads(path.read_text()):
        metric = result["primaryMetric"]
        if result["mode"] != "sample":
            raise ValueError("Tail validation requires JMH sample mode, not throughput/average mode")
        samples = [sum(count for iteration in fork for _, count in iteration)
                   for fork in metric["rawDataHistogram"]]
        sufficient = len(samples) >= 2 and all(count >= math.ceil(minimum / len(samples)) for count in samples)
        rows.append({"benchmark": result["benchmark"], "params": result["params"],
                     "unit": metric["scoreUnit"], "mean": metric["score"],
                     "samplesByFork": samples, "sufficientForP99": sufficient,
                     "p99": metric["scorePercentiles"]["99.0"] if sufficient else None,
                     "observedMaximum": metric["scorePercentiles"]["100.0"]})
    if not rows:
        raise ValueError("Empty JMH result")
    print(json.dumps(rows, ensure_ascii=False, indent=2))
    return all(row["sufficientForP99"] for row in rows)


def resident_processes(executable):
    # comm (not args) avoids recording document contents or unrelated command-line secrets.
    output = subprocess.run(["ps", "-axo", "pid=,rss=,comm="], check=True,
                            capture_output=True, text=True, timeout=5).stdout
    processes = []
    for line in output.splitlines():
        parts = line.strip().split(None, 2)
        if len(parts) == 3 and Path(parts[2]).resolve() == executable:
            processes.append({"pid": int(parts[0]), "rssBytes": int(parts[1]) * 1024})
    return processes


def sample_rss(executable, output, duration, interval):
    executable = executable.resolve(strict=True)
    stopped = False

    def stop(_signal, _frame):
        nonlocal stopped
        stopped = True

    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)
    samples = []
    started = time.monotonic()
    try:
        while not stopped and time.monotonic() - started < duration:
            processes = resident_processes(executable)
            samples.append({"elapsedSeconds": time.monotonic() - started, "processes": processes,
                            "totalRssBytes": sum(process["rssBytes"] for process in processes)})
            time.sleep(interval)
    finally:
        report = {"executable": str(executable),
                  "sha256": hashlib.sha256(executable.read_bytes()).hexdigest(),
                  "intervalSeconds": interval, "elapsedSeconds": time.monotonic() - started,
                  "maximumObservedTotalRssBytes": max((sample["totalRssBytes"] for sample in samples), default=0),
                  "maximumObservedWorkers": max((len(sample["processes"]) for sample in samples), default=0),
                  "samples": samples}
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + "\n")
    if not report["maximumObservedWorkers"]:
        raise ValueError("No matching MyStem process observed; RSS measurement is not valid")
    print(json.dumps({key: value for key, value in report.items() if key != "samples"}, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    tail = commands.add_parser("tail", help="Require at least two populated forks and a minimum tail sample count")
    tail.add_argument("json", type=Path)
    tail.add_argument("--min-samples", type=int, default=1000)
    rss = commands.add_parser("rss", help="Sample processes running exactly this executable; Ctrl-C writes the report")
    rss.add_argument("--executable", type=Path, required=True)
    rss.add_argument("--output", type=Path, required=True)
    rss.add_argument("--duration", type=float, default=1800)
    rss.add_argument("--interval", type=float, default=0.25)
    args = parser.parse_args()
    if args.command == "tail":
        if args.min_samples < 1:
            parser.error("--min-samples must be positive")
        raise SystemExit(0 if summarize(args.json, args.min_samples) else 1)
    if args.duration <= 0 or args.interval <= 0:
        parser.error("--duration and --interval must be positive")
    sample_rss(args.executable, args.output, args.duration, args.interval)


if __name__ == "__main__":
    main()
