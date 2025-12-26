package com.example.javagps4gbs.items;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.*;

public class BuildingMapNavigatorView extends BuildingMapView {

    private String sourceNode = null;
    private String destNode = null;
    private List<String> shortestPath = new ArrayList<>();
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String highlightedNode = null;

    // Optional external node selection listener
    public interface OnNodeSelectedListener {
        void onNodeSelected(String nodeName);
    }

    private OnNodeSelectedListener nodeSelectionListener = null;
    private boolean nodeSelectionMode = false;

    public BuildingMapNavigatorView(Context ctx, @Nullable AttributeSet attrs) {
        super(ctx, attrs);
        highlightPaint.setColor(Color.rgb(255, 140, 0)); // Orange
        highlightPaint.setStrokeWidth(10f);
        highlightPaint.setStyle(Paint.Style.STROKE);
    }

    // === Enable node selection for external Activity ===
    public void enableNodeSelectionMode(OnNodeSelectedListener listener) {
        nodeSelectionListener = listener;
        nodeSelectionMode = true;
        Toast.makeText(getContext(), "Tap a node to select it", Toast.LENGTH_SHORT).show();
    }

    // === OnTouch Logic ===
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        super.onTouchEvent(ev);

        if (ev.getAction() == MotionEvent.ACTION_UP) {
            float wx = (ev.getX() - offsetX) / scale;
            float wy = (ev.getY() - offsetY) / scale;

            Node nearest = getNearestNode(wx, wy);
            if (nearest == null) return true;

            // === If node selection mode is active ===
            if (nodeSelectionMode && nodeSelectionListener != null) {
                nodeSelectionListener.onNodeSelected(nearest.name);
                nodeSelectionMode = false;
                nodeSelectionListener = null;
                invalidate();
                return true;
            }

            // === Otherwise handle normal source/dest tapping ===
            if (sourceNode == null) {
                sourceNode = nearest.name;
                showToast("Source set: " + sourceNode);
            } else if (destNode == null) {
                destNode = nearest.name;
                showToast("Destination set: " + destNode);
                computeShortestPath();
            } else {
                // Reset for new selection
                sourceNode = nearest.name;
                destNode = null;
                shortestPath.clear();
                invalidate();
                showToast("New source set: " + sourceNode);
            }
        }

        return true;
    }


    private Node getNearestNode(float x, float y) {
        Node best = null;
        float bestDist = Float.MAX_VALUE;
        for (Map.Entry<String, Node> entry : nodes.entrySet()) {
            Node n = entry.getValue();
            float d = (float) Math.hypot(n.pos.x - x, n.pos.y - y);
            if (d < bestDist) {
                best = n;
                bestDist = d;
            }
        }
        return (bestDist < 60f) ? best : null;
    }

//dijkstra
    private void computeShortestPath() {
        if (sourceNode == null || destNode == null) return;

        Map<String, List<String>> adj = buildAdjacency();
        Map<String, Float> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();

        for (String n : nodes.keySet()) dist.put(n, Float.MAX_VALUE);
        dist.put(sourceNode, 0f);

        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparing(dist::get));
        pq.add(sourceNode);

        while (!pq.isEmpty()) {
            String u = pq.poll();
            if (u.equals(destNode)) break;

            for (String v : adj.getOrDefault(u, new ArrayList<>())) {
                float alt = dist.get(u) + distance(nodes.get(u).attachedPoint, nodes.get(v).attachedPoint);
                if (alt < dist.get(v)) {
                    dist.put(v, alt);
                    prev.put(v, u);
                    pq.add(v);
                }
            }
        }

        List<String> path = new ArrayList<>();
        for (String at = destNode; at != null; at = prev.get(at)) path.add(at);
        Collections.reverse(path);

        shortestPath = path;
        invalidate();

        if (path.size() < 2)
            showToast("No valid path found.");
        else
            showToast("Shortest path found.");
    }

