package app.tricount.geometry;

import app.tricount.graph.Graph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.noding.IntersectionAdder;
import org.locationtech.jts.noding.MCIndexNoder;
import org.locationtech.jts.noding.NodedSegmentString;

public final class PlanarGraphBuilder {
  public Graph build(List<Segment> segments, double coordTol) {
    if (segments.isEmpty()) {
      return new Graph(List.of(), List.of(), Map.of(), coordTol, List.of(), List.of());
    }
    double tolerance = coordTol > 0 ? coordTol : 1e-9;
    List<NodedSegmentString> segmentStrings = new ArrayList<>();
    List<Coordinate[]> originalEndpoints = new ArrayList<>();
    Map<String, SegmentCollector> collectors = new HashMap<>();
    List<String> segmentOrder = new ArrayList<>();
    for (Segment segment : segments) {
      if (isZeroLength(segment, tolerance)) {
        continue;
      }
      Coordinate[] coords = new Coordinate[] {
          new Coordinate(segment.x1(), segment.y1()),
          new Coordinate(segment.x2(), segment.y2())
      };
      segmentStrings.add(new NodedSegmentString(coords, segment.id()));
      originalEndpoints.add(coords);
      collectors.put(segment.id(), new SegmentCollector(segment));
      segmentOrder.add(segment.id());
    }
    if (segmentStrings.isEmpty()) {
      return new Graph(List.of(), List.of(), Map.of(), tolerance, List.of(), List.of());
    }
    MCIndexNoder noder = new MCIndexNoder(new IntersectionAdder(new RobustLineIntersector()));
    noder.computeNodes(segmentStrings);
    List<NodedSegmentString> noded = new ArrayList<>();
    NodedSegmentString.getNodedSubstrings(segmentStrings, noded);
    Map<Long, List<Integer>> vertexBuckets = new HashMap<>();
    List<Graph.V> vertices = new ArrayList<>();
    Map<Long, Graph.E> edgesByKey = new HashMap<>();
    Map<Long, Integer> multiplicity = new HashMap<>();
    for (NodedSegmentString string : noded) {
      String segmentId = (String) string.getData();
      SegmentCollector collector = collectors.get(segmentId);
      Coordinate[] coords = string.getCoordinates();
      for (int i = 1; i < coords.length; i++) {
        Coordinate a = coords[i - 1];
        Coordinate b = coords[i];
        if (isZeroLength(a, b, tolerance)) {
          continue;
        }
        int va = resolveVertex(a, tolerance, vertexBuckets, vertices);
        int vb = resolveVertex(b, tolerance, vertexBuckets, vertices);
        if (va == vb) {
          continue;
        }
        int u = Math.min(va, vb);
        int v = Math.max(va, vb);
        long key = edgeKey(u, v);
        edgesByKey.computeIfAbsent(key, k -> new Graph.E(u, v));
        multiplicity.merge(key, 1, Integer::sum);
        if (collector != null) {
          collector.addVertex(va, a);
          collector.addVertex(vb, b);
        }
      }
    }
    List<Graph.E> edges = new ArrayList<>(edgesByKey.values());
    List<Graph.E> segmentEdges = new ArrayList<>();
    for (Coordinate[] endpoints : originalEndpoints) {
      Coordinate a = endpoints[0];
      Coordinate b = endpoints[1];
      if (isZeroLength(a, b, tolerance)) {
        continue;
      }
      int va = findVertex(a, tolerance, vertexBuckets, vertices);
      int vb = findVertex(b, tolerance, vertexBuckets, vertices);
      if (va == vb) {
        continue;
      }
      segmentEdges.add(new Graph.E(va, vb));
    }
    List<List<Integer>> segmentPaths = new ArrayList<>();
    for (String id : segmentOrder) {
      SegmentCollector collector = collectors.get(id);
      if (collector != null) {
        segmentPaths.add(collector.build());
      } else {
        segmentPaths.add(List.of());
      }
    }
    return new Graph(vertices, edges, multiplicity, tolerance, segmentEdges, segmentPaths);
  }

  private boolean isZeroLength(Segment segment, double tol) {
    double dx = segment.x2() - segment.x1();
    double dy = segment.y2() - segment.y1();
    return Math.hypot(dx, dy) <= tol;
  }

  private boolean isZeroLength(Coordinate a, Coordinate b, double tol) {
    return a.distance(b) <= tol;
  }

  private int resolveVertex(Coordinate coordinate, double tol, Map<Long, List<Integer>> buckets, List<Graph.V> vertices) {
    long key = coordinateKey(coordinate, tol);
    List<Integer> ids = buckets.computeIfAbsent(key, k -> new ArrayList<>());
    for (int id : ids) {
      Graph.V existing = vertices.get(id);
      double dx = existing.x() - coordinate.getX();
      double dy = existing.y() - coordinate.getY();
      if (Math.hypot(dx, dy) <= tol) {
        return id;
      }
    }
    int id = vertices.size();
    Graph.V vertex = new Graph.V(id, coordinate.getX(), coordinate.getY());
    vertices.add(vertex);
    ids.add(id);
    return id;
  }

  private long coordinateKey(Coordinate coordinate, double tol) {
    double scale = tol > 0 ? 1d / tol : 1e9;
    long qx = Math.round(coordinate.getX() * scale);
    long qy = Math.round(coordinate.getY() * scale);
    return (qx << 32) ^ (qy & 0xffffffffL);
  }

  private long edgeKey(int u, int v) {
    return (((long) u) << 32) | (v & 0xffffffffL);
  }

  private int findVertex(Coordinate coordinate, double tol, Map<Long, List<Integer>> buckets, List<Graph.V> vertices) {
    long key = coordinateKey(coordinate, tol);
    List<Integer> ids = buckets.get(key);
    if (ids != null) {
      for (int id : ids) {
        Graph.V existing = vertices.get(id);
        double dx = existing.x() - coordinate.getX();
        double dy = existing.y() - coordinate.getY();
        if (Math.hypot(dx, dy) <= tol) {
          return id;
        }
      }
    }
    return resolveVertex(coordinate, tol, buckets, vertices);
  }

  private static final class SegmentCollector {
    private final Segment segment;
    private final Map<Integer, Double> params = new HashMap<>();

    SegmentCollector(Segment segment) {
      this.segment = segment;
    }

    void addVertex(int vertexId, Coordinate coordinate) {
      double t = parameter(coordinate);
      params.merge(vertexId, t, Math::min);
    }

    List<Integer> build() {
      return params.entrySet().stream()
          .sorted(Map.Entry.comparingByValue())
          .map(Map.Entry::getKey)
          .toList();
    }

    private double parameter(Coordinate coordinate) {
      double dx = segment.x2() - segment.x1();
      double dy = segment.y2() - segment.y1();
      double len2 = dx * dx + dy * dy;
      if (len2 <= 0) {
        return 0d;
      }
      double px = coordinate.getX() - segment.x1();
      double py = coordinate.getY() - segment.y1();
      double t = (px * dx + py * dy) / len2;
      if (t < 0d) {
        return 0d;
      }
      if (t > 1d) {
        return 1d;
      }
      return t;
    }
  }
}
