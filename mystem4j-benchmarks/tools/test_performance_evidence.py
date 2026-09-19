import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import performance_evidence as evidence


class EvidenceTest(unittest.TestCase):
    def result(self, counts, mode="sample"):
        return [{"benchmark": "indexBatch", "params": {}, "mode": mode, "primaryMetric": {
            "score": 3, "scoreUnit": "ms/op", "scorePercentiles": {"99.0": 4, "100.0": 5},
            "rawDataHistogram": [[[[3, count]]] for count in counts]}}]

    def summarize(self, data):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "jmh.json"
            path.write_text(json.dumps(data))
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                result = evidence.summarize(path, 1000)
            return result, json.loads(output.getvalue())

    def test_requires_samples_in_each_independent_fork(self):
        self.assertTrue(self.summarize(self.result([500, 500]))[0])
        for counts in [[20, 20], [999, 1], [1000]]:
            passed, rows = self.summarize(self.result(counts))
            self.assertFalse(passed)
            self.assertIsNone(rows[0]["p99"])

    def test_rejects_empty_and_throughput_results(self):
        for data in [[], self.result([500, 500], "thrpt")]:
            with self.assertRaises(ValueError):
                self.summarize(data)

    def test_rss_matches_exact_executable_and_sums_kib(self):
        with patch.object(evidence.subprocess, "run", return_value=SimpleNamespace(
                stdout=" 11 123 /tmp/my stem\n 12 456 /tmp/my stem\n 13 789 /other/mystem\n")):
            self.assertEqual([{"pid": 11, "rssBytes": 123 * 1024}, {"pid": 12, "rssBytes": 456 * 1024}],
                             evidence.resident_processes(Path("/tmp/my stem").resolve()))


if __name__ == "__main__":
    unittest.main()
