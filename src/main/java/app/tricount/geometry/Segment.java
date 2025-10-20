package app.tricount.geometry;

import java.util.Objects;

public record Segment(double x1, double y1, double x2, double y2, String id) {
  public Segment {
    Objects.requireNonNull(id, "id");
  }
}
