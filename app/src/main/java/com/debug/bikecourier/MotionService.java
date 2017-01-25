package com.debug.bikecourier;

/**
 * Created by epastma on 2016-12-29.
 */

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.debug.bikecourier.model.Accelerometer;
import com.debug.bikecourier.model.Locations;
import com.debug.bikecourier.model.Profile;
import com.debug.bikecourier.model.Trace;
import com.debug.bikecourier.utils.GeoTrackFilter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import com.kircherelectronics.accelerationexplorer.filter.ImuLaKfQuaternion;
import com.kircherelectronics.accelerationexplorer.filter.ImuLinearAccelerationInterface;

import java.util.ArrayList;
import java.util.Collections;

public class MotionService extends Service implements
        SensorEventListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = "MotionService";
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    private SharedPreferences preferences;

    private final IBinder mBinder = new LocalBinder();
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    private Sensor mMagnetic;
    private Sensor mGravity;

    private boolean mSensorStarted = false;
    private boolean mLocationStarted = false;
    private boolean mLocationConnected = true;
    private boolean mTrackingStarted = false;
    private boolean mNotificationShowed = false;

    NotificationManager mNotifyMgr;
    private int NOTIFICATION_ID = 1;

    private static final int UNKNOWN = 0;
    private static final int ON_BIKE = 1;

    private ArrayList<Profile> profiles;

    protected GoogleApiClient mGoogleApiClient;
    protected LocationRequest mLocationRequest;
    //Kalman filters
    private GeoTrackFilter geoTrackFilter;
    private ImuLinearAccelerationInterface imuLinearAcceleration;

    private volatile float[] acceleration = new float[3];
    private volatile float[] linearAcceleration = new float[3];
    private float[] gravity = new float[3];
    private float[] magnetic = new float[3];
    private float[] rotation = new float[3];
    private boolean dataReady = false;

    private long id = -1;

    private boolean detectionReady = false;
    private int detectionHelper = 0;

    private int activityType = 0;
    private ArrayList<LatLng> coordinateList;
    private Location lastLocation;
    private long lastLocationEntry = -1;
    private boolean mLastLocation = false;
    private long idTrace = -1;
    private long minimumAccuracy;

    public class LocalBinder extends Binder {
        MotionService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MotionService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(mGoogleApiClient == null)
            buildGoogleApiClient();

        //Send settings request
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Constants.SETTINGS_REQUEST));

        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        showForegroundNotification(getString(R.string.service_running));

        imuLinearAcceleration = new ImuLaKfQuaternion();
        profiles = getProfilesList();
        coordinateList = new ArrayList<>();

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSensorUpdate();
        stopLocationUpdates();
        if (mGoogleApiClient != null)
            mGoogleApiClient.disconnect();
        hideForegroundNotification();
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        createLocationRequest();

        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient = null;
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    private void showForegroundNotification(String contentText) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(contentText);
        Intent resultIntent = new Intent(this, MainActivity.class);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(
                this,
                0,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);
        Notification notification = mBuilder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

        mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifyMgr.notify(NOTIFICATION_ID, notification);
        mNotificationShowed = true;
    }

    public void hideForegroundNotification(){
        if(mNotificationShowed) {
            mNotifyMgr.cancel(NOTIFICATION_ID);
            mNotificationShowed = false;
        }
    }

    protected void startLocationUpdates() {
        if (mGoogleApiClient != null && mLocationConnected) {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                //Never called, MapsFragment checks the required permission earlier
            } else {
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
                mLocationStarted = true;
            }
        }
    }

    protected void stopLocationUpdates() {
        if (mGoogleApiClient != null && mLocationStarted) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mLocationStarted = false;
        }
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();

        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    public void startNewTraining(){
        idTrace = createTrace(System.currentTimeMillis(), System.currentTimeMillis(), 0, 0, 0);
        startLocationUpdates();
        startSensorUpdate();
        mTrackingStarted = true;
        mLastLocation = false;
        detectionReady = false;
        minimumAccuracy = Long.parseLong(preferences.getString("minimum_accuracy", getString(R.string.pref_default_minumum_accuracy)));

        //Init kalman filter
        float kalman_speed = Float.parseFloat(preferences.getString("kalman_speed", getString(R.string.pref_default_kalman_speed)));
        geoTrackFilter = new GeoTrackFilter(kalman_speed);

        //Send broadcast to clear the map
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Constants.CLEAR_MAP));
    }

    public void stopTraining(){
        if(mTrackingStarted) {
            stopSensorUpdate();
            stopLocationUpdates();
            mTrackingStarted = false;

            //Reset variables
            coordinateList.clear();
            if(mLastLocation)
                lastLocation.reset();
            idTrace = -1;
            lastLocationEntry = -1;
            id = -1;
        }
    }

    public boolean isTrackingStarted(){
        return mTrackingStarted;
    }

    public long getIdTrace(){
        return idTrace;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location.hasAccuracy() && location.getAccuracy() <= minimumAccuracy) {
            long difftime = 0l;
            float distance_diff = 0f;
            double speed;
            if(mLastLocation) {
                difftime = location.getTime() - lastLocation.getTime();
                distance_diff = location.distanceTo(lastLocation);
            }

            if(!mLastLocation) {
                mLastLocation = true;
                //Add first values to kalman filter
                geoTrackFilter.update_velocity2d(location.getLatitude(), location.getLongitude(), 0);
                //Add first entry
                createLocation(idTrace, location.getTime(), 0, location.getLatitude(), location.getLongitude(), 0, 0, activityType);
            } else {
                //Kalman filter
                geoTrackFilter.update_velocity2d(location.getLatitude(), location.getLongitude(), difftime/1000);
                double[] latLon = geoTrackFilter.get_lat_long();
                speed = geoTrackFilter.get_speed();
                location.setSpeed((float) speed);

                lastLocationEntry = createLocation(idTrace, location.getTime(), difftime, latLon[0], latLon[1], distance_diff, speed, activityType);

                coordinateList.add(new LatLng(latLon[0], latLon[1]));

                if (activityType == ON_BIKE && getLocations(lastLocationEntry).getType() == ON_BIKE) {
                    Trace tr = getTrace(idTrace);
                    tr.setAvgspeed((tr.getAvgspeed() * (lastLocationEntry - 1) + location.getSpeed()) / lastLocationEntry);
                    if (location.getSpeed() > tr.getMaxspeed())
                        tr.setMaxspeed(location.getSpeed());
                    tr.setDistance(tr.getDistance() + distance_diff);
                    tr.setDuration(tr.getDuration() + difftime);
                    tr.setEndTimestamp(location.getTime());
                    updateTrace(tr);
                }
            }
            lastLocation = location;
        }

        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong(Constants.LAST_LATITUDE, Double.doubleToRawLongBits(location.getLatitude()));
        editor.putLong(Constants.LAST_LONGITUDE, Double.doubleToRawLongBits(location.getLongitude()));
        editor.commit();

        Intent i = new Intent(Constants.UPDATE_MAP);
        i.putExtra(Constants.LATITUDE, location.getLatitude());
        i.putExtra(Constants.LONGITUDE, location.getLongitude());
        i.putParcelableArrayListExtra(Constants.COORDINATE_LIST, coordinateList);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }

    ////// ---------------- SENSORS ------------------- ////////

    public void startSensorUpdate(){
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        mMagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mSensorManager.registerListener(this, mMagnetic, SensorManager.SENSOR_DELAY_NORMAL);
/*        mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mSensorManager.registerListener(this, mGravity, SensorManager.SENSOR_DELAY_NORMAL);*/
        mSensorStarted = true;
    }

    public void stopSensorUpdate(){
        if(mSensorStarted)
            mSensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER || event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            dataReady = true;
        }
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            System.arraycopy(event.values, 0, rotation, 0, event.values.length);
            imuLinearAcceleration.setGyroscope(rotation, System.nanoTime());
            linearAcceleration = imuLinearAcceleration.getLinearAcceleration();
