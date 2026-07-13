# Dijkstra's algorithm, and how to put it in this codebase

You are going to delete a library call and replace it with an algorithm you wrote. This document
teaches the algorithm, then walks you through implementing it here. Read the whole thing once before
you write any code, because the last third changes how you should build the first third.

The plan:

1. Understand the problem this project actually asks Dijkstra to solve.
2. Understand the algorithm, including *why* it is correct, not just what it does.
3. Implement it in `engine/`, keeping JGraphT as a test-only oracle to prove you got it right.
4. Improve on what is there today: one multi-source run instead of N single-source runs.

---

## 1. The problem, in this codebase

Open a terminal and read `engine/src/main/java/nz/compliance/engine/egress/EgressAnalyzer.java`.

Fire egress compliance asks: **from any given room, how far must an occupant walk to get out of the
building?** NZBC C/AS2 caps that distance (the "open path" length). If a room's shortest escape route
is longer than the limit, that is a violation, and the check has to point at the offending room and
highlight the route.

"Shortest escape route" is a shortest-path problem, so the floor plan gets turned into a graph:

- **Vertices** are spaces (rooms, corridors), plus one special sentinel vertex `EXTERIOR` meaning
  "outside, you are safe".
- **Edges** are doors. A door between room A and room B is an edge A-B. A door marked as a final exit
  is an edge from its room to `EXTERIOR`.
- **Edge weights** are travel distance in metres. For a door between two rooms, the weight is
  `distance(centroid(A), doorMidpoint) + distance(doorMidpoint, centroid(B))`, because you walk to the
  door, then away from it. For an exit door, it is just `distance(centroid(A), doorMidpoint)`.

So "how far to the nearest exit from room R" is exactly "what is the weight of the shortest path from
vertex R to vertex `EXTERIOR`". That is the entire reduction, and it is worth sitting with for a
minute: the messy real-world question became a graph question, and now a 60-year-old algorithm answers
it. Most of the value in applied algorithms work is in that translation step, not in the algorithm.

Two properties of this graph matter enormously, and you should convince yourself of both:

- **Weights are never negative.** They are Euclidean distances, and distance is non-negative. Hold onto
  this. It is the hinge the whole algorithm swings on.
- **The graph is undirected.** A door you can walk through one way you can walk through the other, and
  `distance(A,B) == distance(B,A)`. This is *not* universally true of egress in the real world (think
  one-way turnstiles, or doors that latch), and v1 assumes it away. It becomes important in step 4.

> **Terminology note.** In this codebase the word "path" is overloaded. `SpaceEgress.pathNodeIds` is the
> list of graph vertices on the escape route. The C/AS2 "open path" is a regulatory term for the part of
> the escape route before you reach a protected exitway. They are not the same concept. The engine
> currently approximates the latter with the former.

---

## 2. The algorithm

### 2.1 Start from the easy case

If every edge had weight 1, you would not need Dijkstra. Breadth-first search would do: explore
outward in rings, and the first time you reach a vertex is necessarily via a fewest-edges path. BFS
works because with uniform weights, "fewest edges" and "shortest distance" are the same thing, and the
queue naturally visits vertices in increasing distance order.

Now let the weights vary, which is our situation: one door might be 2 m away and another 40 m. BFS
breaks immediately. A path with more edges can easily be shorter in metres than a path with fewer.
Reaching a vertex *first* no longer means reaching it *cheapest*.

Dijkstra is the repair. Keep BFS's core idea, visit vertices in increasing order of distance, but make
"distance" mean the real weighted distance rather than the edge count. To do that, instead of a FIFO
queue you need something that always hands you the closest unvisited vertex. That is a priority queue,
and Dijkstra is, in a sentence:

> **BFS, but with a priority queue keyed on distance, and with distance estimates that improve as you
> discover better routes.**

### 2.2 The state you maintain

Three things:

- `dist`: a map from vertex to the **best known distance so far** from the source. Starts at 0 for the
  source and infinity for everything else. These are *estimates*, and they only ever decrease.
- `prev`: a map from vertex to the vertex you arrived from on that best-known route. This is how you
  reconstruct the actual path at the end, not just its length. Without it you know a room is 57 m from
  an exit but cannot draw the route on the plan, which is half the product.
- `settled`: the set of vertices whose distance is **final** and will never improve again.

### 2.3 The loop

