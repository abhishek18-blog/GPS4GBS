package com.example.javagps4gbs.items;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;

import java.util.*;

public class BuildingMapView extends View {


    public final Paint roadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint connectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint nodeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);


    public float scale = 1f;
    public float offsetX = 0f, offsetY = 0f;
    public final ScaleGestureDetector scaleDetector;


    public final PointF cursor = new PointF(500f, 500f);
    public float cursorAngle = 0f;
    public static final float STEP_PX = 20f;


    public final List<Road> roads = new ArrayList<>();
    public final LinkedHashMap<String, Node> nodes = new LinkedHashMap<>();
    public final Stack<Action> actions = new Stack<>();

    private boolean nextNodeLeft = true;
    private boolean cursorManuallyMoved = false;


    public enum Mode { NONE, ADD_NODE, SET_CURSOR }
    private Mode mode = Mode.NONE;
    private String pendingNodeName = null;

    public BuildingMapView(Context ctx, @Nullable AttributeSet attrs) {
        super(ctx, attrs);
        roadPaint.setColor(Color.DKGRAY);
        roadPaint.setStrokeWidth(8f);

        connectorPaint.setColor(Color.LTGRAY);
        connectorPaint.setStrokeWidth(4f);

        nodePaint.setColor(Color.BLUE);
        nodePaint.setStyle(Paint.Style.FILL);

        nodeTextPaint.setColor(Color.BLACK);
        nodeTextPaint.setTextSize(28f);

        cursorPaint.setColor(Color.rgb(0, 150, 0));
        cursorPaint.setStyle(Paint.Style.FILL);

        labelPaint.setColor(Color.BLACK);
        labelPaint.setTextSize(28f);
        labelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        scaleDetector = new ScaleGestureDetector(ctx, new ScaleListener());
    }

    // === Forward step (fixed logic) ===
    public void forwardStep() {
        PointF start = new PointF(cursor.x, cursor.y);
        float rad = (float) Math.toRadians(cursorAngle);
        float nx = start.x + (float) (STEP_PX * Math.cos(rad));
        float ny = start.y + (float) (STEP_PX * Math.sin(rad));
        PointF end = new PointF(nx, ny);

        boolean continueExisting = false;

        if (!roads.isEmpty() && !cursorManuallyMoved) {
            Road last = roads.get(roads.size() - 1);
            float distToLastEnd = distance(cursor, last.end);
            if (distToLastEnd < 2f && Math.abs(normalizeAngle(last.angle - cursorAngle)) < 0.01f) {
                continueExisting = true;
            }
        }

        if (continueExisting) {
            Road last = roads.get(roads.size() - 1);
            last.end = end;
            last.lengthMeters += 1;
            actions.push(new Action(Action.Type.ROAD_EXTENDED, last));
        } else {
            Road r = new Road(start, end, 1, cursorAngle);
            roads.add(r);
            actions.push(new Action(Action.Type.ROAD_ADDED, r));
        }

        cursor.set(end.x, end.y);
        cursorManuallyMoved = false;
        invalidate();
    }

    // === Turn ===
    public void turnBy(float deg) {
        cursorAngle = (cursorAngle + deg) % 360f;
        if (cursorAngle < 0) cursorAngle += 360f;
        invalidate();
    }

    // === Modes ===
    public void enableAddNodeMode(String name) {
        pendingNodeName = name;
        mode = Mode.ADD_NODE;
    }

    public void enableSetCursorMode() {
        mode = Mode.SET_CURSOR;
    }

    // === Delete ===
    public void deleteLast() {
        if (actions.isEmpty()) return;
        Action a = actions.pop();

        switch (a.type) {
            case NODE_ADDED:
                Node n = (Node) a.payload;
                nodes.remove(n.name);
                nextNodeLeft = !nextNodeLeft;
                break;

            case ROAD_ADDED:
                roads.remove(a.payload);
                setCursorToLastEnd();
                break;

            case ROAD_EXTENDED:
                Road r = (Road) a.payload;
                if (r.lengthMeters > 1) {
                    float rad = (float) Math.toRadians(r.angle);
                    r.end.x -= (float) (STEP_PX * Math.cos(rad));
                    r.end.y -= (float) (STEP_PX * Math.sin(rad));
                    r.lengthMeters -= 1;
                } else {
                    roads.remove(r);
                }
                setCursorToLastEnd();
                break;

            default:
                break;
        }
        invalidate();
    }


    private void setCursorToLastEnd() {
        if (!roads.isEmpty()) {
            Road last = roads.get(roads.size() - 1);
            cursor.set(last.end.x, last.end.y);
            cursorAngle = last.angle;
        } else {
            cursor.set(500, 500);
            cursorAngle = 0;
        }
    }

    // === Touch ===
    private float lastX, lastY;
    private boolean panning = false;

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        scaleDetector.onTouchEvent(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = ev.getX();
                lastY = ev.getY();
                panning = true;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (panning && ev.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                    offsetX += ev.getX() - lastX;
                    offsetY += ev.getY() - lastY;
                    lastX = ev.getX();
                    lastY = ev.getY();
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                panning = false;
                float dx = Math.abs(ev.getX() - lastX);
                float dy = Math.abs(ev.getY() - lastY);
                if (dx < 12 && dy < 12) {
                    float wx = (ev.getX() - offsetX) / scale;
                    float wy = (ev.getY() - offsetY) / scale;
                    handleTap(wx, wy);
                }
                return true;

            default:
                return super.onTouchEvent(ev);
        }
    }


    private void handleTap(float wx, float wy) {
        // addingnode
        if (mode == Mode.ADD_NODE && pendingNodeName != null) {
            RoadProjection proj = findNearestRoadProjection(wx, wy);
            if (proj == null) {
                Toast.makeText(getContext(), "Tap near a road", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean sideLeft = nextNodeLeft;
            nextNodeLeft = !nextNodeLeft;

            float nodeOffset = 40f;
            float vx = proj.segment.end.x - proj.segment.start.x;
            float vy = proj.segment.end.y - proj.segment.start.y;
            float len = (float) Math.hypot(vx, vy);
            if (len == 0) len = 1f;
            float nx = -vy / len;
            float ny = vx / len;
            if (!sideLeft) { nx = -nx; ny = -ny; }

            PointF nodePos = new PointF(proj.projPoint.x + nx * nodeOffset, proj.projPoint.y + ny * nodeOffset);
            Node node = new Node(pendingNodeName, nodePos, proj.projPoint, sideLeft);
            nodes.put(node.name, node);
            actions.push(new Action(Action.Type.NODE_ADDED, node));

            pendingNodeName = null;
            mode = Mode.NONE;
            invalidate();
            return;
        }

        // === SET CURSOR MODE ===
        if (mode == Mode.SET_CURSOR) {
            setCursorToSnap(wx, wy);
            mode = Mode.NONE;
            Toast.makeText(getContext(), "Cursor moved", Toast.LENGTH_SHORT).show();
            return;
        }

        // Default tap -> snap and move cursor
        setCursorToSnap(wx, wy);
    }

    // snaopp
    private void setCursorToSnap(float wx, float wy) {
        RoadProjection proj = findNearestRoadProjection(wx, wy);
        if (proj != null && proj.distance <= 20f) {
            cursor.set(proj.projPoint.x, proj.projPoint.y);
            cursorAngle = proj.segment.angle;
        } else {
            cursor.set(wx, wy);
        }
        cursorManuallyMoved = true;
        invalidate();
    }

    // === Drawing ===
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        for (Road r : roads) {
            canvas.drawLine(r.start.x, r.start.y, r.end.x, r.end.y, roadPaint);
            float mx = (r.start.x + r.end.x) / 2f;
            float my = (r.start.y + r.end.y) / 2f;
            canvas.drawText(r.lengthMeters + " m", mx + 10, my - 8, labelPaint);
        }



        for (Node n : nodes.values()) {
            if (n.isJunction) {
                Paint junctionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                junctionPaint.setColor(Color.LTGRAY); // light gray for junctions
                canvas.drawCircle(n.pos.x, n.pos.y, 8f, junctionPaint);
            } else {
                canvas.drawLine(n.pos.x, n.pos.y, n.attachedPoint.x, n.attachedPoint.y, connectorPaint);
                canvas.drawCircle(n.pos.x, n.pos.y, 14f, nodePaint);
                canvas.drawText(n.name, n.pos.x + 20f, n.pos.y + 6f, nodeTextPaint);
            }
        }


        drawArrow(canvas, cursor.x, cursor.y, cursorAngle, 30f);
        canvas.restore();
    }

    private void drawArrow(Canvas c, float x, float y, float ang, float size) {
        Path p = new Path();
        p.moveTo(size, 0);
        p.lineTo(0, -size / 2);
        p.lineTo(0, size / 2);
        p.close();
        c.save();
        c.translate(x, y);
        c.rotate(ang);
        c.drawPath(p, cursorPaint);
        c.restore();
    }

    private static class RoadProjection {
        Road segment; PointF projPoint; float distance;
        RoadProjection(Road s, PointF p, float d){segment=s;projPoint=p;distance=d;}
    }

    private RoadProjection findNearestRoadProjection(float x, float y) {
        if (roads.isEmpty()) return null;
        RoadProjection best = null;
        PointF p = new PointF(x, y);
        for (Road r : roads) {
            PointF proj = nearestPointOnSegment(r.start, r.end, p);
            float d = distance(p, proj);
            if (best == null || d < best.distance) best = new RoadProjection(r, proj, d);
        }
        return best;
    }

    private static PointF nearestPointOnSegment(PointF a, PointF b, PointF p) {
        float vx=b.x-a.x, vy=b.y-a.y;
        float wx=p.x-a.x, wy=p.y-a.y;
        float c1=vx*wx+vy*wy, c2=vx*vx+vy*vy;
        float t=(c2==0)?0:c1/c2;
        t=Math.max(0,Math.min(1,t));
        return new PointF(a.x+t*vx,a.y+t*vy);
    }

    static float distance(PointF a, PointF b){return (float)Math.hypot(a.x-b.x,a.y-b.y);}
    private static float normalizeAngle(float a){float ang=(a+180)%360;if(ang<0)ang+=360;return ang-180;}

    // === Zoom ===
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override public boolean onScale(ScaleGestureDetector d){
            float prev=scale; scale*=d.getScaleFactor();
            scale=Math.max(0.3f,Math.min(3f,scale));
            float fx=d.getFocusX(), fy=d.getFocusY();
            float wxB=(fx-offsetX)/prev, wyB=(fy-offsetY)/prev;
            float wxA=(fx-offsetX)/scale, wyA=(fy-offsetY)/scale;
            offsetX+=(wxA-wxB)*scale; offsetY+=(wyA-wyB)*scale;
            invalidate(); return true;
        }
    }

    // === Data classes ===
    public static class Road {
        PointF start, end; int lengthMeters; float angle;
        Road(PointF s, PointF e, int m, float a){start=s;end=e;lengthMeters=m;angle=a;}
    }



    public static class Node {
        String name;
        PointF pos;
        public PointF attachedPoint;
        boolean sideLeft;
        boolean isJunction; // <-- add this

        Node(String n, PointF p, PointF a, boolean sl) {
            name = n;
            pos = p;
            attachedPoint = a;
            sideLeft = sl;
            isJunction = n.startsWith("J"); // auto-detect by name
        }
    }


    private static class Action {
        enum Type {ROAD_ADDED, ROAD_EXTENDED, NODE_ADDED}
        Type type; Object payload;
        Action(Type t,Object p){type=t;payload=p;}
    }

    // Zoom
    public void focusedZoom(float factor){
        scale*=factor; scale=Math.max(0.3f,Math.min(3f,scale)); invalidate();
    }

    // epxort map to firwetsore
    public Map<String, Object> exportToMap() {
        Map<String, Object> data = new HashMap<>();

        // Save roads
        List<Map<String, Object>> roadList = new ArrayList<>();
        for (Road r : roads) {
            Map<String, Object> rm = new HashMap<>();
            rm.put("startX", r.start.x);
            rm.put("startY", r.start.y);
            rm.put("endX", r.end.x);
            rm.put("endY", r.end.y);
            rm.put("lengthMeters", r.lengthMeters);
            rm.put("angle", r.angle);
            roadList.add(rm);
        }
        data.put("roads", roadList);

        // Save nodes
        List<Map<String, Object>> nodeList = new ArrayList<>();
        for (Node n : nodes.values()) {
            Map<String, Object> nm = new HashMap<>();
            nm.put("name", n.name);
            nm.put("posX", n.pos.x);
            nm.put("posY", n.pos.y);
            nm.put("attachedX", n.attachedPoint.x);
            nm.put("attachedY", n.attachedPoint.y);
            nm.put("sideLeft", n.sideLeft);
            nodeList.add(nm);
        }
        data.put("nodes", nodeList);

        return data;
    }

    //  Firestore import amipp
    @SuppressWarnings("unchecked")
    public void importFromMap(Map<String, Object> data) {
        roads.clear();
        nodes.clear();
        actions.clear();

        if (data == null) {
            invalidate();
            return;
        }

        if (data.containsKey("roads")) {
            List<Map<String, Object>> roadList = (List<Map<String, Object>>) data.get("roads");
            for (Map<String, Object> rm : roadList) {
                float sx = ((Number) rm.get("startX")).floatValue();
                float sy = ((Number) rm.get("startY")).floatValue();
                float ex = ((Number) rm.get("endX")).floatValue();
                float ey = ((Number) rm.get("endY")).floatValue();
                int len = ((Number) rm.get("lengthMeters")).intValue();
                float ang = ((Number) rm.get("angle")).floatValue();
                roads.add(new Road(new PointF(sx, sy), new PointF(ex, ey), len, ang));
            }
        }

        if (data.containsKey("nodes")) {
            List<Map<String, Object>> nodeList = (List<Map<String, Object>>) data.get("nodes");
            for (Map<String, Object> nm : nodeList) {
                String name = (String) nm.get("name");
                float px = ((Number) nm.get("posX")).floatValue();
                float py = ((Number) nm.get("posY")).floatValue();
                float ax = ((Number) nm.get("attachedX")).floatValue();
                float ay = ((Number) nm.get("attachedY")).floatValue();
                boolean sideLeft = (boolean) nm.get("sideLeft");
                nodes.put(name, new Node(name, new PointF(px, py), new PointF(ax, ay), sideLeft));
            }
        }

        // reset cursor
        setCursorToLastEnd();
        invalidate();
    }

    public void rebuildGraphConnections() {
        List<Road> allRoads = new ArrayList<>(roads);

        for (int i = 0; i < allRoads.size(); i++) {
            for (int j = i + 1; j < allRoads.size(); j++) {
                Road r1 = allRoads.get(i);
                Road r2 = allRoads.get(j);

                PointF intersection = getIntersectionPoint(r1.start, r1.end, r2.start, r2.end);
                if (intersection != null) {

                    // Check if we already have a node nearby
                    Node existing = getNearestNode(intersection.x, intersection.y, 10f);
                    if (existing == null) {
                        String id = "J" + (nodes.size() + 1);
                        Node node = new Node(id, intersection, intersection, false);
                        nodes.put(id, node);
                    }
                }
            }
        }


        for (Road r : allRoads) {
            for (Node n : nodes.values()) {
                if (pointToSegmentDistance(n.attachedPoint, r.start, r.end) < 8f) {
                    // Snap node onto the road
                    n.attachedPoint = nearestPointOnSegment(r.start, r.end, n.attachedPoint);
                }
            }
        }
    }

    private Node getNearestNode(float x, float y, float maxDist) {
        Node best = null;
        float bestDist = Float.MAX_VALUE;

        for (Node n : nodes.values()) {
            float d = (float) Math.hypot(n.pos.x - x, n.pos.y - y);
            if (d < bestDist) {
                best = n;
                bestDist = d;
            }
        }
        return (bestDist < maxDist) ? best : null;
    }



    // intersection get
    PointF getIntersectionPoint(PointF A, PointF B, PointF C, PointF D) {
        float a1 = B.y - A.y;
        float b1 = A.x - B.x;
        float c1 = a1 * A.x + b1 * A.y;

        float a2 = D.y - C.y;
        float b2 = C.x - D.x;
        float c2 = a2 * C.x + b2 * C.y;

        float delta = a1 * b2 - a2 * b1;
        if (Math.abs(delta) < 1e-5) return null; // parallel or collinear

        float x = (b2 * c1 - b1 * c2) / delta;
        float y = (a1 * c2 - a2 * c1) / delta;

        // check if the intersection lies within both segments
        if (pointOnSegment(x, y, A, B) && pointOnSegment(x, y, C, D)) {
            return new PointF(x, y);
        }
        return null;
    }

    private boolean pointOnSegment(float x, float y, PointF a, PointF b) {
        return x >= Math.min(a.x, b.x) - 1 && x <= Math.max(a.x, b.x) + 1 &&
                y >= Math.min(a.y, b.y) - 1 && y <= Math.max(a.y, b.y) + 1;
    }

    private float pointToSegmentDistance(PointF p, PointF a, PointF b) {
        // Vector AB
        float vx = b.x - a.x;
        float vy = b.y - a.y;

        // Vector AP
        float wx = p.x - a.x;
        float wy = p.y - a.y;

        // Projection of AP onto AB
        float c1 = vx * wx + vy * wy;
        float c2 = vx * vx + vy * vy;

        float t = (c2 == 0) ? 0 : c1 / c2;
        t = Math.max(0, Math.min(1, t)); // clamp to segment

        // Closest point on the segment
        float closestX = a.x + t * vx;
        float closestY = a.y + t * vy;

        // Distance from P to that closest point
        return (float) Math.hypot(p.x - closestX, p.y - closestY);
    }



}
