package app.tricount.ui;

import app.tricount.geometry.Segment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Point2D;

public final class VertexLabelCalculator {
  private final double labelOffset;

  public VertexLabelCalculator(double labelOffset) {
    this.labelOffset = labelOffset;
  }

  public List<SegmentCanvas.VertexLabel> calculate(
      List<Segment> segments,
      Map<Segment, SegmentMetadata> segmentMetadata,
      VertexRegistry vertexRegistry) {
    Map<String, Point2D> normalSums = new HashMap<>();
    Map<String, List<Point2D>> incidentDirections = new HashMap<>();
    for (Segment segment : segments) {
      SegmentMetadata metadata = segmentMetadata.get(segment);
      if (metadata == null) {
        continue;
      }
      VertexRegistry.Vertex start = vertexRegistry.get(metadata.startVertexId());
      VertexRegistry.Vertex end = vertexRegistry.get(metadata.endVertexId());
      if (start == null || end == null) {
        continue;
      }
      Point2D startPoint = start.point();
      Point2D endPoint = end.point();
      Point2D startVec = endPoint.subtract(startPoint);
      if (startVec.magnitude() <= 1e-6) {
        continue;
      }
      Point2D startDir = startVec.normalize();
      addDirection(incidentDirections, start.id(), startDir);
      Point2D startNormal = new Point2D(-startDir.getY(), startDir.getX());
      accumulateNormal(normalSums, start.id(), startNormal);

      Point2D endVec = startPoint.subtract(endPoint);
      Point2D endDir = endVec.normalize();
      addDirection(incidentDirections, end.id(), endDir);
      Point2D endNormal = new Point2D(-endDir.getY(), endDir.getX());
      accumulateNormal(normalSums, end.id(), endNormal);
    }

    List<SegmentCanvas.VertexLabel> labels = new ArrayList<>();
    for (VertexRegistry.Vertex vertex : vertexRegistry.vertices()) {
      Point2D direction =
          chooseDirection(normalSums.get(vertex.id()), incidentDirections.get(vertex.id()));
      Point2D offset = direction.multiply(labelOffset);
      labels.add(new SegmentCanvas.VertexLabel(vertex.id(), vertex.x(), vertex.y(), offset.getX(), offset.getY()));
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

  private Point2D chooseDirection(Point2D sum, List<Point2D> incidentDirections) {
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
}
