package app.tricount.graph;

import java.util.List;
import java.util.Map;

public final class Graph {
  public static final class V {
    private final int id;
    private final double x;
    private final double y;

    public V(int id, double x, double y) {
      this.id = id;
      this.x = x;
      this.y = y;
    }

    public int id() {
      return id;
    }

    public double x() {
      return x;
    }

    public double y() {
      return y;
    }
  }

  public static final class E {
    private final int u;
    private final int v;

    public E(int u, int v) {
      this.u = Math.min(u, v);
      this.v = Math.max(u, v);
    }

    public int u() {
      return u;
    }

    public int v() {
      return v;
    }
  }

  private final List<V> vertices;
  private final List<E> edges;
  private final Map<Long, Integer> edgeMultiplicity;
  private final double coordinateTolerance;
  private final List<E> segmentEdges;
  private final List<List<Integer>> segmentVertexPaths;

  public Graph(
      List<V> vertices,
      List<E> edges,
      Map<Long, Integer> edgeMultiplicity,
      double coordinateTolerance,
      List<E> segmentEdges,
      List<List<Integer>> segmentVertexPaths) {
    this.vertices = List.copyOf(vertices);
    this.edges = List.copyOf(edges);
    this.edgeMultiplicity = Map.copyOf(edgeMultiplicity);
    this.coordinateTolerance = coordinateTolerance;
    this.segmentEdges = List.copyOf(segmentEdges);
    this.segmentVertexPaths = List.copyOf(segmentVertexPaths);
  }

  public List<V> vertices() {
    return vertices;
  }

  public List<E> edges() {
    return edges;
  }

  public Map<Long, Integer> edgeMultiplicity() {
    return edgeMultiplicity;
  }

  public double coordinateTolerance() {
    return coordinateTolerance;
  }

  public List<E> segmentEdges() {
    return segmentEdges;
  }

  public List<List<Integer>> segmentVertexPaths() {
    return segmentVertexPaths;
  }
}
