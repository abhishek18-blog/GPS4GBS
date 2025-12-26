package com.example.javagps4gbs;

import android.graphics.PointF;
import android.os.Bundle;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javagps4gbs.items.BuildingMapNavigatorView;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.*;

public class MapNavigationActivity extends AppCompatActivity {

    private BuildingMapNavigatorView mapView;
    private FirebaseFirestore db;
    private String buildingId;
    private int currentFloor = 1;

    private Button btnSelectSource, btnSelectDest, btnStartNav, btnNextNode, btnEndNav,btnFloorUp, btnFloorDown;
    private TextView tvInfo, tvFloorLabel;

    private List<String> path = new ArrayList<>();
    private int currentIndex = 0;
    private String sourceNode = null, destNode = null;
    private static final float STEP_METERS = 0.8f;

    private Map<String, Float> nodeAngles = new HashMap<>(); // For direction calculation

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_map_navigation);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        db = FirebaseFirestore.getInstance();
        buildingId = getIntent().getStringExtra("buildingId");

        mapView = findViewById(R.id.navMapView);
        tvInfo = findViewById(R.id.tvInfo);
        tvFloorLabel = findViewById(R.id.tvFloorLabel);

        btnSelectSource = findViewById(R.id.btnSelectSource);
        btnSelectDest = findViewById(R.id.btnSelectDest);
        btnStartNav = findViewById(R.id.btnStartNav);
        btnNextNode = findViewById(R.id.btnNextNode);
        btnEndNav = findViewById(R.id.btnEndNav);
        btnFloorUp = findViewById(R.id.btnFloorUp);
        btnFloorDown = findViewById(R.id.btnFloorDown);
        loadMapFromFirestore();
