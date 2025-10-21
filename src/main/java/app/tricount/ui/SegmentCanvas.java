package app.tricount.ui;

import app.tricount.geometry.Segment;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;

public final class SegmentCanvas extends Pane {
  public enum SnapType {
    VERTEX,
    SEGMENT,
    GRID
  }

  public record VertexLabel(String id, double x, double y, double offsetX, double offsetY) {}

  private final ObservableList<Segment> segments;
  private final Canvas gridCanvas = new Canvas();
  private final Group segmentGroup = new Group();
  private final Group triangleGroup = new Group();
  private final Group vertexGroup = new Group();
  private final Map<Segment, Line> lineBySegment = new HashMap<>();
  private final Map<String, Text> vertexLabels = new HashMap<>();
  private final Line previewLine = new Line();
  private final Circle snapIndicator = new Circle(5);

  public SegmentCanvas(ObservableList<Segment> segments) {
    this.segments = segments;
    setPickOnBounds(true);
    gridCanvas.setMouseTransparent(true);
    previewLine.setVisible(false);
    previewLine.setStrokeWidth(1.5);
    previewLine.getStrokeDashArray().setAll(8d, 6d);
    previewLine.setOpacity(0.7);
    previewLine.getStyleClass().add("preview-line");
    previewLine.setMouseTransparent(true);
    snapIndicator.setVisible(false);
    snapIndicator.setFill(Color.rgb(255, 152, 0, 0.25));
    snapIndicator.setStroke(Color.web("#ff9800"));
    snapIndicator.setMouseTransparent(true);
    segmentGroup.setMouseTransparent(true);
    triangleGroup.setMouseTransparent(true);
    vertexGroup.setMouseTransparent(true);
    getChildren().addAll(gridCanvas, segmentGroup, triangleGroup, vertexGroup, previewLine, snapIndicator);
    previewLine.setVisible(false);
    segments.addListener(this::handleChange);
    for (Segment segment : segments) {
      addSegment(segment);
    }
    widthProperty().addListener((obs, oldV, newV) -> redrawGrid());
    heightProperty().addListener((obs, oldV, newV) -> redrawGrid());
    redrawGrid();
  }

  public void showPreview(double x1, double y1, double x2, double y2) {
    previewLine.setStartX(x1);
    previewLine.setStartY(y1);
    previewLine.setEndX(x2);
    previewLine.setEndY(y2);
    previewLine.setVisible(true);
  }

  public void hidePreview() {
    previewLine.setVisible(false);
  }

  public void showSnapIndicator(double x, double y, SnapType type) {
    switch (type) {
      case VERTEX -> {
        snapIndicator.setFill(Color.rgb(255, 152, 0, 0.25));
        snapIndicator.setStroke(Color.web("#ff9800"));
        snapIndicator.setRadius(5);
      }
      case SEGMENT -> {
        snapIndicator.setFill(Color.rgb(76, 175, 80, 0.2));
        snapIndicator.setStroke(Color.web("#4caf50"));
        snapIndicator.setRadius(7);
      }
      case GRID -> {
        snapIndicator.setFill(Color.rgb(0, 188, 212, 0.2));
        snapIndicator.setStroke(Color.web("#00acc1"));
        snapIndicator.setRadius(9);
      }
    }
    snapIndicator.setCenterX(x);
    snapIndicator.setCenterY(y);
    snapIndicator.setVisible(true);
  }

  public void hideSnapIndicator() {
    snapIndicator.setVisible(false);
  }

  public void updateVertexLabels(Iterable<VertexLabel> labels) {
    Set<String> seen = new HashSet<>();
    for (VertexLabel label : labels) {
      Text text = vertexLabels.computeIfAbsent(label.id(), this::createVertexText);
      positionVertexText(text, label);
      seen.add(label.id());
    }
    vertexLabels.entrySet().removeIf(entry -> {
      if (seen.contains(entry.getKey())) {
        return false;
      }
      vertexGroup.getChildren().remove(entry.getValue());
      return true;
    });
  }

