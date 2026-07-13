"""Training entry point: `uv run roomtype-train`.

The evaluation harness here is complete and is worth reading closely, because measuring a
model honestly is a larger part of the job than fitting one. What is left for you is
build_model(), and features.py.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import joblib
import numpy as np
from sklearn.base import BaseEstimator
from sklearn.metrics import classification_report, confusion_matrix

from roomtype import baseline, dataset
from roomtype.features import FEATURE_NAMES, featurise_all
from roomtype.labels import LABELS

MODEL_PATH = Path(__file__).resolve().parents[2] / "artifacts" / "roomtype.joblib"

#: Plans held out from training. Chosen deliberately, not sampled: see dataset.split_by_plan.
#: Write down WHY each plan is held out, so that a future you does not quietly "fix" a bad
#: score by moving an inconvenient plan into the training set. That is the most tempting and
#: most dishonest thing you can do to a test set.
DEFAULT_TEST_PLANS = {
    # An institutional plan with a labelling vocabulary unlike the houses: if the model
    # generalises to this, it has learned something about rooms rather than about one architect.
    "schenley-high-school-1916",
}


def build_model() -> BaseEstimator:
    """Return an UNFITTED scikit-learn estimator (or Pipeline). THIS IS YOURS TO WRITE.

    Start with LogisticRegression. Not because it is best, but because it is linear, which
    means you can read its coefficients and find out what it actually learned. A model you
    cannot interrogate is a model you cannot trust with a safety decision, and "the occupant
    load of this room" is a safety decision.

    Only once the linear model is honestly measured should you try something with more
    capacity (RandomForest, GradientBoosting) and ask whether the extra accuracy is real or
    is just the model memorising a small training set.

    Two things you will need to think about, and the tutorial walks through both:
      - your features are on wildly different scales (area in the hundreds, door_count in the
        single digits). Some models care about that a great deal. Which, and why?
      - the classes are almost certainly imbalanced. What does that do to a fit that is
        optimising plain accuracy, and what is class_weight="balanced" actually doing about it?
    """
    raise NotImplementedError(
        "train.build_model is the exercise. See docs/tutorials/02-machine-learning.md section 4."
    )


def evaluate(name: str, y_true: list[str], y_pred: list[str]) -> float:
    """Print a scorecard and return macro-F1."""
    print(f"\n=== {name} ===")
    print(classification_report(y_true, y_pred, labels=list(LABELS), zero_division=0))

    print("confusion matrix (rows = truth, cols = predicted)")
    matrix = confusion_matrix(y_true, y_pred, labels=list(LABELS))
    header = "        " + "".join(f"{label:>8}" for label in LABELS)
    print(header)
    for label, row in zip(LABELS, matrix, strict=True):
        print(f"{label:>8}" + "".join(f"{count:>8}" for count in row))

    report = classification_report(
        y_true, y_pred, labels=list(LABELS), zero_division=0, output_dict=True
    )
    return float(report["macro avg"]["f1-score"])


def main() -> None:
    parser = argparse.ArgumentParser(description="Train the room occupancy-type classifier")
    parser.add_argument("--data", type=Path, default=None)
    parser.add_argument("--out", type=Path, default=MODEL_PATH)
    args = parser.parse_args()

    rows = dataset.load(args.data)
    if not rows:
        raise SystemExit(
            "The dataset has no labelled rows yet. Seed it, then fill in occupancy_type by hand."
        )

    train_rows, test_rows = dataset.split_by_plan(rows, DEFAULT_TEST_PLANS)
    print(
        f"{len(rows)} labelled rooms across {len({r.plan_id for r in rows})} plans; "
        f"{len(train_rows)} train / {len(test_rows)} test (grouped by plan)"
    )
    if len(rows) < 100:
        # Not a warning you should silence. It is the actual state of the project.
        print(
            "\nWARNING: this is far too little data to train a model you should trust.\n"
            "Any score below is dominated by noise. Read tutorial section 2 on getting more.\n"
        )

    y_test = [r.occupancy_type for r in test_rows]

    # The number to beat. Always compute it, every run, and print it next to the model's
    # score. A model reported without its baseline is a number without a meaning.
    baseline_f1 = evaluate(
        "BASELINE (keyword matching)",
        y_test,
        [baseline.classify(r.room)[0] for r in test_rows],
    )

    model = build_model()
    x_train = featurise_all([r.room for r in train_rows])
    y_train = [r.occupancy_type for r in train_rows]
    model.fit(x_train, y_train)

    model_f1 = evaluate(
        "MODEL",
        y_test,
        list(model.predict(featurise_all([r.room for r in test_rows]))),
    )

    _report_coefficients(model)

    print(f"\nmacro-F1: baseline {baseline_f1:.3f} | model {model_f1:.3f}")
    if model_f1 <= baseline_f1:
        print(
            "The model does not beat keyword matching. That is a real result, not a bug to "
            "hide: either the features carry no signal the keywords lack, or there is not "
            "enough data to fit them. Do not ship it. Work out which of the two it is."
        )

    args.out.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump({"model": model, "feature_names": FEATURE_NAMES}, args.out)
    print(f"wrote {args.out}")


def _report_coefficients(model: BaseEstimator) -> None:
    """Show what a linear model learned. This is the payoff for starting linear."""
    coefficients = getattr(model, "coef_", None)
    if coefficients is None or not FEATURE_NAMES:
        return
    print("\nlearned weights (positive pushes toward the second class):")
    for name, weight in zip(FEATURE_NAMES, np.ravel(coefficients), strict=False):
        print(f"  {name:>28}  {weight:+.3f}")
    print(
        "  Read these. If a feature you expected to matter has a weight near zero, or one you\n"
        "  did not expect dominates, that is the model telling you something about your data."
    )


if __name__ == "__main__":
    main()