//----------------------------------------------------
//        btnSelectSource.setOnClickListener(v -> selectSource());
//        btnSelectDest.setOnClickListener(v -> selectDestination());
        btnStartNav.setOnClickListener(v -> startNavigation());
        btnNextNode.setOnClickListener(v -> goToNextNode());
        btnEndNav.setOnClickListener(v -> endNavigation());
        btnFloorUp.setOnClickListener(v -> changeFloor(1));
        btnFloorDown.setOnClickListener(v -> changeFloor(-1));
    }

    // === Load map from Firestore ===
    private void loadMapFromFirestore() {
        db.collection("buildings").document(buildingId)
                .collection("floors").document("floor_" + currentFloor)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        mapView.importFromMap(doc.getData());
                        Toast.makeText(this, "Loaded floor " + currentFloor, Toast.LENGTH_SHORT).show();
                       // tvFloorLabel.setText(currentFloor);
                    } else {
                        Toast.makeText(this, "No saved map found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    // === Floor switching ===
    private void changeFloor(int delta) {
        int newFloor = currentFloor + delta;
        if (newFloor < 1) {
            Toast.makeText(this, "Already on lowest floor", Toast.LENGTH_SHORT).show();
            return;
        }
        currentFloor = newFloor;
        loadMapFromFirestore();
        //
    }

    // === Select Source Node ===
    private void selectSource() {
        mapView.enableNodeSelectionMode(name -> {
            sourceNode = name;
            tvInfo.setText("Source selected: " + name);
            Toast.makeText(this, "Selected Source: " + name, Toast.LENGTH_SHORT).show();
        });
    }

    // === Select Destination Node ===
    private void selectDestination() {
        mapView.enableNodeSelectionMode(name -> {
            destNode = name;
            tvInfo.setText("Destination selected: " + name);
            Toast.makeText(this, "Selected Destination: " + name, Toast.LENGTH_SHORT).show();
        });
    }

    // === Start Navigation ===
    private void startNavigation() {
        // Get nodes from mapView instead of local variables
        String src = mapView.getSourceNode();
        String dst = mapView.getDestNode();

        if (src == null || dst == null) {
            Toast.makeText(this, "Select source and destination first", Toast.LENGTH_SHORT).show();
            return;
        }

        path = mapView.findShortestPath(src, dst);
        if (path == null || path.isEmpty()) {
            Toast.makeText(this, "No path found", Toast.LENGTH_SHORT).show();
            return;
        }

        currentIndex = 0;
        showCurrentStep();
    }


    // === Compute directions based on angle changes ===
    private void computeNodeAngles() {
        nodeAngles.clear();
        for (int i = 0; i < path.size() - 1; i++) {
            String a = path.get(i);
            String b = path.get(i + 1);
            PointF pa = mapView.nodes.get(a).attachedPoint;
            PointF pb = mapView.nodes.get(b).attachedPoint;
            float angle = (float) Math.toDegrees(Math.atan2(pb.y - pa.y, pb.x - pa.x));
            nodeAngles.put(a, angle);
        }
    }

    private void showCurrentStep() {
        if (currentIndex < path.size()) {
            String current = path.get(currentIndex);
            String next = (currentIndex < path.size() - 1) ? path.get(currentIndex + 1) : "Destination";

            mapView.highlightCurrentNode(current);

            String direction = "";
            float distanceMeters = 0;

            // Compute turn direction dynamically
            if (currentIndex > 0 && currentIndex < path.size() - 1) {
                String prev = path.get(currentIndex - 1);
                String nxt = path.get(currentIndex + 1);


                PointF p1 = mapView.getNodePosition(prev);
                PointF p2 = mapView.getNodePosition(current);
                PointF p3 = mapView.getNodePosition(nxt);

                if (p1 != null && p2 != null)
                    distanceMeters = (float) Math.hypot(p2.x - p1.x, p2.y - p1.y);


                if (p1 != null && p2 != null && p3 != null) {
                    float ang1 = (float) Math.atan2(p2.y - p1.y, p2.x - p1.x);
                    float ang2 = (float) Math.atan2(p3.y - p2.y, p3.x - p2.x);
                    float diff = (float) Math.toDegrees(ang2 - ang1);

                    // Normalize angle between -180 and +180
                    diff = (diff + 540) % 360 - 180;

                    if (Math.abs(diff) < 25) direction = "Go Straight";
                    else if (diff > 0) direction = "Turn Right";
                    else direction = "Turn left";

                    // Steps estimation (assuming 1m ≈ 1px/scale handled)
                    int steps = Math.round(distanceMeters / STEP_METERS);
                    tvInfo.setText("At: " + current + "\nNext: " + next + "\nDirection: " + (direction.isEmpty() ? "Go Straight" : direction) + "\nSteps: " + steps);

                }
            }

            // 🧩 Display friendly direction info
            tvInfo.setText("At: " + current + "" +
                    "\nNext: " + next +
                    (direction.isEmpty() ? "" : "" +
                            "\nDirection: " + direction));
        }
    }


    // === Display current step information ===
//    private void showCurrentStep() {
//        if (currentIndex < path.size()) {
//            String current = path.get(currentIndex);
//            String next = (currentIndex < path.size() - 1) ? path.get(currentIndex + 1) : "Destination";
//            mapView.highlightCurrentNode(current);
//
//            String direction = "";
//            if (currentIndex > 0 && currentIndex < path.size() - 1) {
//                String prev = path.get(currentIndex - 1);
//                Float prevAngle = nodeAngles.get(prev);
//                Float nextAngle = nodeAngles.get(current);
//
//                if (prevAngle != null && nextAngle != null) {
//                    float diff = normalizeAngle(nextAngle - prevAngle);
//                    if (Math.abs(diff) < 30) direction = "Go Straight";
//                    else if (diff > 0) direction = "Turn Left";
//                    else direction = "Turn Right";
//                }
//            }
//
//            tvInfo.setText("At: " + current + "\nNext: " + next +
//                    (direction.isEmpty() ? "" : "\nDirection: " + direction));
//        }
//    }

    // === Advance to next node ===
    private void goToNextNode() {
        if (path == null || path.isEmpty()) return;

        if (currentIndex < path.size() - 1) {
            currentIndex++;
            showCurrentStep();
        } else {
            Toast.makeText(this, "Destination reached!", Toast.LENGTH_SHORT).show();
            tvInfo.setText("You reached " + destNode + "!");
        }
    }

    // === End navigation ===
    private void endNavigation() {
        mapView.clearHighlight();
        tvInfo.setText("Navigation ended.");
        sourceNode = destNode = null;
        path.clear();
        currentIndex = 0;
    }

    private float normalizeAngle(float angle) {
        angle = (angle + 180) % 360;
        if (angle < 0) angle += 360;
        return angle - 180;
    }
}
