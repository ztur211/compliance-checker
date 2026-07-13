# Tutorials

Two of this project's goals are to ship something useful to the construction industry **and** to
teach the person building it. These are the parts where the second goal wins: the code is
deliberately left unwritten, with the scaffolding, the tests, and the measurement harness built
around the hole so that the interesting decision is the only one left.

Everything else in the repo is fair game for an agent to write. These are not.

| | Tutorial | You write | Scaffolding around it |
|---|---|---|---|
| 1 | [Dijkstra](01-dijkstra.md) | `engine/.../graph/Dijkstra.java`, replacing the JGraphT call in `EgressAnalyzer` | JGraphT demoted to a test-scope oracle for a differential test; `EgressAnalyzerTest` as the behaviour-preservation check |
| 2 | [Machine learning](02-machine-learning.md) | `ml/.../features.py: featurise`, `ml/.../train.py: build_model` | The whole `ml/` service, dataset loader with grouped splitting, keyword baseline to beat, evaluation harness, and the Java seam that calls it |

## The idea in both

In each case the algorithm or the model is the *small* part, and it is surrounded by the part that
actually determines whether the work is any good:

- **Dijkstra** is fifteen lines. Knowing *why* it is correct, and therefore what silently breaks it
  (a negative edge weight), is the thing worth having. The tutorial makes you prove your version
  against a trusted implementation rather than trusting your own reading of it.
- **The classifier** is three lines of scikit-learn. Knowing whether it should ship at all, given a
  keyword matcher that nearly matches it and a dataset of seven rooms from a house, is the thing
  worth having. The tutorial spends most of its length on data and measurement, because that is
  where the real ratio lies.

Both end with the same request: hand back not just the code, but the evidence that it works and your
judgment on whether it is worth having.

## Phases

- **Phase 0 - make it build.** Done. The toolchain (JDK 21), the Testcontainers/Docker API version
  mismatch, and the broken `spring-boot:run` command are fixed; `./mvnw verify` is green and the
  app runs end to end.
- **Phase 1 - Dijkstra.** Tutorial written, `EgressAnalyzer` still on JGraphT, waiting on you.
- **Phase 2 - the ML classifier.** Scaffold built and green, `features.featurise` and
  `train.build_model` waiting on you, and the dataset is the real work.
- **Phase 3 - what is actually next for the product.** Not a tutorial, just the honest list:
  - **Data.** Label the two gold fixtures that have no gold file. This improves the vision eval and
    the classifier's training set at the same time.
  - **Spring Boot 3.3.5 is well behind current.** Not blocking anything, but stale.
  - **The navmesh.** The engine's travel distance is a centroid-to-door-to-centroid approximation,
    and the spec already flags this. Real travel distance is a path across open floor, and on a
    large open-plan floor the current metric can be off by a lot. The Dijkstra you write in Phase 1
    does not change at all; only the graph you feed it does. That is the point of building it as a
    domain-ignorant utility.
