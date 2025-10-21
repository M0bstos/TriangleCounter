package app.tricount.graph;

import app.tricount.geometry.PlanarGraphBuilder;
import app.tricount.geometry.Segment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DefaultTriangleCounter implements TriangleCounter {
  private final PlanarGraphBuilder builder = new PlanarGraphBuilder();

  @Override
  public Graph buildPlanarGraph(List<Segment> segments, double coordTol) {
    return builder.build(segments, coordTol);
  }

  @Override
  public Graph contractStraightVertices(Graph graph, double angleTol) {
    int n = graph.vertices().size();
    if (n == 0 || graph.edges().isEmpty()) {
      return graph;
    }
    List<Set<Integer>> adjacency = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      adjacency.add(new LinkedHashSet<>());
    }
    for (Graph.E edge : graph.edges()) {
      adjacency.get(edge.u()).add(edge.v());
      adjacency.get(edge.v()).add(edge.u());
    }
    boolean[] removed = new boolean[n];
    boolean changed = true;
    double coordTol = graph.coordinateTolerance();
    while (changed) {
      changed = false;
      for (int v = 0; v < n; v++) {
        if (removed[v]) {
          continue;
        }
        Set<Integer> neighbors = adjacency.get(v);
        if (neighbors.size() != 2) {
          continue;
        }
        int[] pair = neighbors.stream().mapToInt(Integer::intValue).toArray();
        if (pair[0] == pair[1] || removed[pair[0]] || removed[pair[1]]) {
          continue;
        }
        Graph.V a = graph.vertices().get(pair[0]);
        Graph.V b = graph.vertices().get(v);
        Graph.V c = graph.vertices().get(pair[1]);
        if (!isCollinear(a, b, c, angleTol, coordTol)) {
          continue;
        }
        removeVertex(v, pair[0], pair[1], adjacency, removed);
        changed = true;
      }
    }
    int[] remap = new int[n];
    List<Graph.V> vertices = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      if (removed[i]) {
        remap[i] = -1;
        continue;
      }
      Graph.V original = graph.vertices().get(i);
      int id = vertices.size();
      vertices.add(new Graph.V(id, original.x(), original.y()));
      remap[i] = id;
    }
    Map<Long, Integer> multiplicity = new HashMap<>();
    List<Graph.E> edges = new ArrayList<>();
    for (int u = 0; u < n; u++) {
      if (removed[u]) {
        continue;
      }
      for (int v : adjacency.get(u)) {
        if (removed[v]) {
          continue;
        }
        int nu = remap[u];
        int nv = remap[v];
        if (nu < 0 || nv < 0 || nu >= nv) {
          continue;
        }
        long key = edgeKey(nu, nv);
        edges.add(new Graph.E(nu, nv));
        multiplicity.merge(key, 1, Integer::sum);
      }
    }
    Map<Long, Graph.E> segmentEdgeMap = new HashMap<>();
    for (Graph.E edge : graph.segmentEdges()) {
      int nu = remap[edge.u()];
      int nv = remap[edge.v()];
      if (nu < 0 || nv < 0 || nu == nv) {
        continue;
      }
      long key = edgeKey(Math.min(nu, nv), Math.max(nu, nv));
      segmentEdgeMap.putIfAbsent(key, new Graph.E(nu, nv));
    }
    List<Graph.E> segmentEdges = new ArrayList<>(segmentEdgeMap.values());
    return new Graph(vertices, edges, multiplicity, graph.coordinateTolerance(), segmentEdges, List.of());
  }

  @Override
  public List<int[]> triangles(Graph graph) {
    if (graph.edges().isEmpty()) {
      return List.of();
    }
    Map<Integer, List<Neighbor>> neighbors = buildSortedNeighbors(graph);
    Map<Long, Boolean> visited = new HashMap<>();
    for (Graph.E edge : graph.edges()) {
      visited.put(directedKey(edge.u(), edge.v()), false);
      visited.put(directedKey(edge.v(), edge.u()), false);
    }
    List<List<Integer>> interiorCandidates = new ArrayList<>();
    List<Integer> outerFace = null;
    List<Integer> outerFaceRaw = null;
    double outerAreaAbs = -1d;
    double tol = graph.coordinateTolerance();
    for (Map.Entry<Integer, List<Neighbor>> entry : neighbors.entrySet()) {
      int u = entry.getKey();
      for (Neighbor neighbor : entry.getValue()) {
        int v = neighbor.vertex();
        long key = directedKey(u, v);
        Boolean state = visited.get(key);
        if (state == null || state) {
          continue;
        }
        List<Integer> face = traceFace(graph, neighbors, visited, u, v);
        if (face.size() < 3) {
          continue;
        }
        double area = signedArea(face, graph);
        if (Math.abs(area) <= tol) {
          continue;
        }
        if (area > 0) {
          if (face.size() == 3) {
            interiorCandidates.add(new ArrayList<>(face));
          }
        } else {
          double absArea = Math.abs(area);
          if (absArea > outerAreaAbs) {
            outerAreaAbs = absArea;
            outerFace = new ArrayList<>(face);
            outerFaceRaw = new ArrayList<>(face);
          }
        }
      }
    }
    Set<Long> boundaryEdges = new HashSet<>();
    Set<Integer> boundaryVertices = new HashSet<>();
    int[] simplifiedOuter = null;
    if (outerFace != null) {
      if (outerFaceRaw != null) {
        boundaryVertices.addAll(outerFaceRaw);
      }
      for (int i = 0; i < outerFace.size(); i++) {
        int a = outerFace.get(i);
        int b = outerFace.get((i + 1) % outerFace.size());
        boundaryEdges.add(directedKey(a, b));
        boundaryEdges.add(directedKey(b, a));
      }
      simplifiedOuter = simplifyOuterTriangle(outerFace, graph, tol);
      if (outerFaceRaw != null && simplifiedOuter != null) {
        for (int v : simplifiedOuter) {
          boundaryVertices.add(v);
        }
      }
    }
    List<int[]> results = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    for (List<Integer> face : interiorCandidates) {
      int outerCount = 0;
      boolean allBoundary = true;
      for (int v : face) {
        if (boundaryVertices.contains(v)) {
          outerCount++;
        } else {
          allBoundary = false;
        }
      }
      if (outerCount >= 2 && !allBoundary) {
        continue;
      }
      int[] tri = toArray(face);
      long key = triangleKey(tri);
      if (seen.add(key)) {
        results.add(tri);
      }
    }
    if (simplifiedOuter != null) {
      long key = triangleKey(simplifiedOuter);
      if (seen.add(key)) {
        results.add(simplifiedOuter);
      }
    }
    Map<Integer, Set<Integer>> segmentAdj = buildSegmentAdjacency(graph);
    double segmentTol = graph.coordinateTolerance();
    for (Map.Entry<Integer, Set<Integer>> entry : segmentAdj.entrySet()) {
      int u = entry.getKey();
      Set<Integer> neighborsU = entry.getValue();
      for (int v : neighborsU) {
        if (v <= u) {
          continue;
        }
        Set<Integer> neighborsV = segmentAdj.get(v);
        if (neighborsV == null) {
          continue;
        }
        for (int w : neighborsV) {
          if (w <= v) {
            continue;
          }
          if (!neighborsU.contains(w)) {
            continue;
          }
          long key = triangleKey(u, v, w);
          if (seen.contains(key)) {
            continue;
          }
          if (!isNonDegenerate(graph, u, v, w, segmentTol)) {
            continue;
          }
          boolean uvBoundary = isBoundaryEdge(u, v, boundaryEdges);
          boolean vwBoundary = isBoundaryEdge(v, w, boundaryEdges);
          boolean wuBoundary = isBoundaryEdge(w, u, boundaryEdges);
          int boundaryCount = (uvBoundary ? 1 : 0) + (vwBoundary ? 1 : 0) + (wuBoundary ? 1 : 0);
          if (boundaryCount > 0 && !(uvBoundary && vwBoundary && wuBoundary)) {
            continue;
          }
          int[] tri = new int[] {u, v, w};
          results.add(tri);
          seen.add(key);
        }
      }
    }
    return results;
  }

  private void removeVertex(int vertex, int a, int b, List<Set<Integer>> adjacency, boolean[] removed) {
    adjacency.get(a).remove(vertex);
    adjacency.get(b).remove(vertex);
    adjacency.get(a).add(b);
    adjacency.get(b).add(a);
    adjacency.get(vertex).clear();
    removed[vertex] = true;
  }

  private boolean isCollinear(Graph.V a, Graph.V b, Graph.V c, double angleTol, double coordTol) {
    double ax = a.x() - b.x();
    double ay = a.y() - b.y();
    double bx = c.x() - b.x();
    double by = c.y() - b.y();
    double normA = Math.hypot(ax, ay);
    double normB = Math.hypot(bx, by);
    if (normA <= coordTol || normB <= coordTol) {
      return false;
    }
    double dot = ax * bx + ay * by;
    double denom = normA * normB;
    double cos = Math.max(-1d, Math.min(1d, dot / denom));
    double angle = Math.acos(cos);
    if (Math.abs(Math.PI - angle) > angleTol) {
      return false;
    }
    double cross = Math.abs(ax * by - ay * bx);
    double limit = coordTol * (normA + normB);
    return cross <= limit;
  }

  private long edgeKey(int u, int v) {
    return (((long) u) << 32) | (v & 0xffffffffL);
  }

  private long directedKey(int u, int v) {
    return (((long) u) << 32) | (v & 0xffffffffL);
  }

  private Map<Integer, List<Neighbor>> buildSortedNeighbors(Graph graph) {
    Map<Integer, Set<Integer>> adjacency = new HashMap<>();
    for (Graph.E edge : graph.edges()) {
      adjacency.computeIfAbsent(edge.u(), k -> new HashSet<>()).add(edge.v());
      adjacency.computeIfAbsent(edge.v(), k -> new HashSet<>()).add(edge.u());
    }
    Map<Integer, List<Neighbor>> neighbors = new HashMap<>();
    for (Map.Entry<Integer, Set<Integer>> entry : adjacency.entrySet()) {
      int u = entry.getKey();
      Graph.V origin = graph.vertices().get(u);
      List<Neighbor> list = new ArrayList<>();
      for (int v : entry.getValue()) {
        Graph.V dest = graph.vertices().get(v);
        double angle = Math.atan2(dest.y() - origin.y(), dest.x() - origin.x());
        list.add(new Neighbor(v, angle));
      }
      list.sort(Comparator.comparingDouble(Neighbor::angle));
      neighbors.put(u, list);
    }
    return neighbors;
  }

  private List<Integer> traceFace(
      Graph graph,
      Map<Integer, List<Neighbor>> neighbors,
      Map<Long, Boolean> visited,
      int startU,
      int startV) {
    List<Integer> face = new ArrayList<>();
    int currU = startU;
    int currV = startV;
    while (true) {
      long key = directedKey(currU, currV);
      Boolean state = visited.get(key);
      if (state == null || state) {
        break;
      }
      visited.put(key, true);
      face.add(currU);
      int next = nextAround(neighbors, currU, currV);
      if (next == -1) {
        break;
      }
      currU = currV;
      currV = next;
      if (currU == startU && currV == startV) {
        break;
      }
    }
    return face;
  }

  private int nextAround(Map<Integer, List<Neighbor>> neighbors, int from, int to) {
    List<Neighbor> out = neighbors.get(to);
    if (out == null || out.isEmpty()) {
      return -1;
    }
    int idx = -1;
    for (int i = 0; i < out.size(); i++) {
      if (out.get(i).vertex() == from) {
        idx = i;
        break;
      }
    }
    if (idx == -1) {
      return -1;
    }
    int prev = (idx - 1 + out.size()) % out.size();
    return out.get(prev).vertex();
  }

  private double signedArea(List<Integer> face, Graph graph) {
    double sum = 0d;
    int size = face.size();
    for (int i = 0; i < size; i++) {
      Graph.V a = graph.vertices().get(face.get(i));
      Graph.V b = graph.vertices().get(face.get((i + 1) % size));
      sum += a.x() * b.y() - a.y() * b.x();
    }
    return 0.5 * sum;
  }

  private int[] toArray(List<Integer> face) {
    int[] result = new int[face.size()];
    for (int i = 0; i < face.size(); i++) {
      result[i] = face.get(i);
    }
    return result;
  }

  private int[] simplifyOuterTriangle(List<Integer> outerFace, Graph graph, double tol) {
    if (outerFace == null || outerFace.size() < 3) {
      return null;
    }
    List<Integer> vertices = new ArrayList<>(outerFace);
    boolean changed = true;
    while (vertices.size() > 3 && changed) {
      changed = false;
      for (int i = 0; i < vertices.size(); i++) {
        int prevIndex = (i - 1 + vertices.size()) % vertices.size();
        int nextIndex = (i + 1) % vertices.size();
        int prev = vertices.get(prevIndex);
        int curr = vertices.get(i);
        int next = vertices.get(nextIndex);
        if (collinear(graph.vertices().get(prev), graph.vertices().get(curr), graph.vertices().get(next), tol)) {
          vertices.remove(i);
          changed = true;
          break;
        }
      }
    }
    if (vertices.size() == 3) {
      return new int[] {vertices.get(0), vertices.get(1), vertices.get(2)};
    }
    return null;
  }

  private boolean collinear(Graph.V a, Graph.V b, Graph.V c, double tol) {
    double area2 = Math.abs((b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x()));
    return area2 <= tol;
  }

  private Map<Integer, Set<Integer>> buildSegmentAdjacency(Graph graph) {
    Map<Integer, Set<Integer>> adjacency = new HashMap<>();
    for (Graph.E edge : graph.segmentEdges()) {
      adjacency.computeIfAbsent(edge.u(), k -> new HashSet<>()).add(edge.v());
      adjacency.computeIfAbsent(edge.v(), k -> new HashSet<>()).add(edge.u());
    }
    return adjacency;
  }

  private boolean isBoundaryEdge(int a, int b, Set<Long> boundaryEdges) {
    return boundaryEdges.contains(directedKey(a, b));
  }

  private boolean isNonDegenerate(Graph graph, int u, int v, int w, double tol) {
    Graph.V a = graph.vertices().get(u);
    Graph.V b = graph.vertices().get(v);
    Graph.V c = graph.vertices().get(w);
    double area2 = Math.abs(
        a.x() * (b.y() - c.y())
            + b.x() * (c.y() - a.y())
            + c.x() * (a.y() - b.y()));
    return area2 > tol;
  }

  private long triangleKey(int[] triangle) {
    return triangleKey(triangle[0], triangle[1], triangle[2]);
  }

  private long triangleKey(int a, int b, int c) {
    int x = Math.min(a, Math.min(b, c));
    int z = Math.max(a, Math.max(b, c));
    int y = a + b + c - x - z;
    return (((long) x) << 42) | (((long) y) << 21) | (long) z;
  }

  private record Neighbor(int vertex, double angle) {}
}
