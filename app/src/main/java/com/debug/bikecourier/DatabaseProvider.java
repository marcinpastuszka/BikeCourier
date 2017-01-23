package com.debug.bikecourier;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.util.HashMap;

/**
 * Created by epastma on 2016-12-30.
 */

public class DatabaseProvider extends ContentProvider {

    // Logcat tag
    private static final String TAG = "DatabaseHelper";

    // Database Version
    private static final int DATABASE_VERSION = 1;

    // Database Name
    private static final String DATABASE_NAME = "Database.db";

    // Table Names
    public static final String TABLE_TRACE = "trace";
    public static final String TABLE_LOCATION = "location";
    public static final String TABLE_ACCELEROMETER = "accelerometer";
    public static final String TABLE_PROFILE = "profile";

    // Common column names
    public static final String KEY_ID = "id";
    public static final String KEY_TIMESTAMP = "timestamp";
    public static final String KEY_TRACE_ID = "trace_id";
    public static final String KEY_PROFILE_ID = "profile_id";

    //Trace column
    public static final String KEY_STARTTIMESTAMP = "start_timestamp";
    public static final String KEY_ENDTIMESTAMP = "end_timestamp";
    public static final String KEY_AVGSPEED = "avgspeed";
    public static final String KEY_MAXSPEED = "maxspeed";
    public static final String KEY_DISTANCE = "distance";
    public static final String KEY_DURATION = "duration";

    //Locations column
    public static final String KEY_LATITUDE = "latitude";
    public static final String KEY_LONGITUDE = "longitude";
    public static final String KEY_DIFFTIME = "diff_time";
    public static final String KEY_SPEED = "speed";
    public static final String KEY_DIFFDISTANCE = "diff_distance";
    public static final String KEY_TYPE = "type";

    //Accelerometer column
    public static final String KEY_X = "x";
    public static final String KEY_Y = "y";
    public static final String KEY_Z = "z";
    public static final String KEY_VECTOR_LENGTH = "vector_length";

    //uri matching
    private static final int TRACE = 1;
    private static final int TRACE_ID = 2;
    private static final int LOCATION = 3;
    private static final int LOCATION_ID = 4;
    private static final int ACCELEROMETER = 5;
    private static final int ACCELEROMETER_ID = 6;
    private static final int PROFILE = 7;
    private static final int PROFILE_ID = 8;

    private static HashMap<String, String> TraceMap;
    private static HashMap<String, String> LocationMap;
    private static HashMap<String, String> AccelerometerMap;
    private static HashMap<String, String> ProfileMap;

    public static final String AUTHORITY = "com.debug.bikecourier.DatabaseProvider";
    public static final Uri CONTENT_URI_TRACE = Uri.parse("content://" + AUTHORITY
            + "/" + TABLE_TRACE);
    public static final Uri CONTENT_URI_LOCATION = Uri.parse("content://" + AUTHORITY
            + "/" + TABLE_LOCATION);
    public static final Uri CONTENT_URI_ACCELEROMETER = Uri.parse("content://" + AUTHORITY
            + "/" + TABLE_ACCELEROMETER);
    public static final Uri CONTENT_URI_PROFILE = Uri.parse("content://" + AUTHORITY
            + "/" + TABLE_PROFILE);

    private static final UriMatcher sURIMatcher = new UriMatcher(
            UriMatcher.NO_MATCH);
    static {
        sURIMatcher.addURI(AUTHORITY, TABLE_TRACE, TRACE);
        sURIMatcher.addURI(AUTHORITY, TABLE_TRACE + "/#", TRACE_ID);
        sURIMatcher.addURI(AUTHORITY, TABLE_LOCATION, LOCATION);
        sURIMatcher.addURI(AUTHORITY, TABLE_LOCATION + "/#", LOCATION_ID);
        sURIMatcher.addURI(AUTHORITY, TABLE_ACCELEROMETER, ACCELEROMETER);
        sURIMatcher.addURI(AUTHORITY, TABLE_ACCELEROMETER + "/#", ACCELEROMETER_ID);
        sURIMatcher.addURI(AUTHORITY, TABLE_PROFILE, PROFILE);
        sURIMatcher.addURI(AUTHORITY, TABLE_PROFILE + "/#", PROFILE_ID);
    }

    private class DatabaseHelper extends SQLiteOpenHelper {