//connect close nodes
    private Map<String, List<String>> buildAdjacency() {
        Map<String, List<String>> adj = new HashMap<>();

        for (String a : nodes.keySet()) {
            adj.put(a, new ArrayList<>());
            for (String b : nodes.keySet()) {
                if (!a.equals(b) && areOnSameRoad(nodes.get(a), nodes.get(b))) {
                    adj.get(a).add(b);
                }
            }
        }

        return adj;
    }

    // Check if two nodes are on the same road or intersecting roads
    private boolean areOnSameRoad(Node n1, Node n2) {
        PointF p1 = n1.attachedPoint;
        PointF p2 = n2.attachedPoint;

        for (Road r : roads) {
            if (pointToSegmentDistance(p1, r.start, r.end) < 10f &&
                    pointToSegmentDistance(p2, r.start, r.end) < 10f) {
                return true;
            }
        }

        // Also check intersections
        for (int i = 0; i < roads.size(); i++) {
            for (int j = i + 1; j < roads.size(); j++) {
                PointF inter = getIntersectionPoint(roads.get(i).start, roads.get(i).end,
                        roads.get(j).start, roads.get(j).end);
                if (inter != null &&
                        distance(p1, inter) < 10f &&
                        distance(p2, inter) < 10f) {
                    return true;
                }
            }
        }

        return false;
    }




    private void showToast(String msg) {
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }


    public void highlightCurrentNode(String nodeName) {
        highlightedNode = nodeName;
        invalidate();
    }

    public void clearHighlight() {
        highlightedNode = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Shortest path
        if (shortestPath != null && shortestPath.size() > 1) {
            for (int i = 0; i < shortestPath.size() - 1; i++) {
                Node a = nodes.get(shortestPath.get(i));
                Node b = nodes.get(shortestPath.get(i + 1));
                if (a != null && b != null) {
                    canvas.drawLine(
                            a.attachedPoint.x, a.attachedPoint.y,
                            b.attachedPoint.x, b.attachedPoint.y,
                            highlightPaint
                    );
                }
            }
        }

        // Highlighted node
        if (highlightedNode != null && nodes.containsKey(highlightedNode)) {
            Node h = nodes.get(highlightedNode);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.RED);
            p.setStyle(Paint.Style.FILL);
            canvas.drawCircle(h.pos.x, h.pos.y, 20f, p);
        }

        // Source node
        if (sourceNode != null && nodes.containsKey(sourceNode)) {
            Node s = nodes.get(sourceNode);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.GREEN);
            p.setStyle(Paint.Style.FILL);
            canvas.drawCircle(s.pos.x, s.pos.y, 18f, p);
        }

        // Destination node
        if (destNode != null && nodes.containsKey(destNode)) {
            Node d = nodes.get(destNode);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.MAGENTA);
            p.setStyle(Paint.Style.FILL);
            canvas.drawCircle(d.pos.x, d.pos.y, 18f, p);
        }
    }

    // === Public Dijkstra call (for Activity use) ===
    public List<String> findShortestPath(String source, String dest) {
        if (source == null || dest == null || !nodes.containsKey(source) || !nodes.containsKey(dest)) {
            showToast("Invalid source or destination");
            return Collections.emptyList();
        }

        Map<String, List<String>> adj = buildAdjacency();
        Map<String, Float> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();

        for (String n : nodes.keySet()) dist.put(n, Float.MAX_VALUE);
        dist.put(source, 0f);

        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparing(dist::get));
        pq.add(source);

        while (!pq.isEmpty()) {
            String u = pq.poll();
            if (u.equals(dest)) break;

            for (String v : adj.getOrDefault(u, new ArrayList<>())) {
                float alt = dist.get(u) + roadDistance(nodes.get(u).attachedPoint, nodes.get(v).attachedPoint);

//                float alt = dist.get(u) + distance(nodes.get(u).attachedPoint, nodes.get(v).attachedPoint);
                if (alt < dist.get(v)) {
                    dist.put(v, alt);
                    prev.put(v, u);
                    pq.add(v);
                }
            }
        }

