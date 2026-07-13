# Machine learning, by building a classifier this product actually needs

The scaffold in `ml/` is done. The service runs, the tests pass, the Java side calls it. Two
functions are missing, and they are the two that constitute the machine learning:

- `features.featurise` - turning a room into numbers
- `train.build_model` - fitting something to those numbers

Everything else exists so you can spend your attention on those. Read this document before you
touch either.

A warning about the shape of this: **most of what follows is not about models.** It is about data,
measurement, and knowing when the model is not worth shipping. That ratio is not padding, it is
the job. People who are good at this are not people who know more model architectures. They are
people who are harder to fool.

---

## 1. The problem, and why it is a good one

Every room on an imported plan has an **occupancy type**. `engine/facts/OccupantDensity.java` maps
that type to m² per person. That density sets the room's occupant load, which sets how much egress
capacity the room needs, which decides whether the building passes.

So the chain is:

```
room  ->  occupancy type  ->  m2/person  ->  occupant load  ->  egress requirement  ->  PASS or FAIL
```

Today the type comes from the vision model's `occupancyTypeGuess`, and **nothing measures whether
it is right**. That is the gap you are filling. It is a good first ML problem for three reasons:

1. **It is genuinely a classification problem.** Input: a room. Output: one of a small set of
   labels. That is the canonical supervised learning setup.
2. **Being wrong has consequences you can articulate.** Call an assembly hall an office and you
   understate its occupant load by a factor of ten, and a building that should fail will pass. This
   is not a Kaggle score. It focuses the mind on the difference between "accurate" and "safe".
3. **There is an obvious dumb solution.** The room is usually *labelled on the plan*. "Office."
   "Dining Hall." A keyword lookup gets you a long way, which means you will have to prove a model
   is worth having at all. Most ML tutorials skip this. It is the most useful lesson in the file.

---

## 2. The data problem (read this before you write any code)

Here is the actual state of your dataset:

```
$ cd ml && uv run roomtype-seed
wealthy-home-sample: 7 rooms
independence-hall-habs: no rooms in the gold (scale-only fixture), skipped
wrote 7 rows ... all with occupancy_type=null
```

**Seven rooms. From one building. A house.** You cannot train anything on that, and if you try, you
will get a model that scores well on a test set of one or two rooms and means absolutely nothing.

I want you to sit with that rather than route around it, because this is the normal condition of an
ML project and the part that gets glossed over everywhere. The algorithm is a commodity;
scikit-learn will fit you a classifier in three lines. **The data is the whole job.** Three specific
problems, in descending order of how much they should worry you:

### 2.1 There is not enough of it

A rough sanity floor for a simple model on a handful of features is on the order of a few dozen
examples *per class*, and more if the classes are imbalanced or the features are noisy. You need
hundreds of labelled rooms across many buildings, not seven from one.

Where they come from, in the order I would try:

- **Label your own.** You have four gold fixtures. Two have no gold file at all
  (`schenley-high-school-1916` has ~24 rooms; `griswold-house-sketch-1862`). Labelling those is
  real, direct progress, and it improves the vision eval at the same time, which is a rare
  two-for-one. This is the highest-value thing you can do, and it is tedious, which is exactly why
  it is undervalued.
- **A public floor-plan dataset.** CubiCasa5K and RPLAN are the usual ones, with polygonised rooms
  and room-type labels. Check the licence before a single byte lands in this repo, and record the
  provenance in `ml/data/README.md` the way `import-gold/README.md` already does for the images.
  Note that both are *residential*, and your product checks *commercial* egress, which brings us to:

### 2.2 It is the wrong kind of building

Every fixture you have is a house. Your product checks **commercial** fire egress. A model trained
on kitchens and bedrooms has never seen an open-plan office, a lecture theatre, or a plant room, and
the label vocabulary barely overlaps. A model is only trustworthy on data that looks like what it
was trained on, and "houses" does not look like "commercial buildings".

This is called **distribution shift**, and it is the thing that kills deployed models. It will not
show up in your test score, because your test set has the same problem as your training set. It
shows up on the first real customer plan. You cannot fix it with a better model; you can only fix it
with better data, or by being honest about where the model is allowed to be used.

### 2.3 The geometry is in pixels

Read `ml/src/roomtype/seed.py`'s docstring. The gold polygons are in **image pixels**, and
`wealthy-home-sample` records its scale as `null` because the plan has no scale bar.

So a room's "area" in your seeded data is in px², and px² means something different on every plan,
depending on the resolution the image happened to be scanned at. Two identical rooms on two scans of
the same drawing would get wildly different areas. Any area feature computed from this is not a
measurement, it is noise dressed up as a number, and a model will happily learn from it and be
confidently wrong.

