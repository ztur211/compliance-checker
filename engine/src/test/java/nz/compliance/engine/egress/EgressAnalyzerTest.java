package nz.compliance.engine.egress;

import nz.compliance.engine.model.Door;
import nz.compliance.engine.model.GeometryDoc;
import nz.compliance.engine.model.Point;
import nz.compliance.engine.model.Space;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EgressAnalyzerTest {

    private static Space rect(String id, double x0, double y0, double x1, double y1) {
        return new Space(id, id, "WB", List.of(
                new Point(x0, y0), new Point(x1, y0), new Point(x1, y1), new Point(x0, y1)));
    }

    // s1: (0,0)-(10,10) centroid (5,5); s2: (10,0)-(20,10) centroid (15,5)
    // door d12 on x=10 mid (10,5); exit e1 on x=0 mid (0,5)
    private static GeometryDoc twoRooms() {
        return new GeometryDoc(1,
                List.of(rect("s1", 0, 0, 10, 10), rect("s2", 10, 0, 20, 10)),
                List.of(
                        new Door("d12", "s1", "s2", List.of(new Point(10, 4), new Point(10, 6)), 1000, false),
                        new Door("e1", "s1", null, List.of(new Point(0, 4), new Point(0, 6)), 1200, true)));
    }

    @Test
    void openPathLength_s1_is5() {
        EgressResult r = EgressAnalyzer.analyze(twoRooms());
        SpaceEgress s1 = r.bySpace().get("s1");
        assertThat(s1.reachesExit()).isTrue();
        assertThat(s1.openPathLengthMetres()).get().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE)
                .isCloseTo(5.0, within(1e-9));
    }

    @Test
    void openPathLength_s2_routesThroughS1_is15() {
        EgressResult r = EgressAnalyzer.analyze(twoRooms());
        SpaceEgress s2 = r.bySpace().get("s2");
        // dist(s2c->d12mid)=5, dist(d12mid->s1c)=5, dist(s1c->e1mid)=5 => 15
        assertThat(s2.openPathLengthMetres()).get().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE)
                .isCloseTo(15.0, within(1e-9));
        assertThat(s2.pathNodeIds()).containsExactly("s2", "s1", "EXTERIOR");
    }

    @Test
    void worstOpenPath_isS2() {
        EgressResult r = EgressAnalyzer.analyze(twoRooms());
        assertThat(r.worstOpenPath()).get()
                .extracting(SpaceEgress::spaceId).isEqualTo("s2");
    }

    @Test
    void isolatedSpace_isUnreachable() {
        GeometryDoc doc = new GeometryDoc(1,
                List.of(rect("s1", 0, 0, 10, 10), rect("s3", 100, 100, 110, 110)),
                List.of(new Door("e1", "s1", null, List.of(new Point(0, 4), new Point(0, 6)), 1200, true)));
        EgressResult r = EgressAnalyzer.analyze(doc);
        assertThat(r.bySpace().get("s3").reachesExit()).isFalse();
        assertThat(r.unreachable()).extracting(SpaceEgress::spaceId).containsExactly("s3");
    }

    @Test
    void selfReferentialDoor_doesNotCrash() {
        // a door whose from==to passes the structural 422 gate; it must not throw
        // "loops not allowed" when the graph is built.
        GeometryDoc doc = new GeometryDoc(1,
                List.of(rect("s1", 0, 0, 10, 10)),
                List.of(
                        new Door("dself", "s1", "s1", List.of(new Point(5, 0), new Point(6, 0)), 900, false),
                        new Door("e1", "s1", null, List.of(new Point(0, 4), new Point(0, 6)), 1200, true)));
        EgressResult r = EgressAnalyzer.analyze(doc);
        assertThat(r.bySpace().get("s1").reachesExit()).isTrue();
    }

    @Test
    void exitDoorWithToSpaceId_stillDischargesToExterior() {
        // a door ticked exit=true but also carrying a toSpaceId must still reach the
        // exterior, consistent with FactsComputer counting any exit door.
        GeometryDoc doc = new GeometryDoc(1,
                List.of(rect("s1", 0, 0, 10, 10), rect("s2", 10, 0, 20, 10)),
                List.of(new Door("e1", "s1", "s2", List.of(new Point(0, 4), new Point(0, 6)), 1200, true)));
        EgressResult r = EgressAnalyzer.analyze(doc);
        SpaceEgress s1 = r.bySpace().get("s1");
        assertThat(s1.reachesExit()).isTrue();
        assertThat(s1.openPathLengthMetres()).get().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.DOUBLE)
                .isCloseTo(5.0, within(1e-9));
    }

    @Test
    void degenerateSpace_isExcludedFromGraph_noBogusRoute() {
        // bow-tie passes the structural 422 gate but is not a simple polygon; its centroid
        // must not be trusted, so it gets no fabricated route (not a confident PASS).
        Space bowtie = new Space("bad", "bad", "WB", List.of(
                new Point(0, 0), new Point(10, 10), new Point(10, 0), new Point(0, 10)));
        GeometryDoc doc = new GeometryDoc(1, List.of(bowtie),
                List.of(new Door("e1", "bad", null, List.of(new Point(0, 4), new Point(0, 6)), 1200, true)));
        EgressResult r = EgressAnalyzer.analyze(doc);
        SpaceEgress bad = r.bySpace().get("bad");
        assertThat(bad.reachesExit()).isFalse();
        assertThat(bad.openPathLengthMetres()).isEmpty();
        assertThat(bad.pathNodeIds()).isEmpty();
    }
}
