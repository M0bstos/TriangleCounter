package app.tricount.ui;

import app.tricount.geometry.Segment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Point2D;

public final class VertexLabelCalculator {
  private static final int ANGLE_SAMPLES = 72;
  private static final double MIN_ACCEPTABLE_ANGLE_DEGREES = 32.0;
  private static final double MIN_CLEARANCE_PIXELS = 14.0;
  private static final double MIN_SIN = Math.sin(Math.toRadians(6));
  private static final double STEP_PIXELS = 6.0;
  private static final double MAX_OFFSET_PIXELS = 140.0;
  private static final double ON_SEGMENT_TOLERANCE = 1e-3;

  private final double baseOffset;

  public VertexLabelCalculator(double baseOffset) {
    this.baseOffset = baseOffset;
  }

  public List<SegmentCanvas.VertexLabel> calculate(
      List<Segment> segments,
      Map<Segment, SegmentMetadata> metadata,
      VertexRegistry registry) {
    Map<String, List<IncidentEdge>> incidentEdges = collectIncidentEdges(segments, metadata, registry);
    List<SegmentCanvas.VertexLabel> labels = new ArrayList<>();
    for (VertexRegistry.Vertex vertex : registry.vertices()) {
      List<IncidentEdge> edges = incidentEdges.get(vertex.id());
      Placement placement = choosePlacement(vertex.point(), edges);
      Point2D offset = placement.direction().multiply(placement.distance());
      labels.add(new SegmentCanvas.VertexLabel(vertex.id(), vertex.x(), vertex.y(), offset.getX(), offset.getY()));
    }
    return labels;
  }

  private Map<String, List<IncidentEdge>> collectIncidentEdges(
      List<Segment> segments,
      Map<Segment, SegmentMetadata> metadata,
      VertexRegistry registry) {
    Map<String, List<IncidentEdge>> incident = new HashMap<>();
    
    for (Segment segment : segments) {
      SegmentMetadata data = metadata.get(segment);
      if (data == null) {
        continue;
      }
      VertexRegistry.Vertex start = registry.get(data.startVertexId());
      VertexRegistry.Vertex end = registry.get(data.endVertexId());
      if (start == null || end == null) {
        continue;
      }
      addEdge(incident, start, end);
      addEdge(incident, end, start);
    }

    for (VertexRegistry.Vertex vertex : registry.vertices()) {
      for (Segment segment : segments) {
        if (segmentContainsPoint(segment, vertex.point())) {
          Point2D a = new Point2D(segment.x1(), segment.y1());
          Point2D b = new Point2D(segment.x2(), segment.y2());
          addEdge(incident, vertex, a);
          addEdge(incident, vertex, b);
        }
      }
    }
    return incident;
  }

  private void addEdge(Map<String, List<IncidentEdge>> map, VertexRegistry.Vertex origin, VertexRegistry.Vertex other) {
    addEdge(map, origin, other.point());
  }

  private void addEdge(Map<String, List<IncidentEdge>> map, VertexRegistry.Vertex origin, Point2D otherPoint) {
    Point2D vector = otherPoint.subtract(origin.point());
    if (vector.magnitude() <= 1e-6) {
      return;
    }
    Point2D direction = vector.normalize();
    map.computeIfAbsent(origin.id(), k -> new ArrayList<>())
        .add(new IncidentEdge(origin.point(), otherPoint, direction));
  }

  private boolean segmentContainsPoint(Segment segment, Point2D point) {
    Point2D a = new Point2D(segment.x1(), segment.y1());
    Point2D b = new Point2D(segment.x2(), segment.y2());
    double distance = distancePointToSegment(point, a, b);
    if (distance > ON_SEGMENT_TOLERANCE) {
      return false;
    }
    double minX = Math.min(a.getX(), b.getX()) - ON_SEGMENT_TOLERANCE;
    double maxX = Math.max(a.getX(), b.getX()) + ON_SEGMENT_TOLERANCE;
    double minY = Math.min(a.getY(), b.getY()) - ON_SEGMENT_TOLERANCE;
    double maxY = Math.max(a.getY(), b.getY()) + ON_SEGMENT_TOLERANCE;
    return point.getX() >= minX && point.getX() <= maxX && point.getY() >= minY && point.getY() <= maxY;
  }

