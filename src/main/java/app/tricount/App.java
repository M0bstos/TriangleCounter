package app.tricount;

import app.tricount.graph.DefaultTriangleCounter;
import app.tricount.graph.Graph;
import app.tricount.graph.TriangleCounter;
import app.tricount.io.ProjectDefinition;
import app.tricount.io.ProjectIO;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class App {
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.out.println("Usage: triangle-counter <input.json> [angleTol]");
      return;
    }
    Path input = Path.of(args[0]);
    double angleTol = args.length > 1 ? Double.parseDouble(args[1]) : 1e-6;
    ProjectIO io = new ProjectIO();
    ProjectDefinition project = io.load(input);
    TriangleCounter counter = new DefaultTriangleCounter();
    Graph planar = counter.buildPlanarGraph(project.segments(), project.tolerance());
    Graph contracted = counter.contractStraightVertices(planar, angleTol);
    List<int[]> triangles = counter.triangles(contracted);
    System.out.println("Segments: " + project.segments().size());
    System.out.println("Vertices: " + contracted.vertices().size());
    System.out.println("Edges: " + contracted.edges().size());
    System.out.println("Triangles: " + triangles.size());
    System.out.println();
    printVertices(contracted);
    printEdges(contracted);
    printTriangles(contracted, triangles);
  }

  private static void printVertices(Graph graph) {
    System.out.println("Vertices:");
    for (Graph.V vertex : graph.vertices()) {
      String name = nameFor(vertex.id());
      System.out.printf("  %s (id=%d): (%.6f, %.6f)%n", name, vertex.id(), vertex.x(), vertex.y());
    }
    System.out.println();
  }

  private static void printEdges(Graph graph) {
    System.out.println("Edges:");
    List<Graph.E> edges = new ArrayList<>(graph.edges());
    edges.sort(Comparator.comparing(e -> edgeKeyName(e)));
    for (Graph.E edge : edges) {
      String uName = nameFor(edge.u());
      String vName = nameFor(edge.v());
      System.out.printf("  %s-%s (ids=%d-%d)%n", uName, vName, edge.u(), edge.v());
    }
    System.out.println();
  }

  private static void printTriangles(Graph graph, List<int[]> triangles) {
    System.out.println("Triangles:");
    List<int[]> ordered = new ArrayList<>(triangles);
    ordered.sort(Comparator.comparing(App::triangleKey));
    for (int[] triangle : ordered) {
      String name = triangleName(triangle);
      double area = triangleArea(graph, triangle);
      System.out.printf(
          "  %s (ids=%d,%d,%d) area=%.6f%n",
          name, triangle[0], triangle[1], triangle[2], area);
    }
  }

  private static double triangleArea(Graph graph, int[] triangle) {
    Graph.V a = graph.vertices().get(triangle[0]);
    Graph.V b = graph.vertices().get(triangle[1]);
    Graph.V c = graph.vertices().get(triangle[2]);
    double area2 =
        Math.abs(
            a.x() * (b.y() - c.y())
                + b.x() * (c.y() - a.y())
                + c.x() * (a.y() - b.y()));
    return 0.5 * area2;
  }

  private static String triangleName(int[] triangle) {
    StringBuilder sb = new StringBuilder();
    for (int index : triangle) {
      sb.append(nameFor(index));
    }
    return sb.toString();
  }

  private static String triangleKey(int[] triangle) {
    String[] names = new String[triangle.length];
    for (int i = 0; i < triangle.length; i++) {
      names[i] = nameFor(triangle[i]);
    }
    Arrays.sort(names);
    return String.join("", names);
  }

  private static String edgeKeyName(Graph.E edge) {
    String[] names = {nameFor(edge.u()), nameFor(edge.v())};
    Arrays.sort(names);
    return names[0] + names[1];
  }

  private static String nameFor(int id) {
    StringBuilder sb = new StringBuilder();
    int value = id;
    while (value >= 0) {
      int rem = value % 26;
      sb.append((char) ('A' + rem));
      value = (value / 26) - 1;
    }
    return sb.reverse().toString();
  }
}