You have three options, and choosing among them is a real modelling decision:

- **Only use plans with a resolvable scale.** Honest, and it is what the Java side already enforces
  (`ImportService` refuses to classify without a positive scale). It also throws away most of your
  already-tiny dataset.
- **Use only scale-invariant features.** Aspect ratio, compactness, door count, and label text do not
  care about pixels. Area does. This may be the right call for v1, and it is a genuinely elegant
  answer, but you lose the single most physically meaningful feature you have.
- **Normalise within a plan.** Use "this room's area as a fraction of the largest room on the plan"
  instead of absolute area. Scale cancels out. Note the cost: the feature now depends on the other
  rooms in the request, which breaks `featurise`'s one-room-at-a-time contract, and a plan with one
  giant atrium will distort every other room's feature. Think about whether that trade is worth it.

**There is no correct answer here that I am withholding from you.** This is what modelling judgment
is, and it is why I gave you the geometry primitives but not the feature vector.

---

## 3. Features: `features.featurise`

A feature is a number you compute from an example, chosen because you believe it carries signal
about the answer. The model's whole world is the vector you hand it. It cannot see the floor plan.
It cannot see anything you did not put in that vector. Everything the model will ever know about a
room, you decide, right here.

Start with the strongest signal, which is not geometry at all: **the label printed on the plan.**
"Office" tells you far more than any area ever will. But it is text, and models eat floats. So the
question becomes how to turn text into numbers, and each answer has a distinct failure mode:

- **Keyword indicator flags** (one float per keyword: does the label contain "office", "dining", …).
  Simple, interpretable, and you already have the keyword list in `baseline.py`. But it can only ever
  know the words you thought of, and it learns nothing about "Lounge" if you did not list it.
- **Bag of words / character n-grams** (`sklearn.feature_extraction.text.TfidfVectorizer`). Learns
  the vocabulary from the data instead of from you, and character n-grams degrade gracefully on typos
  and OCR noise, which matters because these labels come out of a *vision model reading a scan*.
  The cost: the vector's length now depends on the training vocabulary, so it must be fitted on train
  data only, and it must be saved with the model. (Look up why a `Pipeline` exists. This is the
  reason.)
- **Nothing.** Deliberately exclude the label and use geometry only. Why would you ever? Because if
  the model only reads the label, it is a keyword matcher with extra steps and worse latency, and you
  should ship `baseline.py` instead. Running this experiment tells you whether the geometry carries
  *any* independent signal. That is worth knowing even if you do not ship it.

Then geometry, via `geometry.py`: `area`, `aspect_ratio`, `compactness`, plus `door_count` and
`connected_room_count` off the room itself. A corridor is long, thin, low-compactness, and connects
to everything. That is a real pattern and a model can learn it.

Two rules, both of which exist because breaking them is the classic way to produce a model that
looks great and is worthless:

- **Never use anything not available at serving time.** If a feature needs the answer, or needs
  information the import pipeline will not have, it is leakage.
- **Never use the room's id, or its index in the list.** `room-1` is first because the vision model
  emitted it first, and vision models tend to emit the biggest, most obvious room first, which is
  often the assembly space. Your model would learn "room-1 means CA", score brilliantly, and collapse
  the moment the extractor changes its ordering. Leakage is rarely as obvious as copying the answer.
  It is usually a smuggled correlation like this one.

Keep `FEATURE_NAMES` in the same order as the vector. There is a test that will fail if you do not,
and `train.py` uses the names to print what the model learned.

---

## 4. The model: `train.build_model`

**Start with `LogisticRegression`.** Not because it is the best, but because it is *legible*: it
learns one weight per feature, and `train.py` prints them for you. You can look at the output and
see that "label contains 'office'" got a big negative weight toward CA and nod, or see that
`aspect_ratio` dominates everything and realise your label features are broken. A model you cannot
interrogate is one you cannot trust with a safety decision, and occupant load is a safety decision.

Two things you will hit, and both are worth understanding rather than copying:

**Feature scaling.** Your area might be 250 and your door count might be 2. Logistic regression fits
by penalising large coefficients (regularisation), so a feature measured in big units gets an
unfairly small coefficient and is effectively ignored, purely because of the unit it happens to be
in. `StandardScaler` fixes this by rescaling every feature to comparable spread. Tree-based models
(`RandomForest`) do not care at all, because they split on thresholds rather than multiplying by
weights. Knowing *which models care about scaling and why* is worth more than memorising that you
should scale.

