# Triangle Counter

Desktop triangle counter core built in Java 21 with Maven. The project currently focuses on the JSON-driven engine; the JavaFX UI will be added later.

## Build

```bash
mvn clean test
```

## Run

```bash
mvn -q exec:java -Dexec.mainClass=app.tricount.App -Dexec.args="input.json"
```

Optional second argument overrides the angle tolerance used while contracting collinear vertices (default `1e-6` radians).  
The CLI echoes the vertex list, edge list, and every visible triangle with both alphabetic names and raw vertex ids so you can see exactly what the engine found.

## JSON Format

```json
{
  "tolerance": 1e-6,
  "segments": [
    {"id": "s1", "x1": 0, "y1": 0, "x2": 10, "y2": 0},
    {"id": "s2", "x1": 10, "y1": 0, "x2": 5, "y2": 8.66},
    {"id": "s3", "x1": 5, "y1": 8.66, "x2": 0, "y2": 0}
  ]
}
```

* `tolerance` – distance threshold (in the same coordinate space as your segments) used to merge nearly coincident points while planarizing. If omitted or non-positive, the reader falls back to `1e-6`.
* `segments` – list of straight segments the user drew. Each segment is identified by an `id` and two endpoint coordinates.

Zero-length or duplicate segments are ignored on import. Export uses the same structure.

## Pipeline

1. Planarize the raw segments with JTS, splitting at intersections and merging points closer than `tolerance`.
2. Contract collinear degree-two vertices so long straight runs collapse to their endpoints.
3. Walk the planar graph to enumerate interior triangular faces and the outer hull, then add any missing triangles implied by the original segments (e.g., the big outer triangle) while filtering out mixes of boundary and interior vertices.

## Tests

Key scenarios cover triangle counts, collinear contraction, and robustness against grids and crossing lines. Run `mvn test` to execute the suite.
