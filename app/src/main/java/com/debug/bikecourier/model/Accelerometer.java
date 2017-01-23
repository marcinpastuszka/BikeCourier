package com.debug.bikecourier.model;

/**
 * Created by epastma on 2016-12-28.
 */

public class Accelerometer {
    private long id;
    private long id_trace;
    private float x;
    private float y;
    private float z;
    private float vector_length;
    private long timestamp;


    public Accelerometer() {
    }

    public Accelerometer(long id, long id_trace, float x, float y, float z, float vector_length, long timestamp) {
        this.id = id;
        this.id_trace = id_trace;
        this.x = x;
        this.y = y;
        this.z = z;
        this.vector_length = vector_length;
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getId_trace() {
        return id_trace;
    }

    public void setId_trace(long id_trace) {
        this.id_trace = id_trace;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getZ() {
        return z;
    }

    public void setZ(float z) {
        this.z = z;
    }

    public float getVector_length() {
        return vector_length;
    }

    public void setVector_length(float vector_length) {
        this.vector_length = vector_length;
    }
}
