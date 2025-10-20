package app.tricount.ui;

import app.tricount.geometry.Segment;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

public final class MainViewController {
  private static final double MIN_SEGMENT_LENGTH = 2.0;
  private static final double SNAP_TOLERANCE_PX = 10.0;
  private static final double DELETE_HIT_TOLERANCE = 6.0;
  private static final double SEGMENT_SNAP_TOLERANCE = 7.0;
  private static final double GRID_SPACING = 20.0;
  private static final double GRID_SNAP_TOLERANCE = 8.0;
  private static final double GRID_DISTANCE_PENALTY = 2.0;
  private static final double SEGMENT_DIRECTION_BONUS = 4.0;
  private static final double SEGMENT_ALIGNMENT_THRESHOLD = Math.cos(Math.toRadians(15));
  private static final double VERTEX_MERGE_TOLERANCE = 1e-4;
  private static final double VERTEX_LABEL_OFFSET = 22.0;

  @FXML
  private BorderPane root;

  @FXML
  private ToolBar toolbar;

  @FXML
  private Pane canvasPane;

  @FXML
  private VBox sidePanel;

  @FXML
  private ToggleButton drawToggle;

  @FXML
  private ToggleButton deleteToggle;

  @FXML
  private Button undoButton;

  @FXML
  private Button redoButton;

  @FXML
  private Label statusLabel;

  @FXML
  private ListView<String> segmentListView;

  private final ObservableList<Segment> segments = FXCollections.observableArrayList();
  private final ObservableList<String> segmentItems = FXCollections.observableArrayList();
  private final List<Point2D> snapVertices = new ArrayList<>();
  private final Map<Segment, SegmentInfo> segmentInfo = new HashMap<>();
  private final List<VertexEntry> vertexRegistry = new ArrayList<>();
  private final Map<String, VertexEntry> vertexById = new HashMap<>();
  private final Deque<Command> undoStack = new ArrayDeque<>();
  private final Deque<Command> redoStack = new ArrayDeque<>();
  private SegmentCanvas segmentCanvas;
  private boolean drawingActive;
  private double anchorX;
  private double anchorY;
  private int vertexCounter;

  @FXML
  private void initialize() {
    segmentCanvas = new SegmentCanvas(segments);
    segmentCanvas.getStyleClass().add("segment-canvas");
    segmentCanvas.setFocusTraversable(true);
    segmentCanvas.prefWidthProperty().bind(canvasPane.widthProperty());
    segmentCanvas.prefHeightProperty().bind(canvasPane.heightProperty());
    canvasPane.getChildren().setAll(segmentCanvas);
    segmentCanvas.updateVertexLabels(List.of());
    if (segmentListView != null) {
      segmentListView.setItems(segmentItems);
    }
    segments.addListener((ListChangeListener<Segment>) change -> {
      rebuildSnapVertices();
      updateSegmentList();
    });
    rebuildSnapVertices();
    updateSegmentList();
    configureToolbar();
    wireCanvasEvents();
    setStatus("Draw mode: click to start a segment");
  }

  public ObservableList<Segment> segments() {
    return segments;
  }

  public BorderPane getRoot() {
    return root;
  }

  public ToolBar getToolbar() {
    return toolbar;
  }

  public Pane getCanvasPane() {
    return canvasPane;
  }

  public VBox getSidePanel() {
    return sidePanel;
  }

