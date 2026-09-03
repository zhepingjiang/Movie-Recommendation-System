"""Tests for evaluation/metrics.py's ranking metrics."""

import math

from evaluation.metrics import coverage, mean_metric, ndcg_at_k, precision_at_k, recall_at_k


class TestPrecisionAtK:
    def test_none_when_no_relevant_items(self):
        assert precision_at_k([1, 2, 3], set(), k=3) is None

    def test_counts_hits_within_k_over_k(self):
        assert precision_at_k([1, 2, 3, 4], {2, 4}, k=3) == 1 / 3

    def test_ignores_hits_outside_k(self):
        assert precision_at_k([1, 2, 3, 4], {4}, k=2) == 0.0


class TestRecallAtK:
    def test_none_when_no_relevant_items(self):
        assert recall_at_k([1, 2, 3], set(), k=3) is None

    def test_counts_hits_within_k_over_relevant_count(self):
        assert recall_at_k([1, 2, 3, 4], {2, 4}, k=3) == 1 / 2

    def test_full_recall_when_all_relevant_items_found(self):
        assert recall_at_k([1, 2], {1, 2}, k=2) == 1.0


class TestNdcgAtK:
    def test_none_when_no_relevant_items(self):
        assert ndcg_at_k([1, 2, 3], set(), k=3) is None

    def test_perfect_score_when_relevant_item_ranked_first(self):
        assert ndcg_at_k([1, 2, 3], {1}, k=3) == 1.0

    def test_lower_score_when_relevant_item_ranked_later(self):
        # DCG = 1/log2(3) for a hit at position 2 (0-indexed 1); IDCG = 1/log2(2) for a hit at
        # position 1 -- ranking the relevant item later is worth strictly less.
        score = ndcg_at_k([9, 1, 2], {1}, k=3)
        assert score == (1 / math.log2(3)) / (1 / math.log2(2))
        assert 0 < score < 1

    def test_zero_when_relevant_item_outside_k(self):
        assert ndcg_at_k([9, 8, 7], {1}, k=2) == 0.0


class TestMeanMetric:
    def test_drops_nones_rather_than_counting_as_zero(self):
        assert mean_metric([1.0, None, 0.5]) == 0.75

    def test_none_when_everything_is_none(self):
        assert mean_metric([None, None]) is None

    def test_none_for_empty_list(self):
        assert mean_metric([]) is None


class TestCoverage:
    def test_fraction_of_catalog_ever_recommended_within_k(self):
        ranked_lists = {1: [10, 20, 30], 2: [10, 40]}

        assert coverage(ranked_lists, catalog_size=10, k=2) == 3 / 10  # {10, 20, 40}, not 30

    def test_zero_for_empty_catalog(self):
        assert coverage({1: [10]}, catalog_size=0, k=5) == 0.0
