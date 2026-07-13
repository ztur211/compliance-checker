"""The label space.

This is deliberately tied to what the *engine* can consume, not to what a floor plan
happens to call a room. `engine/facts/OccupantDensity.java` maps an occupancy type to
m2/person, and that number drives occupant load, which drives the egress requirement.
Predicting a type the engine does not know is worse than useless: `OccupantDensity`
silently falls back to its default, so a confidently wrong prediction becomes an
invisible one.

So the rule is: **this list and OccupantDensity's key set change together.** If you add a
code here, add its density there in the same commit, or the model will learn to emit a
label that quietly does nothing.
"""

from __future__ import annotations

# Codes the engine currently knows. v1 densities are illustrative placeholders pending
# C/AS2 confirmation, and that is exactly why the taxonomy is data and not an enum
# baked into a model artifact.
WB = "WB"  # working / business (offices, admin)
CA = "CA"  # crowd activity (assembly, dining, retail floor)
UNKNOWN = "UNKNOWN"  # honest abstention, see below

#: Types the model is allowed to emit.
LABELS: tuple[str, ...] = (WB, CA)

#: Emitted when the model is not confident enough to commit. This is NOT a class the
#: model is trained on. It is a decision made at serving time from the predicted
#: probability, so that a low-confidence guess degrades to "a human should look at this"
#: rather than to a wrong compliance verdict. See service.py.
ABSTAIN = UNKNOWN


def is_valid(label: str) -> bool:
    return label in LABELS
