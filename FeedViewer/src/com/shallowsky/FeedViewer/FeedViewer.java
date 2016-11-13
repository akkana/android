package com.shallowsky.FeedViewer;

/*
 * FeedViewer: an Android HTML reader optimized for reading RSS
 * feeds downloaded from the web, then deleting them after reading.
 *
 * Copyright 2010-2014 by Akkana Peck <akkana@shallowsky.com>
 * This software is licensed under the terms of the GPLv2 or,
 * at your option, any later GPL version. Share and enjoy!
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

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
import android.net.Uri;
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
import android.text.method.ScrollingMovementMethod;

import android.os.Build;
import android.graphics.Picture;
import android.webkit.WebView.PictureListener;

public class FeedViewer extends Activity implements OnGestureListener {

    private GestureDetector detector;

    ObservableWebView mWebView;

    Button mBackButton;
    Button mFwdButton;
    Button mContentsButton; // Go to table of contents for this feed
    Button mFeedlistButton; // Go to list of feeds
    Button mDeleteButton;
    Button mBiggerButton;
    TextView mDocNameView;
    TextView mBatteryLevel;

    // KitKat and later doesn't allow writing except in a directory
    // named according to the Java class:
    String mWritableDir = "/Android/data/com.shallowsky.FeedViewer";
    String mFeedDir = null;

    // But then in marshmallow, maybe it does.
    String mStorage = Environment.getExternalStorageDirectory().getPath();
    ArrayList<String> mBasePaths = new ArrayList<String>();

    float mScreenWidth;
    float mScreenHeight;

    // Delays: variables used to hold time and lock actions.
    long mScrollLock = 0;          // delays for scrolling, avoiding longpress

    // Delay saving scroll pos after scrolling.
    long mLastSavedScrollPos = 0;

    // Block saving of scroll position during operations like load and delete.
    boolean mBlockSavingScrollPos = false;

    WebSettings mWebSettings; // Settings for the WebView, e.g. font size.

    // The FeedFetcher and its dialog
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

    /** Android access to storage is such a PITA!
     * In Marshmallow, there is NO WAY to get the path where the
     * SD card is mounted.
     * http://stackoverflow.com/questions/36766016/how-to-get-sd-card-path-in-android6-0-programmatically
     * has a method that it claims works using later SDKs (haven't tried yet)
     * plus a method that loops over the output of the mount command
     * parsing the output. But it turns out that doesn't work either:
     * the mount command doesn't actually return anything that points
     * to a *writable* version of the SD card.
     * Poking around in adb shell, I discovered there was a writable
     * Android/data hierarchy at /storage/NNNN-NNNN (which will be
     * different for every card, of course). So this function looks
     * there for anything that has an Android/data directory in it.
     */
    public static ArrayList<String> getExternalMounts() {
        ArrayList<String> out = new ArrayList<String>();

        File storage = new File("/storage");
        // Loop over days, in reverse order, most recent first:
        File[] subdirs = storage.listFiles();
        for (int i=0; i < subdirs.length; ++i) {
            String dataname = subdirs[i] + File.separator + "Android/data";
            //Log.d("FeedViewer", "Checking for data dir: " + dataname);
            File datadir = new File(dataname);
            if (datadir.exists() && datadir.isDirectory()) {
                out.add(subdirs[i].getPath());
                //Log.d("FeedViewer", "Found one! " + subdirs[i].getPath());
            }
        }
        return out;
    }

    public static ArrayList<String> getExternalMounts2() {
        ArrayList<String> out = new ArrayList<String>();
        //final HashSet<String> out = new HashSet<String>();
        String reg = "(?i).*vold.*(vfat|ntfs|exfat|fat32|ext3|ext4).*rw.*";
        String s = "";
        try {
            final Process process = new ProcessBuilder().command("mount")
                .redirectErrorStream(true).start();
            process.waitFor();
            final InputStream is = process.getInputStream();
            final byte[] buffer = new byte[1024];
            while (is.read(buffer) != -1) {
                s = s + new String(buffer);
            }
            is.close();
        } catch (final Exception e) {
            e.printStackTrace();
        }

        // parse output
        final String[] lines = s.split("\n");
        for (String line : lines) {
            if (!line.toLowerCase().contains("asec")) {
                if (line.matches(reg)) {
                    String[] parts = line.split(" ");
                    for (String part : parts) {
                        if (part.startsWith("/"))
                            if (!part.toLowerCase().contains("vold"))
                                out.add(part);
                    }
                }
            }
        }
        return out;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d("FeedViewer", "onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        detector = new GestureDetector(this, this);

        mWebView = (ObservableWebView)findViewById(R.id.webview);
        mWebSettings = mWebView.getSettings();
        mWebSettings.setJavaScriptEnabled(false);
        mFontSize = mWebSettings.getDefaultFontSize();

        /* Initialize the PITA list of possible data directories */
        ArrayList<String> extMounts = getExternalMounts();

        for (int i = 0; i < extMounts.size(); ++i) {
            mBasePaths.add(extMounts.get(i) + File.separator + mWritableDir);
            Log.d("FeedViewer", "ext mount "
                  + extMounts.get(i) + File.separator + mWritableDir);
        }

        // In KitKat, /sdcard and getExternalStorageDirectory()
        // aren't actually the SD card; the sdcard is /sdcard/sdcard
        // or /mnt/sdcard/external_storage,
        mBasePaths.add("/mnt/extSdCard" + mWritableDir);

        Log.d("FeedViewer", "EXTERNAL STORAGE: " + mStorage);
        mBasePaths.add(mStorage + File.separator + mWritableDir);

        // These are for Gingerbread and Froyo:
        mBasePaths.add(mStorage + File.separator + "sdcard" + File.separator + "feeds");
        mBasePaths.add(mStorage + File.separator + "external_sd" + File.separator + "feeds");
        mBasePaths.add(mStorage + File.separator + "feeds");
        /* End data directory initialization */

        mWebView.setWebViewClient(new WebViewClient() {
            /**
             * Sets up an onPageFinished() callback on mWebView,
             * from which it positions the scroll.
             *
             * Jumping through the hoops. We'll delay the calling of
             * scrollTo for N miliseconds after system loads the page.
             * This is needed since onPageFinished() can be called
             * before it loads the whole page (finished doesn't actually
             * mean finished), so give the system some time.
             * Would be great if Android could give us callbacks
             * when things were REALLY finished loading or scrolling.
             */
             /* onPageFinished gets called twice when starting up. Why?
              * That's probably the reason for some of the page jumpiness
              * we see.
              */
            @Override
            public void onPageFinished(WebView webView, final String url) {
                mBlockSavingScrollPos = true;
                PictureListener pictureListener = new PictureListener() {
                    @Override
                    @Deprecated
                    public void onNewPicture(WebView view,
                                             Picture picture) {
                        Log.d("FeedViewer", "=========== Picture changed! "
                              + SystemClock.uptimeMillis()
                              + ", URL = " + mWebView.getUrl()
                              + ", content height = "
                              + mWebView.getContentHeight());
                        view.setPictureListener(null);

                        //scrollToRememberedPosition();
                        // Sadly, even in onNewPicture you can't depend
                        // on scrollTo() working right away.
                        // We still need a delay.
                        // WebView, what a total piece of crap!
                        mWebView.postDelayed(new Runnable() {
                            public void run() {
                                Log.d("FeedViewer",
                          "*** After delay, scrolling to remembered position");
                                scrollToRememberedPosition();
                            }
                        }, 300);
                    }
                };
                mWebView.setPictureListener(pictureListener);

                // Hide content while all the annoying scrolling happens.
                // But not if we're loading a named anchor in the same page,
                // because scrolling to another part of the same page is fast.
                int hash = url.indexOf('#');
                if (hash <= 0)
                    hideContent();
                else if (! url.substring(0, hash).equals(
                                            mLastUrl.substring(0, hash)))
                        hideContent();

                mLastUrl = url;

                Log.d("FeedViewer", "\nonPageFinished "
                      + SystemClock.uptimeMillis() + " " + url);
                Log.d("FeedViewer", "Content height is "
                      + mWebView.getContentHeight());

                /* onPageFinished doesn't REALLY mean the page is finished.
                 * Apparently it means the WebView has read the bytes
                 * and is ready to start parsing and laying out.
                 * So things like getContentHeight don't work yet.
                 * There apparently is no callback that will tell you when
                 * layout is finished, so the only solution I've found is
                 * to wait -- a LONG time (200msec isn't enough on many
                 * pages; 800msec usually is) before scrolling. So the
                 * user will see the delay and the scroll, a crap user
                 * experience, but that's the Android toolkit.

                 * Some ideas I tried that didn't work::
                 * http: *stackoverflow.com/questions/23093513/android-webview-getcontentheight-always-returns-0
                 * The onSizeChanged is only called initially, when the size
                 * is still 0, then never called again. The PictureListener
                 * doesn't even come close to compiling any more.
                 * http: *stackoverflow.com/questions/22878069/android-get-height-of-webview-content-once-rendered
                 * ViewTreeObserver gets called too early, right after
                 * onSizeChanged, and getMeasuredHeight() is nonzero but small
                 * (480 pixels on a 111020-pixel Slashdot page.
                 */

                /* ***
                // Millisecond delays:
                final int WAIT_FOR_LAYOUT = 900;
                final int WAIT_BEFORE_SAVING_PREFS = 2500;

                 * Comment out both these delayed functions.
                 * Try to do them from PictureListener.
                mWebView.postDelayed(new Runnable() {
                    public void run() {
                        Log.d("FeedViewer", "pageFinished postDelayed "
                              + SystemClock.uptimeMillis() + " " + url
                              + ", content height = "
                              + mWebView.getContentHeight());
                        mPageIsLoaded = true;
                        scrollToRememberedPosition();
                    }
                }, WAIT_FOR_LAYOUT);

                // Schedule a delayed save of wherever we're scrolling.
                // Don't do this with maybeSaveScrollState()
                // because we definitely want to make sure we
                // save the URL, even if not the scroll state.
                // Give it a nice long delay, guaranteed to be much longer
                // than the delay we had to use to set the scroll positions.
                mWebView.postDelayed(new Runnable() {
                    public void run() {
                        Log.d("FeedViewer",
                              "*** Delay after load: saving state");
                        saveScrollPos();
                    }
                }, WAIT_BEFORE_SAVING_PREFS);
                */
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
                //saveStateInPreferences();
                saveScrollPos();

                try {
                    URI uri = new URI(url);
                    if (uri.getScheme().equals("file")) {
                        // If it's file: then we're moving between
                        // internal pages. Go ahead and go to the
                        // link, saving settings.

                        // Don't save mLastUrl here; wait until onPageFinished.
                        //mLastUrl = url;
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
                    showTextMessage("Couldn't load " + failingUrl);
                    mWebView.loadUrl(mLastUrl);
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
                //saveStateInPreferences();
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

        // Now that everything's set up, maybe it's safe to set up
        // the scroll listener:
        mWebView.setOnScrollChangedCallback(new ObservableWebView.OnScrollChangedCallback() {
            public void onScroll(int l, int t) {
                maybeSaveScrollState();
            }
        });

        // Read preferences in onResume instead of here,
        // and load the page there too.
        //readPreferences();

        initialPageLoad();
    } // end onCreate

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
        Log.d("FeedViewer", "onResume");
        super.onResume();

        //initialPageLoad();

        registerMountListener();
    }

    public void initialPageLoad() {
        // Prevent any saving of the scroll position for quite a while:
        // we're going to get a ton of events and and scroll positions
        // and many of them will be wrong.
        mLastSavedScrollPos = 0;

        readPreferences();

        checkForSDCard();

        if (mSDCardMounted)
            loadData();
        else {
            showTextMessage("SD card not mounted");
            mWebView.setVisibility(View.GONE);
        }

        // Now do all the initialization stuff, now that we've read prefs
        // and have our SD card loaded.
        setBrightness(mBrightness);

        if (! onFeedsPage()) {
            try {
                //Log.d("FeedViewer", "Loading remembered " + mLastUrl);
                mWebView.loadUrl(mLastUrl);
                mWebSettings.setDefaultFontSize(mFontSize);

                // On the feeds page, we'll set mFeedDir to the first
                // directory we find. If we have a remembered page,
                // we'll set it to wherever that page is.
                // If mLastUrl isn't a file:// URL, we're in trouble
                // until we go to the feeds page.
                if (mFeedDir == null && mLastUrl.startsWith("file://")) {
                    int lastsep = mLastUrl.lastIndexOf(File.separator);
                    Log.d("FeedViewer", "lastsep 1: " + lastsep);
                    // That gave us a date/feed directory. Move up:
                    if (lastsep > 7) {
                        // Get the date/ directory:
                        lastsep = mLastUrl.lastIndexOf(File.separator, lastsep-1);
                        Log.d("FeedViewer", "lastsep 2: " + lastsep);
                        if (lastsep > 7) {
                            // Finally, get the directory containing
                            // all the date dirs.
                            lastsep = mLastUrl.lastIndexOf(File.separator, lastsep-1);
                            Log.d("FeedViewer", "lastsep 3: " + lastsep);
                            if (lastsep > 7) {
                                mFeedDir = mLastUrl.substring(7, lastsep);
                                Log.d("FeedViewer",
                                      "Setting mFeedDir from initialPageLoad to "
                                      + mFeedDir);
                            }
                        }
                    }
                }
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

    /* If the FeedViewer activity is killed (or crashes) while a
     * FeedFetcher AsyncTask is running, we can get an error like:
  android.view.WindowLeaked: Activity com.shallowsky.FeedViewer.FeedViewer has leaked window com.android.internal.policy.impl.PhoneWindow$DecorView{42b38da0 V.E..... R.....ID 0,0-320,321} that was originally added here
E/WindowManager(32069):         at com.shallowsky.FeedViewer.FeedViewer.showFeedFetcherProgress(FeedViewer.java:1186)
E/WindowManager(32069):         at com.shallowsky.FeedViewer.FeedViewer.onOptionsItemSelected(FeedViewer.java:547)
I/ActivityManager(  818): Process com.shallowsky.FeedViewer (pid 32069) (adj 13) has died.
     * The best way to fix this isn't entirely obvious:
     * here, we call stop() on any existing FeedFetcher,
     * which will call cancel() on any fetcher task that might be running.
     * However, it may take a while for that cancel() to work
     * (especially if it's waiting on an HTTP download)
     * so I wonder if Android might still complain about the leak.
     * Also, are there other places this leak can happen
     * that might not be covered by onDestroy() ?
     */
    @Override
    public void onDestroy() {
        if (mFeedFetcher != null)
            mFeedFetcher.stop();

        // onDestroy() is never supposed to be called without onPause()
        // being called first; but some people say it happens, and
        // clearly we're sometimes getting killed without prefs being
        // saved, so try saving them again here:
        saveScrollPos();
        saveStateInPreferences();

        super.onDestroy();  // to avoid mysterious SuperNotCalledException
    }

    /** Saves app state and unregisters mount listeners.
     *
     * This is basically last point Android system promises you can do anything.
     * You can safely ignore other lifecycle methods.
     */
    @Override
    public void onPause() {
        super.onPause();

        // Unfortunately this usually doesn't work. But doesn't hurt to try:
        saveScrollPos();
        saveStateInPreferences();

        unregisterMountListener();
    }
    /********  END APP LIFECYCLE FUNCTIONS *******/

    /********  HANDLE EXTERNAL SDCARD EVENTS *******/

    /**
     * Register for MEDIA_MOUNTED and MEDIA_UNMOUNTED system intents.
     *
     * We do it this way insted of in AndroidManifest because we're
     * interested in those intent broadcasts only while we're the user
     * is actively using the application.
     */
    private void registerMountListener() {
        IntentFilter intentFilter
            = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addDataScheme("file");
        this.registerReceiver(mBroadcastReceiver, intentFilter);
    }

    /* Code from nev to handle mounts/unmounts */

    /** Unregister for MEDIA_MOUNTED and MEDIA_UNMOUNTED system intents. */
    private void unregisterMountListener() {
        this.unregisterReceiver(mBroadcastReceiver);
    }

    /**
     * creates mBroadcastReceiver which handles mounting and
     * unmounting of sdcard while the application is running.
     *
     * mBroadcastReceiver with the registerReceiver() and
     * unregisterMountListener() methods will provide the
     * functionality of hiding mWebView when the device has unmounted
     * sdcard because it's connected to PC for example. And it'll
     * reshow and reload the mWebView when the sdcard is remounted.
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

    /** Check if the SD Card is mounted. */
    private void checkForSDCard() {
        String storageState = Environment.getExternalStorageState();
        if (storageState.equals(Environment.MEDIA_MOUNTED))
            mSDCardMounted = true;
        else
            mSDCardMounted = false;
    }
    /********  END EXTERNAL SDCARD HANDLING *******/

    /********  General Utility Functions *******/

    // Display a short text message in the doc name area.
    public void showTextMessage(String msg) {
        mDocNameView.setText(msg);
        Log.d("FeedViewer", msg);
    }

    public void hideContent() {
        findViewById(R.id.webview).setVisibility(View.INVISIBLE);
    }

    public void unhideContent() {
        findViewById(R.id.webview).setVisibility(View.VISIBLE);
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

    // Eliminate any # named anchor positioning from a URL.
    private String remove_named_anchor(String url) {
        try {
            int hash = url.indexOf('#');
            if (hash <= 0)
                return url;
            return url.substring(0, hash);
        } catch (Exception e) {
            Log.d("FeedViewer",
                  "Exception in remove_named_anchor, url = " + url);
            return url;
        }
    }

    /* Calculate mWebView position in perecentage */
    private int calculatePagePosition() {
        //if (!mPageIsLoaded)
        //    return 0;

        float contentHeight = mWebView.getContentHeight() * mWebView.getScale();
        float currentY = mWebView.getScrollY();
        return Math.round(100 * currentY / contentHeight);
    }

    // The url passed in here should already have had named anchors stripped.
    private String url_to_scrollpos_key(String url) {
        if (onFeedsPage(url) || nullURL(url))
            return "feeds_scrollpos";
        String urlkey = url;
        if (urlkey.startsWith("file://")) {
            urlkey = urlkey.substring(7);
        }

        return urlkey + "_scrollpos";
    }

    private void saveScrollPos() {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        saveScrollPos(editor);
        editor.commit();
    }

    private void saveScrollPos(SharedPreferences.Editor editor) {
        if (mBlockSavingScrollPos) {
            Log.d("FeedViewer", "Not saving scroll pos: blocked");
            return;
        }

        String url1 = mWebView.getUrl();
        int scrollpos = calculatePagePosition();
        String url = mWebView.getUrl();
        // Race condition: Make sure the URL hasn't changed in the
        // time it took to get the page position.
        if (url == null || url1 == null || !url.equals(url1)) {
            showTextMessage("Race condition, URL changed!");
            return;
        }
        // When this fails, url1 and url are about about:blank
        Log.d("FeedViewer", "Two equal urls were " + url1 + " and " + url
              + ", scrollpos = " + scrollpos);
        url = remove_named_anchor(url);
        String key = url_to_scrollpos_key(url);

        Log.d("FeedViewer", "Saving scroll pos " + scrollpos
              + " for " + key);
        editor.putInt(key, scrollpos);
        mLastSavedScrollPos = SystemClock.uptimeMillis();

        editor.putString("url", url);
    }

    /** Save app state into SharedPreferences */
    private void saveStateInPreferences() {
        Log.d("FeedViewer", "************* Saving preferences");
        SharedPreferences.Editor editor = mSharedPreferences.edit();

        // As long as we're saving, save our other prefs too:
        editor.putInt("font_size", mFontSize);
        editor.putInt("brightness", mBrightness);

        //saveScrollPos(editor);
        // saveStateInPreferences() is called from all over, and in
        // some of those cases it might not be appropriate to save
        // the scroll position directly.
        //maybeSaveScrollState();

        editor.commit();

        printPreferences();
    }

    /** Save the current scroll state (but not other preferences)
     *  only if it's been long enough since the last time we saved.
     * If it's been less than N seconds since we last saved, don't
     * do anything. If it's been more than that, schedule a save
     * to happen after 1 sec (and don't reset the timer until that
     * save fires off).
     * Currently called ONLY from onScroll().
     */
    private void maybeSaveScrollState() {

        final long HOWOFTEN = 5000;  // Don't save more often than this
        final long SCROLL_SAVE_DELAY = 3500;  // delay saves by this much

        long now = SystemClock.uptimeMillis();

        // The first time this is called, we're probably in the middle of
        // initialization, there might be random scrolling going on,
        // and it would be good to delay saving anything new for quite
        // a while to avoid overwriting the scroll position read from prefs.
        if (mLastSavedScrollPos > 0 && now < mLastSavedScrollPos) {
            //Log.d("FeedViewer", "Not saving scroll pos "
            //      + calculatePagePosition() + " -- too early");
            return;
        }
        Log.d("FeedViewer",
              "maybeSaveScrollState: scheduling save  of pos " +
              calculatePagePosition()
              + " in " + SCROLL_SAVE_DELAY/1000 + " sec");
        mLastSavedScrollPos = now + SCROLL_SAVE_DELAY;

        mWebView.postDelayed(new Runnable() {
            public void run() {
                Log.d("FeedViewer", "*** After delay, saving scroll pos");
                saveScrollPos();
                // In case we were blocked from saving that time,
                // let's not block the next one.
                mBlockSavingScrollPos = false;
            }
        }, SCROLL_SAVE_DELAY);
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

    /* After loading a new page, see if we've been here before
     * and scroll to the last known scroll position.
     */
    private void scrollToRememberedPosition() {
        Log.d("FeedViewer", "scrollToRememberedPosition: Content height is "
              + mWebView.getContentHeight());

        String url = mWebView.getUrl();

        // If we went to a named anchor (with #), don't scroll the page.
        // This also implies we shouldn't save named anchors
        // as part of a saved URL since it will prevent us
        // from scrolling when we go back to that page.
        if (url.indexOf('#') > 0) {
            Log.d("FeedViewer", "There's a named anchor");
            unhideContent();
            return;
        }

        int scrollpos = getScrollFromPreferences(url);
        if (scrollpos == 0) {
            Log.d("FeedViewer", "Not scrolling, saved scrollpos = 0");
            unhideContent();
            return;
        }
        int pixelscroll =
            (int)Math.round((mWebView.getContentHeight()
                             * mWebView.getScale()
                             * scrollpos - 1) / 100.0);
        Log.d("FeedViewer", "Trying to scroll to " + scrollpos
              + "% -> " + pixelscroll
              + " = (" + mWebView.getContentHeight() + " * "
              + mWebView.getScale() + " * " + scrollpos
              + " - 1) / 100.0"
              );

        // This type of scroll apparently takes a long time;
        // it will trigger maybeSaveScrollState and a delayed save,
        // but the delay isn't long enough. So make sure we don't
        // save for quite a while after loading a page and scrolling:
        mLastSavedScrollPos = SystemClock.uptimeMillis() + 4000;

        // Scroll a little above the remembered position --
        // else rounding errors may scroll us too far down,
        // to where the most recently read line isn't visible,
        // which is confusing to the user.
        mWebView.scrollTo(0, pixelscroll);

        unhideContent();
    }

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

    boolean onFeedsPage(String url) {
        if (nullURL(url))
            //Log.d("FeedViewer", "On feeds page because mLastUrl is null");
            return true;
        if (url.startsWith("file://") &&
            (url.endsWith("/feeds") ||
             url.endsWith("com.shallowsky.FeedViewer")))
            return true;
        return false;
    }

    boolean onFeedsPage() {
        return onFeedsPage(mLastUrl);
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
                .setPositiveButton("Browse",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int id) {
                                showTextMessage("Browsing " + url);
                                Intent browserIntent
                                    = new Intent(Intent.ACTION_VIEW,
                                                 Uri.parse(url));
                                startActivity(browserIntent);
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
        String savedUrlPath = mFeedDir + File.separator + "saved-urls";
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
     * Java has no basename/dirname utilities.
     */
    public String basename(File f) {
        String path = f.getPath();
        int sep = path.lastIndexOf(File.separator);
        return path.substring(sep + 1);
    }

    /*
     * Show the original home screen, listing toplevel feeds by day.
     *
     * Directory structure looks like: [baseDir]/dayname/feedname/index.html
     */
    public void loadFeedList() {
        Log.d("FeedViewer", "Loading feed list");

        // Save scroll position in the current document, without a delay,
        // if we have a current document:
        if (mLastUrl != null)
            saveScrollPos();

        String resultspage = "<html>\n<head>\n";
        resultspage += "<title>Feeds</title>\n";

        // Loop over base dirs, in main storage and on the SD card to
        // add stylesheets. stylesheet needs absolute URLs for style
        for (int base = 0; base < mBasePaths.size(); ++base) {
            resultspage += "<link rel=\"stylesheet\" type=\"text/css\" title=\"Feeds\" href=\"file://"
                + mBasePaths.get(base) + File.separator + "feeds.css\"/>\n";
        }
        resultspage += "</head>\n<body>";

        // Loop over the basedirs again to find story dirs to show:
        for (int base = 0; base < mBasePaths.size(); ++base) {
            File basedir = new File(mBasePaths.get(base));
            if (!basedir.isDirectory()) {
                //resultspage += basedir.getPath() + " not a directory.<br>\n";
                continue;
            }
            // Loop over days, in reverse order, most recent first:
            File[] daydirs = basedir.listFiles();
            Arrays.sort(daydirs);
            for (int day = daydirs.length-1; day >= 0; --day) {
                if (daydirs[day].isDirectory()) {
                    Boolean showedHeader = false;
                    // Loop over feeds for that day
                    File[] feeds = daydirs[day].listFiles();
                    Arrays.sort(feeds);
                    for (int feed = 0; feed < feeds.length; ++feed) {
                        if (feeds[feed].isDirectory()) {
                            File indexfile = new File(feeds[feed].getPath()
                                                      + File.separator
                                                      + "index.html");
                            if (indexfile.canRead()) {
                                // If there's any real content, show a
                                // header for the directory.
                                if (! showedHeader) {
                                    resultspage += basename(daydirs[day])
                                        + ":<br>\n";
                                    showedHeader = true;
                                }
                                resultspage += "<div class=\"index\"><a href='"
                                    + indexfile.toURI()
                                    + "'>" + daydirs[day].getName() + " "
                                    + feeds[feed].getName() + "</a></div>\n";
                                // mFeedDir will be the first of
                                // mBasePaths that actually has files in it.
                                if (mFeedDir == null) {
                                    mFeedDir = basedir.getPath();
                                    Log.d("FeedViewer",
                                          "Setting mFeedDir from loadFeedsList to " + mFeedDir);
                                }
                            }
                            else {
                                // If we erroneously don't get an
                                // index.html file, we'll end up
                                // showing the directory but giving no
                                // way to read or delete it. So show
                                // something:
                                resultspage += daydirs[day].getName() + " "
                                    + feeds[feed].getName()
                                    + "</a> (no index!)<br>\n";
                            }
                        }
                    }
                }
            }
        }
        resultspage += "<p>End of feed list</br>\n";

        mWebView.loadDataWithBaseURL("file://" + mFeedDir, resultspage,
                                     "text/html","utf-8", null);

        // Keep the font size the way the user asked:
        mWebSettings.setDefaultFontSize(mFontSize);
        //updateBatteryLevel();
        mLastUrl = null;
           // don't count on this null -- may get overridden. Use onFeedsPage().
    }

    /*
     * Go to the index page for the current feed.
     */
    public void tableOfContents() {
        if (onFeedsPage())
            return;

        // Save scroll position in the current document, without delay:
        saveScrollPos();

        // In theory, we're already in the right place, so just load relative
        // index.html -- but nope, that doesn't work.
        try {
            URI uri = new URI(mWebView.getUrl());
            File feeddir = new File(uri.getPath()).getParentFile();
            mWebView.loadUrl("file://" + feeddir.getAbsolutePath()
                    + File.separator + "index.html");
        } catch (URISyntaxException e) {
            showTextMessage("ToC: URI Syntax: URL was "
                            + mWebView.getUrl());
        } catch (NullPointerException e) {
            showTextMessage("NullPointerException: URL was "
                            + mWebView.getUrl());
        }
        mWebSettings.setDefaultFontSize(mFontSize);
    }

    /*
     * Go back to the previous page, or re-generate the feeds list if needed.
     */
    public void goBack() {
        // Save scroll position in the current document, without a delay:
        saveScrollPos();

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
            Log.d("FeedViewer", "upup = " + upup + " from " + filepath);

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
            }
            /*
             * WebView.canGoBack() isn't reliable: it often returns true
             * even when it can't go back, and goBack() will be a no-op.
             * Unfortunately there's no way to check after calling goBack
             * that it failed, so the only option seems to be to
             * stop trusting canGoBack() and never use goBack().
            else if (mWebView.canGoBack()) {
                // Try to use mWebView.goBack() if we can:
                Log.d("FeedViewer", "Trying to goBack()");
                mWebView.goBack();
                mWebSettings.setDefaultFontSize(mFontSize);
                //updateBatteryLevel();
            }
             */
            else if (upupFeeds) {
                // We're on a third-level page;
                // go to the table of contents page for this feed.
                Log.d("FeedViewer", "going to table of contents");
                tableOfContents();
            }
            else {
                // Don't know where we are! Shouldn't happen, but probably does.
                showTextMessage("Can't go back! " + uri);
                return;
            }
        } catch (Exception e) {
            showTextMessage("Can't go back! " + mWebView.getUrl());
            Log.d("FeedViewer", "Exception was: " + e);
        }

        // Save the new document location regardless of where we ended up:
        // But this won't work because the document hasn't loaded yet.
        //saveStateInPreferences();
    }

    public void goForward() {
        saveScrollPos();
        mWebView.goForward();
        mWebSettings.setDefaultFontSize(mFontSize);
        //updateBatteryLevel();
    }

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
        //lp.buttonBrightness = LayoutParams.BRIGHTNESS_OVERRIDE_OFF;

        getWindow().setAttributes(lp);

        // Save the new brightness:
        saveStateInPreferences();
    }

    /*
     * Recursively delete a directory.
     */
    boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i=0; i<children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
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
    // that was deleted previously and somehow didn't get its pref removed.
    private void cleanUpScrollPrefs() {
        Log.d("FeedViewer", "Trying to delete old scroll prefs");
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        Map<String,?> allprefs = mSharedPreferences.getAll();
        for (Map.Entry<String,?> entry : allprefs.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("_scrollpos") && !key.startsWith("feeds_")) {
                String path = key.substring(0, key.length() - 10);
                File file = new File(path);
                if(! file.exists()) {
                    editor.remove(key);
                    Log.d("FeedViewer", "Removed pref " + key);
                }
            }
        }
        editor.commit();

        // printPreferences();
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
             * and what we want to delete is the dir feeds/dayname/feedname
             * along with everything inside it.
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
                                    mBlockSavingScrollPos = true;
                                    Log.d("FeedViewer",
                                          "deleting "
                                            + feeddir.getAbsolutePath());

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

                                    // Load the feeds list before cleaning
                                    // up scroll prefs.
                                    // But the first thing loadFeedList does
                                    // is save position on the previous page,
                                    // which we can't do since we just deleted
                                    // the previous page and will end up
                                    // saving the position from the previous
                                    // page for the feeds list URL.
                                    // So encode that into mLastUrl:
                                    // XXX This didn't help.
                                    // mLastUrl = null;
                                    loadFeedList();

                                    // Don't retain scroll position
                                    // for deleted pages.
                                    cleanUpScrollPrefs();
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

    /************** UI CODE **************/

    /************** Main Menu **************/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    /* Handles menu item selections */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.bigger:
            mWebSettings.setDefaultFontSize(++mFontSize);
            showTextMessage("bigger:" + mFontSize);
            saveStateInPreferences();
            return true;
        case R.id.smaller:
            mWebSettings.setDefaultFontSize(--mFontSize);
            showTextMessage("smaller:" + mFontSize);
            saveStateInPreferences();
            return true;
        case R.id.feedfetcher:
            showFeedFetcherProgress();
            return true;
        }
        return false;
    }

    /************** Events **************/

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

    // Volume keys give an annoying beep if you don't override onKeyUp:
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP
            || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
            return true;
        return super.onKeyUp(keyCode, event);
    }

    /*
     * For some reason, onTouchEvent() is needed to catch events on a WebView.
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

    // Tapping in the corners of the screen scroll up or down.
    public boolean scrollIfInTargetZone(MotionEvent e) {
        // Only accept taps in the corners, not the center --
        // otherwise we'll have no way to tap on links at top/bottom.
        float w = mScreenWidth / 4;
        if (e.getRawX() > w && e.getRawX() < mScreenWidth - w) {
            return false;
        }

        // Was the tap at the top or bottom of the screen?
        if (e.getRawY() > mScreenHeight * .8) {
            mScrollLock = SystemClock.uptimeMillis();
            mWebView.pageDown(false);
            // Don't try to save page position: we'll do that after scroll
            // when we have a new page position.
           return true;
        }
        //else if (e.getRawY() < mScreenHeight * .23) {
        else if (e.getRawY() < 130) {
            // ICK! but how do we tell how many pixels the buttons take? XXX
            mScrollLock = SystemClock.uptimeMillis();
            mWebView.pageUp(false);
            // Again, don't save page position here, wait for callback.
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

    // Set brightness if the user scrolls (drags)
    // along the left edge of the screen
    public boolean onScroll(MotionEvent e1, MotionEvent e2,
                            float distanceX, float distanceY) {
        final int XTHRESH = 30;    // How close to the left edge need it be?
        if (e1.getRawX() > XTHRESH) return false;
        if (e2.getRawX() > XTHRESH) return false;
        if (distanceY == 0) return false;

        int y = (int)(mScreenHeight - e2.getRawY());
        int b = (int)(y * 100 / mScreenHeight);
        showTextMessage("bright " + b + " (y = " + y
                        + "/" + mScreenHeight + ")");
        setBrightness(b);
        mBrightness = b;
        return true;
    }

    /***** Events we're required to override to implement OnGestureListener
     *     even if we don't use them.
     */

    // Would like to use longpress for something useful, like following
    // a link or viewing an image, if I could figure out how.
    public void onLongPress(MotionEvent e) {
        // Want to convert a longpress into a regular single tap,
        // so the user can longpress to activate links rather than scrolling.
        showTextMessage("onLongPress");
        super.onTouchEvent(e);
        //scrollIfInTargetZone(e);
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

    public void onShowPress(MotionEvent e) {
        //showTextMessage("onShowPress");
    }

    /* We don't actually have to implement onDoubleTap:
    public boolean onDoubleTap(MotionEvent e) {
        showTextMessage("onDoubleTap");
        return super.onTouchEvent(e);
    }
    */

    /***** End required OnGestureListener events we don't actually use */

    /*********** CODE NOT CURRENTLY USED (but maybe some day) ********/

    /*
    // Try to disable the obnoxiously bright button backlight.
    // http://stackoverflow.com/questions/1966019/turn-off-buttons-backlight
    // Alas, it does nothing on Samsungs (big surprise).
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

    /**
     * Computes the battery level by registering a receiver to the intent triggered
     * by a battery status/level change.
     * Thank you http://mihaifonoage.blogspot.com/2010/02/getting-battery-level-in-android-using.html
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
     */

    /********** FeedFetcher dialog ******/

    private void showFeedFetcherProgress() {

        // Pop up a dialog with a textview that we can modify later:
        if (mFeedFetcherDialog != null) {
            mFeedFetcherText.append("\n\nRe-showing the old dialog\n");

            // Try to scroll to the bottom, though this doesn't always work,
            // particularly during the actual downloading phase:
            ScrollView scrollView = (ScrollView)mFeedFetcherDialog.findViewById(R.id.fetcherTextScroller);
            if (scrollView != null)
                scrollView.fullScroll(View.FOCUS_DOWN);
        }
        else {
            mFeedFetcherDialog = new Dialog(this);
            mFeedFetcherDialog.setTitle("Feed fetcher progress");
            mFeedFetcherDialog.setContentView(R.layout.feedfetcher);

            mFeedFetcherText =
                (TextView)mFeedFetcherDialog.findViewById(R.id.feedFetcherText);
            mFeedFetcherText.setMovementMethod(new ScrollingMovementMethod());

            Button imgToggle =
                (Button)mFeedFetcherDialog.findViewById(R.id.ffImgToggle);
            imgToggle.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        toggleFeedFetcherImages(v);
                    }
                });

            Button stopBtn =
                (Button)mFeedFetcherDialog.findViewById(R.id.ffStopBtn);
            stopBtn.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        stopFeeds();
                    }
                });

            mFeedFetcherText.setText("Making a brand new dialog\n\n");
        }

        if (mFeedFetcher == null) {
            mFeedFetcherText.append("Creating a new Feed Fetcher\n");
            Log.d("FeedFetcher", "Creating a FeedFetcher with mFeedDir = "
                  + mFeedDir);
            mFeedFetcher = new FeedFetcher(this, "http://shallowsky.com",
                                           mFeedDir,
                                           new FeedProgress(mFeedFetcherText,
                                                            (ScrollView)mFeedFetcherDialog.findViewById(R.id.fetcherTextScroller)));
            if (! mFeedFetcher.fetchFeeds())
                mFeedFetcherText.append("\n\nCouldn't run fetchFeeds\n");
        }

        mFeedFetcherDialog.show();
    }

    /********** Callbacks for buttons in the FeedFetcher dialog ******/

    private void toggleFeedFetcherImages(View v) {
        Boolean fetch = mFeedFetcher.toggleImages();
        // Set the text of the button to indicate what's being fetched
        if (fetch)
            ((Button)v).setText("No images");
        else
            ((Button)v).setText("Images");
    }

    private void stopFeeds() {
        mFeedFetcher.stop();
        mFeedFetcher = null;
        mFeedFetcherDialog.dismiss();
    }
}
