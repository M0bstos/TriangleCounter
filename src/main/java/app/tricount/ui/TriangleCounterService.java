package app.tricount.ui;

import app.tricount.geometry.Segment;
import app.tricount.graph.DefaultTriangleCounter;
import app.tricount.graph.Graph;
import app.tricount.graph.TriangleCounter;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.util.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class TriangleCounterService {
  public interface Listener {
    void onStart();
    void onSuccess(Result result);
    void onFailure(Throwable error);
  }

  public static final class Result {
    private final int segmentCount;
    private final int vertexCount;
    private final int triangleCount;
    private final List<List<Point2D>> triangles;

    public Result(int segmentCount, int vertexCount, int triangleCount, List<List<Point2D>> triangles) {
      this.segmentCount = segmentCount;
      this.vertexCount = vertexCount;
      this.triangleCount = triangleCount;
      this.triangles = triangles;
    }

    public int segmentCount() {
      return segmentCount;
    }

    public int vertexCount() {
      return vertexCount;
    }

    public int triangleCount() {
      return triangleCount;
    }

    public List<List<Point2D>> triangles() {
      return triangles;
    }
  }

  private static final double DEFAULT_COORD_TOL = 1e-6;
  private static final double DEFAULT_ANGLE_TOL = 1e-6;

  private final ObservableList<Segment> segments;
  private final TriangleCounter counter;
  private final PauseTransition debounce = new PauseTransition(Duration.millis(150));
  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
    Thread thread = new Thread(r, "triangle-counter");
    thread.setDaemon(true);
    return thread;
  });

  private Listener listener;
  private long sequence;
  private Future<?> inFlight;

  private final ListChangeListener<Segment> segmentListener = change -> request();

  public TriangleCounterService(ObservableList<Segment> segments) {
    this(segments, new DefaultTriangleCounter());
  }

  public TriangleCounterService(ObservableList<Segment> segments, TriangleCounter counter) {
    this.segments = segments;
    this.counter = counter;
    this.segments.addListener(segmentListener);
    debounce.setOnFinished(evt -> submit());
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  public void request() {
    debounce.playFromStart();
  }

  public void dispose() {
    debounce.stop();
    segments.removeListener(segmentListener);
    if (inFlight != null) {
      inFlight.cancel(true);
    }
    executor.shutdownNow();
  }

  private void submit() {
    final long runId = ++sequence;
    List<Segment> snapshot = List.copyOf(segments);
    notifyStart();
    if (inFlight != null) {
      inFlight.cancel(true);
    }
    inFlight = executor.submit(() -> {
      try {
        Result result = compute(snapshot);
        deliverSuccess(runId, result);
      } catch (Throwable error) {
        deliverFailure(runId, error);
      }
    });
  }

  private Result compute(List<Segment> snapshot) {
    if (snapshot.isEmpty()) {
      return new Result(0, 0, 0, List.of());
    }
    dumpSnapshotIfRequested(snapshot);
    Graph planar = counter.buildPlanarGraph(snapshot, DEFAULT_COORD_TOL);
    Graph contracted = counter.contractStraightVertices(planar, DEFAULT_ANGLE_TOL);
    List<int[]> triangles = counter.triangles(contracted);
    List<List<Point2D>> points = new ArrayList<>(triangles.size());
    for (int[] tri : triangles) {
      List<Point2D> poly = new ArrayList<>(tri.length);
      for (int idx : tri) {
        Graph.V vertex = contracted.vertices().get(idx);
        poly.add(new Point2D(vertex.x(), vertex.y()));
      }
      points.add(poly);
    }
    return new Result(snapshot.size(), contracted.vertices().size(), triangles.size(), points);
  }

  private void dumpSnapshotIfRequested(List<Segment> snapshot) {
    if (System.getProperty("tricount.debug.json") == null) {
      return;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"tolerance\": ").append(String.format(Locale.US, "%.9f", DEFAULT_COORD_TOL)).append(",\n");
    sb.append("  \"segments\": [\n");
    for (int i = 0; i < snapshot.size(); i++) {
      Segment segment = snapshot.get(i);
      sb.append("    {\"id\": \"").append(segment.id()).append("\", ");
      sb.append("\"x1\": ").append(String.format(Locale.US, "%.6f", segment.x1())).append(", ");
      sb.append("\"y1\": ").append(String.format(Locale.US, "%.6f", segment.y1())).append(", ");
      sb.append("\"x2\": ").append(String.format(Locale.US, "%.6f", segment.x2())).append(", ");
      sb.append("\"y2\": ").append(String.format(Locale.US, "%.6f", segment.y2())).append("}");
      if (i < snapshot.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ]\n");
    sb.append("}\n");
    System.out.println("[TriangleDebug] Snapshot passed to counter:");
    System.out.print(sb.toString());
  }

  private void notifyStart() {
    if (listener != null) {
      Platform.runLater(listener::onStart);
    }
  }

  private void deliverSuccess(long runId, Result result) {
    if (runId != sequence) {
      return;
    }
    if (listener != null) {
      Platform.runLater(() -> listener.onSuccess(result));
    }
  }

  private void deliverFailure(long runId, Throwable error) {
    if (runId != sequence) {
      return;
    }
    if (listener != null) {
      Platform.runLater(() -> listener.onFailure(error));
    }
  }
}
