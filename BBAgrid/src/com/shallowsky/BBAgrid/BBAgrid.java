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

//import com.google.android.gms.location.LocationResult;

import java.util.Set;
import java.util.HashSet;

public class BBAgrid extends Activity {

    private static final int REQUEST_ENABLE_BT = 1;

    Button mBtnCheckLoc;
    TextView mOutput;
    //MyLocation mGetLoc;
    //LocationResult mLocationResult = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mBtnCheckLoc = (Button)findViewById(R.id.checkloc);
        mOutput = (TextView)findViewById(R.id.output);

        /*
        mGetLoc = new MyLocation();

        mLocationResult = new LocationResult() {
            @Override
            public void gotLocation(Location loc) {
                mOutput.setText(loc.getLatitude() + "\n"
                                + loc.getLongitude() + "\n"
                                + loc.getAltitude());
            }
        };
        */

        updateLoc();

        mBtnCheckLoc.setOnClickListener(btnUpdateOnClickListener);

        /*
        registerReceiver(ActionFoundReceiver,
          new IntentFilter(BluetoothDevice.ACTION_FOUND));
         */
    }

    /*
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(ActionFoundReceiver);
    }
    */

    private void updateLoc() {
        LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        mOutput.setText("\n\n\n"
                        + loc.getLatitude() + "\n"
                        + loc.getLongitude() + "\n"
                        + loc.getAltitude());
    }

    /*
    private void updateLoc() {
        LocationResult loc;
        if (!mGetLoc.getLocation(this, loc))
            mOutput.setText("Couldn't get location");
    }
    */

    private Button.OnClickListener btnUpdateOnClickListener
        = new Button.OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    updateLoc();
                }};

    /*
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            CheckBlueToothState();
        }
    }

    private final BroadcastReceiver ActionFoundReceiver
        = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device
                      = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (mMacSet.contains(device.getAddress()))
                        return;
                    mMacSet.add(device.getName());
                    mBTArrayAdapter.add(device.getName() + "\n"
                                        + device.getAddress());
                    mBTArrayAdapter.notifyDataSetChanged();
                }
            }};
    */

    private class MyLocationListener implements LocationListener {

        public void onLocationChanged(Location location) {
            String message = String.format(
                                           "New Location \n Longitude: %1$s \n Latitude: %2$s",
                                           location.getLongitude(), location.getLatitude()
                                           );

            Toast.makeText(BBAgrid.this, message, Toast.LENGTH_LONG).show();
        }

        public void onStatusChanged(String s, int i, Bundle b) {
            Toast.makeText(BBAgrid.this, "Provider status changed",
                           Toast.LENGTH_LONG).show();
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
