package com.debug.bikecourier;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.debug.bikecourier.model.Trace;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;


public class DetailsFragment extends Fragment {

    private Trace mTrace;

    public DetailsFragment() {
        // Required empty public constructor
    }

    public static DetailsFragment newInstance(Trace trace) {
        DetailsFragment fragment = new DetailsFragment();
        Bundle args = new Bundle();
        args.putParcelable("traceObject",trace);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mTrace = getArguments().getParcelable("traceObject");

        View view = inflater.inflate(R.layout.fragment_details, container, false);

        TextView time = (TextView) view.findViewById(R.id.time);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String date = formatter.format(new Date(mTrace.getStartTimestamp()));
        time.setText(date);
        TextView maxspeed = (TextView) view.findViewById(R.id.maxspeed);
        maxspeed.setText(String.valueOf(mTrace.getMaxspeed())+" km/h");
        TextView avgspeed = (TextView) view.findViewById(R.id.avgspeed);
        avgspeed.setText(String.valueOf(mTrace.getAvgspeed())+" km/h");
        TextView duration = (TextView) view.findViewById(R.id.duration);
        String hms = String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(mTrace.getDuration()),
                TimeUnit.MILLISECONDS.toMinutes(mTrace.getDuration()) % TimeUnit.HOURS.toMinutes(1),
                TimeUnit.MILLISECONDS.toSeconds(mTrace.getDuration()) % TimeUnit.MINUTES.toSeconds(1));
        duration.setText(String.valueOf(hms));
        TextView distance = (TextView) view.findViewById(R.id.distance);
        distance.setText(String.valueOf(mTrace.getDistance()+" m"));

        return view;
    }
}