  private Text createVertexText(String id) {
    Text text = new Text(id);
    text.getStyleClass().add("vertex-label");
    text.setFill(Color.web("#1b5e20"));
    text.setMouseTransparent(true);
    text.setTextOrigin(VPos.CENTER);
    vertexGroup.getChildren().add(text);
    text.layoutBoundsProperty().addListener((obs, old, bounds) -> text.setTranslateX(-bounds.getWidth() / 2.0));
    return text;
  }

  private void positionVertexText(Text text, VertexLabel label) {
    text.setText(label.id());
    text.setX(label.x() + label.offsetX());
    text.setY(label.y() + label.offsetY());
    text.applyCss();
    text.setTranslateX(-text.getLayoutBounds().getWidth() / 2.0);
  }

  private void handleChange(ListChangeListener.Change<? extends Segment> change) {
    while (change.next()) {
      if (change.wasRemoved()) {
        for (Segment segment : change.getRemoved()) {
          removeSegment(segment);
        }
      }
      if (change.wasAdded()) {
        for (Segment segment : change.getAddedSubList()) {
          addSegment(segment);
        }
      }
    }
  }

  private void addSegment(Segment segment) {
    Line line = new Line(segment.x1(), segment.y1(), segment.x2(), segment.y2());
    line.getStyleClass().add("segment-line");
    line.setMouseTransparent(true);
    line.setStrokeWidth(2.0);
    segmentGroup.getChildren().add(line);
    lineBySegment.put(segment, line);
  }

  private void removeSegment(Segment segment) {
    Line line = lineBySegment.remove(segment);
    if (line != null) {
      segmentGroup.getChildren().remove(line);
    }
  }

  private void redrawGrid() {
    double width = getWidth();
    double height = getHeight();
    if (width <= 0 || height <= 0) {
      return;
    }
    gridCanvas.setWidth(width);
    gridCanvas.setHeight(height);
    GraphicsContext gc = gridCanvas.getGraphicsContext2D();
    gc.clearRect(0, 0, width, height);
    double minorSpacing = 20.0;
    int majorFrequency = 5;
    int verticalLines = (int) Math.ceil(width / minorSpacing);
    int horizontalLines = (int) Math.ceil(height / minorSpacing);
    Color majorColor = Color.rgb(56, 142, 60, 0.25);
    Color minorColor = Color.rgb(129, 199, 132, 0.18);

    gc.setLineWidth(1.0);
    for (int i = 0; i <= verticalLines; i++) {
      double x = i * minorSpacing;
      gc.setStroke(i % majorFrequency == 0 ? majorColor : minorColor);
      gc.strokeLine(x, 0, x, height);
    }
    for (int j = 0; j <= horizontalLines; j++) {
      double y = j * minorSpacing;
      gc.setStroke(j % majorFrequency == 0 ? majorColor : minorColor);
      gc.strokeLine(0, y, width, y);
    }
  }

  public void updateTriangles(List<List<Point2D>> triangles) {
    triangleGroup.getChildren().clear();
    if (triangles == null || triangles.isEmpty()) {
      return;
    }
    int size = triangles.size();
    for (int i = 0; i < size; i++) {
      List<Point2D> triangle = triangles.get(i);
      if (triangle.size() < 3) {
        continue;
      }
      javafx.scene.shape.Polygon polygon = new javafx.scene.shape.Polygon();
      for (Point2D point : triangle) {
        polygon.getPoints().addAll(point.getX(), point.getY());
      }
      double hue = (i * 280.0 / Math.max(1, size)) % 360;
      Color base = Color.hsb(hue, 0.6, 0.85);
      polygon.setFill(new Color(base.getRed(), base.getGreen(), base.getBlue(), 0.3));
      polygon.setStroke(base.deriveColor(0d, 1d, 0.8, 1d));
      polygon.setStrokeWidth(1.2);
      polygon.setMouseTransparent(true);
      triangleGroup.getChildren().add(polygon);
    }
  }

  public void setTriangleOverlayVisible(boolean visible) {
    triangleGroup.setVisible(visible);
    triangleGroup.setManaged(visible);
  }
}
