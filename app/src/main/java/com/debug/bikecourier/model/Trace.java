package com.debug.bikecourier.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by epastma on 2016-12-28.
 */

public class Trace implements Parcelable {

    private long id;
    private long startTimestamp;
    private long endTimestamp;
    private long duration;
    private float avgspeed;
    private float maxspeed;
    private float distance;

    public Trace() {
    }

    public Trace(float maxspeed, long id, long startTimestamp, long endTimestamp, long duration, float avgspeed) {
        this.maxspeed = maxspeed;
        this.id = id;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.duration = duration;
        this.avgspeed = avgspeed;
        this.distance = distance;

    }

    public Trace(Parcel in) {
        id = in.readLong();
        startTimestamp = in.readLong();
        endTimestamp = in.readLong();
        duration = in.readLong();
        avgspeed = in.readFloat();
        maxspeed = in.readFloat();
        distance = in.readFloat();
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(id);
        out.writeLong(startTimestamp);
        out.writeLong(endTimestamp);
        out.writeLong(duration);
        out.writeFloat(avgspeed);
        out.writeFloat(maxspeed);
        out.writeFloat(distance);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<Trace> CREATOR = new Parcelable.Creator<Trace>()
    {
        @Override
        public Trace createFromParcel(Parcel in) {
            return new Trace(in);
        }

        @Override
        public Trace[] newArray(int size) {
            return new Trace[size];
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

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public long getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(long endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public float getAvgspeed() {
        return avgspeed;
    }

    public void setAvgspeed(float avgspeed) {
        this.avgspeed = avgspeed;
    }

    public float getMaxspeed() {
        return maxspeed;
    }

    public void setMaxspeed(float maxspeed) {
        this.maxspeed = maxspeed;
    }
}