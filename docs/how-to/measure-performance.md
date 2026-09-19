# Measure performance

Run these commands from the repository root with Java 25 available. The local
development dependency is iCLI `0.1.0`; `-Pmystem4j.useMavenLocal=true` enables its
local Maven installation. Other dependencies still come from Maven Central.

## Measure Java processing

```bash
./gradlew :mystem4j-benchmarks:test :mystem4j-benchmarks:jmh \
  -Pmystem4j.useMavenLocal=true \
  -PjmhArgs='.*MystemCoreBenchmark.* -f 2 -prof gc -rf json -rff build/core-jmh.json -foe true'
```

This measures JSON parsing, Unicode preparation, search tokenization and the
Lucene token stream separately on documents of 1,024, 16,384 and 131,072 UTF-16
units. Four independently authored fixtures cover Russian prose, technical text,
messages and queries. Each is measured separately (`documentIndex=0,1,2,3`),
including punctuation, numbers, URLs, emoji, combining marks and soft hyphens.
Fixtures are repeated or truncated at a code point boundary to the selected size;
large query fixtures are stress workloads, not claims about typical query length. The Lucene client returns cached synthetic responses;
these results describe Java processing cost, not native MyStem throughput.
Throughput is operations per second; allocation is Java heap bytes per operation.

`jmhSmoke` runs a short 1,024-unit case to check wiring. Its numbers are not a
performance baseline.

## Measure native processing and indexing

Prepare a working MyStem 3.1 executable, then run:

```bash
./gradlew :mystem4j-benchmarks:nativeJmh \
  -Pmystem4j.useMavenLocal=true \
  -Dmystem4j.executable=/absolute/path/to/mystem
```

Results are written to `mystem4j-benchmarks/build/reports/jmh/native.json`.
The executable path is passed to every fork. Setup opens the pool, builds an index
and verifies document counts and a term-query round trip before measurement starts.

| Measurement | One operation includes |
| --- | --- |
| `nativeRequest` | Send one prepared document through the pool and capture raw JSON |
| `luceneAnalysis` | Prepare, chunk, analyze and consume one complete document as Lucene terms |
| `indexBatch` | Create an in-memory directory, index four different fixture documents, commit/close the writer and close the directory |

Individual document calls cycle through the corpus; index batches select its first four documents.
Defaults use four concurrent callers, pools of one and four workers, all three
document sizes, two separate JVM forks, three one-second warmup iterations and
five one-second measurement iterations, with a fixed 512 MiB JVM heap. Results include sampled latency (ms/op),
percentiles and JVM allocation. Index latency is per four-document batch, not per
document. Pool waiting is part of the measured latency. Process startup belongs
to setup and is excluded. Allocation excludes native MyStem memory.

The full matrix can take many minutes, especially through Rosetta. Iteration
duration is a lower bound: JMH still waits for long indexing operations and all
concurrent callers to finish. Keep the complete run for a baseline.

For a quick native wiring check, add:

```text
-PnativeJmhArgs='.*MystemNativeBenchmark.* -p inputChars=1024 -p poolSize=1 -wi 1 -i 1 -f 1 -w 100ms -r 100ms'
```

## Use application documents

Add `-PbenchmarkCorpusDir=/absolute/path/to/documents` to either Gradle benchmark
command to replace the fixtures with your own nonempty UTF-8 `.txt` files. Files
are loaded in name order during setup, without network access. Keep at least four
for a varied four-document index batch. `inputChars` still repeats/truncates each
file to a fixed size; choose a size meaningful for your documents. In core JMH,
`documentIndex` selects a file (modulo corpus size); specify more indices to cover
a larger corpus. Retain the corpus or its hashes alongside results.

## Obtain enough tail observations

One-second iterations only check broad costs; long operations can produce too
few samples for useful p99. For a longer measurement of four-document indexing
at the largest size and four workers, run:

```bash
./gradlew :mystem4j-benchmarks:nativeJmh \
  -Pmystem4j.useMavenLocal=true \
  -Dmystem4j.executable=/absolute/path/to/mystem \
  -PnativeJmhArgs='.*MystemNativeBenchmark.indexBatch -p inputChars=131072 -p poolSize=4 -wi 3 -w 2s -i 5 -r 90s -f 2'
python3 mystem4j-benchmarks/tools/performance_evidence.py tail \
  mystem4j-benchmarks/build/reports/jmh/native.json
```

The second command fails if there are fewer than two forks or fewer than 1,000
samples divided across them (at least 500 in each of two forks). It suppresses
p99 for insufficient samples. This is a minimum evidence check, not a confidence
guarantee: inspect variation between forks and rerun comparisons. Extend `-r`
when it fails. Use the same procedure for another operation or pool size when
that tail matters to your application. These settings can take 15 minutes or more.

Each run overwrites `native.json`; copy it before running another case. Run
comparisons sequentially on an otherwise quiet machine.

## Observe native memory

On macOS or Linux, start this sampler in another terminal before the native run:

```bash
python3 mystem4j-benchmarks/tools/performance_evidence.py rss \
  --executable /absolute/path/to/mystem --output build/native-rss.json
```

Stop it with Ctrl-C after the benchmark (or set `--duration` in seconds). It records
PID/RSS samples with a 250 ms pause between reads for processes running that exact executable and
reports maximum observed aggregate RSS. Avoid concurrent unrelated work using
the same binary. No matching process makes the command fail. The report includes
the binary hash; retain it with JMH JSON. Sampling includes setup and warmup, may
miss short peaks and adds measurement overhead; summed RSS can count shared pages
more than once. It is distinct from Java allocation and from an exact OS peak.

Validate the evidence tools with
`python3 -m unittest discover -s mystem4j-benchmarks/tools -p 'test_*.py'`.

## Interpret and retain results

Keep the JSON result and console log together with the source revision and diff,
CPU, memory, OS, JDK, MyStem version and binary SHA-256. State whether MyStem runs
natively or through Rosetta. Record JVM heap overrides and benchmark arguments.
Use the same environment for before/after comparisons and inspect confidence
intervals and sample counts; small differences or sparse p99 samples are not
evidence of a regression. This corpus is a reproducible workload, not a substitute
for measuring the application's own documents.

Use the independent resource gate when changing process management:

```bash
./gradlew realMystemPoolSoak -Pmystem4j.useMavenLocal=true \
  -Dmystem4j.executable=/absolute/path/to/mystem
```

The soak checks response ownership, worker rotation, process release and file
descriptor counts under 30,000 short requests. Its throughput measures that short
workload; it cannot be substituted for document indexing latency.