```
dist[source] = 0, dist[everything else] = INFINITY
pq = priority queue containing (0, source)

while pq is not empty:
    (d, u) = pq.poll()            # the unsettled vertex with the smallest estimate
    if u in settled: continue     # a stale entry, see 2.6
    settled.add(u)

    for each edge (u, v) with weight w:
        if v in settled: continue
        if dist[u] + w < dist[v]:     # we found a better route to v, through u
            dist[v] = dist[u] + w
            prev[v] = u
            pq.add((dist[v], v))
```

That inner `if` is called **relaxation**, and it is the only place a distance ever changes. Read it as:
"I currently believe v costs `dist[v]`. But I could get to u for `dist[u]`, and hop from u to v for `w`.
If that total is cheaper, my belief about v was too pessimistic, so improve it."

That is the whole algorithm. It is about fifteen lines. The interesting part is why it works.

### 2.4 Why it is correct (this is the part to actually understand)

The claim that makes Dijkstra work is:

> **When a vertex u is polled from the priority queue with the smallest estimate among unsettled
> vertices, that estimate is already its true shortest distance. It can never be improved later.**

If that claim holds, the algorithm is obviously correct, because every vertex gets settled with its
true distance. So why does it hold?

Suppose it did not. Suppose we poll u with estimate `dist[u]`, but there is secretly some genuinely
shorter path P from the source to u that we have not discovered. Walk along P from the source toward u.
P starts at a settled vertex (the source, settled first) and ends at u (not yet settled), so somewhere
along P there must be a first vertex that is not yet settled. Call it x. (x might be u itself.)

Now, the vertex just before x on P is settled, so we have already relaxed its edges, which means we
have already relaxed the edge into x. So `dist[x]` is at most the true cost of the portion of P from the
source to x. Written out: `dist[x] <= cost of P up to x`.

Here is the hinge. The rest of P, from x onward to u, has some total weight, and **because no weight is
negative, that remainder cannot be negative**. So:

```
cost(P) = cost of P up to x + cost of P from x to u  >=  cost of P up to x  >=  dist[x]
```

But we assumed P is shorter than `dist[u]`, so `dist[u] > cost(P) >= dist[x]`, giving `dist[x] < dist[u]`.
And that is a contradiction: we just polled u as the vertex with the *smallest* estimate among unsettled
vertices, yet x is unsettled and has a smaller one. So no such shorter path P exists, and `dist[u]` was
correct when we polled it.

Read that argument again and notice exactly where non-negativity was used: to say the tail of P from x to
u cannot *reduce* the cost. If an edge could have negative weight, a longer-looking prefix could be
rescued by a bargain later on, the contradiction evaporates, and the algorithm is simply wrong. It does
not merely get slower, it returns incorrect answers and does so silently.

This is why I made you check earlier that our weights are Euclidean distances. Dijkstra is *licensed*
here. If someone later adds a "this route is preferred, subtract 5 m" heuristic edge, they will silently
break the correctness of every check in this product, and no test will obviously say so. That is worth a
comment in the code.

### 2.5 Complexity

Each vertex is settled once, so we poll at most V times. Each edge can trigger at most one push, so we
push at most E times, and each heap operation is `O(log)` of the heap size. With a binary heap (Java's
`PriorityQueue`) that is:

```
O((V + E) log V)
```

For our floor plans V and E are in the dozens, so this is instant and the constant factors are
irrelevant. Do not let that make you sloppy about it: knowing the complexity is what tells you the
approach still holds if someone imports a 40-storey hospital with 5,000 rooms. It does.

You may read about Fibonacci heaps giving `O(E + V log V)` by supporting `decrease-key` in `O(1)`
amortised. That is a real theoretical improvement and, in practice, almost always slower due to constant
factors. Ignore it. It is a classic example of an asymptotic win that loses on real hardware.

### 2.6 The stale-entry trick

Java's `PriorityQueue` has no `decrease-key` operation. When you find a better route to v, you cannot
cheaply reach into the heap and lower v's existing key. So you do the lazy thing: **push a second entry
for v with the better distance, and leave the old worse entry in the heap as garbage.**

The heap now contains entries that are out of date. That is fine, and this line is what makes it safe:

```
if u in settled: continue
```

The better entry has a smaller key, so it is always polled *before* the stale one. By the time the stale
entry surfaces, v is already settled, and we discard it. This is why the heap can hold up to E entries
rather than V, which is where the `E log V` term comes from.

Getting this wrong is the single most common Dijkstra bug. If you skip the settled check, you will
re-settle vertices and re-relax their edges. On some graphs you will still get the right answer, which is
worse than failing, because your tests may pass while the code is wrong.