//        List<String> path = new ArrayList<>();
//        for (String at = dest; at != null; at = prev.get(at)) path.add(at);
//        Collections.reverse(path);

        List<String> path = new ArrayList<>();
        for (String at = destNode; at != null; at = prev.get(at)) path.add(at);
        Collections.reverse(path);

// 🔹 Filter out junctions before using in navigation
        List<String> filteredPath = new ArrayList<>();
        for (String n : path) {
            Node node = nodes.get(n);
            if (node != null && !node.isJunction) {
                filteredPath.add(n);
            }
        }

        shortestPath = filteredPath;
        invalidate();


        shortestPath = path;
        invalidate();

        if (path.size() < 2)
            showToast("No valid path found.");
        else
            showToast("Shortest path found.");

        return path;
    }

    private float roadDistance(PointF p1, PointF p2) {
        for (BuildingMapView.Road r : roads) {
            float d1 = pointToSegmentDistance(p1, r.start, r.end);
            float d2 = pointToSegmentDistance(p2, r.start, r.end);
            if (d1 < 20f && d2 < 20f) {
                // approximate by projected distance along the road
                return (float) Math.hypot(p1.x - p2.x, p1.y - p2.y);
            }
        }
        // not on same road — treat as no direct connection
        return Float.MAX_VALUE / 2;
    }


    // === Geometry helper methods ===
    private boolean close(PointF a, PointF b) {
        return Math.hypot(a.x - b.x, a.y - b.y) < 20f; // tolerance in px
    }

    private float direction(PointF a, PointF b, PointF c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    private boolean segmentsIntersect(PointF a1, PointF a2, PointF b1, PointF b2) {
        float d1 = direction(b1, b2, a1);
        float d2 = direction(b1, b2, a2);
        float d3 = direction(a1, a2, b1);
        float d4 = direction(a1, a2, b2);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
                ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    // === Road connectivity check ===
    private boolean roadsConnected(BuildingMapView.Road r1, BuildingMapView.Road r2) {
        if (r1 == r2) return true;
        if (close(r1.start, r2.start) || close(r1.start, r2.end) ||
                close(r1.end, r2.start) || close(r1.end, r2.end))
            return true;
        return segmentsIntersect(r1.start, r1.end, r2.start, r2.end);
    }

    // === Helper to get all roads that contain a node ===
    private List<BuildingMapView.Road> getRoadsForNode(PointF p) {
        List<BuildingMapView.Road> list = new ArrayList<>();
        for (BuildingMapView.Road r : roads) {
            if (pointToSegmentDistance(p, r.start, r.end) < 20f)
                list.add(r);
        }
        return list;
    }

    // === Distance from a point to a line segment ===
    private float pointToSegmentDistance(PointF p, PointF a, PointF b) {
        float vx = b.x - a.x;
        float vy = b.y - a.y;
        float wx = p.x - a.x;
        float wy = p.y - a.y;
        float c1 = vx * wx + vy * wy;
        if (c1 <= 0) return (float) Math.hypot(p.x - a.x, p.y - a.y);
        float c2 = vx * vx + vy * vy;
        if (c2 <= c1) return (float) Math.hypot(p.x - b.x, p.y - b.y);
        float t = c1 / c2;
        float projX = a.x + t * vx;
        float projY = a.y + t * vy;
        return (float) Math.hypot(p.x - projX, p.y - projY);
    }

    public String getSourceNode() {
        return sourceNode;
    }

    public String getDestNode() {
        return destNode;
    }

    public PointF getNodePosition(String nodeName) {
        Node n = nodes.get(nodeName);
        if (n == null) return null;
        return n.attachedPoint != null ? n.attachedPoint : n.pos;
    }



}
