package app.tricount.ui;

import app.tricount.geometry.Segment;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
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

  @FXML
  private Label segmentCountLabel;

  @FXML
  private Label vertexCountLabel;

  @FXML
  private Label triangleCountLabel;

  @FXML
  private CheckBox highlightToggle;

  @FXML
  private ProgressIndicator recomputeIndicator;

  private final ObservableList<Segment> segments = FXCollections.observableArrayList();
  private final ObservableList<String> segmentItems = FXCollections.observableArrayList();
  private final VertexRegistry vertexRegistry = new VertexRegistry(VERTEX_MERGE_TOLERANCE);
  private final Map<Segment, SegmentMetadata> segmentMetadata = new HashMap<>();
  private final SnapResolver snapResolver =
      new SnapResolver(
          SNAP_TOLERANCE_PX,
          SEGMENT_SNAP_TOLERANCE,
          GRID_SPACING,
          GRID_SNAP_TOLERANCE,
          GRID_DISTANCE_PENALTY,
          SEGMENT_DIRECTION_BONUS,
          SEGMENT_ALIGNMENT_THRESHOLD);
  private final VertexLabelCalculator vertexLabelCalculator = new VertexLabelCalculator(VERTEX_LABEL_OFFSET);
  private final List<Point2D> snapVertices = new ArrayList<>();
  private final Deque<Command> undoStack = new ArrayDeque<>();
  private final Deque<Command> redoStack = new ArrayDeque<>();
  private TriangleCounterService triangleService;
  private List<List<Point2D>> currentTriangles = List.of();

  private SegmentCanvas segmentCanvas;
  private boolean drawingActive;
  private double anchorX;
  private double anchorY;

  @FXML
  private void initialize() {
    segmentCanvas = new SegmentCanvas(segments);
    segmentCanvas.getStyleClass().add("segment-canvas");
    segmentCanvas.setFocusTraversable(true);
    segmentCanvas.prefWidthProperty().bind(canvasPane.widthProperty());
    segmentCanvas.prefHeightProperty().bind(canvasPane.heightProperty());
    canvasPane.getChildren().setAll(segmentCanvas);
    segmentCanvas.setTriangleOverlayVisible(false);
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
    configureHighlightToggle();
    configureTriangleService();
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

  private void configureHighlightToggle() {
    if (highlightToggle != null) {
      highlightToggle.setSelected(false);
      highlightToggle.selectedProperty().addListener((obs, old, value) -> applyTriangleOverlay());
    }
    if (recomputeIndicator != null) {
      recomputeIndicator.setVisible(false);
      recomputeIndicator.setManaged(false);
    }
  }

  private void configureTriangleService() {
    triangleService = new TriangleCounterService(segments);
    triangleService.setListener(new TriangleCounterService.Listener() {
      @Override
      public void onStart() {
        if (recomputeIndicator != null) {
          recomputeIndicator.setManaged(true);
          recomputeIndicator.setVisible(true);
        }
      }

      @Override
      public void onSuccess(TriangleCounterService.Result result) {
        if (recomputeIndicator != null) {
          recomputeIndicator.setManaged(false);
          recomputeIndicator.setVisible(false);
        }
        if (segmentCountLabel != null) {
          segmentCountLabel.setText(Integer.toString(result.segmentCount()));
        }
        if (vertexCountLabel != null) {
          vertexCountLabel.setText(Integer.toString(result.vertexCount()));
        }
        if (triangleCountLabel != null) {
          triangleCountLabel.setText(Integer.toString(result.triangleCount()));
        }
        currentTriangles = result.triangles();
        applyTriangleOverlay();
      }

      @Override
      public void onFailure(Throwable error) {
        if (recomputeIndicator != null) {
          recomputeIndicator.setManaged(false);
          recomputeIndicator.setVisible(false);
        }
        setStatus("Triangle update failed: " + error.getMessage());
      }
    });
    triangleService.request();
  }


  private void handleCanvasClick(MouseEvent event) {
    if (event.getButton() != MouseButton.PRIMARY) {
      return;
    }
    Point2D cursor = new Point2D(event.getX(), event.getY());
    if (deleteToggle.isSelected()) {
      Segment target = findSegmentNear(cursor.getX(), cursor.getY());
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
    SnapResolver.SnapResult snap = resolveSnap(cursor);
    if (snap == null) {
      Point2D fallback = snapResolver.snapToGrid(cursor, true);
      snap = new SnapResolver.SnapResult(fallback, SegmentCanvas.SnapType.GRID);
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
    Point2D cursor = new Point2D(event.getX(), event.getY());
    SnapResolver.SnapResult snap = resolveSnap(cursor);
    if (snap == null) {
      Point2D fallback = snapResolver.snapToGrid(cursor, false);
      if (fallback != null) {
        snap = new SnapResolver.SnapResult(fallback, SegmentCanvas.SnapType.GRID);
      }
    }
    if (drawToggle.isSelected() && drawingActive) {
      Point2D target = snap != null ? snap.point() : cursor;
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

  private SnapResolver.SnapResult resolveSnap(Point2D cursor) {
    Point2D anchorPoint = drawingActive ? new Point2D(anchorX, anchorY) : null;
    Point2D movementDir = null;
    if (anchorPoint != null) {
      Point2D movement = cursor.subtract(anchorPoint);
      if (movement.magnitude() > 1e-6) {
        movementDir = movement.normalize();
      }
    }
    return snapResolver.resolve(cursor, anchorPoint, movementDir, snapVertices, segments);
  }

  private void cancelDrawing() {
    drawingActive = false;
    segmentCanvas.hidePreview();
    segmentCanvas.hideSnapIndicator();
  }

  private void rebuildSnapVertices() {
    snapVertices.clear();
    snapVertices.addAll(vertexRegistry.snapPoints());
  }

  private void updateSegmentList() {
    segmentItems.clear();
    for (Segment segment : segments) {
      SegmentMetadata metadata = segmentMetadata.get(segment);
      if (metadata == null) {
        segmentItems.add(formatSegmentFallback(segment));
      } else {
        segmentItems.add(formatSegment(segment, metadata));
      }
    }
    segmentCanvas.updateVertexLabels(
        vertexLabelCalculator.calculate(segments, segmentMetadata, vertexRegistry));
  }

  private String formatSegment(Segment segment, SegmentMetadata metadata) {
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

  private void executeCommand(Command command) {
    command.redo();
    undoStack.push(command);
    redoStack.clear();
    updateUndoRedoButtons();
  }

  private void applyTriangleOverlay() {
    if (segmentCanvas == null) {
      return;
    }
    boolean highlight = highlightToggle != null && highlightToggle.isSelected();
    if (highlight) {
      segmentCanvas.updateTriangles(currentTriangles);
      segmentCanvas.setTriangleOverlayVisible(true);
    } else {
      segmentCanvas.setTriangleOverlayVisible(false);
    }
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
        VertexRegistry.Vertex startVertex = vertexRegistry.register(startPoint);
        VertexRegistry.Vertex endVertex = vertexRegistry.register(endPoint);
        startVertexId = startVertex.id();
        endVertexId = endVertex.id();
        String segmentId = (startVertexId + endVertexId).toLowerCase(Locale.ROOT);
        segment =
            new Segment(startPoint.getX(), startPoint.getY(), endPoint.getX(), endPoint.getY(), segmentId);
      } else {
        vertexRegistry.restore(startVertexId, startPoint);
        vertexRegistry.restore(endVertexId, endPoint);
      }
      segmentMetadata.put(segment, new SegmentMetadata(startVertexId, endVertexId));
      int insert = Math.min(insertionIndex, segments.size());
      segments.add(insert, segment);
    }

    @Override
    public void undo() {
      segmentMetadata.remove(segment);
      vertexRegistry.decrement(startVertexId);
      vertexRegistry.decrement(endVertexId);
      segments.remove(segment);
    }
  }

  private final class RemoveSegmentCommand implements Command {
    private final Segment segment;
    private final SegmentMetadata metadata;
    private final int index;

    private RemoveSegmentCommand(Segment segment) {
      this.segment = segment;
      this.metadata = segmentMetadata.get(segment);
      this.index = segments.indexOf(segment);
    }

    @Override
    public void redo() {
      if (metadata != null) {
        segmentMetadata.remove(segment);
        vertexRegistry.decrement(metadata.startVertexId());
        vertexRegistry.decrement(metadata.endVertexId());
      }
      segments.remove(segment);
    }

    @Override
    public void undo() {
      if (metadata != null) {
        vertexRegistry.restore(metadata.startVertexId(), new Point2D(segment.x1(), segment.y1()));
        vertexRegistry.restore(metadata.endVertexId(), new Point2D(segment.x2(), segment.y2()));
        segmentMetadata.put(segment, metadata);
      }
      int insert = Math.min(index, segments.size());
      segments.add(insert, segment);
    }
  }
}