---

## 3. Implement it

### Step 1: write the algorithm, standalone and dependency-free

Create `engine/src/main/java/nz/compliance/engine/graph/Dijkstra.java`.

Design it as a **pure, reusable graph utility that knows nothing about buildings**. No `Space`, no `Door`,
no JTS. It takes an abstract weighted graph and returns distances and predecessors. Keeping it ignorant of
the domain is what will let you unit-test it against hand-computed graphs, and it is the same instinct that
keeps `engine/` free of Spring.

A shape that works well:

```java
public record ShortestPaths(Map<String, Double> distances, Map<String, String> predecessors) {
    public boolean isReachable(String vertex) { ... }
    public OptionalDouble distanceTo(String vertex) { ... }
    public List<String> pathTo(String vertex) { ... }   // walk predecessors back, then reverse
}

public static ShortestPaths from(String source, Map<String, List<Edge>> adjacency) { ... }
```

with a small `record Edge(String to, double weight)`. An adjacency map is the right representation here:
our graphs are sparse (rooms have a handful of doors, not hundreds), so a map of lists beats an adjacency
matrix on both memory and iteration cost.

Things to get right, each of which is a bug I want you to consciously avoid:

- Unreachable vertices must be distinguishable from vertices at distance 0. A room with no route out is
  not "0 m from an exit". This is a real safety property: silently reporting 0 would turn a
  no-means-of-escape room into a *passing* check. Return an absent value, do not return 0, and do not
  return `Double.POSITIVE_INFINITY` to callers.
- `pathTo` on the source itself should return just `[source]`, not an empty list.
- Guard against a `prev` chain that loops forever if you have a bug. A cheap defensive bound (you can
  never visit more vertices than exist) turns an infinite hang into a loud failure.
- Reject a negative weight explicitly. Throw `IllegalArgumentException`. Do not let the one condition your
  correctness proof depends on be an unstated assumption. This is the comment I mentioned in 2.4, except
  enforced by the compiler's friend, the runtime.

**Checkpoint.** Before wiring anything up, write `DijkstraTest` with graphs small enough to verify by hand:
a straight line A-B-C, a diamond where the shorter route has more edges (this is the case BFS gets wrong,
so it proves you are doing better than BFS), a disconnected vertex, a self-loop, two routes of exactly
equal weight, and a single-vertex graph. Then a negative weight, asserting it throws. If those pass, your
algorithm is almost certainly right.

### Step 2: prove it against an oracle

This is the step that turns "I think it works" into "I know it works", and it is the most valuable habit
in this whole document.

You already have a trusted, battle-tested implementation in the repo: JGraphT's `DijkstraShortestPath`.
Rather than deleting it, **demote it to test scope and use it as an oracle**.

In `engine/pom.xml`, the `jgrapht-core` dependency currently has no `<scope>`, so it is a compile
dependency. Once `EgressAnalyzer` no longer imports it, add `<scope>test</scope>`. If the module still
compiles, you have *proved* that no production code depends on JGraphT any more, which is a much stronger
statement than grepping for imports.

Then write a **differential test**: generate random graphs, run both implementations, assert they agree.

```java
@Test
void matchesJGraphTOnRandomGraphs() {
    for (long seed = 0; seed < 500; seed++) {
        var random = new Random(seed);
        // build a random graph: 1..12 vertices, random edges, random non-negative weights.
        // include disconnected components sometimes, that is where implementations disagree.
        // run ours, run JGraphT's, assert equal distances for every vertex
        // (compare doubles with a tolerance, e.g. 1e-9, never with ==)
        // and assert both agree on which vertices are unreachable.
    }
}
```

A fixed seed range makes failures reproducible: if seed 237 fails, you can replay exactly that graph.
Do not use an unseeded random, or you will get a flaky test that fails once in CI and never again, which is
worse than no test.

Note carefully what you should and should not compare. **Distances must match exactly** (within floating
point tolerance). **Paths need not.** When two routes tie, your implementation and JGraphT may legitimately
pick different ones, and neither is wrong. Assert on the path's *weight*, not its vertex list. Writing an
assertion that is stricter than the specification is how you end up with a test that fails for no reason.

### Step 3: swap `EgressAnalyzer` over

Now change `EgressAnalyzer.analyze` to build your adjacency map instead of a `SimpleWeightedGraph`, and call
your `Dijkstra`.