        // Table Create Statements
        private static final String CREATE_TABLE_TRACE = "CREATE TABLE " + TABLE_TRACE
                + "("
                + KEY_ID + " INTEGER PRIMARY KEY, "
                + KEY_STARTTIMESTAMP + " DATETIME, "
                + KEY_ENDTIMESTAMP + " DATETIME, "
                + KEY_DURATION + " REAL, "
                + KEY_DISTANCE + " REAL, "
                + KEY_AVGSPEED + " REAL, "
                + KEY_MAXSPEED + " REAL"
                + ")";

        private static final String CREATE_TABLE_LOCATION = "CREATE TABLE " + TABLE_LOCATION
                + "("
                + KEY_ID + " INTEGER PRIMARY KEY, "
                + KEY_TIMESTAMP + " DATETIME, "
                + KEY_DIFFTIME + " REAL, "
                + KEY_LATITUDE + " REAL, "
                + KEY_LONGITUDE + " REAL, "
                + KEY_DIFFDISTANCE + " REAL, "
                + KEY_SPEED + " REAL, "
                + KEY_TYPE + " INTEGER, "
                + KEY_TRACE_ID + " INTEGER, "
                + "FOREIGN KEY(" + KEY_TRACE_ID + ") REFERENCES " + TABLE_TRACE + "(" + KEY_ID + ") "
                + "ON DELETE CASCADE ON UPDATE CASCADE)";

        private static final String CREATE_TABLE_ACCELEROMETER = "CREATE TABLE " + TABLE_ACCELEROMETER
                + "( "
                + KEY_ID + " INTEGER PRIMARY KEY, "
                + KEY_X + " REAL, "
                + KEY_Y + " REAL, "
                + KEY_Z + " REAL, "
                + KEY_VECTOR_LENGTH + " REAL, "
                + KEY_TIMESTAMP + " DATETIME, "
                + KEY_TRACE_ID + " INTEGER, "
                + "FOREIGN KEY(" + KEY_TRACE_ID + ") REFERENCES " + TABLE_TRACE + "(" + KEY_ID + ") "
                + "ON DELETE CASCADE ON UPDATE CASCADE)";

