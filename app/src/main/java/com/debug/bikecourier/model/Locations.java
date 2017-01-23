package com.debug.bikecourier.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

/**
 * Created by epastma on 2016-12-28.
 */

public class Locations implements Parcelable {
    private long id;
    private long id_trace;
    private long timestamp;
    private double latitude;
    private double longitude;
    private float speed;
    private int type;
    private long diffTime;
    private float distance;

    public Locations() {
    }

    public Locations(float distance, long id, long id_trace, long timestamp, double latitude, double longitude, float speed, int type, long diffTime) {
        this.distance = distance;
        this.id = id;
        this.id_trace = id_trace;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.type = type;
        this.diffTime = diffTime;
    }

    public Locations(Parcel in) {
        id = in.readLong();
        id_trace = in.readLong();
        timestamp = in.readLong();
        diffTime = in.readLong();
        latitude = in.readDouble();
        longitude = in.readDouble();
        speed = in.readFloat();
        distance = in.readFloat();
        type = in.readInt();
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(id);
        out.writeLong(id_trace);
        out.writeLong(timestamp);
        out.writeLong(diffTime);
        out.writeDouble(latitude);
        out.writeDouble(longitude);
        out.writeFloat(speed);
        out.writeFloat(distance);
        out.writeInt(type);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<Locations> CREATOR = new Parcelable.Creator<Locations>()
    {
        @Override
        public Locations createFromParcel(Parcel in) {
            return new Locations(in);
        }

        @Override
        public Locations[] newArray(int size) {
            return new Locations[size];
        }
    };

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
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

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getDiffTime() {
        return diffTime;
    }

    public void setDiffTime(long diffTime) {
        this.diffTime = diffTime;
    }
}
