/* XXX TO DO:
   - Show distance to nearest other grid blocks.
   - Change the MIN_DISTANCE_CHANGE if we're near a block boundary:
     maybe to something like half the distance to the nearest boundary,
     or 10m if we're within 100m of a boundary.
     (Mouser says his GPS noise is between 5 and 9 meters w/good reception.)
   - investigate going to sleep when the app isn't in the foreground,
     and updating position upon being displayed.
     (But maybe don't make that mandatory, see next item.)
   - Notification in the status bar showing current grid.
     https://developer.android.com/guide/topics/ui/notifiers/notifications.html
   - Draw a picture of where we are in the grid block relative to other
     squares (Canvas?
     https://developer.android.com/reference/android/graphics/drawable/package-summary.html
     https://developer.android.com/training/custom-views/custom-drawing.html
 */

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

import android.util.Log;

//import java.util.HashTable;
//import java.util.Dictionary;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class BBAgrid extends Activity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final long MIN_TIME_BETWEEN_UPDATES = 15000;   // msec
    private static final float MIN_DISTANCE_CHANGE = 10;          // m

    public static final double EARTH_RADIUS = 6371000.;           // m

    Button mBtnCheckLoc;
    TextView mOutput;
    LocationManager mLocMgr;

    // NW corners of every grid block, including fake blocks to the
    // S, E and SE to close the blocks on the edges.
    double[][][] mNWCoords;
    int mNumRows;
    int[] mStartColumn;
    int[] mColsInRow;

    int mSequence = 0;    // Mostly for debugging

    // Current grid block and distance to adjacent blocks:
    int mCurRow;
    int mCurCol;
    double mWestDist;
    double mEastDist;
    double mNorthDist;
    double mSouthDist;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        //Log.d("BBAgrid", " Initialized mNWCoords:" + Arrays.deepToString(mNWCoords));
        mNumRows = 10;
        mStartColumn = new int[] { 1, 1, 1, 1, 1, 1,  0, 0, 5, 6 };
        mColsInRow = new int[] { 6, 6, 6, 6, 6, 6, 10, 9, 4, 2 };

        mBtnCheckLoc = (Button)findViewById(R.id.checkloc);
        mOutput = (TextView)findViewById(R.id.output);

        mLocMgr = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        mLocMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                                       MIN_TIME_BETWEEN_UPDATES,
                                       MIN_DISTANCE_CHANGE,
                                       new MyLocationListener());

        updateLoc();

        mBtnCheckLoc.setOnClickListener(btnUpdateOnClickListener);
    }

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

    public void updateText(Location loc, String where) {
        locationToGridBlock(loc);
        // mCurRow and mCurCol, plus mWestDist, etc., are now set.

        if (mCurRow < 0 || mCurCol < 0) {
            mOutput.setText("Outside the grid");
        }

        // This causes crashes:
        //    String.format("(%d %s)\n\n%.4s\n%.4f\n%.0f",
        // but positional formatting doesn't.

        /*
        mOutput.setText(String.format("Block %1$s\n\n%2$f\n%3$f\n%4$d m\n\n(%5$s %6$d)",
                                      block,
                                      loc.getLongitude(), loc.getLatitude(),
                                      (int)(loc.getAltitude()),
                                      where, mSequence));
         */
        String output = String.format("Block %1$d%2$d\n", mCurRow, mCurCol);
        if (mCurRow >= 0 && mCurCol >= 0) {
            if (mWestDist < mEastDist)
                output += String.format("\n%1$dm W to %2$d%3$d",
                                        (int)mWestDist, mCurRow, mCurCol-1);
            else
                output += String.format("\n%1$dm E to %2$d%3$d",
                                        (int)mEastDist, mCurRow, mCurCol+1);
            if (mNorthDist < mSouthDist)
                output += String.format("\n%1$dm N to %2$d%3$d",
                                        (int)mNorthDist, mCurRow-1, mCurCol);
            else
                output += String.format("\n%1$dm S to %2$d%3$d",
                                        (int)mSouthDist, mCurRow+1, mCurCol);
        }
        mOutput.setText(output);

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
