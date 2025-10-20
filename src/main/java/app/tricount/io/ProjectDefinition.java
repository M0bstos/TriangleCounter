package app.tricount.io;

import app.tricount.geometry.Segment;
import java.util.List;

public record ProjectDefinition(double tolerance, List<Segment> segments) {}