/*            id = createAccelerometer(idTrace, linearAcceleration[0],
                    linearAcceleration[1],
                    linearAcceleration[2],
                    Math.sqrt(Math.pow(linearAcceleration[0], 2)
                            + Math.pow(linearAcceleration[1], 2)
                            + Math.pow(linearAcceleration[2], 2)),
                    System.currentTimeMillis());*/
            id = createAccelerometer(idTrace, linearAcceleration[0],
                    linearAcceleration[1],
                    linearAcceleration[2],
                    Math.sqrt(Math.pow(linearAcceleration[0], 2)
                            + Math.pow(linearAcceleration[1], 2)
                            + Math.pow(linearAcceleration[2], 2)),
                    0);
            activityType = detectActivity(id);
            Accelerometer a = getAccelerometer(id);
            a.setTimestamp(activityType);
            updateAccelerometer(a);
            /*float[] devRelativeAcc = new float[4], R = new float[16], I = new float[16],
                    inv = new float[16], earthRelativeAcc = new float[16];

            devRelativeAcc[0] = linearAcceleration[0];
            devRelativeAcc[1] = linearAcceleration[1];
            devRelativeAcc[2] = linearAcceleration[2];
            devRelativeAcc[3] = 0;

            SensorManager.getRotationMatrix(R, I, gravity, magnetic);
            android.opengl.Matrix.invertM(inv, 0, R, 0);
            android.opengl.Matrix.multiplyMV(earthRelativeAcc, 0, inv, 0, devRelativeAcc, 0);

            long id = createAccelerometer(idTrace, earthRelativeAcc[0],
                    earthRelativeAcc[1],
                    earthRelativeAcc[2],
                    Math.sqrt(Math.pow(earthRelativeAcc[0], 2)+ Math.pow(earthRelativeAcc[1], 2)),
                    System.currentTimeMillis());*/
            //activityType = detectActivity(id);
