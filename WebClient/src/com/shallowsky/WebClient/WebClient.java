package com.shallowsky.WebClient;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

public class WebClient extends Activity {
    WebView mWebView;
    Button mContentsButton; // Go to table of contents for this feed
    String mStorage = Environment.getExternalStorageDirectory().getPath();
    String mMainBasePath = null;
    String[] mBasePaths = {
            mStorage + File.separator + "sdcard" + File.separator + "WebClient",
            mStorage + File.separator + "external_sd" + File.separator + "WebClient",
            mStorage + File.separator + "WebClient",
            };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);
        mWebView = (WebView) findViewById(R.id.webview);

        // make sure to enable javascript, since that's the whole point:
        mWebView.getSettings().setJavaScriptEnabled(true);

        // This might help with playing mp3 files:
        mWebView.getSettings().setAllowFileAccess(true);

        // enable pinch-zoom
        mWebView.getSettings().setBuiltInZoomControls(true);

        // http://stackoverflow.com/questions/2083909/android-webview-refusing-user-input
        // suggests requesting FOCUS_DOWN might make onKeyUp events work.
        // Doesn't work, though; I guess that's just to make input work at all.
        //mWebView.requestFocus(View.FOCUS_DOWN);
        mWebView.setFocusableInTouchMode(true);

        mWebView.setWebViewClient(new WebViewClient() {
            /*
             * Called for every page load, even if it was caused by calling loadUrl().
             * Return false to have the webview handle the url normally,
             * true if we handled it.
             */
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Argh, Android is so limited -- you'd think we could just use
                // the system-defined actions, but no, every app has to do this
                // explicitly:
                if (url.endsWith(".mp3")) {
                    MediaPlayer mediaPlayer = new MediaPlayer();
                    try {
                        mediaPlayer.setDataSource(getApplicationContext(), Uri.parse(url));
                        mediaPlayer.prepare();
                        mediaPlayer.start();
                    }
                    catch (IllegalArgumentException e) { showMessage("Illegal argument exception on " + url); }
                    catch (IllegalStateException e) { showMessage("Illegal State exception on " + url); }
                    catch (IOException e) { showMessage("I/O exception on " + url); }

                    /*
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(url), "audio/mpeg");
                    view.getContext().startActivity(intent);
                    */
                    return true;
                } else if (url.endsWith(".ogg")) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.parse(url), "audio/ogg");
                        view.getContext().startActivity(intent);
                        return true;
                } else if (url.endsWith(".mp4") || url.endsWith(".3gp")) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.parse(url), "video/*");
                        view.getContext().startActivity(intent);
                        return true;
                } else {
                    return super.shouldOverrideUrlLoading(view, url);
                }
            }
        });

        /*
        mContentsButton = (Button) findViewById(R.id.toc);
        mContentsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showIndexPage();
            }
        });
        Button mBackButton = (Button) findViewById(R.id.back);
        mBackButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mWebView.goBack();
            }
        });
        Button mFwdButton = (Button) findViewById(R.id.forward);
        mFwdButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mWebView.goForward();
            }
        });
        */

        showIndexPage();
    }

    public void showIndexPage() {
        // Build up and return a string to show on the index page
        Boolean foundOne = false;
        String lastdir = "";
        String indexPage = "";
        for (int base = 0; base < mBasePaths.length; ++base) {
            File basedir = new File(mBasePaths[base]);
            if (!basedir.isDirectory()) {
                continue;
            }
            foundOne = true;
            lastdir = mBasePaths[base];
            File[] apps = basedir.listFiles();
            Arrays.sort(apps);
            for (int app = apps.length-1; app >= 0; --app) {
                if (!apps[app].isDirectory()) {
                    indexPage += "<p>\n" + apps[app] + " is not a directory\n";
                    continue;
                }
                indexPage += "<p>\n<a href=\"" + apps[app] + File.separator + "index.html\">"
                             + apps[app].getName() + "</a>\n";
            }
        }

        if (foundOne)
            mWebView.loadDataWithBaseURL("file://" + lastdir,
                    indexPage, "text/html", "utf-8", null);
        else {
            indexPage = "<h1>No web content found</h1>\n";
            indexPage += "<p>No web content found in:\n<ul>\n";
            for (int base = 0; base < mBasePaths.length; ++base) {
                indexPage += "<li>" + mBasePaths[base] + "</li>\n";
            }
            indexPage += "</ul>";
            mWebView.loadDataWithBaseURL("file:///", indexPage, "text/html", "utf-8", null);
        }
    }

    private void showMessage(String msg) {
        // Pop up a question dialog:
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                            }
                        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    //
    // The main menu
    //
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
        case R.id.toc:
            showIndexPage();
            return true;
        case R.id.back:
            mWebView.goBack();
            return true;
        case R.id.forward:
            mWebView.goForward();
            return true;
      }
        return false;
     }
}
