package com.debug.bikecourier;

import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.app.ListFragment;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.debug.bikecourier.model.Locations;
import com.debug.bikecourier.model.Trace;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by epastma on 2017-01-02.
 */

public class TraceListFragment extends ListFragment implements SwipeRefreshLayout.OnRefreshListener {

    private ArrayList<Trace> arrayOfTraces;
    private TracesAdapter adapter;
    private int currentPosition;
    SwipeRefreshLayout swipeLayout;

    public TraceListFragment() {
    }

    @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (container == null) {
            return null;
        }
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_list, container, false);

        swipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_container);
        swipeLayout.setOnRefreshListener(this);

        arrayOfTraces = getAllTraces();
        adapter = new TracesAdapter(getActivity(), arrayOfTraces);

        final ListView listView = (ListView) view.findViewById(android.R.id.list);

        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                currentPosition = position;
                if ((((MainActivity) getActivity()).ismServiceStarted()) && (((MainActivity) getActivity()).ismBound()))
                    if(((MainActivity) getActivity()).getmMotionService().getIdTrace() == arrayOfTraces.get(currentPosition).getId()){
                        Toast.makeText(getContext(), getString(R.string.delete_rationale), Toast.LENGTH_SHORT).show();
                        return true;
                    }
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.delele_title)
                        .setMessage(R.string.delete_message)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                deleteTrace(arrayOfTraces.get(currentPosition).getId());
                                refreshList();
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // do nothing
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
                return true;
            }
        });

        return view;
    }

    @Override
    public void onRefresh() {
        refreshList();
        swipeLayout.setRefreshing(false);
    }

    public void refreshList(){
        if(!arrayOfTraces.isEmpty()) {
            arrayOfTraces.clear();
            arrayOfTraces = getAllTraces();
            adapter.clear();
            adapter.addAll(arrayOfTraces);
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        long idTrace = arrayOfTraces.get(position).getId();
        ArrayList<Locations> arrayOfLocations = getAllLocations(idTrace);
        if(arrayOfLocations.isEmpty()){
            Toast.makeText(getContext(), R.string.no_entry_info, Toast.LENGTH_SHORT).show();
        } else {
            Intent intent = new Intent(getContext(), TraceActivity.class);
            intent.putParcelableArrayListExtra(Constants.LOCATION_LIST, arrayOfLocations);
            intent.putExtra(Constants.TRACE_OBJECT, arrayOfTraces.get(position));
            startActivity(intent);
        }
    }

    public class TracesAdapter extends ArrayAdapter<Trace> {
        public TracesAdapter(Context context, ArrayList<Trace> traces) {
            super(context, R.layout.item_trace, traces);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the data item for this position
            Trace trace = getItem(position);
            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_trace, parent, false);
            }
            // Lookup view for data population
            TextView id1 = (TextView) convertView.findViewById(R.id.id1);
            //Populate the data into the template view using the data object
            Date date = new Date(trace.getStartTimestamp());
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            id1.setText(formatter.format(date));
            //Return the completed view to render on screen
            return convertView;
        }
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
        Cursor c = getContext().getContentResolver().query(DatabaseProvider.CONTENT_URI_TRACE, projection, selectionClause, selectionArgs, null);

        if (c != null) {
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

        return getContext().getContentResolver().delete(singleUri,selectionClause,selectionArgs);
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

}