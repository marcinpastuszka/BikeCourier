package com.debug.bikecourier;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.PersistableBundle;

import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.model.LatLng;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private SectionsPagerAdapter mSectionsPagerAdapter;

    private ViewPager mViewPager;
    private MapsFragment mapsFragment;
    private TraceListFragment traceListFragment;
    private MotionService mMotionService;
    private boolean mBound = false;
    private boolean mServiceStarted = false;
    private IntentFilter mFilter;
    private SharedPreferences preferences;
    protected static final int REQUEST_CHECK_SETTINGS = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;
    private static final int FILE_CHOOSER = 3;

    private MyBroadcastReceiver mBroadcastReceiver = new MyBroadcastReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if(savedInstanceState != null){
            mServiceStarted = savedInstanceState.getBoolean("serviceStarted", false);
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Create the adapter that will return a fragment for each of the three primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        mapsFragment = MapsFragment.newInstance(1);
        traceListFragment = new TraceListFragment();

        if(isServiceRunning(MotionService.class)){
            mServiceStarted = true;
        }

        if(!isGooglePlayServicesAvailable(this)){
            Toast.makeText(this, getString(R.string.google_play_services_not_available), Toast.LENGTH_SHORT).show();
            stopServices();
        }
        //importProfilesFromCSV();
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean("serviceStarted", mServiceStarted);
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindSevice();
        boolean runBackgroundService = preferences.getBoolean("run_service_on_start", false);
        if(runBackgroundService)
            startServices();
        mFilter = new IntentFilter(Constants.UPDATE_MAP);
        mFilter.addAction(Constants.SETTINGS_REQUEST);
        mFilter.addAction(Constants.CLEAR_MAP);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, mFilter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unBindService();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unBindService();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);

    }

    @Override
    protected void onResume() {
        super.onResume();
        bindSevice();
        mFilter = new IntentFilter(Constants.UPDATE_MAP);
        mFilter.addAction(Constants.SETTINGS_REQUEST);
        mFilter.addAction(Constants.CLEAR_MAP);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, mFilter);

        if(mServiceStarted)
            mapsFragment.changeStartButton();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopServices();
    }

    @Override
    public void onBackPressed() {
        if (mServiceStarted) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.exit_title)
                    .setMessage(R.string.exit_message)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            mMotionService.stopTraining();
                            stopServices();
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert).show();
        } else
            finish();
    }

    public void settingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mMotionService.mLocationRequest);
        builder.setAlwaysShow(true); //this is the key ingredient

        mMotionService.buildGoogleApiClient();

        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mMotionService.mGoogleApiClient, builder.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                final LocationSettingsStates state = result.getLocationSettingsStates();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        // All location settings are satisfied. The client can initialize location requests here.
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied. But could be fixed by showing the user a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(), and check the result in onActivityResult().
                            status.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException e) {
                            // Ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way to fix the settings so we won't show the dialog.
                        break;
                }
            }
        });
    }

    protected void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (mapsFragment.ismMapReady())
                mapsFragment.onMapReady(mapsFragment.getGoogleMap());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        //ok
                        break;
                    case Activity.RESULT_CANCELED:
                        settingsRequest();//keep asking if imp or do whatever
                        break;
                }
                break;
            case FILE_CHOOSER:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        //Uri uri = Uri.fromFile(new File(data.getData().getPath()));
                        //importProfilesFromCSV();
                        break;
                    case Activity.RESULT_CANCELED:
                        break;
                }
                break;
        }
    }

    protected void startServices() {
        mServiceStarted = true;
        Intent motionIntent = new Intent(MainActivity.this, MotionService.class);
        startService(motionIntent);
        mapsFragment.changeStartButton();
        bindSevice();
    }

    protected void stopServices() {
        if (mServiceStarted){
            mMotionService.hideForegroundNotification();
            stopService(new Intent(MainActivity.this, MotionService.class));
            mServiceStarted = false;
            mapsFragment.changeStartButton();
        }
    }

    protected void bindSevice() {
        Intent intent = new Intent(this, MotionService.class);
        bindService(intent, mConnectionService, Context.BIND_AUTO_CREATE);
    }

    protected void unBindService() {
        if (mBound) {
            unbindService(mConnectionService);
            mBound = false;
        }
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void refreshList(){
        traceListFragment.refreshList();
    }

    public boolean isGooglePlayServicesAvailable(Context context){
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
        return resultCode == ConnectionResult.SUCCESS;
    }

    public class MyBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Constants.UPDATE_MAP)) {
                if (mapsFragment.ismMapReady()) {
                    double latitude = intent.getDoubleExtra(Constants.LATITUDE, 0d);
                    double longitude = intent.getDoubleExtra(Constants.LONGITUDE, 0d);
                    ArrayList<LatLng> coordinateList = intent.getParcelableArrayListExtra(Constants.COORDINATE_LIST);
                    float zoom = mapsFragment.getGoogleMap().getCameraPosition().zoom;
                    mapsFragment.removePolylines();
                    mapsFragment.addPolylines(coordinateList);
                    mapsFragment.updateLocationOnMap(new LatLng(latitude, longitude), zoom);
                }
            } else if(intent.getAction().equals(Constants.SETTINGS_REQUEST)) {
                settingsRequest();
            } else if(intent.getAction().equals(Constants.CLEAR_MAP)) {
                mapsFragment.removePolylines();
            }
        }
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

    public void openFileChooser(){
        Intent chooseFile;
        Intent intent;
        chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.setType("file/*");
        intent = Intent.createChooser(chooseFile, "Choose a file");
        startActivityForResult(intent, FILE_CHOOSER);
    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return mapsFragment;
                case 1:
                    return traceListFragment;
                default:
                    break;
            }

            return null;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Maps";
                case 1:
                    return "List";
            }
            return null;
        }
    }

    private ServiceConnection mConnectionService = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                // We've bound to LocalService, cast the IBinder and get LocalService instance
                MotionService.LocalBinder binder = (MotionService.LocalBinder) service;
                mMotionService = binder.getService();
                mBound = true;
            }

            @Override
            public void onServiceDisconnected(ComponentName arg0) {
                mBound = false;
            }
        };

    public boolean ismServiceStarted() {
        return mServiceStarted;
    }

    public boolean ismBound() {
        return mBound;
    }

    public MotionService getmMotionService() {
        return mMotionService;
    }

    public void importProfilesFromCSV(){
        deleteProfiles();

        InputStream is;
        AssetManager am = getAssets();
        try {
            is = am.open("profiles.csv");
            BufferedReader buffer = new BufferedReader(new InputStreamReader(is));
            String line = "";
            while ((line = buffer.readLine()) != null) {
                String[] columns = line.split(",");
                if (columns.length != 5) {
                    continue;
                }
                double x = Double.parseDouble(columns[0].trim());
                double y = Double.parseDouble(columns[1].trim());
                double z = Double.parseDouble(columns[2].trim());
                double v = Double.parseDouble(columns[3].trim());
                long id = Long.parseLong(columns[4].trim());
                createProfile(x,y,z,v,id);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public long createProfile(double x, double y, double z, double vector_length, long id) {

        ContentValues values = new ContentValues();
        values.put(DatabaseProvider.KEY_X, x);
        values.put(DatabaseProvider.KEY_Y, y);
        values.put(DatabaseProvider.KEY_Z, z);
        values.put(DatabaseProvider.KEY_VECTOR_LENGTH, vector_length);
        values.put(DatabaseProvider.KEY_PROFILE_ID, id);
        Uri uri = getContentResolver().insert(DatabaseProvider.CONTENT_URI_PROFILE, values);

        return Long.valueOf(uri.getLastPathSegment());
    }

    public long deleteProfiles() {
        String selectionClause = null;
        String[] selectionArgs = null;
        return getContentResolver().delete(DatabaseProvider.CONTENT_URI_PROFILE, selectionClause, selectionArgs);
    }

}
