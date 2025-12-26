package com.example.javagps4gbs;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputFilter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javagps4gbs.items.BuildingMapView;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MapEditorActivity extends AppCompatActivity {

    private BuildingMapView mapView;
    private FirebaseFirestore db;
    private String buildingId = "";
    private int currentFloor = 1;

    private TextView tvFloorLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_map_editor);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        db = FirebaseFirestore.getInstance();
        mapView = findViewById(R.id.mapView);
        tvFloorLabel = findViewById(R.id.tvFloorLabel);

        promptBuildingId();

        // === Buttons ===
        Button btnForward = findViewById(R.id.btnForward);
        Button btnLeft = findViewById(R.id.btnLeft);
        Button btnRight = findViewById(R.id.btnRight);
        Button btnAddNode = findViewById(R.id.btnAddNode);
        Button btnSetStart = findViewById(R.id.btnSetStart);
        Button btnDelete = findViewById(R.id.btnDelete);
        Button btnZoomIn = findViewById(R.id.btnZoomIn);
        Button btnZoomOut = findViewById(R.id.btnZoomOut);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnAddFloor = findViewById(R.id.btnAddFloor);
        Button btnPrevFloor = findViewById(R.id.btnPrevFloor);
        Button btnNextFloor = findViewById(R.id.btnNextFloor);

        // === Road Controls ===
        btnForward.setOnClickListener(v -> mapView.forwardStep());
        btnLeft.setOnClickListener(v -> mapView.turnBy(-90f));
        btnRight.setOnClickListener(v -> mapView.turnBy(90f));

        // === Node ===
        btnAddNode.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setHint("Room name (e.g. 101)");
            new AlertDialog.Builder(this)
                    .setTitle("Add Room Node")
                    .setView(input)
                    .setPositiveButton("OK", (d, w) -> {
                        String name = input.getText().toString().trim();
                        if (!name.isEmpty()) {
                            mapView.enableAddNodeMode(name);
                            Toast.makeText(this, "Tap on path to place node '" + name + "'", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // === Cursor reposition ===
        btnSetStart.setOnClickListener(v -> {
            mapView.enableSetCursorMode();
            Toast.makeText(this, "Tap anywhere to move cursor", Toast.LENGTH_SHORT).show();
        });

        // === Delete / Undo ===
        btnDelete.setOnClickListener(v -> mapView.deleteLast());

        // === Zoom ===
        btnZoomIn.setOnClickListener(v -> mapView.focusedZoom(1.25f));
        btnZoomOut.setOnClickListener(v -> mapView.focusedZoom(0.8f));

        // === Save ===
        btnSave.setOnClickListener(v -> {
            if (buildingId.isEmpty()) {
                promptBuildingId();
            } else {
                saveMapToFirestore();
            }
        });

        // === Floor navigation ===
        btnAddFloor.setOnClickListener(v -> addNewFloor());
        btnPrevFloor.setOnClickListener(v -> switchFloor(currentFloor - 1));
        btnNextFloor.setOnClickListener(v -> switchFloor(currentFloor + 1));
    }

    // === Prompt for building ID ===
    private void promptBuildingId() {
        EditText idInput = new EditText(this);
        idInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        idInput.setHint("4-digit ID");
        new AlertDialog.Builder(this)
                .setTitle("Enter 4-digit Building ID")
                .setView(idInput)
                .setPositiveButton("OK", (d, w) -> {
                    String id = idInput.getText().toString().trim();
                    if (id.length() == 4) {
                        buildingId = id;
                        saveMapToFirestore();
                    } else {
                        Toast.makeText(this, "ID must be 4 digits", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // === Save to Firestore ===
    private void saveMapToFirestore() {
        mapView.rebuildGraphConnections();
        if (buildingId.isEmpty()) {
            Toast.makeText(this, "No building ID!", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> mapData = mapView.exportToMap();
        db.collection("buildings")
                .document(buildingId)
                .collection("floors")
                .document("floor_" + currentFloor)
                .set(mapData)
                .addOnSuccessListener(a -> Toast.makeText(this, "Saved Floor " + currentFloor, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }


    private void addNewFloor() {
        if (!buildingId.isEmpty()) {
            saveMapToFirestore(); // ✅ Save current floor before creating new one
        }

        currentFloor++;
        mapView.importFromMap(new HashMap<>()); // clear current drawing
        tvFloorLabel.setText("Floor " + currentFloor);
        Toast.makeText(this, "Added Floor " + currentFloor, Toast.LENGTH_SHORT).show();
    }

    // === Floor controls ===
//    private void addNewFloor() {
//        currentFloor++;
//        mapView.importFromMap(new HashMap<>()); // clear drawing
//        tvFloorLabel.setText("Floor " + currentFloor);
//        Toast.makeText(this, "Added Floor " + currentFloor, Toast.LENGTH_SHORT).show();
//    }

    private void switchFloor(int floor) {
        if (floor < 1) {
            Toast.makeText(this, "No lower floors", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ Save current floor before switching
        if (!buildingId.isEmpty()) {
            saveMapToFirestore();
        }

        currentFloor = floor;
        tvFloorLabel.setText("Floor " + currentFloor);

        if (buildingId.isEmpty()) {
            Toast.makeText(this, "Enter ID first", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("buildings")
                .document(buildingId)
                .collection("floors")
                .document("floor_" + currentFloor)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        mapView.importFromMap(doc.getData());
                        Toast.makeText(this, "Loaded Floor " + currentFloor, Toast.LENGTH_SHORT).show();
                    } else {
                        mapView.importFromMap(new HashMap<>());
                        Toast.makeText(this, "New Floor " + currentFloor, Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
