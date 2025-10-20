package app.tricount.ui;

import app.tricount.geometry.Segment;
import java.util.List;
import javafx.geometry.Point2D;

public final class SnapResolver {
  private final double vertexSnapTolerance;
  private final double segmentSnapTolerance;
  private final double gridSpacing;
  private final double gridSnapTolerance;
  private final double gridDistancePenalty;
  private final double segmentDirectionBonus;
  private final double segmentAlignmentThreshold;

  public SnapResolver(
      double vertexSnapTolerance,
      double segmentSnapTolerance,
      double gridSpacing,
      double gridSnapTolerance,
      double gridDistancePenalty,
      double segmentDirectionBonus,
      double segmentAlignmentThreshold) {
    this.vertexSnapTolerance = vertexSnapTolerance;
    this.segmentSnapTolerance = segmentSnapTolerance;
    this.gridSpacing = gridSpacing;
    this.gridSnapTolerance = gridSnapTolerance;
    this.gridDistancePenalty = gridDistancePenalty;
    this.segmentDirectionBonus = segmentDirectionBonus;
    this.segmentAlignmentThreshold = segmentAlignmentThreshold;
  }

  public SnapResult resolve(
      Point2D cursor,
      Point2D anchorPoint,
      Point2D movementDir,
      List<Point2D> vertexPoints,
      List<Segment> segments) {
    SnapResult best = null;
    double bestScore = Double.POSITIVE_INFINITY;

    if (vertexPoints != null) {
      for (Point2D vertex : vertexPoints) {
        if (anchorPoint != null && vertex.distance(anchorPoint) < 1e-6) {
          continue;
        }
        double distance = vertex.distance(cursor);
        if (distance > vertexSnapTolerance) {
          continue;
        }
        double base = 0.0;
        if (anchorPoint != null && vertex.distance(anchorPoint) <= vertexSnapTolerance * 0.5) {
          base -= 5.0;
        }
        double score = base + distance;
        if (score < bestScore) {
          best = new SnapResult(vertex, SegmentCanvas.SnapType.VERTEX);
          bestScore = score;
        }
      }
    }

    if (segments != null) {
      for (Segment segment : segments) {
        Point2D projection = projectOntoSegment(segment, cursor);
        if (projection == null) {
          continue;
        }
        double distance = projection.distance(cursor);
        if (distance > segmentSnapTolerance) {
          continue;
        }
        double base = 10.0;
        if (anchorPoint != null && movementDir != null) {
          Point2D candidateVec = projection.subtract(anchorPoint);
          if (candidateVec.magnitude() > 1e-6) {
            Point2D candidateDir = candidateVec.normalize();
            double dot = movementDir.dotProduct(candidateDir);
            if (dot >= segmentAlignmentThreshold) {
              base -= segmentDirectionBonus;
            }
          }
        }
        double score = base + distance;
        if (score < bestScore) {
          best = new SnapResult(projection, SegmentCanvas.SnapType.SEGMENT);
          bestScore = score;
        }
      }
    }

    Point2D gridSnap = snapToGrid(cursor, false);
    if (gridSnap != null) {
      double distance = gridSnap.distance(cursor);
      double score = 20.0 + gridDistancePenalty + distance;
      if (score < bestScore) {
        best = new SnapResult(gridSnap, SegmentCanvas.SnapType.GRID);
        bestScore = score;
      }
    }

    return best;
  }

  public Point2D snapToGrid(Point2D point, boolean force) {
    double gridX = Math.round(point.getX() / gridSpacing) * gridSpacing;
    double gridY = Math.round(point.getY() / gridSpacing) * gridSpacing;
    Point2D snapped = new Point2D(gridX, gridY);
    if (force || snapped.distance(point) <= gridSnapTolerance) {
      return snapped;
    }
    return null;
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

  public record SnapResult(Point2D point, SegmentCanvas.SnapType type) {}
}
