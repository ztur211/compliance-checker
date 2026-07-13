"""Feature extraction. THIS FILE IS YOURS TO WRITE.

This is the exercise, and it is deliberately the one thing the scaffold does not do for you,
because choosing features *is* the modelling. Everything else here (the service, the data
loader, the evaluation, the Java seam) is plumbing that exists so that you can spend your
attention here.

Read docs/tutorials/02-machine-learning.md section 3 before you start.

The contract:

    featurise(room) -> a fixed-length vector of floats.

Fixed-length and fixed-order matters more than it looks. The vector you build at training
time and the vector you build at serving time must mean the same thing, position by
position, or the model will read "area" out of the slot where you put "door count" and be
confidently wrong. FEATURE_NAMES exists to make that contract explicit and testable, and
there is a test asserting len(FEATURE_NAMES) == len(featurise(...)). Keep them in step.

You have geometry.py for shape primitives. Things worth considering, roughly in order of
how much signal I would bet they carry:

  - the room's printed label ("Office", "Dining", "Corridor"). This is by far the strongest
    signal, and it is text, not a number. How do you get text into a float vector? That is
    the interesting question. Bag-of-words? A handful of keyword indicator flags? Character
    n-grams? Each has a different failure mode on labels the training set never saw, and on
    a plan whose labels are in a different vocabulary ("Lounge" vs "Family Room").
  - area, which separates a broom cupboard from an assembly hall.
  - aspect ratio and compactness, which is how a corridor announces itself.
  - door count and graph degree, because a room everything connects to is circulation.

And things worth being suspicious of:

  - Anything derived from the room's *id* or its position in the input list. That is not a
    property of the building, it is an artifact of how the extractor happened to order its
    output, and a model that learns from it will look brilliant in testing and fall over in
    production. This class of mistake is called leakage and it is the single most common way
    a model that "works" turns out not to.
"""

from __future__ import annotations

import numpy as np

from roomtype.schema import RoomIn

#: Names of the features produced by featurise(), in the SAME ORDER. Used for the
#: coefficient report in train.py, which is how you find out what your model actually
#: learned rather than what you assume it learned.
FEATURE_NAMES: tuple[str, ...] = ()


def featurise(room: RoomIn) -> np.ndarray:
    """Turn one room into a fixed-length float vector.

    Must be deterministic, must not depend on the other rooms in the request, and must
    return exactly len(FEATURE_NAMES) values.
    """
    raise NotImplementedError(
        "features.featurise is the exercise. See docs/tutorials/02-machine-learning.md section 3. "
        "Until you implement it, the service falls back to the keyword baseline."
    )


def featurise_all(rooms: list[RoomIn]) -> np.ndarray:
    """Stack a batch into an (n_rooms, n_features) matrix, the shape scikit-learn wants."""
    return np.vstack([featurise(r) for r in rooms])