/*            if(detectActivity(id) == ON_BIKE){
                activityType = ON_BIKE;
            } else {
                activityType = UNKNOWN;
            }*/
        }
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, acceleration, 0, event.values.length);
            imuLinearAcceleration.setAcceleration(acceleration);
        }
/*        if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            System.arraycopy(event.values, 0, gravity, 0, event.values.length);
        }*/
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetic, 0, event.values.length);
            imuLinearAcceleration.setMagnetic(magnetic);
        }
    }
    private int detectActivity(long id){
        if(!detectionReady) {
            if (++detectionHelper >= 5) {
                detectionReady = true;
                detectionHelper = 0;
            }
        }

        if(detectionReady) {
            detectionReady = false;
            ArrayList<Accelerometer> lastValuesList = new ArrayList<>();
            for (int i = 4; i >= 0; i--) {
                lastValuesList.add(getAccelerometer(id - i));
            }
            if(!profiles.isEmpty()){
                ArrayList<Integer> counterList = new ArrayList<>();
                for (Profile model : profiles) {
                    int counter = 0;
                    double temp = 0d;
                    double temp2 = 0d;
                    for (int i = 0; i < 5; i++) {
                        temp += model.getVec_xy()[i];
                        temp2 += lastValuesList.get(i).getVector_length();
/*                        if (lastValuesList.get(i).getVector_length() < 1.4 * model.getVec_xy()[i]
                                && lastValuesList.get(i).getVector_length() > 0.6 * model.getVec_xy()[i]) {
                            counter++;
                        }*/
                        temp /=5;
                        temp2 /=5;
                        if (temp2 < 1.4*temp && temp2 < 0.6*temp )
                            return 1;
                    }
                    counterList.add(counter);
/*                    if (counter >= 8) {
                        //return ON_BIKE;
                    }*/
                    return Collections.max(counterList);
                }
            }
        }
        return UNKNOWN;
    }

    //-------------------------------- DATABASE METHODS ----------------------------//

    public ArrayList<Profile> getProfilesList() {
        String[] projection = {
                DatabaseProvider.KEY_ID,
                DatabaseProvider.KEY_X,
                DatabaseProvider.KEY_Y,
                DatabaseProvider.KEY_Z,
                DatabaseProvider.KEY_VECTOR_LENGTH,
                DatabaseProvider.KEY_PROFILE_ID,
        };

        Cursor c = getContentResolver().query(DatabaseProvider.CONTENT_URI_PROFILE, projection, null, null, null);

        double[] x = new double[5];
        double[] y = new double[5];
        double[] z = new double[5];
        double[] vec = new double[5];

        ArrayList<Profile> profiles = new ArrayList<>();

        if(c != null) {
            if (c.moveToFirst()) {
                int i = 0;
                do {
                    x[i] = c.getDouble(c.getColumnIndex(DatabaseProvider.KEY_X));
                    y[i] = c.getDouble(c.getColumnIndex(DatabaseProvider.KEY_Y));
                    z[i] = c.getDouble(c.getColumnIndex(DatabaseProvider.KEY_Z));
                    vec[i] = c.getDouble(c.getColumnIndex(DatabaseProvider.KEY_VECTOR_LENGTH));
                    if(i == 4) {
                        i = 0;
                        Profile p = new Profile();
                        p.setP_id(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_PROFILE_ID)));
                        p.setX(x);
                        p.setY(y);
                        p.setZ(z);
                        p.setVec_xy(vec);
                        profiles.add(p);
                    }
                    i++;
                } while (c.moveToNext());

            }
            c.close();
        }

        return profiles;
    }

    @Nullable
    public long createTrace(long startTimestamp, long endTimestamp, long duration, float avgspeed, float maxspeed) {

        ContentValues values = new ContentValues();
        values.put(DatabaseProvider.KEY_STARTTIMESTAMP, startTimestamp);
        values.put(DatabaseProvider.KEY_ENDTIMESTAMP, endTimestamp);
        values.put(DatabaseProvider.KEY_DURATION, duration);
        values.put(DatabaseProvider.KEY_AVGSPEED, avgspeed);
        values.put(DatabaseProvider.KEY_MAXSPEED, maxspeed);
        Uri uri = getContentResolver().insert(DatabaseProvider.CONTENT_URI_TRACE, values);

        return Long.valueOf(uri.getLastPathSegment());
    }

    public Trace getTrace(long id) {
        String[] projection = {
                DatabaseProvider.KEY_ID,
                DatabaseProvider.KEY_STARTTIMESTAMP,
                DatabaseProvider.KEY_ENDTIMESTAMP,
                DatabaseProvider.KEY_DURATION,
                DatabaseProvider.KEY_DISTANCE,
                DatabaseProvider.KEY_AVGSPEED,
                DatabaseProvider.KEY_MAXSPEED
        };
        Uri singleUri = ContentUris.withAppendedId(DatabaseProvider.CONTENT_URI_TRACE, id);
        String selectionClause = null;
        String[] selectionArgs = null;
        Cursor c = getContentResolver().query(singleUri, projection, selectionClause, selectionArgs, null);

        Trace trace = new Trace();

        if(c != null) {
            if (c.moveToFirst()) {
                trace.setId(c.getLong(c.getColumnIndexOrThrow(DatabaseProvider.KEY_ID)));
                trace.setStartTimestamp(c.getLong(c.getColumnIndexOrThrow(DatabaseProvider.KEY_STARTTIMESTAMP)));
                trace.setEndTimestamp(c.getLong(c.getColumnIndexOrThrow(DatabaseProvider.KEY_ENDTIMESTAMP)));
                trace.setDuration(c.getLong(c.getColumnIndexOrThrow(DatabaseProvider.KEY_DURATION)));
                trace.setDistance(c.getLong(c.getColumnIndexOrThrow(DatabaseProvider.KEY_DISTANCE)));
                trace.setAvgspeed(c.getFloat(c.getColumnIndexOrThrow(DatabaseProvider.KEY_AVGSPEED)));
                trace.setMaxspeed(c.getFloat(c.getColumnIndexOrThrow(DatabaseProvider.KEY_MAXSPEED)));
            }
            c.close();
        }

        return trace;
    }

    public ArrayList<Trace> getAllTraces() {
        ArrayList<Trace> traces = new ArrayList<Trace>();

        String[] projection = {
                DatabaseProvider.KEY_ID,
                DatabaseProvider.KEY_STARTTIMESTAMP,
                DatabaseProvider.KEY_ENDTIMESTAMP,
                DatabaseProvider.KEY_DURATION,
                DatabaseProvider.KEY_DISTANCE,
                DatabaseProvider.KEY_AVGSPEED,
                DatabaseProvider.KEY_MAXSPEED
        };
        String selectionClause = null;
        String[] selectionArgs = null;
        Cursor c = getContentResolver().query(DatabaseProvider.CONTENT_URI_TRACE, projection, selectionClause, selectionArgs, null);

        if(c != null) {
            if (c.moveToFirst()) {
                do {
                    Trace trace = new Trace();
                    trace.setId(c.getLong(c.getColumnIndexOrThrow(DatabaseProvider.KEY_ID)));
                    trace.setStartTimestamp(c.getLong(c.getColumnIndexOrThrow(DatabaseProvider.KEY_STARTTIMESTAMP)));
                    trace.setEndTimestamp(c.getLong(c.getColumnIndexOrThrow(DatabaseProvider.KEY_ENDTIMESTAMP)));
                    trace.setDuration(c.getLong(c.getColumnIndexOrThrow(DatabaseProvider.KEY_DURATION)));
                    trace.setDistance(c.getLong(c.getColumnIndexOrThrow(DatabaseProvider.KEY_DISTANCE)));
                    trace.setAvgspeed(c.getFloat(c.getColumnIndexOrThrow(DatabaseProvider.KEY_AVGSPEED)));
                    trace.setMaxspeed(c.getFloat(c.getColumnIndexOrThrow(DatabaseProvider.KEY_MAXSPEED)));

                    traces.add(trace);
                } while (c.moveToNext());
            }
            c.close();
        }

        return traces;
    }

    public long deleteTrace(long id) {
        String selectionClause = null;
        String[] selectionArgs = null;
        Uri singleUri = ContentUris.withAppendedId(DatabaseProvider.CONTENT_URI_TRACE, id);

        return getContentResolver().delete(singleUri,selectionClause,selectionArgs);
    }

    public int updateTrace(Trace trace) {

        ContentValues values = new ContentValues();
        values.put(DatabaseProvider.KEY_STARTTIMESTAMP, trace.getStartTimestamp());
        values.put(DatabaseProvider.KEY_ENDTIMESTAMP, trace.getEndTimestamp());
        values.put(DatabaseProvider.KEY_DURATION, trace.getDuration());
        values.put(DatabaseProvider.KEY_DISTANCE, trace.getDistance());
        values.put(DatabaseProvider.KEY_AVGSPEED, trace.getAvgspeed());
        values.put(DatabaseProvider.KEY_MAXSPEED, trace.getMaxspeed());

        Uri singleUri = ContentUris.withAppendedId(DatabaseProvider.CONTENT_URI_TRACE, trace.getId());

        return getContentResolver().update(singleUri, values, null, null);
    }

    @Nullable
    public long createAccelerometer(long id_trace, float x, float y, float z, double vector_length, long timestamp) {
        ContentValues values = new ContentValues();
        values.put(DatabaseProvider.KEY_X, x);
        values.put(DatabaseProvider.KEY_Y, y);
        values.put(DatabaseProvider.KEY_Z, z);
        values.put(DatabaseProvider.KEY_TIMESTAMP, timestamp);
        values.put(DatabaseProvider.KEY_VECTOR_LENGTH, vector_length);
        values.put(DatabaseProvider.KEY_TRACE_ID, id_trace);
        Uri uri = getContentResolver().insert(DatabaseProvider.CONTENT_URI_ACCELEROMETER, values);

        return Long.valueOf(uri.getLastPathSegment());
    }

    public Accelerometer getAccelerometer(long id) {
        String[] projection = {
                DatabaseProvider.KEY_ID,
                DatabaseProvider.KEY_TIMESTAMP,
                DatabaseProvider.KEY_X,
                DatabaseProvider.KEY_Y,
                DatabaseProvider.KEY_Z,
                DatabaseProvider.KEY_VECTOR_LENGTH,
                DatabaseProvider.KEY_TRACE_ID
        };
        Uri singleUri = ContentUris.withAppendedId(DatabaseProvider.CONTENT_URI_ACCELEROMETER, id);
        String selectionClause = null;
        String[] selectionArgs = null;
        Cursor c = getContentResolver().query(singleUri, projection, selectionClause, selectionArgs, null);
        Accelerometer ac = new Accelerometer();

        if(c != null){
            if(c.moveToFirst()) {
                ac.setId(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_ID)));
                ac.setTimestamp(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_TIMESTAMP)));
                ac.setX(c.getFloat(c.getColumnIndex(DatabaseProvider.KEY_X)));
                ac.setY(c.getFloat(c.getColumnIndex(DatabaseProvider.KEY_Y)));
                ac.setZ(c.getFloat(c.getColumnIndex(DatabaseProvider.KEY_Z)));
                ac.setVector_length(c.getFloat(c.getColumnIndex(DatabaseProvider.KEY_VECTOR_LENGTH)));
                ac.setId_trace(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_TRACE_ID)));
            }
            c.close();
        }

        return ac;
    }

    public int updateAccelerometer(Accelerometer accelerometer) {

        ContentValues values = new ContentValues();
        values.put(DatabaseProvider.KEY_TIMESTAMP, accelerometer.getTimestamp());
        values.put(DatabaseProvider.KEY_X, accelerometer.getX());
        values.put(DatabaseProvider.KEY_Y, accelerometer.getY());
        values.put(DatabaseProvider.KEY_Z, accelerometer.getZ());
        values.put(DatabaseProvider.KEY_VECTOR_LENGTH, accelerometer.getVector_length());

        Uri singleUri = ContentUris.withAppendedId(DatabaseProvider.CONTENT_URI_ACCELEROMETER, accelerometer.getId());

        return getContentResolver().update(singleUri, values, null, null);
    }

    public ArrayList<Accelerometer> getAllAccelerometers() {
        ArrayList<Accelerometer> accs = new ArrayList<>();

        String[] projection = {
                DatabaseProvider.KEY_ID,
                DatabaseProvider.KEY_TIMESTAMP,
                DatabaseProvider.KEY_X,
                DatabaseProvider.KEY_Y,
                DatabaseProvider.KEY_Z,
                DatabaseProvider.KEY_VECTOR_LENGTH,
                DatabaseProvider.KEY_TRACE_ID
        };
        String selectionClause = null;
        String[] selectionArgs = null;
        Cursor c = getContentResolver().query(DatabaseProvider.CONTENT_URI_ACCELEROMETER, projection, selectionClause, selectionArgs, null);

        if(c != null) {
            if (c.moveToFirst()) {
                do {
                    Accelerometer ac = new Accelerometer();
                    ac.setId(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_ID)));
                    ac.setTimestamp(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_TIMESTAMP)));
                    ac.setX(c.getFloat(c.getColumnIndex(DatabaseProvider.KEY_X)));
                    ac.setY(c.getFloat(c.getColumnIndex(DatabaseProvider.KEY_Y)));
                    ac.setZ(c.getFloat(c.getColumnIndex(DatabaseProvider.KEY_Z)));
                    ac.setVector_length(c.getFloat(c.getColumnIndex(DatabaseProvider.KEY_VECTOR_LENGTH)));
                    ac.setId_trace(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_TRACE_ID)));

                    // adding to list
                    accs.add(ac);
                } while (c.moveToNext());
            }
            c.close();
        }
        return accs;
    }

    @Nullable
    public long createLocation(long id_trace, long timestamp, long difftime, double latitude, double longitude, float distance, float speed, int type) {

        ContentValues values = new ContentValues();
        values.put(DatabaseProvider.KEY_TIMESTAMP, timestamp);
        values.put(DatabaseProvider.KEY_LATITUDE, latitude);
        values.put(DatabaseProvider.KEY_LONGITUDE, longitude);
        values.put(DatabaseProvider.KEY_DIFFDISTANCE, distance);
        values.put(DatabaseProvider.KEY_DIFFTIME, difftime);
        values.put(DatabaseProvider.KEY_SPEED, speed);
        values.put(DatabaseProvider.KEY_TYPE, type);
        values.put(DatabaseProvider.KEY_TRACE_ID, id_trace);
        Uri uri = getContentResolver().insert(DatabaseProvider.CONTENT_URI_LOCATION, values);

        return Long.valueOf(uri.getLastPathSegment());
    }

    @Nullable
    public long createLocation(long id_trace, long timestamp, long difftime, double latitude, double longitude, float distance, double speed, int type) {

        ContentValues values = new ContentValues();
        values.put(DatabaseProvider.KEY_TIMESTAMP, timestamp);
        values.put(DatabaseProvider.KEY_LATITUDE, latitude);
        values.put(DatabaseProvider.KEY_LONGITUDE, longitude);
        values.put(DatabaseProvider.KEY_DIFFDISTANCE, distance);
        values.put(DatabaseProvider.KEY_DIFFTIME, difftime);
        values.put(DatabaseProvider.KEY_SPEED, speed);
        values.put(DatabaseProvider.KEY_TYPE, type);
        values.put(DatabaseProvider.KEY_TRACE_ID, id_trace);
        Uri uri = getContentResolver().insert(DatabaseProvider.CONTENT_URI_LOCATION, values);

        return Long.valueOf(uri.getLastPathSegment());
    }

    public Locations getLocations(long id) {
        String[] projection = {
                DatabaseProvider.KEY_ID,
                DatabaseProvider.KEY_TIMESTAMP,
                DatabaseProvider.KEY_DIFFTIME,
                DatabaseProvider.KEY_DIFFDISTANCE,
                DatabaseProvider.KEY_SPEED,
                DatabaseProvider.KEY_LATITUDE,
                DatabaseProvider.KEY_LONGITUDE,
                DatabaseProvider.KEY_TYPE,
                DatabaseProvider.KEY_TRACE_ID
        };
        Uri singleUri = ContentUris.withAppendedId(DatabaseProvider.CONTENT_URI_LOCATION, id);
        String selectionClause = null;
        String[] selectionArgs = null;
        Cursor c = getContentResolver().query(singleUri, projection, selectionClause, selectionArgs, null);

        Locations loc = new Locations();

        if(c != null) {
            if (c.moveToFirst()) {
                loc.setId(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_ID)));
                loc.setTimestamp(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_TIMESTAMP)));
                loc.setDiffTime(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_DIFFTIME)));
                loc.setDistance(c.getFloat(c.getColumnIndex(DatabaseProvider.KEY_DIFFDISTANCE)));
                loc.setSpeed(c.getFloat(c.getColumnIndex(DatabaseProvider.KEY_SPEED)));
                loc.setLatitude(c.getDouble(c.getColumnIndex(DatabaseProvider.KEY_LATITUDE)));
                loc.setLongitude(c.getDouble(c.getColumnIndex(DatabaseProvider.KEY_LONGITUDE)));
                loc.setType(c.getInt(c.getColumnIndex(DatabaseProvider.KEY_TYPE)));
                loc.setId_trace(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_TRACE_ID)));
            }
            c.close();
        }

        return loc;
    }

    public ArrayList<Locations> getAllLocations() {
        ArrayList<Locations> locs = new ArrayList<>();

        String[] projection = {
                DatabaseProvider.KEY_ID,
                DatabaseProvider.KEY_TIMESTAMP,
                DatabaseProvider.KEY_DIFFTIME,
                DatabaseProvider.KEY_DIFFDISTANCE,
                DatabaseProvider.KEY_SPEED,
                DatabaseProvider.KEY_LATITUDE,
                DatabaseProvider.KEY_LONGITUDE,
                DatabaseProvider.KEY_TYPE,
                DatabaseProvider.KEY_TRACE_ID
        };
        String selectionClause = null;
        String[] selectionArgs = null;
        Cursor c = getContentResolver().query(DatabaseProvider.CONTENT_URI_LOCATION, projection, selectionClause, selectionArgs, null);

        if(c != null) {
            if (c.moveToFirst()) {
                do {
                    Locations loc = new Locations();
                    loc.setId(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_ID)));
                    loc.setTimestamp(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_TIMESTAMP)));
                    loc.setDiffTime(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_DIFFTIME)));
                    loc.setDistance(c.getFloat(c.getColumnIndex(DatabaseProvider.KEY_DIFFDISTANCE)));
                    loc.setSpeed(c.getFloat(c.getColumnIndex(DatabaseProvider.KEY_SPEED)));
                    loc.setLatitude(c.getDouble(c.getColumnIndex(DatabaseProvider.KEY_LATITUDE)));
                    loc.setLongitude(c.getDouble(c.getColumnIndex(DatabaseProvider.KEY_LONGITUDE)));
                    loc.setType(c.getInt(c.getColumnIndex(DatabaseProvider.KEY_TYPE)));
                    loc.setId_trace(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_TRACE_ID)));

                    // adding to list
                    locs.add(loc);
                } while (c.moveToNext());
            }
            c.close();
        }
        return locs;
    }

    public ArrayList<Locations> getAllLocations(long idTrace) {
        ArrayList<Locations> locs = new ArrayList<>();

        String[] projection = {
                DatabaseProvider.KEY_ID,
                DatabaseProvider.KEY_TIMESTAMP,
                DatabaseProvider.KEY_DIFFTIME,
                DatabaseProvider.KEY_DIFFDISTANCE,
                DatabaseProvider.KEY_SPEED,
                DatabaseProvider.KEY_LATITUDE,
                DatabaseProvider.KEY_LONGITUDE,
                DatabaseProvider.KEY_TYPE,
                DatabaseProvider.KEY_TRACE_ID
        };

        String selectionClause = DatabaseProvider.KEY_TRACE_ID + " = ?";
        String[] selectionArgs = new String[] { String.valueOf(idTrace) };
        Cursor c = getContentResolver().query(DatabaseProvider.CONTENT_URI_LOCATION, projection, selectionClause, selectionArgs, null);

        if(c != null) {
            if (c.moveToFirst()) {
                do {
                    Locations loc = new Locations();
                    loc.setId(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_ID)));
                    loc.setTimestamp(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_TIMESTAMP)));
                    loc.setDiffTime(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_DIFFTIME)));
                    loc.setDistance(c.getFloat(c.getColumnIndex(DatabaseProvider.KEY_DIFFDISTANCE)));
                    loc.setSpeed(c.getFloat(c.getColumnIndex(DatabaseProvider.KEY_SPEED)));
                    loc.setLatitude(c.getDouble(c.getColumnIndex(DatabaseProvider.KEY_LATITUDE)));
                    loc.setLongitude(c.getDouble(c.getColumnIndex(DatabaseProvider.KEY_LONGITUDE)));
                    loc.setType(c.getInt(c.getColumnIndex(DatabaseProvider.KEY_TYPE)));
                    loc.setId_trace(c.getLong(c.getColumnIndex(DatabaseProvider.KEY_TRACE_ID)));
                    // adding to list
                    locs.add(loc);
                } while (c.moveToNext());
            }
            c.close();
        }
        return locs;
    }

    public int updateLocation(Locations location) {

        ContentValues values = new ContentValues();
        values.put(DatabaseProvider.KEY_TIMESTAMP, location.getTimestamp());
        values.put(DatabaseProvider.KEY_DIFFTIME, location.getDiffTime());
        values.put(DatabaseProvider.KEY_DIFFDISTANCE, location.getDistance());
        values.put(DatabaseProvider.KEY_LATITUDE, location.getLatitude());
        values.put(DatabaseProvider.KEY_LONGITUDE, location.getLongitude());
        values.put(DatabaseProvider.KEY_SPEED, location.getSpeed());

        Uri singleUri = ContentUris.withAppendedId(DatabaseProvider.CONTENT_URI_LOCATION, location.getId());

        return getContentResolver().update(singleUri, values, null, null);
    }


}