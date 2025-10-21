package app.tricount.graph;

import app.tricount.geometry.PlanarGraphBuilder;
import app.tricount.geometry.Segment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VisualTriangleCounter implements TriangleCounter {
  private final PlanarGraphBuilder builder = new PlanarGraphBuilder();

  @Override
  public Graph buildPlanarGraph(List<Segment> segments, double coordTol) {
    return builder.build(segments, coordTol);
  }

  @Override
  public Graph contractStraightVertices(Graph graph, double angleTol) {
    return graph;
  }

  @Override
  public List<int[]> triangles(Graph graph) {
    return trianglesFromGraph(graph);
  }

  @Override
  public List<int[]> countTriangles(List<Segment> segments, double coordTol, double angleTol) {
    Graph planar = builder.build(segments, coordTol);
    return trianglesFromGraph(planar);
  }

  private List<int[]> trianglesFromGraph(Graph graph) {
    int n = graph.vertices().size();
    if (n < 3) {
      return List.of();
    }
    Map<Integer, Set<Integer>> share = buildSharedSegmentMap(graph.segmentVertexPaths());
    List<int[]> triangles = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    double tol = graph.coordinateTolerance();
    for (int a = 0; a < n - 2; a++) {
      for (int b = a + 1; b < n - 1; b++) {
        if (!connected(share, graph, a, b)) {
          continue;
        }
        for (int c = b + 1; c < n; c++) {
          if (!connected(share, graph, a, c) || !connected(share, graph, b, c)) {
            continue;
          }
          if (area(graph, a, b, c) <= tol) {
            continue;
          }
          long key = triangleKey(a, b, c);
          if (seen.add(key)) {
            triangles.add(new int[] {a, b, c});
          }
        }
      }
    }
    return triangles;
  }

  private Map<Integer, Set<Integer>> buildSharedSegmentMap(List<List<Integer>> segmentPaths) {
    Map<Integer, Set<Integer>> share = new HashMap<>();
    for (List<Integer> path : segmentPaths) {
      int size = path.size();
      if (size < 2) {
        continue;
      }
      for (int i = 0; i < size; i++) {
        int u = path.get(i);
        for (int j = i + 1; j < size; j++) {
          int v = path.get(j);
          share.computeIfAbsent(u, k -> new HashSet<>()).add(v);
          share.computeIfAbsent(v, k -> new HashSet<>()).add(u);
        }
      }
    }
    return share;
  }

  private boolean connected(Map<Integer, Set<Integer>> share, Graph graph, int u, int v) {
    if (u == v) {
      return true;
    }
    Set<Integer> neighbors = share.get(u);
    if (neighbors == null) {
      return false;
    }
    if (neighbors.contains(v)) {
      return true;
    }
    Graph.V start = graph.vertices().get(u);
    Graph.V target = graph.vertices().get(v);
    double tx = target.x() - start.x();
    double ty = target.y() - start.y();
    double tol = Math.max(graph.coordinateTolerance(), 1e-9);
    List<Integer> queue = new ArrayList<>();
    Set<Integer> visited = new HashSet<>();
    queue.add(u);
    visited.add(u);
    int index = 0;
    while (index < queue.size()) {
      int curr = queue.get(index++);
      Set<Integer> currNeighbors = share.get(curr);
      if (currNeighbors == null) {
        continue;
      }
      boolean junction = curr != u && currNeighbors.size() > 2;
      Graph.V currVertex = graph.vertices().get(curr);
      for (int next : currNeighbors) {
        if (!visited.add(next)) {
          continue;
        }
        if (junction) {
          continue;
        }
        Graph.V nextVertex = graph.vertices().get(next);
        double sx = nextVertex.x() - currVertex.x();
        double sy = nextVertex.y() - currVertex.y();
        double cross = tx * sy - ty * sx;
        if (Math.abs(cross) > tol * (Math.abs(tx) + Math.abs(ty) + Math.abs(sx) + Math.abs(sy) + 1)) {
          continue;
        }
        if (next == v) {
          return true;
        }
        queue.add(next);
      }
    }
    return false;
  }

  private double area(Graph graph, int a, int b, int c) {
    Graph.V va = graph.vertices().get(a);
    Graph.V vb = graph.vertices().get(b);
    Graph.V vc = graph.vertices().get(c);
    double value = va.x() * (vb.y() - vc.y()) + vb.x() * (vc.y() - va.y()) + vc.x() * (va.y() - vb.y());
    return Math.abs(value) * 0.5;
  }

  private long triangleKey(int a, int b, int c) {
    int x = Math.min(a, Math.min(b, c));
    int z = Math.max(a, Math.max(b, c));
    int y = a + b + c - x - z;
    return (((long) x) << 42) | (((long) y) << 21) | (long) z;
  }
}
