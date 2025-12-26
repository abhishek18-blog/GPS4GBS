package com.example.javagps4gbs;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Random;

public class HomeActivity extends AppCompatActivity {

    private EditText etBuildingId;
    private Button btnCreateNew, btnLoadMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        etBuildingId = findViewById(R.id.etBuildingId);
        btnCreateNew = findViewById(R.id.btnCreateNew);
        btnLoadMap = findViewById(R.id.btnLoadMap);

        btnCreateNew.setOnClickListener(v -> {
            String id = generateBuildingId();
            openEditor(id);
        });

        btnLoadMap.setOnClickListener(v -> {
            String id = etBuildingId.getText().toString().trim();
            if (id.length() == 4) {
//                openEditor(id);
                Intent i = new Intent(this, MapNavigationActivity.class);
                i.putExtra("buildingId", id);
                startActivity(i);

            } else {
                Toast.makeText(this, "Enter a valid 4-digit ID", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openEditor(String id) {
        Intent i = new Intent(this, MapEditorActivity.class);
        i.putExtra("buildingId", id);
        startActivity(i);
    }

    private String generateBuildingId() {
        return String.format("%04d", new Random().nextInt(10000));
    }
}
