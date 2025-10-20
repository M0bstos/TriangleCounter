package app.tricount.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Point2D;

public final class VertexRegistry {
  private final double mergeTolerance;
  private final List<Vertex> vertices = new ArrayList<>();
  private final Map<String, Vertex> byId = new HashMap<>();
  private int counter;

  public VertexRegistry(double mergeTolerance) {
    this.mergeTolerance = mergeTolerance;
  }

  public Vertex register(Point2D point) {
    Vertex existing = find(point);
    if (existing != null) {
      existing.incrementUsage();
      return existing;
    }
    String id = alphabeticalName(counter++);
    Vertex vertex = new Vertex(id, point.getX(), point.getY());
    vertex.incrementUsage();
    vertices.add(vertex);
    byId.put(id, vertex);
    return vertex;
  }

  public void restore(String id, Point2D point) {
    Vertex vertex = byId.get(id);
    if (vertex == null) {
      vertex = new Vertex(id, point.getX(), point.getY());
      vertices.add(vertex);
      byId.put(id, vertex);
    }
    vertex.incrementUsage();
  }

  public void decrement(String id) {
    Vertex vertex = byId.get(id);
    if (vertex == null) {
      return;
    }
    vertex.decrementUsage();
    if (vertex.usage() == 0) {
      byId.remove(id);
      vertices.remove(vertex);
    }
  }

  public Vertex get(String id) {
    return byId.get(id);
  }

  public List<Vertex> vertices() {
    return List.copyOf(vertices);
  }

  public List<Point2D> snapPoints() {
    List<Point2D> result = new ArrayList<>(vertices.size());
    for (Vertex vertex : vertices) {
      result.add(vertex.point());
    }
    return result;
  }

  private Vertex find(Point2D point) {
    for (Vertex vertex : vertices) {
      if (vertex.point().distance(point) <= mergeTolerance) {
        return vertex;
      }
    }
    return null;
  }

  private String alphabeticalName(int value) {
    StringBuilder sb = new StringBuilder();
    int current = value;
    while (current >= 0) {
      int rem = current % 26;
      sb.append((char) ('A' + rem));
      current = (current / 26) - 1;
    }
    return sb.reverse().toString();
  }

  public static final class Vertex {
    private final String id;
    private final double x;
    private final double y;
    private int usage;

    private Vertex(String id, double x, double y) {
      this.id = id;
      this.x = x;
      this.y = y;
    }

    public String id() {
      return id;
    }

    public double x() {
      return x;
    }

    public double y() {
      return y;
    }

    public int usage() {
      return usage;
    }

    public Point2D point() {
      return new Point2D(x, y);
    }

    private void incrementUsage() {
      usage++;
    }

    private void decrementUsage() {
      if (usage > 0) {
        usage--;
      }
    }
  }
}
