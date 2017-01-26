package com.debug.bikecourier;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.debug.bikecourier.model.Accelerometer;
import com.debug.bikecourier.model.Locations;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.List;


public class GraphFragment extends Fragment {

    private static final int GRAPH_LOCATION = 1;
    private static final int GRAPH_ACCELEROMETER = 2;

    private long idTrace;
    private int graphType;
    private float minBound;
    private float maxBound;

    public GraphFragment() {
        // Required empty public constructor
    }

    public static GraphFragment newInstance(long idTrace, int type){
        GraphFragment fragment = new GraphFragment();
        Bundle args = new Bundle();
        args.putLong("idTrace",idTrace);
        args.putInt("graph_type",type);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            idTrace = getArguments().getLong("idTrace");
            graphType = getArguments().getInt("graph_type");
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        minBound = Float.parseFloat(preferences.getString("min_bound", getString(R.string.pref_default_range_min)));
        maxBound = Float.parseFloat(preferences.getString("max_bound", getString(R.string.pref_default_range_max)));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_graph, container, false);

        LineChart chart = (LineChart) view.findViewById(R.id.chart);

        if (graphType == GRAPH_LOCATION) {
            ArrayList<Locations> locs = getAllLocations(idTrace);
            if (!locs.isEmpty()) {
                long diff = locs.get(0).getTimestamp();
                List<Entry> entries = new ArrayList<>();

                long i = 0l;
                for (Locations loc : locs) {
                    entries.add(new Entry(i++, loc.getSpeed()));
                }

                LineDataSet dataSet = new LineDataSet(entries, "Speed [kmh]");
                dataSet.setColor(Color.BLUE);
                dataSet.setLineWidth(2);
                dataSet.setCircleColor(Color.BLACK);

                LineData lineData = new LineData(dataSet);
                chart.setData(lineData);
                chart.invalidate();
            }
        } else {
            if (graphType == GRAPH_ACCELEROMETER) {
                ArrayList<Accelerometer> accs = getAllAccelerometers(idTrace);
                if (!accs.isEmpty()) {
                    long diff = accs.get(0).getTimestamp();
                    List<Entry> entries_x = new ArrayList<>();
                    List<Entry> entries_y = new ArrayList<>();
                    List<Entry> entries_z = new ArrayList<>();

                    long i = 0l;
                    for (Accelerometer acc : accs) {
                        entries_x.add(new Entry(i++, acc.getX()));
                        entries_y.add(new Entry(i++, acc.getY()));
                        entries_z.add(new Entry(i++, acc.getZ()));
                    }

                    LineDataSet dataSetx = new LineDataSet(entries_x, "Acceleration X");
                    dataSetx.setColor(Color.BLUE);
                    dataSetx.setLineWidth(2);
                    dataSetx.setCircleColor(Color.BLACK);

                    LineDataSet dataSety = new LineDataSet(entries_y, "Acceleration Y");
                    dataSety.setColor(Color.RED);
                    dataSety.setLineWidth(2);
                    dataSety.setCircleColor(Color.BLACK);

                    LineDataSet dataSetz = new LineDataSet(entries_z, "Acceleration Z");
                    dataSetz.setColor(Color.GREEN);
                    dataSetz.setLineWidth(2);
                    dataSetz.setCircleColor(Color.BLACK);

                    List<ILineDataSet> dataSets = new ArrayList<>();
                    dataSets.add(dataSetx);
                    dataSets.add(dataSety);
                    dataSets.add(dataSetz);

                    LineData lineData = new LineData(dataSets);
                    chart.setData(lineData);
                    chart.invalidate();
                }
            }
        }

        return view;
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
        Cursor c = getContext().getContentResolver().query(DatabaseProvider.CONTENT_URI_LOCATION, projection, selectionClause, selectionArgs, null);

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

    public ArrayList<Accelerometer> getAllAccelerometers(long idTrace) {
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
        String selectionClause = DatabaseProvider.KEY_TRACE_ID + " = ?";
        String[] selectionArgs = new String[] { String.valueOf(idTrace) };
        Cursor c = getContext().getContentResolver().query(DatabaseProvider.CONTENT_URI_ACCELEROMETER, projection, selectionClause, selectionArgs, null);

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

}
