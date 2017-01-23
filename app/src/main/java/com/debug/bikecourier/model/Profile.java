package com.debug.bikecourier.model;

/**
 * Created by epastma on 2016-12-28.
 */

public class Profile {
    private double[] x;
    private double[] y;
    private double[] z;
    private double[] vec_xy;
    private long p_id;


    public Profile() {
    }

    public double[] getX() {
        return x;
    }

    public void setX(double[] x) {
        this.x = x;
    }

    public double[] getY() {
        return y;
    }

    public void setY(double[] y) {
        this.y = y;
    }

    public double[] getZ() {
        return z;
    }

    public void setZ(double[] z) {
        this.z = z;
    }

    public double[] getVec_xy() {
        return vec_xy;
    }

    public void setVec_xy(double[] vec_xy) {
        this.vec_xy = vec_xy;
    }

    public long getP_id() {
        return p_id;
    }

    public void setP_id(long p_id) {
        this.p_id = p_id;
    }
}
