package app.tricount.graph;

import app.tricount.geometry.Segment;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class DefaultTriangleCounterTest {
  private static final double COORD_TOL = 1e-6;
  private static final double ANGLE_TOL = 1e-6;

  @Test
  void simpleOuterTriangle() {
    TriangleCounter counter = new VisualTriangleCounter();
    List<Segment> segments = List.of(
        segment("ab", 0, 0, 10, 0),
        segment("bc", 10, 0, 5, 8.66),
        segment("ca", 5, 8.66, 0, 0));
    List<int[]> triangles = counter.countTriangles(segments, COORD_TOL, ANGLE_TOL);
    assertEquals(1, triangles.size());
  }

  @Test
  void outerTriangleWithMedian() {
    TriangleCounter counter = new VisualTriangleCounter();
    List<Segment> segments = List.of(
        segment("ab", 0, 0, 10, 0),
        segment("bc", 10, 0, 5, 8.66),
        segment("ca", 5, 8.66, 0, 0),
        segment("median", 5, 8.66, 5, 0));
    List<int[]> triangles = counter.countTriangles(segments, COORD_TOL, ANGLE_TOL);
    assertEquals(3, triangles.size());
  }

  @Test
  void threeFanTriangles() {
    TriangleCounter counter = new VisualTriangleCounter();
    List<Segment> segments = List.of(
        segment("oa", 0, 0, 1, 0),
        segment("ab", 1, 0, 0, 1),
        segment("bo", 0, 1, 0, 0),
        segment("bc", 0, 1, -1, 0),
        segment("co", -1, 0, 0, 0),
        segment("cd", -1, 0, 0, -1),
        segment("do", 0, -1, 0, 0));
    List<int[]> triangles = counter.countTriangles(segments, COORD_TOL, ANGLE_TOL);
    assertEquals(3, triangles.size());
  }

  @Test
  void gridHasNoTriangles() {
    TriangleCounter counter = new VisualTriangleCounter();
    List<Segment> segments = List.of(
        segment("h1", 0, 0, 2, 0),
        segment("h2", 0, 1, 2, 1),
        segment("v1", 0, 0, 0, 1),
        segment("v2", 1, 0, 1, 1),
        segment("v3", 2, 0, 2, 1));
    List<int[]> triangles = counter.countTriangles(segments, COORD_TOL, ANGLE_TOL);
    assertEquals(0, triangles.size());
  }

  @Test
  void splitSideContractsToOneTriangle() {
    TriangleCounter counter = new VisualTriangleCounter();
    List<Segment> segments = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      segments.add(segment("base" + i, i, 0, i + 1, 0));
    }
    segments.add(segment("left", 0, 0, 5, 8));
    segments.add(segment("right", 10, 0, 5, 8));
    List<int[]> triangles = counter.countTriangles(segments, COORD_TOL, ANGLE_TOL);
    assertEquals(1, triangles.size());
  }

  @Test
  void crossingLinesHaveNoTriangle() {
    TriangleCounter counter = new VisualTriangleCounter();
    List<Segment> segments = List.of(
        segment("d1", 0, 0, 1, 1),
        segment("d2", 0, 1, 1, 0));
    List<int[]> triangles = counter.countTriangles(segments, COORD_TOL, ANGLE_TOL);
    assertEquals(0, triangles.size());
  }

  @Test
  void outerTrianglePersistsWithAttachmentPoint() {
    TriangleCounter counter = new VisualTriangleCounter();
    List<Segment> segments = List.of(
        segment("ab", 0, 0, 10, 0),
        segment("bc", 10, 0, 5, 8.66),
        segment("ca", 5, 8.66, 0, 0),
        segment("cd", 5, 8.66, 5, 0));
    List<int[]> triangles = counter.countTriangles(segments, COORD_TOL, ANGLE_TOL);
    assertEquals(3, triangles.size());
  }

  @Test
  void outerWithInnerTriangleProducesFiveVisible() {
    TriangleCounter counter = new DefaultTriangleCounter();
    List<Segment> segments = List.of(
        segment("ab", 0, 6, -6, 0),
        segment("bc", -6, 0, 6, 0),
        segment("ca", 6, 0, 0, 6),
        segment("ad", 0, 6, -2, 2),
        segment("ae", 0, 6, 2, 2),
        segment("bd", -6, 0, -2, 2),
        segment("bf", -6, 0, 0, 1),
        segment("ce", 6, 0, 2, 2),
        segment("cf", 6, 0, 0, 1),
        segment("de", -2, 2, 2, 2),
        segment("ef", 2, 2, 0, 1),
        segment("fd", 0, 1, -2, 2));
    Graph planar = counter.buildPlanarGraph(segments, COORD_TOL);
    Graph contracted = counter.contractStraightVertices(planar, ANGLE_TOL);
    List<int[]> triangles = counter.triangles(contracted);
    assertEquals(5, triangles.size());
  }

  @Test
  void nearCollinearContractsWithinTolerance() {
    TriangleCounter counter = new DefaultTriangleCounter();
    List<Segment> segments = List.of(
        segment("s1", -1, 0, 0, 1e-8),
        segment("s2", 0, 1e-8, 1, 0));
    Graph planar = counter.buildPlanarGraph(segments, COORD_TOL);
    Graph contracted = counter.contractStraightVertices(planar, 1e-4);
    assertEquals(2, contracted.vertices().size());
    Graph contractedTight = counter.contractStraightVertices(planar, 1e-8);
    assertEquals(3, contractedTight.vertices().size());
  }

  private Segment segment(String id, double x1, double y1, double x2, double y2) {
    return new Segment(x1, y1, x2, y2, id);
  }

}
