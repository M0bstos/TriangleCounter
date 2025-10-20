package app.tricount.graph;

import app.tricount.geometry.Segment;
import java.util.List;

public interface TriangleCounter {
  Graph buildPlanarGraph(List<Segment> segments, double coordTol);

  Graph contractStraightVertices(Graph graph, double angleTol);

  List<int[]> triangles(Graph graph);

  default List<int[]> countTriangles(List<Segment> segments, double coordTol, double angleTol) {
    Graph planar = buildPlanarGraph(segments, coordTol);
    Graph simplified = contractStraightVertices(planar, angleTol);
    return triangles(simplified);
  }
}