  private Placement choosePlacement(Point2D origin, List<IncidentEdge> edges) {
    if (edges == null || edges.isEmpty()) {
      return new Placement(new Point2D(0, -1), baseOffset);
    }
    List<Point2D> blocked = new ArrayList<>(edges.size());
    for (IncidentEdge edge : edges) {
      blocked.add(edge.direction());
    }

    double minAngleRad = Math.toRadians(MIN_ACCEPTABLE_ANGLE_DEGREES);
    double bestScore = Double.NEGATIVE_INFINITY;
    double bestAngle = 0d;
    boolean found = false;

    for (int i = 0; i < ANGLE_SAMPLES; i++) {
      double angle = (Math.PI * 2d * i) / ANGLE_SAMPLES;
      Point2D candidate = directionFromAngle(angle);
      double clearance = minimumAngle(candidate, blocked);
      if (clearance >= minAngleRad && clearance > bestScore) {
        bestScore = clearance;
        bestAngle = angle;
        found = true;
      }
    }

    if (!found) {
      bestScore = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < ANGLE_SAMPLES; i++) {
        double angle = (Math.PI * 2d * i) / ANGLE_SAMPLES;
        Point2D candidate = directionFromAngle(angle);
        double clearance = minimumAngle(candidate, blocked);
        if (clearance > bestScore
            || (Math.abs(clearance - bestScore) <= 1e-6 && candidate.getY() < Math.sin(bestAngle))) {
          bestScore = clearance;
          bestAngle = angle;
        }
      }
    }

    Point2D direction = directionFromAngle(bestAngle);
    double distance = computeOffsetDistance(origin, direction, edges);
    return new Placement(direction, distance);
  }

  private double minimumAngle(Point2D direction, List<Point2D> blocked) {
    double min = Double.POSITIVE_INFINITY;
    for (Point2D incident : blocked) {
      double dot = Math.max(-1d, Math.min(1d, direction.dotProduct(incident)));
      double diff = Math.acos(Math.abs(dot));
      if (diff < min) {
        min = diff;
      }
    }
    return min;
  }

  private double computeOffsetDistance(Point2D origin, Point2D direction, List<IncidentEdge> edges) {
    double distance = baseOffset;
    double max = Math.max(baseOffset, MAX_OFFSET_PIXELS);
    while (distance <= max) {
      Point2D candidate = origin.add(direction.multiply(distance));
      if (clearOfEdges(candidate, edges)) {
        return distance;
      }
      distance += STEP_PIXELS;
    }
    return MAX_OFFSET_PIXELS;
  }

  private boolean clearOfEdges(Point2D candidate, List<IncidentEdge> edges) {
    for (IncidentEdge edge : edges) {
      double dist = distancePointToSegment(candidate, edge.origin(), edge.other());
      if (dist < MIN_CLEARANCE_PIXELS) {
        return false;
      }
    }
    return true;
  }

  private double distancePointToSegment(Point2D point, Point2D a, Point2D b) {
    double dx = b.getX() - a.getX();
    double dy = b.getY() - a.getY();
    if (Math.abs(dx) < 1e-9 && Math.abs(dy) < 1e-9) {
      return point.distance(a);
    }
    double t = ((point.getX() - a.getX()) * dx + (point.getY() - a.getY()) * dy) / (dx * dx + dy * dy);
    t = Math.max(0d, Math.min(1d, t));
    double projX = a.getX() + t * dx;
    double projY = a.getY() + t * dy;
    return Math.hypot(point.getX() - projX, point.getY() - projY);
  }

  private Point2D directionFromAngle(double angle) {
    return new Point2D(Math.cos(angle), Math.sin(angle));
  }

  private record IncidentEdge(Point2D origin, Point2D other, Point2D direction) {}

  private record Placement(Point2D direction, double distance) {}
}
