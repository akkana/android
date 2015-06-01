package com.shallowsky.FeedViewer;

/*
 * FeedViewer: an Android HTML reader optimized for reading short
 * RSS feeds downloaded from the web, then deleting them after reading.
 *
 * Copyright 2010-2012 by Akkana Peck <akkana@shallowsky.com>
 * This software is licensed under the terms of the GPLv2 or, at your
 * option, any later GPL version. Share and enjoy!
 */

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;

import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager.LayoutParams;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ScrollView;

public class FeedViewer extends Activity implements OnGestureListener {

    private GestureDetector detector;

    WebView mWebView;

    Button mBackButton;
    Button mFwdButton;
    Button mContentsButton; // Go to table of contents for this feed
    Button mFeedlistButton; // Go to list of feeds
    Button mDeleteButton;
    Button mBiggerButton;
    TextView mDocNameView;
    TextView mBatteryLevel;
    // XXX This needs to be revisited, obviously,
    // to allow the option of non external SD card too.
    String mWritableDir = "/mnt/extSdCard/Android/data/com.shallowsky.FeedViewer";

    // /sdcard and getExternalStorageDirectory() aren't actually the SD card;
    // the sdcard is /sdcard/sdcard or /mnt/sdcard/external_storage,
    // or, in KitKat, /mnt/extSdCard which has no relation to
    // getExternalStorageDirectory.
    String mStorage = Environment.getExternalStorageDirectory().getPath();
    String mMainBasePath = null;
    String[] mBasePaths = {
        mStorage + File.separator + "sdcard" + File.separator + "feeds",
        mStorage + File.separator + "external_sd" + File.separator + "feeds",
        mWritableDir,
        mStorage + File.separator + "feeds",
    };

    float mScreenWidth;
    float mScreenHeight;
    long mScrollLock = 0;  // used for delays for scrolling, avoiding longpress
    WebSettings mWebSettings; // Settings for the WebView, e.g. font size.

    FeedFetcher mFeedFetcher = null;
    Dialog mFeedFetcherDialog = null;
    TextView mFeedFetcherText = null;

    // Params that can be saved
    int mFontSize = 18;
    int mBrightness = 0;
    String mLastUrl = "";

    // From nev's helpful webview scroll and sdcard load demo:
    private boolean mSDCardMounted = false;

    private boolean mPageIsLoaded = false;

    private BroadcastReceiver mBroadcastReceiver;

    private SharedPreferences mSharedPreferences;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        detector = new GestureDetector(this, this);

        mWebView = (WebView) findViewById(R.id.webview);
        mWebSettings = mWebView.getSettings();
        mWebSettings.setJavaScriptEnabled(false);
        mFontSize = mWebSettings.getDefaultFontSize();

        mWebView.setWebViewClient(new WebViewClient() {
            /**
             * Sets up an onPageFinished() callback on mWebView,
             * from which it positions the scroll.
             *
             * Jumping through the hoops. We'll delay the calling of
             * scrollTo for N miliseconds after system loads the page.
             * This is needed since onPageFinished() can be called
             * before it loads whole page for some reason, so give the
             * system some time...
             */
            @Override
            public void onPageFinished(WebView webView, final String url) {
                Log.d("FeedViewer", "onPageFinished " + url);
                mWebView.postDelayed(new Runnable() {
                    // XXX Eclipse gives me a "Method must override
                    // superclass method" error if I include the
                    // Override. But will it work without it? Override
                    public void run() {
                        Log.d("FeedViewer", "pageFinished postDelayed " + url);
                        int scrollpos = getScrollFromPreferences(url);
                        // Scroll a little above the remembered
                        // position -- else rounding errors may scroll
                        // us too far down to where the most recently
                        // read story isn't visible, which is
                        // confusing to the user.
                        if (scrollpos > 0 && mWebView.getScrollY() == 0)
                            mWebView.scrollTo(0,
                                    (int)Math.round((mWebView.getContentHeight() * mWebView.getScale() * scrollpos - 1) / 100.0));
                        else if (scrollpos > 0)
                            Log.d("FeedViewer", "Not scrolling because page is already scrolled to " + mWebView.getScrollY());
                        mPageIsLoaded = true;
                        Log.d("FeedViewer", "Page finished: " + url
                              + " scrolled to " + scrollpos);
                    }
                }, 300);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                /* intercept all page load attempts */
                Log.d("FeedViewer", "shouldOverrideUrlLoading " + url);

                // Don't load if we're waiting for a longpress to time out:
                long scrollDelay = 2000;
                if (SystemClock.uptimeMillis() < (mScrollLock + scrollDelay)) {
                    return true;
                }

                // Otherwise, we'll try to load something.
                // First, save our position on the current page.
                showTextMessage("Saving scroll position");
                saveStateInPreferences(mWebView.getUrl());

                try {
                    URI uri = new URI(url);
                    if (uri.getScheme().equals("file")) {
                        // If it's file: then we're moving between
                        // internal pages. Go ahead and go to the
                        // link, saving settings.
                        mLastUrl = url;
                        //showTextMessage("will try to load " + url);
                        //saveSettings();
                        return false;
                    }
                    else {
                        // The scheme isn't file:// so we need to handle it.
                        handleExternalLink(url);
                        return true;
                    }
                } catch (URISyntaxException e) {
                    return false;
                }
            }

            @Override
            public void onReceivedError(WebView webview, int errorCode,
                    String description, String failingUrl) {
                if (failingUrl.startsWith("file://")) {
                    loadFeedList();
                    showTextMessage("Couldn't load " + failingUrl);
                }
            }
        });