  private void configureToolbar() {
    ToggleGroup modeGroup = new ToggleGroup();
    drawToggle.setToggleGroup(modeGroup);
    deleteToggle.setToggleGroup(modeGroup);
    drawToggle.setSelected(true);
    modeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
      if (newToggle == drawToggle) {
        setStatus("Draw mode: click to start a segment");
      } else if (newToggle == deleteToggle) {
        cancelDrawing();
        setStatus("Delete mode: click a segment to remove it");
      }
    });
    undoButton.setOnAction(e -> undo());
    redoButton.setOnAction(e -> redo());
    updateUndoRedoButtons();
  }

  private void wireCanvasEvents() {
    segmentCanvas.addEventHandler(MouseEvent.MOUSE_CLICKED, this::handleCanvasClick);
    segmentCanvas.addEventHandler(MouseEvent.MOUSE_MOVED, this::handleCanvasMove);
    segmentCanvas.addEventHandler(MouseEvent.MOUSE_EXITED, e -> segmentCanvas.hideSnapIndicator());
    segmentCanvas.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKeyPress);
    segmentCanvas.setOnMouseEntered(e -> segmentCanvas.requestFocus());
  }

  private void handleCanvasClick(MouseEvent event) {
    if (event.getButton() != MouseButton.PRIMARY) {
      return;
    }
    double x = event.getX();
    double y = event.getY();
    if (deleteToggle.isSelected()) {
      Segment target = findSegmentNear(x, y);
      if (target != null) {
        executeCommand(new RemoveSegmentCommand(target));
        setStatus("Segment removed (" + segments.size() + " remaining)");
      } else {
        setStatus("No segment at cursor");
      }
      event.consume();
      return;
    }
    if (!drawToggle.isSelected()) {
      return;
    }
    SnapResult snap = findSnapPoint(x, y);
    if (snap == null) {
      Point2D fallback = snapToGrid(new Point2D(x, y), true);
      snap = new SnapResult(fallback, SegmentCanvas.SnapType.GRID);
    }
    Point2D point = snap.point();
    if (!drawingActive) {
      anchorX = point.getX();
      anchorY = point.getY();
      drawingActive = true;
      segmentCanvas.showPreview(anchorX, anchorY, anchorX, anchorY);
      segmentCanvas.showSnapIndicator(point.getX(), point.getY(), snap.type());
      setStatus("Select end point");
    } else {
      double dx = point.getX() - anchorX;
      double dy = point.getY() - anchorY;
      if (Math.hypot(dx, dy) >= MIN_SEGMENT_LENGTH) {
        executeCommand(new AddSegmentCommand(new Point2D(anchorX, anchorY), point));
        setStatus("Segment added (" + segments.size() + " total)");
      } else {
        setStatus("Segment too short");
      }
      cancelDrawing();
    }
    event.consume();
  }

  private void handleCanvasMove(MouseEvent event) {
    SnapResult snap = findSnapPoint(event.getX(), event.getY());
    if (snap == null) {
      Point2D fallback = snapToGrid(new Point2D(event.getX(), event.getY()));
      if (fallback != null) {
        snap = new SnapResult(fallback, SegmentCanvas.SnapType.GRID);
      }
    }
    if (drawToggle.isSelected() && drawingActive) {
      Point2D target = snap != null ? snap.point() : new Point2D(event.getX(), event.getY());
      segmentCanvas.showPreview(anchorX, anchorY, target.getX(), target.getY());
    }
    if (snap != null) {
      segmentCanvas.showSnapIndicator(snap.point().getX(), snap.point().getY(), snap.type());
    } else {
      segmentCanvas.hideSnapIndicator();
    }
  }

  private void handleKeyPress(KeyEvent event) {
    if (event.getCode() == KeyCode.ESCAPE && drawingActive) {
      cancelDrawing();
      setStatus("Drawing cancelled");
      event.consume();
    } else if (event.isControlDown()) {
      if (event.getCode() == KeyCode.Z) {
        undo();
        event.consume();
      } else if (event.getCode() == KeyCode.Y) {
        redo();
        event.consume();
      }
    }
  }

  private void cancelDrawing() {
    drawingActive = false;
    segmentCanvas.hidePreview();
    segmentCanvas.hideSnapIndicator();
  }

  private void rebuildSnapVertices() {
    snapVertices.clear();
    for (VertexEntry entry : vertexRegistry) {
      snapVertices.add(entry.point());
    }
  }

  private void updateSegmentList() {
    segmentItems.clear();
    for (Segment segment : segments) {
      SegmentInfo info = segmentInfo.get(segment);
      if (info == null) {
        segmentItems.add(formatSegmentFallback(segment));
      } else {
        segmentItems.add(formatSegment(segment, info));
      }
    }
    segmentCanvas.updateVertexLabels(buildVertexLabels());
  }

  private String formatSegment(Segment segment, SegmentInfo info) {
    String id = segment.id();
    return String.format(
        Locale.US,
        "%s: (%.2f, %.2f) -> (%.2f, %.2f)",
        id,
        segment.x1(),
        segment.y1(),
        segment.x2(),
        segment.y2());
  }

  private String formatSegmentFallback(Segment segment) {
    return String.format(
        Locale.US,
        "%s: (%.2f, %.2f) -> (%.2f, %.2f)",
        segment.id(),
        segment.x1(),
        segment.y1(),
        segment.x2(),
        segment.y2());
  }

  private SnapResult findSnapPoint(double x, double y) {
    Point2D cursor = new Point2D(x, y);
    SnapResult best = null;
    double bestScore = Double.POSITIVE_INFINITY;
    Point2D anchorPoint = drawingActive ? new Point2D(anchorX, anchorY) : null;
    Point2D movementDir = null;
    if (anchorPoint != null) {
      Point2D movement = cursor.subtract(anchorPoint);
      if (movement.magnitude() > 1e-6) {
        movementDir = movement.normalize();
      }
    }

    for (Point2D vertex : snapVertices) {
      if (anchorPoint != null && vertex.distance(anchorPoint) < 1e-6) {
        continue;
      }
      double distance = vertex.distance(cursor);
      if (distance > SNAP_TOLERANCE_PX) {
        continue;
      }
      double base = 0.0;
      if (anchorPoint != null && vertex.distance(anchorPoint) <= SNAP_TOLERANCE_PX * 0.5) {
        base -= 5.0;
      }
      double score = base + distance;
      if (score < bestScore) {
        best = new SnapResult(vertex, SegmentCanvas.SnapType.VERTEX);
        bestScore = score;
      }
    }

    for (Segment segment : segments) {
      Point2D projection = projectOntoSegment(segment, cursor);
      if (projection == null) {
        continue;
      }
      double distance = projection.distance(cursor);
      if (distance > SEGMENT_SNAP_TOLERANCE) {
        continue;
      }
      double base = 10.0;
      if (anchorPoint != null && movementDir != null) {
        Point2D candidateVec = projection.subtract(anchorPoint);
        if (candidateVec.magnitude() > 1e-6) {
          Point2D candidateDir = candidateVec.normalize();
          double dot = movementDir.dotProduct(candidateDir);
          if (dot >= SEGMENT_ALIGNMENT_THRESHOLD) {
            base -= SEGMENT_DIRECTION_BONUS;
          }
        }
      }
      double score = base + distance;
      if (score < bestScore) {
        best = new SnapResult(projection, SegmentCanvas.SnapType.SEGMENT);
        bestScore = score;
      }
    }

    Point2D gridSnap = snapToGrid(cursor);
    if (gridSnap != null) {
      double distance = gridSnap.distance(cursor);
      double score = 20.0 + GRID_DISTANCE_PENALTY + distance;
      if (score < bestScore) {
        best = new SnapResult(gridSnap, SegmentCanvas.SnapType.GRID);
        bestScore = score;
      }
    }
    return best;
  }

  private Segment findSegmentNear(double x, double y) {
    Segment bestSegment = null;
    double bestDistance = Double.MAX_VALUE;
    for (Segment segment : segments) {
      double distance = distanceToSegment(x, y, segment);
      if (distance <= DELETE_HIT_TOLERANCE && distance < bestDistance) {
        bestDistance = distance;
        bestSegment = segment;
      }
    }
    return bestSegment;
  }

  private List<SegmentCanvas.VertexLabel> buildVertexLabels() {
    Map<String, Point2D> normalSums = new HashMap<>();
    Map<String, List<Point2D>> incidentDirections = new HashMap<>();
    for (Segment segment : segments) {
      SegmentInfo info = segmentInfo.get(segment);
      if (info == null) {
        continue;
      }
      VertexEntry start = vertexById.get(info.startVertexId());
      VertexEntry end = vertexById.get(info.endVertexId());
      if (start == null || end == null) {
        continue;
      }
      Point2D startPoint = start.point();
      Point2D endPoint = end.point();
      Point2D vecStart = endPoint.subtract(startPoint);
      if (vecStart.magnitude() <= 1e-6) {
        continue;
      }
      Point2D dirStart = vecStart.normalize();
      addDirection(incidentDirections, start.id, dirStart);
      Point2D normalStart = new Point2D(-dirStart.getY(), dirStart.getX());
      accumulateNormal(normalSums, start.id, normalStart);

      Point2D vecEnd = startPoint.subtract(endPoint);
      Point2D dirEnd = vecEnd.normalize();
      addDirection(incidentDirections, end.id, dirEnd);
      Point2D normalEnd = new Point2D(-dirEnd.getY(), dirEnd.getX());
      accumulateNormal(normalSums, end.id, normalEnd);
    }
    List<SegmentCanvas.VertexLabel> labels = new ArrayList<>();
    for (VertexEntry entry : vertexRegistry) {
      Point2D direction = chooseLabelDirection(normalSums.get(entry.id), incidentDirections.get(entry.id));
      Point2D offset = direction.multiply(VERTEX_LABEL_OFFSET);
      labels.add(new SegmentCanvas.VertexLabel(entry.id, entry.x, entry.y, offset.getX(), offset.getY()));
    }
    return labels;
  }

  private void addDirection(Map<String, List<Point2D>> map, String vertexId, Point2D direction) {
    if (direction.magnitude() <= 1e-6) {
      return;
    }
    Point2D normalized = direction.normalize();
    List<Point2D> list = map.computeIfAbsent(vertexId, k -> new ArrayList<>());
    list.add(normalized);
    list.add(normalized.multiply(-1));
  }

  private void accumulateNormal(Map<String, Point2D> normals, String vertexId, Point2D normal) {
    Point2D existing = normals.get(vertexId);
    if (existing == null) {
      normals.put(vertexId, normal);
    } else {
      normals.put(vertexId, new Point2D(existing.getX() + normal.getX(), existing.getY() + normal.getY()));
    }
  }

  private Point2D chooseLabelDirection(Point2D sum, List<Point2D> incidentDirections) {
    if (sum != null && sum.magnitude() > 1e-4) {
      return sum.normalize();
    }
    if (incidentDirections == null || incidentDirections.isEmpty()) {
      return new Point2D(0, -1);
    }
    return findGapDirection(incidentDirections);
  }

  private Point2D findGapDirection(List<Point2D> incidentDirections) {
    double twoPi = Math.PI * 2d;
    double angleTolerance = Math.toRadians(14);
    List<Double> angles = new ArrayList<>();
    for (Point2D direction : incidentDirections) {
      double theta = Math.atan2(direction.getY(), direction.getX());
      if (theta < 0) {
        theta += twoPi;
      }
      angles.add(theta);
    }
    if (angles.isEmpty()) {
      return new Point2D(0, -1);
    }
    angles.sort(Double::compare);
    double bestGap = -1d;
    double bestAngle = 0d;
    int n = angles.size();
    for (int i = 0; i < n; i++) {
      double start = angles.get(i);
      double end = (i == n - 1) ? angles.get(0) + twoPi : angles.get(i + 1);
      double gap = end - start;
      double usableGap = gap - (2d * angleTolerance);
      if (usableGap > bestGap) {
        bestGap = usableGap;
        double candidate = start + angleTolerance + Math.max(usableGap, 0d) / 2d;
        bestAngle = candidate;
      }
    }
    if (bestGap > 1e-6) {
      return new Point2D(Math.cos(bestAngle), Math.sin(bestAngle));
    }
    // All directions crowded; pick the one with lowest maximum alignment.
    double bestScore = Double.POSITIVE_INFINITY;
    double resolvedAngle = 0d;
    int samples = 64;
    for (int i = 0; i < samples; i++) {
      double angle = (twoPi * i) / samples;
      double cos = Math.cos(angle);
      double sin = Math.sin(angle);
      Point2D candidate = new Point2D(cos, sin);
      double worstAlignment = 0d;
      for (Point2D dir : incidentDirections) {
        double alignment = Math.abs(candidate.dotProduct(dir));
        if (alignment > worstAlignment) {
          worstAlignment = alignment;
        }
      }
      if (worstAlignment + 1e-6 < bestScore
          || (Math.abs(worstAlignment - bestScore) <= 1e-6 && sin < Math.sin(resolvedAngle))) {
        bestScore = worstAlignment;
        resolvedAngle = angle;
      }
    }
    return new Point2D(Math.cos(resolvedAngle), Math.sin(resolvedAngle));
  }

  private Point2D snapToGrid(Point2D point) {
    return snapToGrid(point, false);
  }

  private Point2D snapToGrid(Point2D point, boolean force) {
    double gridX = Math.round(point.getX() / GRID_SPACING) * GRID_SPACING;
    double gridY = Math.round(point.getY() / GRID_SPACING) * GRID_SPACING;
    Point2D snapped = new Point2D(gridX, gridY);
    if (force || snapped.distance(point) <= GRID_SNAP_TOLERANCE) {
      return snapped;
    }
    return null;
  }

  private double distanceToSegment(double px, double py, Segment segment) {
    double x1 = segment.x1();
    double y1 = segment.y1();
    double x2 = segment.x2();
    double y2 = segment.y2();
    double dx = x2 - x1;
    double dy = y2 - y1;
    if (dx == 0 && dy == 0) {
      return Math.hypot(px - x1, py - y1);
    }
    double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
    t = Math.max(0, Math.min(1, t));
    double projX = x1 + t * dx;
    double projY = y1 + t * dy;
    return Math.hypot(px - projX, py - projY);
  }

  private Point2D projectOntoSegment(Segment segment, Point2D point) {
    double x1 = segment.x1();
    double y1 = segment.y1();
    double x2 = segment.x2();
    double y2 = segment.y2();
    double dx = x2 - x1;
    double dy = y2 - y1;
    double lengthSq = dx * dx + dy * dy;
    if (lengthSq == 0) {
      return null;
    }
    double t = ((point.getX() - x1) * dx + (point.getY() - y1) * dy) / lengthSq;
    if (t <= 0 || t >= 1) {
      return null;
    }
    double projX = x1 + t * dx;
    double projY = y1 + t * dy;
    return new Point2D(projX, projY);
  }

  private void executeCommand(Command command) {
    command.redo();
    undoStack.push(command);
    redoStack.clear();
    updateUndoRedoButtons();
  }

  private void undo() {
    if (undoStack.isEmpty()) {
      return;
    }
    Command command = undoStack.pop();
    command.undo();
    redoStack.push(command);
    updateUndoRedoButtons();
    setStatus("Undo");
  }

  private void redo() {
    if (redoStack.isEmpty()) {
      return;
    }
    Command command = redoStack.pop();
    command.redo();
    undoStack.push(command);
    updateUndoRedoButtons();
    setStatus("Redo");
  }

  private void updateUndoRedoButtons() {
    undoButton.setDisable(undoStack.isEmpty());
    redoButton.setDisable(redoStack.isEmpty());
  }

  private void setStatus(String message) {
    if (statusLabel != null) {
      statusLabel.setText(message);
    }
  }

  private VertexEntry registerVertex(Point2D point) {
    VertexEntry entry = findVertexEntry(point);
    if (entry == null) {
      String id = alphabeticalName(vertexCounter++);
      entry = new VertexEntry(id, point.getX(), point.getY());
      vertexRegistry.add(entry);
      vertexById.put(id, entry);
    }
    entry.usage++;
    return entry;
  }

  private void restoreVertex(String vertexId, Point2D point) {
    VertexEntry entry = vertexById.get(vertexId);
    if (entry == null) {
      entry = new VertexEntry(vertexId, point.getX(), point.getY());
      vertexRegistry.add(entry);
      vertexById.put(vertexId, entry);
    }
    entry.usage++;
  }

  private void decrementVertexUsage(String vertexId) {
    VertexEntry entry = vertexById.get(vertexId);
    if (entry == null) {
      return;
    }
    entry.usage = Math.max(0, entry.usage - 1);
    if (entry.usage == 0) {
      vertexById.remove(vertexId);
      vertexRegistry.removeIf(e -> e.id.equals(vertexId));
    }
  }

  private VertexEntry findVertexEntry(Point2D point) {
    for (VertexEntry entry : vertexRegistry) {
      if (entry.point().distance(point) <= VERTEX_MERGE_TOLERANCE) {
        return entry;
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

  private record SnapResult(Point2D point, SegmentCanvas.SnapType type) {}

  private interface Command {
    void redo();

    void undo();
  }

  private final class AddSegmentCommand implements Command {
    private final Point2D startPoint;
    private final Point2D endPoint;
    private Segment segment;
    private String startVertexId;
    private String endVertexId;
    private final int insertionIndex;

    private AddSegmentCommand(Point2D startPoint, Point2D endPoint) {
      this.startPoint = startPoint;
      this.endPoint = endPoint;
      this.insertionIndex = segments.size();
    }

    @Override
    public void redo() {
      if (segment == null) {
        VertexEntry startEntry = registerVertex(startPoint);
        VertexEntry endEntry = registerVertex(endPoint);
        startVertexId = startEntry.id;
        endVertexId = endEntry.id;
        String segmentId =
            (startVertexId + endVertexId).toLowerCase(Locale.ROOT);
        segment =
            new Segment(startPoint.getX(), startPoint.getY(), endPoint.getX(), endPoint.getY(), segmentId);
      } else {
        restoreVertex(startVertexId, startPoint);
        restoreVertex(endVertexId, endPoint);
      }
      segmentInfo.put(segment, new SegmentInfo(startVertexId, endVertexId));
      int insert = Math.min(insertionIndex, segments.size());
      segments.add(insert, segment);
    }

    @Override
    public void undo() {
      segmentInfo.remove(segment);
      decrementVertexUsage(startVertexId);
      decrementVertexUsage(endVertexId);
      segments.remove(segment);
    }
  }

  private final class RemoveSegmentCommand implements Command {
    private final Segment segment;
    private final SegmentInfo info;
    private final int index;

    private RemoveSegmentCommand(Segment segment) {
      this.segment = segment;
      this.info = segmentInfo.get(segment);
      this.index = segments.indexOf(segment);
    }

    @Override
    public void redo() {
      if (info != null) {
        segmentInfo.remove(segment);
        decrementVertexUsage(info.startVertexId());
        decrementVertexUsage(info.endVertexId());
      }
      segments.remove(segment);
    }

    @Override
    public void undo() {
      if (info != null) {
        restoreVertex(info.startVertexId(), new Point2D(segment.x1(), segment.y1()));
        restoreVertex(info.endVertexId(), new Point2D(segment.x2(), segment.y2()));
        segmentInfo.put(segment, info);
      }
      int insert = Math.min(index, segments.size());
      segments.add(insert, segment);
    }
  }

  private record SegmentInfo(String startVertexId, String endVertexId) {}

  private static final class VertexEntry {
    private final String id;
    private final double x;
    private final double y;
    private int usage;

    private VertexEntry(String id, double x, double y) {
      this.id = id;
      this.x = x;
      this.y = y;
    }

    private Point2D point() {
      return new Point2D(x, y);
    }
  }
}
