package com.example.javagps4gbs.items;

import android.graphics.PointF;

import java.util.UUID;

public class Road {
    public String id;
    public PointF start;
    public PointF end;
    public double lengthMeters;

    public Road() {
        // default constructor for Firestore
    }

    public Road(PointF start, PointF end, double lengthMeters) {
        this.id = UUID.randomUUID().toString();
        this.start = start;
        this.end = end;
        this.lengthMeters = lengthMeters;
    }
}
