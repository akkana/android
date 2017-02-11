package com.shallowsky.BBAgrid;

import android.os.Bundle;

import android.app.Activity;

import android.content.Context;
//import android.content.BroadcastReceiver;
//import android.content.Intent;
//import android.content.IntentFilter;

import android.view.View;

import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;

import java.util.Set;
import java.util.HashSet;

public class BBAgrid extends Activity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final long MIN_TIME_BETWEEN_UPDATES = 30000;   // msec
    private static final float MIN_DISTANCE_CHANGE = 50;          // m

    Button mBtnCheckLoc;
    TextView mOutput;
    LocationManager mLocMgr;

    int mSequence = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mBtnCheckLoc = (Button)findViewById(R.id.checkloc);
        mOutput = (TextView)findViewById(R.id.output);

        mLocMgr = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        mLocMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                                       MIN_TIME_BETWEEN_UPDATES,
                                       MIN_DISTANCE_CHANGE,
                                       new MyLocationListener());

        //updateLoc();

        mBtnCheckLoc.setOnClickListener(btnUpdateOnClickListener);
    }

    public void updateText(Location loc, String where) {
        // This causes crashes:
        //mOutput.setText(String.format("(%d %s)\n\n%.4s\n%.4f\n%.0f",
        // but positional formatting doesn't:
        mOutput.setText(String.format("(%1$s %2$s)\n\n%3$s\n%4$f\n%5$f",
                                      where, mSequence,
                                      loc.getLongitude(),
                                      loc.getLatitude(),
                                      loc.getAltitude()));
        mSequence += 1;
    }

    private Button.OnClickListener btnUpdateOnClickListener
        = new Button.OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    updateLoc();
                }};

    private void updateLoc() {
        Location loc = mLocMgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        updateText(loc, "button");
    }

    private class MyLocationListener implements LocationListener {

        public void onLocationChanged(Location loc) {
            updateText(loc, "loc changed");
        }

        public void onStatusChanged(String providerStr, int status, Bundle b) {
            String s;
            if (status == LocationProvider.OUT_OF_SERVICE)
                s = "Status changed: Out of service";
            else if (status == LocationProvider.AVAILABLE)
                s = "Status changed: Available";
            else if (status == LocationProvider.TEMPORARILY_UNAVAILABLE)
                s = "Status changed: Temporarily unavailable";
            else
                s = "Provider status changed to " + status;
            Toast.makeText(BBAgrid.this, s, Toast.LENGTH_LONG).show();
        }

        public void onProviderDisabled(String s) {
            Toast.makeText(BBAgrid.this,
                           "Provider disabled by the user. GPS turned off",
                           Toast.LENGTH_LONG).show();
        }

        public void onProviderEnabled(String s) {
            Toast.makeText(BBAgrid.this,
                           "Provider enabled by the user. GPS turned on",
                           Toast.LENGTH_LONG).show();
        }
    }
}
