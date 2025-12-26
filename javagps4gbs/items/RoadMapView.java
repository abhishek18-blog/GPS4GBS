package com.example.javagps4gbs.items;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;


import java.util.*;

public class RoadMapView extends View {

    public enum Mode { NONE, DRAW_ROAD }

    private Mode currentMode = Mode.NONE;

    private final Paint roadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roadFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Roads and rooms
    private final List<Road> roads = new ArrayList<>();
    private final Map<String, PointF> rooms = new LinkedHashMap<>();

    // undo last action
    private final Stack<Runnable> undoStack = new Stack<>();

    // temporary selection when drawing a road
    private PointF tempStart = null;
    private PointF tempEnd = null;

    // virtual canvas transform (zoom & pan)
    private float scale = 1.0f;
    private final float minScale = 0.3f, maxScale = 3.0f;
    private float offsetX = 0f, offsetY = 0f;

    // touch handling
    private float lastTouchX, lastTouchY;
    private boolean isPanning = false;

    public RoadMapView(Context ctx) {
        super(ctx);
        init();
    }

    public RoadMapView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init();
    }

    private void init() {
        roadPaint.setColor(Color.DKGRAY);
        roadPaint.setStrokeWidth(10f);
        roadPaint.setStyle(Paint.Style.STROKE);

        roadFillPaint.setColor(Color.LTGRAY);
        roadFillPaint.setStyle(Paint.Style.FILL);

        markerPaint.setColor(Color.BLUE);
        markerPaint.setStyle(Paint.Style.FILL);

        roomPaint.setColor(Color.parseColor("#0D47A1"));
        roomPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(28f);
    }

    // ----------------- Drawing -----------------
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // apply pan/zoom
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        // draw roads
        for (Road r : roads) {
            canvas.drawLine(r.start.x, r.start.y, r.end.x, r.end.y, roadPaint);
        }

        // draw rooms (small circles)
        for (Map.Entry<String, PointF> e : rooms.entrySet()) {
            canvas.drawCircle(e.getValue().x, e.getValue().y, 14f, roomPaint);
            canvas.drawText(e.getKey(), e.getValue().x + 20f, e.getValue().y + 8f, textPaint);
        }

        // draw temporary markers for start/end if in drawing mode
        if (tempStart != null) {
            canvas.drawCircle(tempStart.x, tempStart.y, 12f, markerPaint);
        }
        if (tempEnd != null) {
            canvas.drawCircle(tempEnd.x, tempEnd.y, 12f, markerPaint);
            // optionally preview the line
            if (tempStart != null) {
                Paint preview = new Paint(roadPaint);
                preview.setColor(Color.GRAY);
                preview.setPathEffect(new DashPathEffect(new float[]{15, 10}, 0));
                canvas.drawLine(tempStart.x, tempStart.y, tempEnd.x, tempEnd.y, preview);
            }
        }

        canvas.restore();
    }

    // ----------------- Touch handling -----------------
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // convert screen -> canvas coords (taking pan & zoom into account)
        float cx = (event.getX() - offsetX) / scale;
        float cy = (event.getY() - offsetY) / scale;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                // if in draw mode, set temp start or end
                if (currentMode == Mode.DRAW_ROAD) {
                    if (tempStart == null) {
                        tempStart = new PointF(cx, cy);
                    } else {
                        tempEnd = new PointF(cx, cy);
                        // when both points present, prompt for length and finalize
                        promptForLengthAndFinalize(tempStart, tempEnd);
                    }
                    invalidate();
                    return true;
                } else {
                    // start panning
                    isPanning = true;
                    return true;
                }

            case MotionEvent.ACTION_MOVE:
                if (isPanning) {
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;
                    offsetX += dx;
                    offsetY += dy;
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isPanning = false;
                break;
        }

        return super.onTouchEvent(event);
    }

    // ----------------- Methods to finalize roads -----------------
    private void promptForLengthAndFinalize(PointF start, PointF end) {
        // show input dialog
        EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Length in meters (e.g., 12.5)");

        new AlertDialog.Builder(getContext())
                .setTitle("Enter path length (meters)")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    String s = input.getText().toString().trim();
                    double len;
                    try {
                        len = Double.parseDouble(s);
                    } catch (NumberFormatException e) {
                        // fallback: compute Euclidean distance
                        len = distanceMeters(start, end);
                    }
                    addRoadInternal(new Road(start, end, len));
                    // clear temp markers
                    tempStart = null;
                    tempEnd = null;
                    invalidate();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // cancel: drop the temporary end and keep start so user may try again
                    tempEnd = null;
                    invalidate();
                })
                .show();
    }

    private double distanceMeters(PointF a, PointF b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        // The view coordinate system is arbitrary; treat 1 unit = 1 meter for now (you can scale later)
        return Math.hypot(dx, dy);
    }

    private void addRoadInternal(Road road) {
        roads.add(road);
        // push undo action
        undoStack.push(() -> {
            // remove the road with matching id
            for (Iterator<Road> it = roads.iterator(); it.hasNext(); ) {
                Road r = it.next();
                if (r.id.equals(road.id)) {
                    it.remove();
                    break;
                }
            }
            invalidate();
        });
    }

    // ----------------- Public helpers -----------------
    public void setMode(Mode m) {
        currentMode = m;
    }

    public List<Road> getRoads() {
        return new ArrayList<>(roads);
    }

    public Map<String, PointF> getRooms() {
        return new LinkedHashMap<>(rooms);
    }

    public void undoLastAction() {
        if (!undoStack.isEmpty()) {
            Runnable r = undoStack.pop();
            r.run();
            invalidate();
        }
    }

    public void clearAll() {
        List<Road> copy = new ArrayList<>(roads);
        Map<String, PointF> roomsCopy = new LinkedHashMap<>(rooms);
        roads.clear();
        rooms.clear();
        undoStack.push(() -> {
            roads.addAll(copy);
            rooms.putAll(roomsCopy);
            invalidate();
        });
        invalidate();
    }

    // zoom / pan API for on-screen controls:
    public void zoomIn() {
        scale = Math.min(maxScale, scale + 0.2f);
        invalidate();
    }

    public void zoomOut() {
        scale = Math.max(minScale, scale - 0.2f);
        invalidate();
    }

    /**
     * panBy moves the viewport; dx/dy are in view pixels, not scaled units.
     * Positive dx moves content to right (i.e. offsetX increases).
     */
    public void panBy(float dx, float dy) {
        offsetX += dx;
        offsetY += dy;
        invalidate();
    }

    // parse roads loaded from Firestore and replace current
    public void setRoadsAndRooms(List<Road> rds, Map<String, PointF> rms) {
        roads.clear();
        roads.addAll(rds);
        rooms.clear();
        rooms.putAll(rms);
        undoStack.clear();
        invalidate();
    }
}
