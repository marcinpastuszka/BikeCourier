package com.debug.bikecourier;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.debug.bikecourier.model.Locations;
import com.debug.bikecourier.model.Trace;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;

public class TraceActivity extends AppCompatActivity {

    private static final String TAG = "TraceActivity";
    private static final int GRAPH_LOCATION = 1;
    private static final int GRAPH_ACCELEROMETER = 2;

    private SectionsPagerAdapter mSectionsPagerAdapter;

    private ViewPager mViewPager;
    private MapsFragment mapsFragment;
    private DetailsFragment detailsFragment;
    private GraphFragment graphFragmentLoc;
    private GraphFragment graphFragmentAcc;
    private MotionService mMotionService;
    ArrayList<Locations> arrayOfLocations;
    private boolean mBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Create the adapter that will return a fragment for each of the three primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        mViewPager.setCurrentItem(1);

        arrayOfLocations = this.getIntent().getParcelableArrayListExtra(Constants.LOCATION_LIST);
        Trace trace = this.getIntent().getParcelableExtra(Constants.TRACE_OBJECT);
        long idTrace = trace.getId();

        mapsFragment = MapsFragment.newInstance(2);
        detailsFragment = DetailsFragment.newInstance(trace);
        graphFragmentLoc = GraphFragment.newInstance(idTrace, GRAPH_LOCATION);
        graphFragmentAcc = GraphFragment.newInstance(idTrace, GRAPH_ACCELEROMETER);
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindSevices();}

    @Override
    protected void onStop() {
        super.onStop();
        unBindServices();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    public ArrayList<LatLng> getCoordiates(){
        if(!arrayOfLocations.isEmpty()){
            ArrayList<LatLng> list = new ArrayList<>();
            for(Locations loc: arrayOfLocations){
                list.add(new LatLng(loc.getLatitude(), loc.getLongitude()));
            }
            return list;
        } else
            return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivity(i);
        }

        return super.onOptionsItemSelected(item);
    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return detailsFragment;
                case 1:
                    return mapsFragment;
                case 2:
                    return graphFragmentLoc;
                case 3:
                    return graphFragmentAcc;
                default:
                    break;
            }

            return null;
        }

        @Override
        public int getCount() {
            return 4;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Info";
                case 1:
                    return "Maps";
                case 2:
                    return "Speed";
                case 3:
                    return "Acceleration";
            }
            return null;
        }
    }

    protected void bindSevices() {
        Intent intent = new Intent(this, MotionService.class);
        bindService(intent, mConnectionService, Context.BIND_AUTO_CREATE);
    }

    protected void unBindServices() {
        if (mBound) {
            unbindService(mConnectionService);
            mBound = false;
        }
    }

    private ServiceConnection mConnectionService = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            MotionService.LocalBinder binder = (MotionService.LocalBinder) service;
            mMotionService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
}
