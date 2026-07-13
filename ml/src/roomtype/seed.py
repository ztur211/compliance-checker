"""`uv run roomtype-seed` - extract rooms from the import-gold fixtures into data/rooms.jsonl.

What this gives you is the *geometry* of every room in the gold fixtures, in the dataset's
shape, with `occupancy_type` left as null. What it does not give you is the labels. Those are
human judgment, and inventing them here would be manufacturing ground truth, which is the one
thing a ground-truth file must never contain.

So this is a starting point of a few dozen rows, and it is nowhere near enough. Read
docs/tutorials/02-machine-learning.md section 2 for where the rest of the data comes from.

A caveat you must not skip: the gold polygons are in PIXELS, and this writes them through
unchanged, because the fixtures have no reliable scale (wealthy-home's gold explicitly records
scale as null). A pixel area is not an area. Any area-based feature you compute from these rows
is measured in a unit that differs per plan, which means it is not comparable across plans, and
a model trained on it will learn nonsense. That is a real, load-bearing data-quality problem
sitting in the middle of your dataset, and section 2 of the tutorial is about what to do with it.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
GOLD_DIR = REPO_ROOT / "app" / "src" / "test" / "resources" / "import-gold"
DEFAULT_OUT = REPO_ROOT / "ml" / "data" / "rooms.jsonl"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gold-dir", type=Path, default=GOLD_DIR)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()

    gold_files = sorted(args.gold_dir.glob("*.gold.json"))
    if not gold_files:
        raise SystemExit(f"no *.gold.json under {args.gold_dir}")

    rows: list[dict] = []
    for gold_file in gold_files:
        plan_id = gold_file.name.removesuffix(".gold.json")
        gold = json.loads(gold_file.read_text())
        rooms = gold.get("rooms") or []
        if not rooms:
            print(f"{plan_id}: no rooms in the gold (scale-only fixture), skipped")
            continue
        for index, room in enumerate(rooms, start=1):
            rows.append(
                {
                    "plan_id": plan_id,
                    "id": f"room-{index}",
                    "label": room.get("label", ""),
                    # PIXELS, not metres. See the module docstring. This is not an oversight.
                    "polygon": room.get("polygonPx", []),
                    "door_count": 0,
                    "connected_room_count": 0,
                    "has_exit_door": False,
                    # Yours to fill in. Null means "not labelled", and dataset.load skips it.
                    "occupancy_type": None,
                }
            )
        print(f"{plan_id}: {len(rooms)} rooms")

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w") as fh:
        for row in rows:
            fh.write(json.dumps(row) + "\n")

    print(
        f"\nwrote {len(rows)} rows to {args.out}, all with occupancy_type=null.\n"
        "Nothing will train until you label them. That is deliberate."
    )


if __name__ == "__main__":
    main()