The right way to combine them is a `Pipeline`, so that the scaler is fitted on the training data and
then applied unchanged at serving time. Fitting the scaler on all your data before splitting is a
subtle, extremely common form of leakage: your training set has now peeked at the test set's mean.

**Class imbalance.** If 80% of your rooms are WB, a model that predicts WB for everything scores 80%
accuracy and has learned nothing. This is why `train.py` prints a **confusion matrix** and
**macro-F1** rather than accuracy. Read the confusion matrix. It tells you exactly which class is
being sacrificed, and in this product the sacrificed class matters enormously: missing a CA room
means understating occupant load by 10x, in the unsafe direction. `class_weight="balanced"` tells the
fit to care proportionally more about the rare class. Understand what it is doing to the loss
function before you reach for it.

---

## 5. Measurement, which is the actual skill

`train.py` gives you the harness. Your job is to read it correctly.

**Always compare against the baseline.** It prints both. If your model does not beat keyword
matching, that is a *result*, not a bug: it means either your features carry no signal the keywords
lack, or you have too little data to fit them. Both are true things about your project that you
would rather know. The dishonest move, and it is very tempting, is to keep tweaking until the number
goes up. Which brings us to the most important paragraph in this document:

**The test set is not a scoreboard, it is a one-shot estimate of generalisation, and you burn a
little of it every time you look.** If you tune your features, re-run, see a worse number, tune
again, and repeat until it looks good, you have optimised your model *against the test set*. It is
no longer an unbiased estimate of anything, it is just a slower, more expensive way of overfitting.
The discipline: iterate using cross-validation on the *training* set, and look at the test set at the
end, ideally once. If you must look repeatedly, know that the number is now optimistic and say so.

**Grouped splitting.** `dataset.split_by_plan` will not let you split rooms randomly, and its
docstring explains why: rooms from one building are not independent, and a random split lets your
model memorise a building it will then be tested on. Expect your honest grouped score to be
noticeably worse than a shuffled one. The grouped one is the true one.

**Calibration and the abstention threshold.** `predict.py` abstains below `CONFIDENCE_THRESHOLD`,
returning `UNKNOWN` so the Java side leaves the field for a human. That is only meaningful if the
model's "0.8 confident" actually corresponds to being right about 80% of the time. Models are
frequently overconfident, and logistic regression is better than most but is not automatically
calibrated. Look up `CalibratedClassifierCV` and a reliability diagram. Then choose the threshold
from held-out data by asking a product question, not a statistical one: *how often am I willing to
put a wrong occupancy type in front of a reviewer, versus how often am I willing to make them fill it
in themselves?* Those two errors do not cost the same, and 0.5 is almost never the right answer.

---

## 6. Where the LLM fits

You are not replacing the vision model, and it is worth being precise about why a small trained
classifier can be the better tool here even though a frontier LLM is "smarter":

- **It is measurable.** You have a test set and a confusion matrix. "The LLM seemed to do fine" is
  not a number, and this project's whole architecture is built on the idea that the check path must
  be auditable.
- **It is deterministic and free.** The same room gets the same answer every time, with no API call,
  no key, no rate limit, and no cost per plan.
- **It abstains honestly.** A calibrated probability is a real signal about doubt. An LLM's stated
  confidence is not the same thing, and is often confidently wrong.
- **It is one dependency lighter.** The import already needs an LLM to *see* the plan. It does not
  also need one to reason about a room's use once the geometry is extracted.

And the reverse: the LLM sees things your feature vector never will. It reads the title block, notices
the fire-egress plan legend, understands that a room labelled "Sample Room 4" beside a nurses' station
is clinical. The right architecture is the one already scaffolded: **vision model extracts, classifier
fills the gaps it left, human approves, engine checks deterministically.** Each part does what it is
best at, and the only thing that reaches a compliance verdict is a value a human signed off.

---

## 7. What to hand back

- `features.featurise` + `FEATURE_NAMES`, with tests
- `train.build_model`
- a labelled `ml/data/rooms.jsonl` that is materially bigger than 7 rows, with its provenance recorded
- the output of `uv run roomtype-train`, including the baseline comparison and confusion matrix
- **your written call on whether this model should ship at all**, given those numbers

That last one is the deliverable I care most about. "The model beats the baseline by 9 points of
macro-F1 on a grouped split, so ship it behind the abstention threshold" and "the model does not beat
the baseline and I do not have the data to make it, so we ship the keyword baseline and I have written
down what data would change my mind" are both excellent answers. "I got it to 94%" is not an answer
until I know what it is 94% *of*.
