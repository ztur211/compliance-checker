"""Loading the labelled dataset, and splitting it without cheating.

Dataset format is JSON Lines: one room per line, each with the room's geometry, its printed
label, the occupancy type a human assigned, and crucially the *plan* it came from.

    {"plan_id": "wealthy-home-sample", "id": "room-1", "label": "Eat-in Kitchen",
     "polygon": [{"x": 4.0, "y": 15.0}, ...], "door_count": 2,
     "connected_room_count": 2, "has_exit_door": false, "occupancy_type": "WB"}

## Why plan_id is not optional

The obvious way to split data is to shuffle all the rooms and take 80% for training and 20%
for testing. Here that would be cheating, and it would flatter your model badly.

Rooms from the same building are not independent. They share an architect, a drawing
convention, a labelling vocabulary, a scale, and a style of extraction error. If room 3 of
the Schenley plan is in your training set and room 4 of the Schenley plan is in your test
set, then at test time your model is being asked about a building it has already seen. It can
succeed by memorising that building's quirks rather than by learning what an office looks
like. Your test score goes up, your real-world performance does not, and you find out on a
plan from an architect you have never seen.

The fix is a **grouped split**: every room from a given plan goes entirely into train or
entirely into test, never both. That is what split_by_plan does, and it is why the loader
refuses rows without a plan_id. Expect your honest grouped score to be noticeably worse than
the cheating shuffled score. The grouped one is the true one.
"""

from __future__ import annotations

import json
from collections.abc import Iterator
from pathlib import Path

from pydantic import BaseModel

from roomtype.labels import is_valid
from roomtype.schema import Point, RoomIn

DEFAULT_DATASET = Path(__file__).resolve().parents[2] / "data" / "rooms.jsonl"


class LabelledRoom(BaseModel):
    plan_id: str
    room: RoomIn
    occupancy_type: str


def load(path: Path | None = None) -> list[LabelledRoom]:
    """Read the dataset, skipping unlabelled rows and rejecting invalid ones loudly.

    Unlabelled rows (occupancy_type null) are how a freshly seeded dataset arrives: the
    geometry is extracted for you, the human judgment is not. They are skipped rather than
    guessed, because a fabricated label is worse than a missing one.
    """
    path = path or DEFAULT_DATASET
    if not path.exists():
        raise FileNotFoundError(
            f"No dataset at {path}. Run `uv run roomtype-seed` to extract rooms from the "
            "import-gold fixtures, then label the occupancy_type column by hand."
        )
    rows: list[LabelledRoom] = []
    for line_no, raw in enumerate(_nonblank_lines(path), start=1):
        obj = json.loads(raw)
        occupancy = obj.get("occupancy_type")
        if occupancy is None:
            continue  # not yet labelled by a human
        if not is_valid(occupancy):
            raise ValueError(
                f"{path}:{line_no}: occupancy_type {occupancy!r} is not in the label space. "
                "Add it to labels.LABELS and to OccupantDensity on the Java side, or fix the row."
            )
        plan_id = obj.get("plan_id")
        if not plan_id:
            raise ValueError(
                f"{path}:{line_no}: missing plan_id. It is required so that rooms from one "
                "building cannot be split across train and test. See this module's docstring."
            )
        rows.append(
            LabelledRoom(
                plan_id=plan_id,
                occupancy_type=occupancy,
                room=RoomIn(
                    id=obj["id"],
                    label=obj.get("label", ""),
                    polygon=[Point(**p) for p in obj["polygon"]],
                    door_count=obj.get("door_count", 0),
                    connected_room_count=obj.get("connected_room_count", 0),
                    has_exit_door=obj.get("has_exit_door", False),
                ),
            )
        )
    return rows


def split_by_plan(
    rows: list[LabelledRoom], test_plans: set[str]
) -> tuple[list[LabelledRoom], list[LabelledRoom]]:
    """Grouped train/test split: a plan is wholly in one side or the other.

    test_plans is passed in explicitly, rather than sampled randomly, because with a handful
    of plans a random grouped split is high-variance enough to be meaningless: you would be
    measuring which plans you happened to draw, not how good your model is. Choose the held-out
    plans deliberately, write down why, and keep them fixed.
    """
    unknown = test_plans - {r.plan_id for r in rows}
    if unknown:
        raise ValueError(f"test_plans names plans absent from the dataset: {sorted(unknown)}")
    train = [r for r in rows if r.plan_id not in test_plans]
    test = [r for r in rows if r.plan_id in test_plans]
    return train, test


def _nonblank_lines(path: Path) -> Iterator[str]:
    with path.open() as fh:
        for line in fh:
            if line.strip():
                yield line