Keep every existing behaviour, and note that the current code has real, hard-won edge-case handling that you
must not lose. Read the class Javadoc and the inline comments. Topologically-invalid spaces are deliberately
excluded from the graph (a self-intersecting polygon has no trustworthy centroid, so producing a route from
it would be fabricating a safety claim). Self-referential doors are skipped. Parallel doors between the same
two rooms keep only the *minimum* weight, which is correct because if two doors join the same pair of rooms
you would walk through the closer one.

The existing `EgressAnalyzerTest` covers these. Do not modify it. If it still passes untouched, your swap is
behaviour-preserving. If you find yourself wanting to change an assertion in it, stop and work out what you
broke.

**Checkpoint.** `./mvnw -pl engine verify` green, with `EgressAnalyzerTest` unmodified. Then `./mvnw verify`
green. Then run the app and re-check a plan, and confirm you still get a located violation with a highlighted
route.

### Step 4: now make it better than what was there

Here is the payoff, and the reason this exercise is not busywork.

Look at what the current code does: it constructs a `DijkstraShortestPath` and then calls `getPath(space,
EXTERIOR)` **once per space**. For a plan with N rooms, that is N shortest-path computations, each of which
explores the graph. It is `O(N * (V+E) log V)`.

But every one of those runs is computing a distance to *the same destination*. And our graph is undirected,
so `distance(room -> EXTERIOR) == distance(EXTERIOR -> room)`.

So: **run Dijkstra once, from `EXTERIOR`.** A single run gives you the distance from `EXTERIOR` to every
room simultaneously, which by symmetry is every room's distance to the nearest exit. N runs collapse into
one. This is the multi-source trick, and the trick is that you do not need to write a multi-source variant
at all. The `EXTERIOR` sentinel that already exists in the model *is* the virtual super-source, and every
exit door is already an edge into it. Whoever designed this data model left the door open for you.

The path needs one adjustment: `prev` now points *away* from `EXTERIOR`, so the reconstructed path for a room
comes out as `EXTERIOR -> ... -> room`. Reverse it to get the `room -> ... -> EXTERIOR` order that
`SpaceEgress.pathNodeIds` promises and that the frontend draws. Get this backwards and the highlighted escape
route on the plan will render pointing the wrong way, which is exactly the kind of bug that passes tests and
embarrasses you in a demo.

Two things to be careful about, and I am not going to tell you the answers because working them out is the
exercise:

1. Does the symmetry argument still hold if a future version adds one-way egress (a door passable only
   outward)? What breaks, and what would you have to do instead? Write your answer as a comment above the
   single-source call, because the next person to touch this needs to know the assumption they are standing
   on.
2. `EgressAnalyzerTest` must still pass **completely unmodified** after this change. If it does, you have
   made the code asymptotically faster and structurally simpler with zero behaviour change, and you have the
   test evidence to prove it. That is what a good refactor looks like.

---

## 4. What to hand back

When you are done, I will review:

- `Dijkstra.java` and `DijkstraTest.java`
- the differential test against JGraphT
- the rewritten `EgressAnalyzer`
- `jgrapht-core` moved to `<scope>test</scope>`
- an unmodified `EgressAnalyzerTest`, still green

Things I will specifically look for, so you may as well pre-empt them: the settled check on poll (2.6),
unreachable distinguishable from zero, the negative-weight guard, no `==` on doubles, a seeded random in the
differential test, and whether you asserted on path weights rather than path vertex lists.

---

## 5. Where this goes next

The honest limitation of all of the above: **the graph is an approximation of walking.** Real travel distance
is not centroid-to-door-to-centroid. It is a walk through open floor space, around furniture and internal
walls, and the true metric is a shortest path across a *continuous* region, not a handful of discrete
vertices. In a big open-plan floor, the centroid model can be off by a lot, and the spec acknowledges this
(see the "centroid door-graph path metric in v1" decision in the design doc, with navmesh flagged as
roadmap).

The fix is a **navigation mesh**: triangulate the walkable area of each space, make each triangle a vertex,
connect adjacent triangles, and run the same Dijkstra over that much finer graph. The algorithm you are about
to write does not change at all. Only the graph you feed it changes.

That is the real lesson, and it is why I had you build `Dijkstra` as a domain-ignorant utility in step 1.
The algorithm is a stable, reusable core. The modelling, deciding what a vertex *is*, is where the judgment
lives, and it is where this product will actually get better or worse. Algorithms are the easy half.
