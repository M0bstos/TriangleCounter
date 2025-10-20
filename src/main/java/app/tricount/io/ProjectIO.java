package app.tricount.io;

import app.tricount.geometry.Segment;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ProjectIO {
  private final ObjectMapper mapper;

  public ProjectIO() {
    mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public ProjectDefinition load(Path path) throws IOException {
    try (var reader = Files.newBufferedReader(path)) {
      ProjectDto dto = mapper.readValue(reader, ProjectDto.class);
      double tolerance = dto.tolerance;
      if (!(tolerance > 0)) {
        tolerance = 1e-6;
      }
      List<Segment> segments = new ArrayList<>();
      Set<Long> seen = new HashSet<>();
      int counter = 0;
      if (dto.segments != null) {
        for (SegmentDto segment : dto.segments) {
          double x1 = requireFinite(segment.x1, "x1");
          double y1 = requireFinite(segment.y1, "y1");
          double x2 = requireFinite(segment.x2, "x2");
          double y2 = requireFinite(segment.y2, "y2");
          if (isZeroLength(x1, y1, x2, y2, tolerance)) {
            continue;
          }
          long key = segmentKey(x1, y1, x2, y2, tolerance);
          if (!seen.add(key)) {
            continue;
          }
          String id = segment.id;
          if (id == null || id.isBlank()) {
            id = "s" + (++counter);
          }
          segments.add(new Segment(x1, y1, x2, y2, id));
        }
      }
      return new ProjectDefinition(tolerance, List.copyOf(segments));
    }
  }

  public void save(Path path, double tolerance, List<Segment> segments) throws IOException {
    ProjectDto dto = new ProjectDto();
    dto.tolerance = tolerance;
    dto.segments = new ArrayList<>();
    for (Segment segment : segments) {
      SegmentDto entry = new SegmentDto();
      entry.id = segment.id();
      entry.x1 = segment.x1();
      entry.y1 = segment.y1();
      entry.x2 = segment.x2();
      entry.y2 = segment.y2();
      dto.segments.add(entry);
    }
    try (var writer = Files.newBufferedWriter(path)) {
      mapper.writerWithDefaultPrettyPrinter().writeValue(writer, dto);
    }
  }

  private double requireFinite(double value, String label) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(label + " must be finite");
    }
    return value;
  }

  private boolean isZeroLength(double x1, double y1, double x2, double y2, double tol) {
    double dx = x2 - x1;
    double dy = y2 - y1;
    return Math.hypot(dx, dy) <= tol;
  }

  private long segmentKey(double x1, double y1, double x2, double y2, double tol) {
    long a = coordinateKey(x1, y1, tol);
    long b = coordinateKey(x2, y2, tol);
    long lo = Math.min(a, b);
    long hi = Math.max(a, b);
    return lo ^ (hi * 1_000_000_007L);
  }

  private long coordinateKey(double x, double y, double tol) {
    double scale = tol > 0 ? 1d / tol : 1e9;
    long qx = Math.round(x * scale);
    long qy = Math.round(y * scale);
    return (qx << 32) ^ (qy & 0xffffffffL);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class ProjectDto {
    public double tolerance;
    public List<SegmentDto> segments;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class SegmentDto {
    public String id;
    public double x1;
    public double y1;
    public double x2;
    public double y2;
  }
}