        createBroadcastReceiver();

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mDocNameView = (TextView) findViewById(R.id.docName);
        mBatteryLevel = (TextView) findViewById(R.id.batteryLevel);

        mBackButton = (Button) findViewById(R.id.backButton);
        mBackButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                goBack();
            }
        });

        mContentsButton = (Button) findViewById(R.id.contentsButton);
        mContentsButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                tableOfContents();
            }
        });

        mFwdButton = (Button) findViewById(R.id.fwdButton);
        mFwdButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mWebView.goForward();
            }
        });

        mFeedlistButton = (Button) findViewById(R.id.feedListButton);
        mFeedlistButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                saveStateInPreferences(mWebView.getUrl());
                loadFeedList();
            }
        });

        mDeleteButton = (Button) findViewById(R.id.deleteButton);
        mDeleteButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                maybeDelete();
            }
        });

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;

        mWebView.setBackgroundColor(0xff999999);
            // can't use black, no way to change fg

        // Read preferences in onResume instead of here,
        // and load the page there too.
        //readPreferences();
    } // end onCreate

    // Display a short text message in the doc name area.
    public void showTextMessage(String msg) {
        mDocNameView.setText(msg);
    }

    /*********************** begin nev-derived code **************************/
    /**
     * creates mBroadcastReceiver which handles mounting and unmounting of sdcard
     * while the application is running.
     *
     * <p>
     * mBroadcastReceiver with the registerReceiver() and unregisterMountListener() methods
     * will provide the functionality of hiding mWebView when the device has unmounted sdcard
     * because it's connected to PC for example. And it'll reshow and reload the mWebView when
     * the sdcard is remounted.
     * </p>
     */
    private void createBroadcastReceiver() {
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                showTextMessage("Received intent action: " + action);

                if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                    mSDCardMounted = true;

                    loadData();
                }

                if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                    mSDCardMounted = false;

                    //calculatePagePosition();

                    mPageIsLoaded = false;

                    showTextMessage("SD card not mounted");
                    mWebView.setVisibility(View.GONE);
                }
            }
        };
    }

    /**
     * Main entry point to the app.
     *
     * This is called after onCreate() or anytime when the system
     * switches back to your app. Use it to load data, return to saved
     * state and add custom behaviour.
     */
    @Override
    public void onResume() {
        Log.d("FeedViewer", "onResume");
        super.onResume();

        readPreferences();

        checkForSDCard();

        if (mSDCardMounted)
            loadData();
        else {
            showTextMessage("SD card not mounted");
            mWebView.setVisibility(View.GONE);
        }

        //Log.d("FeedViewer", "Inside onResume() mScrollPosition is: " + getScrollFromPreferences(mLastUrl)
        //        + " for " + mLastUrl);

        registerMountListener();

        // Now do all the initialization stuff, now that we've read prefs
        // and have our SD card loaded.
        setBrightness(mBrightness);
        Log.d("FeedViewer", "Setting brightness to remembered " + mBrightness);

        if (! onFeedsPage()) {
            Log.d("FeedViewer", "Not on feeds page; trying to load that url");
            try {
                //showTextMessage("Remembered " + mLastUrl);
                mWebView.loadUrl(mLastUrl);
                mWebSettings.setDefaultFontSize(mFontSize);
            }
            catch (Exception e) {
                mLastUrl = null;
            }
        }
        // If that didn't work, or if no last url, load the feeds list.
        if (onFeedsPage()) {
            loadFeedList();
        }
    }

    /** Check if the SD Card is mounted. */
    private void checkForSDCard() {
        String storageState = Environment.getExternalStorageState();
        if (storageState.equals(Environment.MEDIA_MOUNTED))
            mSDCardMounted = true;
        else
            mSDCardMounted = false;
    }

    /** Load the webpage into mWebView. */
    private void loadData() {
        mPageIsLoaded = false;

        if (! nullURL(mLastUrl))
            mWebView.loadUrl(mLastUrl);
        else
            loadFeedList();

        mWebView.setVisibility(View.VISIBLE);
    }

    /**
     * Register for MEDIA_MOUNTED and MEDIA_UNMOUNTED system intents.
     *
     * <p>
     * We do it this way insted of in AndroidManifest because we're interested in those
     * intent broadcasts only while we're the user is actively using the application.
     * </p>
     */
    private void registerMountListener() {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addDataScheme("file");
        this.registerReceiver(mBroadcastReceiver, intentFilter);
    }

    /** Unregister for MEDIA_MOUNTED and MEDIA_UNMOUNTED system intents. */
    private void unregisterMountListener() {
        this.unregisterReceiver(mBroadcastReceiver);
    }

    /** Saves app state and unregisters mount listeners.
     *
     * This is basically last point Android system promises you can do anything.
     * You can safely ignore other lifecycle methods.
     */
    @Override
    public void onPause() {
        super.onPause();

        saveStateInPreferences(mWebView.getUrl());

        unregisterMountListener();
    }
    private String url_to_scrollpos_key(String url) {
        Log.d("FeedViewer", "url = '" + url + "'");
        Log.d("FeedViewer", "mLastUrl = '" + mLastUrl + "'");
        if (onFeedsPage() || nullURL(url))
            return "feeds_scrollpos";
        Log.d("FeedViewer", "Not on feeds page. Is it empty? " + url.isEmpty());
        String urlkey = url;
        if (urlkey.startsWith("file://")) {
            urlkey = urlkey.substring(7);
        }

        // Eliminate any # positioning
        int hash = urlkey.indexOf('#');
        if (hash > 0)
            urlkey = urlkey.substring(0, hash);

        return urlkey + "_scrollpos";
    }

    /** Save app state into SharedPreferences */
    private void saveStateInPreferences(String url) {
        Log.d("FeedViewer", "************* Saving preferences");
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        String key = url_to_scrollpos_key(url);
        int scrollpos = calculatePagePosition();
        //showTextMessage("save pos " + scrollpos + " for " + key);
        Log.d("FeedViewer", "save pos " + scrollpos + " for " + key);
        editor.putInt(key, scrollpos);

        // As long as we're saving, save our other prefs too:
        editor.putInt("font_size", mFontSize);
        editor.putInt("brightness", mBrightness);
        editor.putString("url", mLastUrl);

        editor.commit();

        printPreferences();
    }

    private void printPreferences() {
        Log.d("FeedViewer", "==========\nNow complete pref list looks like:");
        Map<String,?> allprefs = mSharedPreferences.getAll();
        for (Map.Entry<String,?> entry : allprefs.entrySet())
            if (entry != null && entry.getKey() != null) {
                if (entry.getValue() == null)
                    Log.d("FeedViewer", entry.getKey() + " : null");
                else
                    Log.d("FeedViewer", entry.getKey() + " : '"
                          + entry.getValue() + "'");
            }
        Log.d("FeedViewer", "==========");
    }

    /* Read all values (except scroll positions for specific pages) from pref */
    private void readPreferences() {
        Log.d("FeedViewer", "readPreferences");
        printPreferences();

        mFontSize = mSharedPreferences.getInt("font_size", mFontSize);
        mBrightness = mSharedPreferences.getInt("brightness", mBrightness);
        mLastUrl = mSharedPreferences.getString("url", "null");
    }

    /* Get the saved scroll position for a specific page */
    private int getScrollFromPreferences(String url) {
        String key = url_to_scrollpos_key(url);
        int scrollpos = mSharedPreferences.getInt(key, 0);
        Log.d("FeedViewer", "read pos " + scrollpos + " for " + key);
        return scrollpos;
    }

    /* Calculate mWebView position in perecentage */
    private int calculatePagePosition() {
        if (!mPageIsLoaded)
            return 0;

        float contentHeight = mWebView.getContentHeight() * mWebView.getScale();
        float currentY = mWebView.getScrollY();
        return Math.round(100 * currentY / contentHeight);
    }
    /*********************** end nev code **************************/

    // Figure out a sane URI that can be turned into a path
    // for the webview's current URI. Otherwise, we'll get things
    // like URISyntaxExceptions if there's a named anchor
    // or any other weirdness.
    // Returns null if not a file: URI.
    String getPathForURI() throws URISyntaxException {
        URI uri = new URI(mWebView.getUrl());
        if (! uri.getScheme().equals("file"))
            return null;
        return uri.getPath();
    }

    Boolean nullURL(String url) {
        return (url == null || url.equals("null") || url.length() == 0
                || url.isEmpty()|| url.equals("about:blank"));
    }

    boolean onFeedsPage() {
        if (nullURL(mLastUrl))
            //Log.d("FeedViewer", "On feeds page because mLastUrl is null");
            return true;
        if (mLastUrl.startsWith("file://") &&
            (mLastUrl.endsWith("/feeds") ||
             mLastUrl.endsWith("com.shallowsky.FeedMe")))
            return true;
        return false;
    }

    /*
     * Attempt to not restart on rotate: this doesn't seem needed or helpful:
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        showTextMessage("config changed");
        saveSettings();
        super.onConfigurationChanged(newConfig);
        //setContentView(R.layout.main);
    }
    */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.bigger:
            mWebSettings.setDefaultFontSize(++mFontSize);
            showTextMessage("bigger:" + mFontSize);
            return true;
        case R.id.smaller:
            mWebSettings.setDefaultFontSize(--mFontSize);
            showTextMessage("smaller:" + mFontSize);
            return true;
        case R.id.feedfetcher:
            showFeedFetcherProgress();
            return true;
       }
        return false;
     }

    public void handleExternalLink(final String url) {
        // Pop up a dialog:
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Action for external link " + url + " ?")
                .setCancelable(false)
                .setNeutralButton("Save",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int id) {
                                showTextMessage("Saving " + url);
                                saveUrlForLater(url);
                            }
                        })
                .setPositiveButton("Visit",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int id) {
                                showTextMessage("Visiting " + url);
                                mWebView.loadUrl(url);
                            }
                        })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int id) {
                                dialog.cancel();
                            }
                        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    public void saveUrlForLater(String url) {
        String savedUrlPath = mMainBasePath + File.separator + "saved-urls";
        try {
            FileOutputStream fos;
            fos = new FileOutputStream(new File(savedUrlPath), true);  // append
            //fos = openFileOutput("saved_urls", Context.MODE_APPEND);
            fos.write((url + "\n").getBytes());
            fos.close();
            showTextMessage("Saved");
        } catch (Exception e) {
            showTextMessage("Couldn't save URL to " + savedUrlPath);
        }
    }

    /*
     * Show the original home screen, listing toplevel feeds by day.
     *
     * Directory structure looks like: [baseDir]/dayname/feedname/index.html
     */
    public void loadFeedList() {
        // Save scroll position in the current document:
        saveStateInPreferences(mWebView.getUrl());

        String resultspage = "<html>\n<head>\n";
        resultspage += "<title>Feeds</title>\n";

        // Loop over base dirs, in main storage and on the SD card to add stylesheets.
        // stylesheet needs absolute URLs for style
        for (int base = 0; base < mBasePaths.length; ++base) {
            resultspage += "<link rel=\"stylesheet\" type=\"text/css\" title=\"Feeds\" href=\"file://"
                + mBasePaths[base] + File.separator + "feeds.css\"/>\n";
        }
        resultspage += "</head>\n<body>";

        // Loop over the basedirs again to find story dirs to show:
        for (int base = 0; base < mBasePaths.length; ++base) {
            File basedir = new File(mBasePaths[base]);
            if (!basedir.isDirectory()) {
                //resultspage += basedir.getPath() + " not a directory.<br>\n";
                continue;
            }
            // Loop over days, in reverse order, most recent first:
            File[] daydirs = basedir.listFiles();
            Arrays.sort(daydirs);
            for (int day = daydirs.length-1; day >= 0; --day) {
                if (daydirs[day].isDirectory()) {
                    resultspage += daydirs[day] + ":<br>\n";
                    // Loop over feeds for that day
                    File[] feeds = daydirs[day].listFiles();
                    Arrays.sort(feeds);
                    for (int feed = 0; feed < feeds.length; ++feed) {
                        if (feeds[feed].isDirectory()) {
                            File indexfile = new File(feeds[feed].getPath()
                                                      + File.separator
                                                      + "index.html");
                            if (indexfile.canRead()) {
                                resultspage += "<div class=\"index\"><a href='" + indexfile.toURI()
                                        + "'>" + daydirs[day].getName() + " "
                                        + feeds[feed].getName() + "</a></div>\n";
                                // mMainBasePath will be the first of
                                // mBasePaths that actually has files in it.
                                if (mMainBasePath == null)
                                    mMainBasePath = basedir.getPath();
                            }
                            else {
                                // If we erroneously don't get an
                                // index.html file, we'll end up
                                // showing the directory but giving no
                                // way to read or delete it. So show
                                // something:
                                resultspage += daydirs[day].getName() + " "
                                    + feeds[feed].getName() + "</a> (no index!)<br>\n";
                            }
                        }
                    }
                }
                //else resultspage += "Day " + files[day] + " not a directory.<br>\n";
            }
        }
        resultspage += "<p>End of feed list</br>\n";

        mWebView.loadDataWithBaseURL("file://" + mMainBasePath, resultspage,
                                     "text/html","utf-8", null);

        // Keep the font size the way the user asked:
        mWebSettings.setDefaultFontSize(mFontSize);
        updateBatteryLevel();
        mLastUrl = null;
           // don't count on this null -- may get overridden. Use onFeedsPage().

        //saveSettings();
    }

    /*
     * Go back to the previous page, or re-generate the feeds list if needed.
     */
    public void goBack() {
        // Save scroll position in the current document:
        saveStateInPreferences(mWebView.getUrl());

        try {
            if (onFeedsPage()) {  // already on a generated page, probably feeds
                //showTextMessage("Already on feeds");
                return;
            }

            // Now we know we have a location of some sort. Where is it?
            URI uri = new URI(getPathForURI());
            final File filepath = new File(uri.getPath());
            String upup = filepath.getParentFile().getParentFile()
                .getParentFile().getName();
            Log.d("FeedViewer", "upup = " + upup);

            // Unfortunately WebView doesn't handle history properly
            // for generated pages, so canGoBack() will return true
            // when back would lead to the feeds list (even though we
            // set the history entry to null), but then goBack() will
            // fail since it's forgotten the generated data. So
            // intercept the case where we're on a ToC page and back
            // would lead to the feeds list.
            Boolean upupFeeds = (upup.equals("feeds") ||
                                 upup.equals("com.shallowsky.FeedViewer"));
            if (upupFeeds &&
                filepath.getName().equals("index.html")) {
                loadFeedList();
                return;
            }
            else if (mWebView.canGoBack()) {
                mWebView.goBack();
                mWebSettings.setDefaultFontSize(mFontSize);
                updateBatteryLevel();
                return;
            }
            else if (upupFeeds) {
                // We're on a third-level page;
                // go to the table of contents page for this feed.
                tableOfContents();
                return;
            }
            else {
                // Don't know where we are! Shouldn't happen, but probably does.
                showTextMessage("Can't go back! " + uri);
                return;
            }
        } catch (Exception e) {
            showTextMessage("Can't go back! " + mWebView.getUrl());
        }
    }

    public void goForward() {
        saveStateInPreferences(mWebView.getUrl());
        mWebView.goForward();
        mWebSettings.setDefaultFontSize(mFontSize);
        updateBatteryLevel();
   }

    /*
     * Go to the index page for the current feed.
     */
    public void tableOfContents() {
        // Save scroll position in the current document:
        saveStateInPreferences(mWebView.getUrl());

        // In theory, we're already in the right place, so just load relative
        // index.html -- but nope, that doesn't work.
        try {
            URI uri = new URI(mWebView.getUrl());
            File feeddir = new File(uri.getPath()).getParentFile();
            mWebView.loadUrl("file://" + feeddir.getAbsolutePath()
                    + File.separator + "index.html");
        } catch (URISyntaxException e) {
            showTextMessage("ToC: URI Syntax");
        }
        mWebSettings.setDefaultFontSize(mFontSize);

        //saveSettings();  // Hopefully this will happen on page load.
    }

    /*
     * Recursively delete a directory.
     */
    boolean deleteDir(File dir) {
        //mDocNameView.setText("deleteDir " + dir.getAbsolutePath());
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i=0; i<children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    //mDocNameView.setText("Oops, couldn't delete " + new File(dir, children[i]).getAbsolutePath());
                    mDocNameView.setText("Couldn't delete");
                    return false;
                }
            }
        }
        // The directory is now empty so delete it.
        return dir.delete();
    }

    // Clean up any scroll preferences for files/directories we've deleted.
    // That includes not just what we just immediately deleted, but anything
    // that was deleted previously and somehow didn't get its preference removed.
    private void cleanUpScrollPrefs(String dirstr) {
        Log.d("FeedViewer", "==========\nTrying to delete prefs matching " + dirstr);
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        Map<String,?> allprefs = mSharedPreferences.getAll();
        for (Map.Entry<String,?> entry : allprefs.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("_scrollpos") && !key.startsWith("feeds_")) {
                String path = key.substring(0, key.length() - 10);
                //Log.d("FeedViewer", "Checking whether " + path + "still exists");
                File file = new File(path);
                if(! file.exists()) {
                    editor.remove(key);
                    Log.d("FeedViewer", "Removed pref " + key);
                }
            }
        }
        editor.commit();

        /*
        Log.d("FeedViewer", "==========\nNow complete pref list looks like:");
        allprefs = mSharedPreferences.getAll();
        for (Map.Entry<String,?> entry2 : allprefs.entrySet())
            Log.d("FeedViewer", entry2.getKey());
         */
    }

    /*
     * Confirm whether to delete the current feed, then do so.
     */
    public void maybeDelete() {
        try {
            // Clear out the message area
            showTextMessage("");

            /*
             * We're reading a file in feeds/dayname/feedname/filename.html
             * or possibly the directory itself, feeds/dayname/feedname/
             * and what we want to delete is the directory feeds/dayname/feedname
             * along with everything inside it.
             * So we need the
             */
            final File feeddir;
            final File curfile = new File(getPathForURI());

            if (curfile.isDirectory())
                feeddir = curfile;
            else
                feeddir = curfile.getParentFile();
            final String feedname = feeddir.getName();
            final String dayname = feeddir.getParentFile().getName();

            // Pop up a question dialog:
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Delete" + dayname + " " + feedname + "?")
                    .setCancelable(false)
                    .setPositiveButton("Delete",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int id) {
                                    deleteDir(feeddir);

                                    // If this was the last feed and
                                    // the parent (daydir) is now
                                    // empty, delete it too. Don't
                                    // want to do this in deleteDir()
                                    // since it would have to check on
                                    // every recursion.
                                    File parent = feeddir.getParentFile();
                                    File[] children = parent.listFiles();
                                    if (children.length == 0)
                                        parent.delete();
                                    else {
                                        // There might still be files
                                        // there, like LOG, but as
                                        // long as there are no more
                                        // subdirectories, it's time
                                        // to delete.
                                        Boolean hasChildDirs = false;
                                        for (int i=0; i < children.length; ++i)
                                            if (children[i].isDirectory()) {
                                                hasChildDirs = true;
                                                break;
                                            }
                                        if (! hasChildDirs) {
                                            deleteDir(parent);
                                        }
                                    }

                                    // If we deleted anything, then make sure
                                    // make sure we're not going to remember
                                    // scroll position from the deleted page.
                                    // deletePrefsFor(feeddir.getAbsolutePath());
                                    cleanUpScrollPrefs(feeddir.getAbsolutePath());

                                    loadFeedList();
                                }
                            })
                    .setNegativeButton("Cancel",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int id) {
                                    dialog.cancel();
                                }
                            });
            AlertDialog alert = builder.create();
            alert.show();

        } catch (URISyntaxException e) {
            showTextMessage("URI syntax exception on " + mWebView.getUrl());
            e.printStackTrace();
        } catch (Exception e) {
            showTextMessage("Couldn't delete " + mWebView.getUrl());
        }

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        showTextMessage("");
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            goBack();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            mWebView.pageDown(false);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            mWebView.pageUp(false);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    /*
     * For some reason, this onTouchEvent() is needed to catch events on a WebView.
     * THANK YOU,

     * http://www.tutorialforandroid.com/2009/06/implement-gesturedetector-in-android.html
     * also, http://stackoverflow.com/questions/9519559/handle-touch-events-inside-webview-in-android
     *
     * For these gesture events, return true if we consumed the event,
     * false if we want the event to be handled normally. Except of
     * course for LongPress which for some reason doesn't let us
     * return a status, so we have to go through absurd machinations
     * to prevent triggering links.
     * XXX Though, something to try for the LongPress case: XXX
     * http://stackoverflow.com/questions/3329871/android-webview-long-press-not-on-link-i-e-in-white-space
     *
     * @see android.app.Activity#dispatchTouchEvent(android.view.MotionEvent)
    */
    @Override
    public boolean dispatchTouchEvent(MotionEvent me) {
        //showTextMessage("");
        super.dispatchTouchEvent(me);
        return this.detector.onTouchEvent(me);
    }

    public boolean scrollIfInTargetZone(MotionEvent e) {
        // Only accept taps in the corners, not the center --
        // otherwise we'll have no way to tap on links at top/bottom.
        float w = mScreenWidth / 4;
        if (e.getX() > w && e.getX() < mScreenWidth - w)
            return false;

        // Was the tap at the top or bottom of the screen?
        if (e.getY() > mScreenHeight * .6) {
            mScrollLock = SystemClock.uptimeMillis();
            mWebView.pageDown(false);
            // Save scroll position in the current document.
            // (This may save the previous position, not the newly
            // scrolled to one, but at least we'll guarantee we've
            // saved the current document.)
            saveStateInPreferences(mWebView.getUrl());
           return true;
        }
        else if (e.getY() < mScreenHeight * .23) {
            mScrollLock = SystemClock.uptimeMillis();
            mWebView.pageUp(false);
            // Save scroll position in the current document (see above caveat):
            saveStateInPreferences(mWebView.getUrl());
           return true;
        }

        // Else the tap was somewhere else: pass it along.
        return false;
    }

    // From the docs, I would have assumed I should use onSingleTapConfirmed
    // or maybe onTouchEvent to handle taps. But in practice those never fire,
    // and onSingleTapUp works. Go figure.
    public boolean onSingleTapConfirmed(MotionEvent e) {
        //showTextMessage("onSingleTapConfirmed");
        return scrollIfInTargetZone(e);
    }
    public boolean onSingleTapUp(MotionEvent e) {
        //showTextMessage("onSingleTapUp");
        return scrollIfInTargetZone(e);
    }

    public void onLongPress(MotionEvent e) {
        // Want to convert a longpress into a regular single tap,
        // so the user can longpress to activate links rather than scrolling.
        showTextMessage("onLongPress");
        super.onTouchEvent(e);
        //scrollIfInTargetZone(e);
    }

    public boolean onDoubleTap(MotionEvent e) {
        // Want to convert a longpress into a regular single tap,
        // so the user can longpress to activate links rather than scrolling.
        showTextMessage("onDoubleTap");
        return super.onTouchEvent(e);
    }

    public boolean onDown(MotionEvent e) {
        //showTextMessage("onDown");
        return false;
    }

    // Horizontal flings do back/forward.
    // In practice this can be a pain since it can interfere with h scrolling.
    // Try to tune the velocity so that only real flings get caught here.
    public boolean onFling(MotionEvent e1, MotionEvent e2,
                           float velocityX, float velocityY) {
    /*
        //showTextMessage(Float.toString(velocityX));

        // If the event is too short, ignore it
        if (Math.abs(e1.getX() - e2.getX()) < 250.)
            return false;

        // If there was much vertical movement, ignore it:
        if (Math.abs(e1.getY() - e2.getY()) > 20.)
            return false;

        if (velocityX < -750.) {
            goBack();
            return true;
        }
        else if (velocityX > 750.) {
            goForward();
            return true;
        }
    */
        return false;
    }

    // Set brightness if the user scrolls (drags)
    // along the left edge of the screen
    public boolean onScroll(MotionEvent e1, MotionEvent e2,
                            float distanceX, float distanceY) {

        final int XTHRESH = 30;
        if ((e1.getX() < XTHRESH || e2.getX() < XTHRESH) &&
                (Math.abs(e1.getX() - e2.getX()) < XTHRESH) &&
                (Math.abs(e1.getY() - e2.getY()) >
                 2 * Math.abs(e1.getX() - e2.getX()))) {
            if (distanceY != 0) {
                int y = (int)(mScreenHeight - e2.getY());
                int b = (int)(y * 100 / mScreenHeight);
                showTextMessage("bright " + b + " (y = " + y
                                + "/" + mScreenHeight + ")");
                Log.d("FeedViewer", "bright " + b + " (y = " + y
                      + "/" + mScreenHeight + ")");
                setBrightness(b);
                mBrightness = b;
            }
            return true;
        }
        return false;
    }

    public void onShowPress(MotionEvent e) {
        //showTextMessage("onShowPress");
    }

    // Try to disable the obnoxiously bright button backlight.
    // http://stackoverflow.com/questions/1966019/turn-off-buttons-backlight
    // Alas, it does nothing on Samsungs (big surprise).
    /*
    private void setDimButtons(boolean dimButtons) {
        Window window = getWindow();
        LayoutParams layoutParams = window.getAttributes();
        float val = dimButtons ? 0 : -1;
        try {
            Field buttonBrightness = layoutParams.getClass().getField(
                    "buttonBrightness");
            buttonBrightness.set(layoutParams, val);
        } catch (Exception e) {
            //e.printStackTrace();
        }
        window.setAttributes(layoutParams);
    }
    */

    public void setBrightness(int value) {
        // If mBrightness is 0, brightness probably hasn't been read
        // from preferences yet.
        if (mBrightness <= 0)
            return;

        LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = (float) value / 100;
        //lp.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

        // This is supposed to turn off the annoying button lights: see
        // http://developer.android.com/reference/android/view/WindowManager.LayoutParams.html#buttonBrightness
        // Alas, it doesn't actually do anything.
        lp.buttonBrightness = LayoutParams.BRIGHTNESS_OVERRIDE_OFF;

        getWindow().setAttributes(lp);
    }

    /**
     * Computes the battery level by registering a receiver to the intent triggered
     * by a battery status/level change.
     * Thank you http://mihaifonoage.blogspot.com/2010/02/getting-battery-level-in-android-using.html
     */
    private void updateBatteryLevel() {
        BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                int rawlevel = intent.getIntExtra("level", -1);
                int scale = intent.getIntExtra("scale", -1);
                int level = -1;
                if (rawlevel >= 0 && scale > 0) {
                    level = (rawlevel * 100) / scale;
                }
                mBatteryLevel.setText(level + "%");
            }
        };
        IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryLevelReceiver, batteryLevelFilter);
    }

    /*
    private void showFeedFetcherProgress() {
        // Pop up a dialog:
        String s = "";
        if (mFeedFetcherDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("foo")
                .setNegativeButton("Dismiss",
                                   new DialogInterface.OnClickListener() {
                                       public void onClick(DialogInterface dialog,
                                                           int id) {
                                           dialog.hide();
                                       }
                                   });
            mFeedFetcherDialog = builder.create();
        }
        for (int i = 0; i < 99; ++i) {
            s += "\nThis is line " + i;
        }
        mFeedFetcherDialog.setMessage(s);
        mFeedFetcherDialog.show();
    }
    */

    private void showFeedFetcherProgress() {

        // Pop up a dialog with a textview that we can modify later:
        if (mFeedFetcherDialog != null && mFeedFetcher != null) {
            //mFeedFetcherDialog.ShowDialog();
            mFeedFetcherText.append("\n\nRe-showing the old dialog\n");
        }
        else {
            mFeedFetcherDialog = new Dialog(this);
            mFeedFetcherDialog.setTitle("Feed fetcher progress");
            mFeedFetcherDialog.setContentView(R.layout.feedfetcher);

            mFeedFetcherText = (TextView)mFeedFetcherDialog.findViewById(R.id.feedFetcherText);

            mFeedFetcherText.setText("Making a brand new dialog\n\n");

            mFeedFetcher = new FeedFetcher(this, "http://shallowsky.com",
                                           mWritableDir,
                                           new FeedProgress(mFeedFetcherText,
                                                            (ScrollView)mFeedFetcherDialog.findViewById(R.id.fetcherTextScroller)));
            if (mFeedFetcher.fetchFeeds())
                mFeedFetcherText.append("\n\nSuccess running fetchFeeds\n");
            else
                mFeedFetcherText.append("\n\nFailure running fetchFeeds\n");
        }

        mFeedFetcherDialog.show();
    }
}
