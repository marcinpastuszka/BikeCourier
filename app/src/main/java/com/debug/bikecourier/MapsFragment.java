package com.debug.bikecourier;

/**
 * Created by epastma on 2017-01-02.
 */

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;

public class MapsFragment extends Fragment implements OnMapReadyCallback, ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = "MapsFragment";

    private static final int REQUEST_LOCATION = 1;

    private SupportMapFragment mapFragment;
    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private boolean mMapReady;
    private int instanceId;
    private SharedPreferences preferences;
    private ArrayList<Polyline> polylines;
    private FloatingActionButton fab_start;
    private FloatingActionButton fab_add;
    private boolean mButtonStatus = true;;
    private boolean mActivityCreated = false;

    public MapsFragment() {
        // Required empty public constructor
    }

    public static MapsFragment newInstance(int id) {
        Bundle args = new Bundle();
        args.putInt("instanceId", id);

        MapsFragment fragment = new MapsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instanceId = getArguments().getInt("instanceId", 0);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("activity_created", mActivityCreated);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();
        setButtons();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (container == null) {
            return null;
        }
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        FragmentManager fm = getChildFragmentManager();
        mapFragment =  SupportMapFragment.newInstance();
        fm.beginTransaction().replace(R.id.mapContainer, mapFragment).commit();
        mapFragment.getMapAsync(this);

        if (savedInstanceState == null){
            mapFragment.setRetainInstance(true);
        }

        fab_start = (FloatingActionButton) view.findViewById(R.id.fab_start);
        fab_add = (FloatingActionButton) view.findViewById(R.id.fab_add);

        polylines = new ArrayList<>();

        preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mActivityCreated = true;
        setButtons();
    }

    public void setButtons(){
        if(instanceId == 1) {
            fab_start.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (((MainActivity) getActivity()).ismSeviceStarted()) {
                        ((MainActivity) getActivity()).stopServices();
                    } else {
                        ((MainActivity) getActivity()).startServices();
                    }
                }
            });

            if ((((MainActivity) getActivity()).ismSeviceStarted()))
                changeStartButton();
            fab_add.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if ((((MainActivity) getActivity()).ismSeviceStarted()) && (((MainActivity) getActivity()).ismBound())) {
                        if (((MainActivity) getActivity()).getmMotionService().isTrackingStarted()) {
                            ((MainActivity) getActivity()).getmMotionService().stopTraining();
                            Toast.makeText(getContext(), getString(R.string.stop_tracking), Toast.LENGTH_SHORT).show();
                        } else {
                            ((MainActivity) getActivity()).getmMotionService().startNewTraining();
                            ((MainActivity) getActivity()).refreshList();
                            Toast.makeText(getContext(), getString(R.string.start_tracking), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(getContext(), getString(R.string.tracking_rationale), Toast.LENGTH_SHORT).show();
                    }
                }
            });

        } else if(instanceId == 2) {
            fab_add.hide();
            fab_start.hide();
        }
    }

    public void changeStartButton(){
        //if(mActivityCreated) {
            if (mButtonStatus) {
                fab_start.setImageResource(R.drawable.cast_ic_expanded_controller_stop);
                mButtonStatus = false;
            } else {
                fab_start.setImageResource(R.drawable.cast_ic_expanded_controller_play);
                mButtonStatus = true;
            }
        //}

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMapReady = true;

        if (ActivityCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ((MainActivity) getActivity()).requestLocationPermission();
        } else {
            mMap.setMyLocationEnabled(true);
        }

        switch (instanceId) {
            case 1:
                float mapZoomMaps = preferences.getFloat(Constants.MAPS_ZOOM_OBJ, Constants.DEFAULT_ZOOM);
                double latitude = Double.longBitsToDouble(preferences.getLong(Constants.LAST_LATITUDE, 0l));
                double longitude = Double.longBitsToDouble(preferences.getLong(Constants.LAST_LONGITUDE, 0l));
                if (latitude != 0 && longitude != 0) {
                    LatLng latLng = new LatLng(latitude, longitude);
                    CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, mapZoomMaps);
                    mMap.moveCamera(cameraUpdate);
                }
                break;
            case 2:
                float mapZoomTrace = preferences.getFloat(Constants.TRACE_ZOOM_OBJ, Constants.DEFAULT_ZOOM);
                ArrayList<LatLng> coordList = ((TraceActivity)getActivity()).getCoordiates();
                addPolylines(coordList);
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(coordList.get(coordList.size()-1), mapZoomTrace);
                mMap.moveCamera(cameraUpdate);
                break;
        }

        mMap.setOnCameraMoveListener(new GoogleMap.OnCameraMoveListener() {
            @Override
            public void onCameraMove() {
                CameraPosition cameraPosition = mMap.getCameraPosition();
                float zoom = cameraPosition.zoom;
                SharedPreferences.Editor editor = preferences.edit();
                switch (instanceId) {
                    case 1:
                        editor.putFloat(Constants.MAPS_ZOOM_OBJ, zoom);
                        editor.commit();
                        break;
                    case 2:
                        editor.putFloat(Constants.TRACE_ZOOM_OBJ, zoom);
                        editor.commit();
                        break;
                }
            }
        });
    }

    protected void updateLocationOnMap(LatLng latlng, float zoom){
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latlng, zoom);
        mMap.animateCamera(cameraUpdate);
    }

    protected void addPolylines(ArrayList<LatLng> coordList){
        if(!coordList.isEmpty()) {
            PolylineOptions  polylineOptions = new PolylineOptions();
            polylineOptions.addAll(coordList);
            polylineOptions
                    .width(5)
                    .color(Color.RED);
            polylines.add(mMap.addPolyline(polylineOptions));
        }
    }

    protected void removePolylines(){
        if(!polylines.isEmpty()) {
            for(Polyline line : polylines)
                line.remove();
            polylines.clear();
        }
    }

    public boolean ismMapReady() {
        return mMapReady;
    }

    public GoogleMap getGoogleMap(){
        return mMap;
    }

}