        private static final String CREATE_TABLE_PROFILE = "CREATE TABLE " + TABLE_PROFILE
                + "( "
                + KEY_ID + " INTEGER PRIMARY KEY, "
                + KEY_PROFILE_ID + " REAL, "
                + KEY_X + " REAL, "
                + KEY_Y + " REAL, "
                + KEY_Z + " REAL, "
                + KEY_VECTOR_LENGTH + " REAL)";

        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // creating required tables
            db.execSQL(CREATE_TABLE_TRACE);
            db.execSQL(CREATE_TABLE_LOCATION);
            db.execSQL(CREATE_TABLE_ACCELEROMETER);
            db.execSQL(CREATE_TABLE_PROFILE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // on upgrade drop older tables
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRACE);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOCATION);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ACCELEROMETER);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PROFILE);

            // create new tables
            onCreate(db);
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            super.onOpen(db);
            db.execSQL("PRAGMA foreign_keys = ON;");
        }
    }

    private SQLiteDatabase db;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);

        db = dbHelper.getWritableDatabase();
        return (db == null)? false:true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        int uriType = sURIMatcher.match(uri);
        switch (uriType) {
            case TRACE:
                queryBuilder.setTables(TABLE_TRACE);
                queryBuilder.setProjectionMap(TraceMap);
                break;
            case TRACE_ID:
                queryBuilder.setTables(TABLE_TRACE);
                queryBuilder.setProjectionMap(TraceMap);
                queryBuilder.appendWhere(KEY_ID + "=" + uri.getLastPathSegment());
                break;
            case LOCATION:
                queryBuilder.setTables(TABLE_LOCATION);
                queryBuilder.setProjectionMap(LocationMap);
                break;
            case LOCATION_ID:
                queryBuilder.setTables(TABLE_LOCATION);
                queryBuilder.setProjectionMap(LocationMap);
                queryBuilder.appendWhere(KEY_ID + "=" + uri.getLastPathSegment());
                break;
            case ACCELEROMETER:
                queryBuilder.setTables(TABLE_ACCELEROMETER);
                queryBuilder.setProjectionMap(AccelerometerMap);
                break;
            case ACCELEROMETER_ID:
                queryBuilder.setTables(TABLE_ACCELEROMETER);
                queryBuilder.setProjectionMap(AccelerometerMap);
                queryBuilder.appendWhere(KEY_ID + "=" + uri.getLastPathSegment());
                break;
            case PROFILE:
                queryBuilder.setTables(TABLE_PROFILE);
                queryBuilder.setProjectionMap(AccelerometerMap);
                break;
            case PROFILE_ID:
                queryBuilder.setTables(TABLE_PROFILE);
                queryBuilder.setProjectionMap(ProfileMap);
                queryBuilder.appendWhere(KEY_ID + "=" + uri.getLastPathSegment());
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        Cursor c = queryBuilder.query(db, projection, selection, selectionArgs,null, null, sortOrder);

        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Nullable
    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Uri _uri = null;
        int uriType = sURIMatcher.match(uri);
        switch (uriType) {
            case TRACE:
                long rowID1 = db.insert(TABLE_TRACE, "", values);
                if (rowID1 > 0) {
                    _uri = ContentUris.withAppendedId(CONTENT_URI_TRACE, rowID1);
                    getContext().getContentResolver().notifyChange(_uri, null);
                }
                break;
            case LOCATION:
                long rowID2 = db.insert(TABLE_LOCATION, "", values);
                if (rowID2 > 0) {
                    _uri = ContentUris.withAppendedId(CONTENT_URI_LOCATION, rowID2);
                }
                break;
            case ACCELEROMETER:
                long rowID3 = db.insert(TABLE_ACCELEROMETER, "", values);
                if (rowID3 > 0) {
                    _uri = ContentUris.withAppendedId(CONTENT_URI_ACCELEROMETER, rowID3);
                    getContext().getContentResolver().notifyChange(_uri, null);
                }
                break;
            case PROFILE:
                long rowID4 = db.insert(TABLE_PROFILE, "", values);
                if (rowID4 > 0) {
                    _uri = ContentUris.withAppendedId(CONTENT_URI_PROFILE, rowID4);
                    getContext().getContentResolver().notifyChange(_uri, null);
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);

        }
        return _uri;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        int uriType = sURIMatcher.match(uri);
        switch (uriType) {
            case TRACE_ID:
                count = db.update(TABLE_TRACE, values,
                        KEY_ID + " = " + uri.getPathSegments().get(1) +
                                (!TextUtils.isEmpty(selection) ? " AND (" +selection + ')' : ""), selectionArgs);
                break;
            case LOCATION_ID:
                count = db.update(TABLE_LOCATION, values,
                        KEY_ID + " = " + uri.getPathSegments().get(1) +
                                (!TextUtils.isEmpty(selection) ? " AND (" +selection + ')' : ""), selectionArgs);
                break;
            case ACCELEROMETER_ID:
                count = db.update(TABLE_ACCELEROMETER, values,
                        KEY_ID + " = " + uri.getPathSegments().get(1) +
                                (!TextUtils.isEmpty(selection) ? " AND (" +selection + ')' : ""), selectionArgs);
                break;
            case PROFILE_ID:
                count = db.update(TABLE_PROFILE, values,
                        KEY_ID + " = " + uri.getPathSegments().get(1) +
                                (!TextUtils.isEmpty(selection) ? " AND (" +selection + ')' : ""), selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        int uriType = sURIMatcher.match(uri);
        switch (uriType) {
            case TRACE_ID:
                String id_tr = uri.getPathSegments().get(1);
                count = db.delete(TABLE_TRACE, KEY_ID +  " = " + id_tr +
                        (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
                break;
            case LOCATION_ID:
                String id_loc = uri.getPathSegments().get(1);
                count = db.delete(TABLE_LOCATION, KEY_ID +  " = " + id_loc +
                        (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
                break;
            case ACCELEROMETER_ID:
                String id_acc = uri.getPathSegments().get(1);
                count = db.delete(TABLE_ACCELEROMETER, KEY_ID +  " = " + id_acc +
                        (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
                break;
            case PROFILE:
                count = db.delete(TABLE_PROFILE,
                        (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
                break;
            case PROFILE_ID:
                String id_prof = uri.getPathSegments().get(1);
                count = db.delete(TABLE_PROFILE, KEY_ID +  " = " + id_prof +
                        (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);

        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}


