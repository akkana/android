/* XXX TO DO:
   https://developer.android.com/reference/android/location/LocationManager.html#requestLocationUpdates%28java.lang.String,%20long,%20float,%20android.location.LocationListener%29
   says don't use interval < 5 min if in the background
   and that using minDistance at all will be a power problem.
   Example with two listeners:
   http://stackoverflow.com/questions/14478179/background-service-with-location-listener-in-android

   - grey out the quit button if we're already not doing updates.

   - Change the MIN_DISTANCE_CHANGE if we're near a block boundary:
     maybe to something like half the distance to the nearest boundary,
     or 10m if we're within 100m of a boundary.
     (Mouser says his GPS noise is between 5 and 9 meters w/good reception.)

   - investigate going to sleep when the app isn't in the foreground,
     and updating position upon being displayed.
     (But maybe don't make that mandatory, see next item.)
 */

package com.shallowsky.BBAgrid;

import android.os.Bundle;

import android.app.Activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import android.view.View;

import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;

import android.util.Log;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class BBAgrid extends Activity {

    private static final int REQUEST_ENABLE_BT = 1;

    // How often will we update when the app is in the foreground
    // and we're far from a grid boundary?
    private static final long UPDATE_TIME_FOREGROUND = 15000;     // msec

    // How often will we update when the app is in the background
    // and we're far from a grid boundary?
    private static final long UPDATE_TIME_BACKGROUND = 30000;     // msec

    // Don't update more often than this, even if close to a boundary.
    private static final long MIN_UPDATE_TIME = 8000;             // msec

    // Would be nice to specify min distance, but that still requires
    // the device to wake up frequently to check location, so Google
    // warns it's not good for battery life.
    //private static final float MIN_DISTANCE_CHANGE = 10;        // m

    // If we're closer than this (meters) to a boundary, update faster:
    private static final double NEAR_BOUNDARY = 250;

    public static final double EARTH_RADIUS = 6371000.;           // m

    private static final int BBA_NOTIFICATION_ID = 1;

    // Update time: how long between GPS requests?
    // 0 means we're only requesting manual updates.
    // Anything positive is the time in milliseconds between updates,
    // which will be adjusted depending on how close we are to a boundary.
    long mUpdateTime = UPDATE_TIME_FOREGROUND;

    DrawGridView mDrawGridView = null;
    LocationManager mLocMgr;
    MyLocationListener mGPSLocListener = null;

    // NW corners of every grid block, including fake blocks to the
    // S, E and SE to close the blocks on the edges.
    double[][][] mNWCoords;
    int mNumRows;
    int[] mStartColumn;
    int[] mColsInRow;

    int mSequence = 0;    // Mostly for debugging

    // Current grid block
    int mCurRow;
    int mCurCol;
    // distances to adjacent blocks, in meters:
    double mWestDist;
    double mEastDist;
    double mNorthDist;
    double mSouthDist;

    boolean mForeground = true;
    private SharedPreferences mSharedPreferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDrawGridView = new DrawGridView(this);

        setContentView(R.layout.main);

        // All the grid blocks and their coordinates.
        mNWCoords = new double[][][] {
            {    // row 0
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
            },
            {    // row 1
                { 0., 0. },
                { -106.399763, 35.973427 },
                { -106.371377, 35.973459 },
                { -106.343656, 35.973484 },
                { -106.315936, 35.973502 },
                { -106.288215, 35.973514 },
                { -106.260495, 35.973520 },
                { -106.232774, 35.973519 },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
            },
            {    // row 2
                { 0., 0. },
                { -106.399721, 35.950894 },
                { -106.371342, 35.950926 },
                { -106.343630, 35.950951 },
                { -106.315917, 35.950969 },
                { -106.288204, 35.950981 },
                { -106.260492, 35.950987 },
                { -106.232779, 35.950986 },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
            },
            {    // row 3
                { 0., 0. },
                { -106.399678, 35.928361 },
                { -106.371308, 35.928393 },
                { -106.343603, 35.928418 },
                { -106.315898, 35.928436 },
                { -106.288193, 35.928448 },
                { -106.260489, 35.928454 },
                { -106.232784, 35.928453 },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
            },
            {    // row 4
                { 0., 0. },
                { -106.399636, 35.905828 },
                { -106.371273, 35.905860 },
                { -106.343577, 35.905885 },
                { -106.315880, 35.905903 },
                { -106.288183, 35.905915 },
                { -106.260486, 35.905921 },
                { -106.232789, 35.905920 },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
            },
            {    // row 5
                { 0., 0. },
                { -106.399593, 35.883295 },
                { -106.371239, 35.883327 },
                { -106.343550, 35.883351 },
                { -106.315861, 35.883370 },
                { -106.288172, 35.883382 },
                { -106.260483, 35.883387 },
                { -106.232794, 35.883387 },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
            },
            {    // row 6
                { 0., 0. },
                { -106.399551, 35.860761 },
                { -106.371205, 35.860793 },
                { -106.343524, 35.860818 },
                { -106.315842, 35.860836 },
                { -106.288161, 35.860848 },
                { -106.260480, 35.860854 },
                { -106.232798, 35.860853 },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
            },
            {    // row 7
                { -106.426517, 35.838191 },
                { -106.399509, 35.838228 },
                { -106.371170, 35.838260 },
                { -106.343497, 35.838285 },
                { -106.315824, 35.838303 },
                { -106.288150, 35.838315 },
                { -106.260477, 35.838320 },
                { -106.232803, 35.838320 },
                { -106.205130, 35.838313 },
                { -106.177456, 35.838299 },
                { -106.149783, 35.838279 },
            },
            {    // row 8
                { -106.426467, 35.815658 },
                { -106.399467, 35.815695 },
                { -106.371136, 35.815726 },
                { -106.343471, 35.815751 },
                { -106.315805, 35.815769 },
                { -106.288139, 35.815781 },
                { -106.260474, 35.815787 },
                { -106.232808, 35.815786 },
                { -106.205142, 35.815779 },
                { -106.177477, 35.815766 },
                { -106.149783, 35.815766 },
            },
            {    // row 9
                { -106.426467, 35.793248 },
                { -106.399467, 35.793248 },
                { -106.371136, 35.793248 },
                { -106.343471, 35.793248 },
                { -106.315805, 35.793248 },
                { -106.288129, 35.793248 },
                { -106.260471, 35.793253 },
                { -106.232813, 35.793253 },
                { -106.205155, 35.793245 },
                { -106.177497, 35.793232 },
                { 0., 0. },
            },
            {    // row 10
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
                { -106.288129, 35.770720 },
                { -106.260468, 35.770720 },
                { -106.232818, 35.770719 },
                { -106.205168, 35.770712 },
                { -106.177497, 35.770712 },
                { 0., 0. },
            },
            {    // row 11
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
                { 0., 0. },
                { -106.260465, 35.748186 },
                { -106.232823, 35.748185 },
                { -106.205180, 35.748178 },
                { 0., 0. },
                { 0., 0. },
            },
        };
        mNumRows = 10;
        mStartColumn = new int[] { 1, 1, 1, 1, 1, 1,  0, 0, 5, 6 };
        mColsInRow = new int[] { 6, 6, 6, 6, 6, 6, 10, 9, 4, 2 };

        mDrawGridView = (DrawGridView)findViewById(R.id.drawgridview);

        mLocMgr = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        mGPSLocListener = new MyLocationListener();

        Button btn = (Button)findViewById(R.id.checkloc);
        btn.setOnClickListener(checkLocation);

        btn = (Button)findViewById(R.id.toggleChecking);
        btn.setOnClickListener(toggleChecking);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    /********  APP LIFECYCLE FUNCTIONS *******/
    /**
     * Main entry point to the app.
     *
     * This is called after onCreate() or any time when the system
     * switches back to the app. Use it to load data, return to saved
     * state and add custom behaviour.
     */
    @Override
    public void onResume() {
        super.onResume();
        Log.d("BBAgrid", "**** onResume");

        mForeground = true;

        mUpdateTime = mSharedPreferences.getLong("updateTime",
                                                 UPDATE_TIME_FOREGROUND);
        // Don't pay attention to the actual update time;
        // always reset to UPDATE_TIME_FOREGROUND.
        if (mUpdateTime != 0)
            mUpdateTime = UPDATE_TIME_FOREGROUND;

        updateButtonState();

        mGPSLocListener.startUpdating();
    }

    public void updateButtonState() {
        // Update the state of the buttons according to the update time.
        // If we're updating, then the check button isn't needed.
        // If we're not updating, the user will tap Check when they
        // want a new update.
        Button toggleBtn = (Button)findViewById(R.id.toggleChecking);
        Button checkBtn = (Button)findViewById(R.id.checkloc);
        if (mUpdateTime != 0) {
            toggleBtn.setText("Stop updating");
            checkBtn.setEnabled(false);
        } else {
            toggleBtn.setText("Start updating");
            checkBtn.setEnabled(true);
        }
    }

    /**
     *
     * This is basically last point Android system promises you can do anything.
     * You can safely ignore other lifecycle methods.
     */
    @Override
    public void onPause() {
        super.onPause();
        Log.d("BBAgrid", "**** onPause");

        mForeground = false;
        if (mUpdateTime != 0)
            resetUpdateTime();

        saveUpdateTime();
    }

    public void saveUpdateTime() {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putLong("updateTime", mUpdateTime);
        editor.commit();
    }

    /********  GEO CALCULATION FUNCTIONS *******/

    public void locationToGridBlock(Location loc) {
        // Given a location, figure out what LA-BBA block it's in
        // and return that as a string. Also fills in mCurRow, mCurCol.
        double lat = loc.getLatitude();
        double lon = loc.getLongitude();

        for (int row = 1; row < mNumRows+1; ++row) {
            for (int col = mStartColumn[row-1];
                 col < mStartColumn[row-1] + mColsInRow[row-1]; ++col) {
                double west = (mNWCoords[row][col][0]
                               + mNWCoords[row+1][col][0]) / 2.;
                double east = (mNWCoords[row][col+1][0]
                               + mNWCoords[row+1][col+1][0]) / 2.;
                double north = (mNWCoords[row][col][1]
                                + mNWCoords[row][col+1][1]) / 2.;
                double south = (mNWCoords[row+1][col][1]
                                + mNWCoords[row+1][col+1][1]) / 2.;
                if (lat <= north && lat > south && lon >= west && lon < east) {
                    mCurRow = row;
                    mCurCol = col;
                    findBlockDistances(lat, lon);
                    return;
                }
            }
        }
        mCurRow = mCurCol = -1;
    }

    public void findBlockDistances(double lat, double lon) {
        // After determining mCurRow and mCurCol, calculate
        // mWestDist, mEastDist, mNorthDist, mSouthDist.
        double westGridLon = (mNWCoords[mCurRow][mCurCol][0]
                              + mNWCoords[mCurRow+1][mCurCol][0]) / 2.;
        mWestDist = haversine_dist(lat, westGridLon, lat, lon);

        double eastGridLon = (mNWCoords[mCurRow][mCurCol+1][0]
                              + mNWCoords[mCurRow+1][mCurCol+1][0]) / 2.;
        mEastDist = haversine_dist(lat, eastGridLon, lat, lon);

        double northGridLat = (mNWCoords[mCurRow][mCurCol][1]
                               + mNWCoords[mCurRow][mCurCol+1][1]) / 2.;
        mNorthDist = haversine_dist(northGridLat, lon, lat, lon);

        double southGridLat = (mNWCoords[mCurRow+1][mCurCol][1]
                               + mNWCoords[mCurRow+1][mCurCol+1][1]) / 2.;
        mSouthDist = haversine_dist(southGridLat, lon, lat, lon);

        mDrawGridView.setDistances(mWestDist, mEastDist,
                                   mNorthDist, mSouthDist);
    }

    public double haversine_dist(double lat1, double lon1,
                                 double lat2, double lon2) {
        // Haversine distance between two points, expressed in meters.

        double d_lat = Math.toRadians(lat1 - lat2);
        double d_lon = Math.toRadians(lon1 - lon2);
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double a = Math.sin(d_lat / 2.) * Math.sin(d_lat / 2.) +
            Math.sin(d_lon / 2.) * Math.sin(d_lon / 2.) *
            Math.cos(lat1) * Math.cos(lat2);
        double c = 2. * Math.atan2(Math.sqrt(a), Math.sqrt(1. - a));
        return EARTH_RADIUS * c;
    }

    /******** FUNCTIONS TO MANAGE UPDATES AND UI *******/

    /**
     * Calculate update time according to the base time
     * (which reflects whether we're in foreground or background)
     * and the distance to the nearest grid boundary
     * (distances should already have been calculated).
     * If we're near a grid boundary, update a lot more frequently.
     */
    public void resetUpdateTime() {
        long basetime = (mForeground ? UPDATE_TIME_FOREGROUND
                                     : UPDATE_TIME_BACKGROUND);
        double mindist = Math.min(mWestDist,
                                  Math.min(mEastDist,
                                           Math.min(mNorthDist, mSouthDist)));
        long msecs;

        Log.d("BBAgrid", "resetUpdateTime, mindist = " + mindist);
        if (mindist > NEAR_BOUNDARY) {
            msecs = basetime;
            Log.d("BBAgrid", "Not near a boundary, using " + msecs);
        }
        else {
            // We're near a boundary. How near? Adjust update time accordingly.
            // Figure a typical walking speed is of 50 meters per minute
            // (just under 2mph).

            // Very near a boundary, we'd like to update so it's unlikely
            // the user will travel more than 10m before the next update.
            // At 50 m/min that's about 12 seconds (let's say 10).
            // Farther away, all that matters is that we haven't gotten
            // more than 80% of the distance to the boundary before the
            // next update.
            msecs = (long)(mindist * 600.);
            //Log.d("BBAgrid", "Calculated " + msecs + " msecs");
            if (msecs > basetime)
                msecs = basetime;
            else if (msecs < MIN_UPDATE_TIME)
                msecs = MIN_UPDATE_TIME;
        }

        // Has it changed much since the last update?
        // Don't want to be constantly changing the location manager
        // if the differences are only trivial.
        if (Math.abs(msecs - mUpdateTime) > 1000) {
            mUpdateTime = msecs;

            mLocMgr.removeUpdates(mGPSLocListener);
            mLocMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                                           mUpdateTime,
                                           0,   //MIN_DISTANCE_CHANGE,
                                           mGPSLocListener);
            Log.d("BBAgrid", "*** Changed update time to " + mUpdateTime);
        }
    }

    public void updateUI(Location loc, String where) {
        try {
            locationToGridBlock(loc);
        } catch (final Exception e) {
            mDrawGridView.setCommentString("No location yet");
            mDrawGridView.setRowCol(-1, -1);
            mDrawGridView.redraw();
            return;
        }


        // mCurRow and mCurCol, plus mWestDist, etc., are now set.
        if (mCurRow < 0 || mCurCol < 0) {
            mDrawGridView.setRowCol(-1, -1);
            mDrawGridView.redraw();
            return;
        }

        mDrawGridView.setRowCol(mCurRow, mCurCol);

        // For debugging, include the serial of the update
        // and whether it came from the request or the button.
        // Comment this out for real users.
        mDrawGridView.setCommentString("(" + where + " " + mSequence + ", "
                                       + (int)(mUpdateTime/1000) + ")");

        mDrawGridView.redraw();

        // And put a notification in the status bar, too.
        Intent resultIntent = new Intent(this, BBAgrid.class);
        PendingIntent resultPendingIntent =
            PendingIntent.getActivity(this,
                                      0,
                                      resultIntent,
                                      PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder mBuilder
            = new Notification.Builder(getApplicationContext())
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("LABBA Block " + mCurRow + mCurCol)
            .setContentText("Los Alamos Breeding Bird Atlas")
            .setContentIntent(resultPendingIntent);

        NotificationManager manager
            = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(BBA_NOTIFICATION_ID, mBuilder.build());

        mSequence += 1;
    }

    // Called when the user taps the Start/Stop checking button.
    // This should toggle whether we're checking regularly.
    private Button.OnClickListener toggleChecking
        = new Button.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (mUpdateTime > 0) {
                    // We were updating, so stop.
                    mLocMgr.removeUpdates(mGPSLocListener);
                    mUpdateTime = 0;
                    mDrawGridView.setCommentString("Not updating");
                    mDrawGridView.redraw();
                }
                else {
                    // start updating
                    mUpdateTime = UPDATE_TIME_FOREGROUND;
                    mGPSLocListener.startUpdating();
                }

                // Either way, update the buttons and save the new
                // update time in preferences,
                // so if the app exits, we'll start up in the same mode.
                updateButtonState();
                saveUpdateTime();
            }};

    // Called when the user taps the Check Location button.
    // If we're updating anyway, this should do nothing
    // (and should be greyed out).
    // If we're not updating, it should do a single update.
    private Button.OnClickListener checkLocation
        = new Button.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (mUpdateTime != 0) {
                    Log.d("BBAgrid",
                          "Check Location should have been greyed out");
                    return;
                }
                // Start a single update, with mUpdateTime = 0
                mUpdateTime = 0;
                mGPSLocListener.startUpdating();
            }};

    /******** THE LOCATION LISTENER *******/

    private class MyLocationListener implements LocationListener {

        public void onLocationChanged(Location loc) {
            updateUI(loc, "listener");

            // If mUpdateTime is zero, that probably means that
            // the user requested a one-time update.
            // LocationListener has no way to request a one-time update,
            // so the only option is to set a timeout then remove it
            // the first time we get called back.
            if (mUpdateTime == 0)
                mLocMgr.removeUpdates(mGPSLocListener);

            // Otherwise, reset the update time according to
            // our distance from a boundary.
            else
                resetUpdateTime();
        }

        public void startUpdating() {
            Location loc
                = mLocMgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            updateUI(loc, "starting");

            // Start requesting updates. But if mUpdateTime is 0,
            // we'll stop updating after the first one comes in.
            mLocMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                                           UPDATE_TIME_FOREGROUND,
                                           0,   //MIN_DISTANCE_CHANGE,
                                           this);
            Log.d("BBAgrid", "*** Location Listener: startUpdating "
                  + mUpdateTime);
        }

        public void stopUpdating() {
            mLocMgr.removeUpdates(mGPSLocListener);
            mUpdateTime = 0;
            Log.d("BBAgrid", "*** Location Listener: stopUpdating");
        }

        // Methods Java requires we define whether we need them or not:

        public void onStatusChanged(String providerStr, int status, Bundle b) {
            /*
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
             */
        }

        public void onProviderEnabled(String s) {
            /*
            Toast.makeText(BBAgrid.this,
                           "Provider enabled by the user. GPS turned on",
                           Toast.LENGTH_LONG).show();
             */
        }

        public void onProviderDisabled(String s) {
            /*
            Toast.makeText(BBAgrid.this,
                           "Provider disabled by the user. GPS turned off",
                           Toast.LENGTH_LONG).show();
             */
        }
    }
}
