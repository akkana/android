package com.shallowsky.FeedViewer;

/**
 * FeedFetcher: fetch a directory of feeds (already converted from RSS)
 * from a server.
 * ServerURL: the base URL of the server.
 * LocalDir: the place where we'll save the feeds.
 *
 * First, we initiate the feed by fetching the special url
 *   $ServerURL/feedme/urlrss.cgi?xtraurls=STR
 * where STR is a concatenation of extra URLS wanted,
 * URL encoded and connected by the string '%0a'.
 * For instance,
 *   /feedme/urlrss.cgi?xtraurls=http%3A%2F%2Fblog.arduino.cc%2F2013%2F07%2F10%2Fsend-in-the-clones%2F%0ahttp%3A%2F%2Fread.bi%2F10Lbfh9%0ahttp%3A%2F%2Fwww.popsci.com%2Ftechnology%2Farticle%2F2013-07%2Fdrones-civil-war%0ahttp%3A%2F%2Fwww.thisamericanlife.org%2Fblog%2F2015%2F05%2Fcanvassers-study-in-episode-555-has-been-retracted
 * The saved URLs come from the (line separated) file
 * /mnt/extSdCard/Android/data/com.shallowsky.FeedViewer/saved-urls.
 *
 * Once the initial urlrss.cgi URL has been requested,
 * we wait for it to finish.
 * baseurl = serverurl + "/feeds/" + strftime("%m-%d-%a")
 * We wait for baseurl/LOG to appear,
 * meanwhile showing progress by fetching baseurl and parsing it
 * to show which directories have appeared.
 *
 * Finally, when LOG has appeared, the feeds are ready to download.
 * Download everything inside baseurl.
 * This might be tricky because we can't ls the directories inside it
 * (they have index.html files inside them); we can either fetch each
 * index.html file, parse it and fetch all the links inside,
 * or modify urlrss on the server to put a manifest telling us
 * what to download.
 *
 * So far, of course, this class does none of this. It just demonstrates
 * how to fetch a single test file.
 */

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Reader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;

import android.os.AsyncTask;

import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

import android.app.Activity;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import android.content.Context;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

import java.text.SimpleDateFormat;
import android.text.Html;

// https://developer.android.com/training/basics/network-ops/index.html

public class FeedFetcher {

    Context mContext;
    String mServerUrl;
    String mLocalDir;
    FeedProgress mFeedProgress;
    FetchFeedsTask mFetchTask = null;
    Boolean mFetchImages = true;
    String mSavedURLs;

    public FeedFetcher(Context context, String serverurl, String localdir,
                       FeedProgress fp) {
        mContext = context;
        mServerUrl = serverurl;
        mLocalDir = localdir;
        mFeedProgress = fp;
    }

    public void setServerURL(String serverurl) {
        mServerUrl = serverurl;
    }

    public void stop() {
        logProgress("Stopping FeedFetcher");
        if (mFetchTask == null) {
            // For some reason, we get into this clause
            // even when the task is still running.
            logProgress("Already stopped");
            return;
        }
        logProgress("Cancelling");
        mFetchTask.cancel(true);
        // This might be too early to set mFetchTask to null:
        // I'm hoping it doesn't get garbage collected and
        // cleans up after itself, since I don't know how to
        // find out when it finishes.
        mFetchTask = null;
    }

    // Control whether images are fetched
    public Boolean toggleImages() {
        mFetchImages = !mFetchImages;
        if (mFetchImages)
            logProgress("Will include images.");
        else
            logProgress("NOT including images.");
        return mFetchImages;
    }

    // Fetch feeds. Return true for success or false otherwise.
    // We're still on the main thread here.
    public Boolean fetchFeeds() {
        Log.d("FeedFetcher", "Trying to fetch feeds.");

        // Before attempting to fetch anything, makes sure the net's up:
        ConnectivityManager connMgr = (ConnectivityManager) 
            mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        // Next line crashes. Why?
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();

        // If the net is up, start an async task to fetch the URL:
        if (networkInfo == null || !networkInfo.isConnected()) {
            logProgress("No network connection available.");
            return false;
        }

        // We must pass xtraurls= in the URL even if we have no extras,
        // because that's what urlrss.cgi uses to decide if it's
        // being called as a CGI script.
        String urlrssURL = mServerUrl + "/feedme/urlrss.cgi?xtraurls=";
        Boolean haveURLs = false;

        // Read any saved URLs we need to pass to urlrss.
        // XXX Of course this should be relative to wherever we're
        // reading feeds, or have several options of path. Eventually.
        mSavedURLs =
            "/mnt/extSdCard/Android/data/com.shallowsky.FeedViewer/saved-urls";
        try {
            InputStream fis = new FileInputStream(mSavedURLs);
            InputStreamReader isr = null;
            isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                if (haveURLs)
                    urlrssURL += "%0A";
                else {
                    haveURLs = true;
                }
                urlrssURL += URLEncoder.encode(line, "UTF-8");
                logProgress("URL: " + line);
            }
            isr.close();
            // Closing the reader closes the stream as well.
            // Doing it in finally {} is a lot more elaborate because
            // InputStreamReader.close() throws IOException so you have
            // to add another try inside the finally. <eyeroll>
            // Nobody seems to want to bother with this in code samples
            // I've found online, and neither do I.
        } catch(Throwable t) {
            logProgress("Couldn't read any saved urls");
            // Zero out the filename so we won't later try to delete it.
            mSavedURLs = null;
            urlrssURL += "none";
        }

        mFetchTask = new FetchFeedsTask();
        mFetchTask.execute(urlrssURL);

        // Now wait for the task to complete.
        // The UI can still send a signal to us to stop.
        // XXX

        // If we're finished, the feeds task shouldn't be running any more.
        mFetchTask = null;
        return true;
    }

    // Most of FeedFetcher runs as an AsyncTask,
    // comuunicating back to the parent thread about its progress.
    // Within the AsyncTask, we'll just wait for feeds.
    // FeedFetcher can kill the task if it takes way too long.
    // https://developer.android.com/training/basics/network-ops/connecting.html
    private class FetchFeedsTask extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... urls) {
            // params comes from the execute() call: params[0] is the url.
            String output;
            String filepath = null;

            // Figure out our feed directory based on the date:
            Date curDate = new Date();
            SimpleDateFormat format = new SimpleDateFormat("MM-dd-EEE");
            String todayStr = format.format(curDate);
            String feeddir = mServerUrl + "/feeds/"
                + todayStr + "/";
            String manifestURL = feeddir + "MANIFEST";
            String manifest;

            // Has feedme already run? Check whether the manifest
            // is already there.
            try {
                manifest = downloadUrl(manifestURL);
                publishProgress("Feedme already ran.\n");
            } catch (IOException e) {
                manifest = null;
                publishProgress("No MANIFEST there yet\n");
            }

            if (manifest == null) {
                // First, call urlrss to initiate feedme:
                try {
                    publishProgress(urls[0]);
                    output = downloadUrl(urls[0]);
                    publishProgress("\nStarting feedme ...\n");
                    publishProgress(output);
                } catch (IOException e) {
                    return "Couldn't initiate feedme: IOException on "
                        + urls[0];
                }

                // Now, we wait for MANIFEST to appear,
                // periodically checking what's in the directory.
                int delay = 5000;   // polling interval, milliseconds
                Boolean feedmeRan = false;
                Set<String> subdirSet = new TreeSet<String>();
                while (true) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        // Thread.sleep() requires that we catch this.
                        // But throwing this error clears the interrupt bit,
                        // so in case we actually needed to be interrupted:
                        Thread.currentThread().interrupt();
                    }

                    // AsyncTask has to check itself for cancellation.
                    // But this doesn't work: even after calling cancel
                    // it runs forever.
                    if (isCancelled()) {
                        publishProgress("Task was cancelled");
                        break;
                    }

                    // Now check what directories are there so far:
                    try {
                        output = downloadUrl(feeddir);
                    } catch (IOException e) {
                        publishProgress("Couldn't read dirs: IOException");
                        continue;
                    }
                    List<String> subdirs = HTMLDirToList(output);
                    for (String subdir : subdirs) {
                        if (! subdirSet.contains(subdir)) {
                            subdirSet.add(subdir);
                            publishProgress("  " + subdir);
                        }
                        if (subdir.startsWith("MANIFEST")) {
                            publishProgress("feedme finished!");
                            feedmeRan = true;
                        }
                    }

                    if (feedmeRan) {
                        // Feedme ran: get the manifest!
                        try {
                            manifest = downloadUrl(manifestURL);
                        } catch (IOException e) {
                            return "Couldn't read MANIFEST: IOException";
                        }

                        // Delete the local saved-urls file, if any.
                        if (mSavedURLs != null) {
                            File savefile = new File(mSavedURLs);
                            if (savefile.exists()) {
                                savefile.delete();
                                publishProgress("Deleted " + mSavedURLs);
                            }
                        }
                        break;
                    }
                }
            }
            Log.d("FeedFetcher", "Fetched manifest");

            if (manifest.length() == 0) {
                Log.d("FeedFetcher", "MANIFEST was zero length!");
                return "MANIFEST was zero length!";
            }

            Log.d("FeedFetcher", "\n=======================\nDownloading");
            String datedir = mLocalDir + "/" + todayStr + "/";
            File dd = new File(datedir);
            dd.mkdir();
            String[] filenames = manifest.split("\n+");
            for (String f : filenames) {
                // Skip directories; we'll make them later with mkdirs.
                if (f.endsWith("/")) {
                    Log.d("FeedDetcher", f + " is a directory, skipping");
                    continue;
                }
                if (!mFetchImages) {
                    String fl = f.toLowerCase();
                    if (fl.endsWith(".jpg") || fl.endsWith("jpeg")
                        || fl.endsWith(".png") || fl.endsWith("gif")) {
                        Log.d("FeedDetcher", "Skipping image " + f);
                        continue;
                    }
                }
                String furl = feeddir + f;
                filepath = datedir + f;
                publishProgress("Saving " + furl);
                publishProgress("  to " + filepath);
                File fstat = new File(filepath);
                if (fstat.exists()) {
                    publishProgress(filepath + " is already here");
                    continue;
                }

                // Create the parent directories, if need be.
                File dirfile = fstat.getParentFile();
                Log.d("FeedDetcher", "dirfile is " + dirfile);
                if (!dirfile.exists()) {
                    Log.d("FeedDetcher", "mkdirs " + dirfile);
                    dirfile.mkdirs();
                    if (!dirfile.exists()) {
                        publishProgress("Skipping " + filepath
                                        + ", can't make directory "
                                        + dirfile.getPath());
                        continue;
                    }
                }

                try {
                    FileOutputStream fos = new FileOutputStream(fstat);
                    downloadUrlToFile(furl, fos);
                    fos.close();
                } catch (FileNotFoundException e) {
                    publishProgress("Skipping " + filepath
                                    + ":  FileNotFoundException: "
                                    + fstat);
                    continue;
                } catch (IOException e) {
                    return "Couldn't download " + furl + ": IOException";
                }
            }

            return "Finished with FeedFetcher";
        }

        protected void onProgressUpdate(String... progress) {
            logProgress(progress[0]);
        }

        // onPostExecute displays the return value of the AsyncTask.
        @Override
        protected void onPostExecute(String message) {
            logProgress("\nDone with FeedFetcher!\n");
            logProgress(message);
            //logProgressOnUIThread(message);
        }
    }

    private List<String> HTMLDirToList(String html) {
        List<String> subdirs = new ArrayList<String>();

        String linkpat = "<a [^>]+>(.+?)</a>";
        Matcher matcher = Pattern.compile(linkpat,
                   Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(html);
        Boolean started = false;
        while (matcher.find()) {
            if (started)
                subdirs.add(matcher.group(1));
            if (matcher.group(1).equals("Parent Directory"))
                started = true;
        }
        return subdirs;
}

    public void logProgress(String s) {
        mFeedProgress.log(s + "\n");
        Log.d("FeedFetcher", s);
    }

    private void logProgressOnUIThread(String s) {
        // Should append to the textview in the FeedViewer dialog.
        Log.d("FeedFetcher", s);
        // The call inside runOnUiThread doesn't see s,
        // but it will see a new final string that's a copy of s:
        final String ss = "(subthread) " + s + "\n";
        ((Activity)mContext).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mFeedProgress.log(ss);
                }
            });
    }

    // Given a URL, establishes an HttpUrlConnection and retrieves
    // the web page content as a InputStream, which it returns as
    // a string. Synchronous.
    // Don't use this for non-string content, like files with image data.
    private String downloadUrl(String urlstr) throws IOException {
        Log.d("FeedFetcher", "downloadUrl " + urlstr);
        InputStream is = null;
        
        try {
            URL url = new URL(urlstr);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setReadTimeout(10000    /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            // Starts the query
            conn.connect();
            int response = conn.getResponseCode();
            if (response != 200)
                logProgressOnUIThread("Response code: " + response);
            else
                Log.d("FeedFetcher", "Response code: " + response);
            is = conn.getInputStream();

            // Convert the InputStream into a string
            String contentAsString = readIt(is);
            return contentAsString;
        
            // Makes sure that the InputStream is closed after the app is
            // finished using it.
        } finally {
            if (is != null) {
                is.close();
            } 
        }
    }

    // Given a URL, establishes an HttpUrlConnection and retrieves
    // the web page content as a byte stream, then writes it to a file.
    // Ick: this has a lot of duplicated code from the previous function.
    private void downloadUrlToFile(String urlstr, FileOutputStream fos)
        throws IOException {

        Log.d("FeedFetcher", "downloadUrlToFile " + urlstr);
        InputStream is = null;

        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        
        try {
            URL url = new URL(urlstr);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setReadTimeout(10000);      /* milliseconds */
            conn.setConnectTimeout(15000);   /* milliseconds */
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            // Starts the query
            conn.connect();
            int response = conn.getResponseCode();
            Log.d("FeedFetcher", "Response code: " + response);
            if (response != 200)
                logProgressOnUIThread("Response code: " + response);
            is = conn.getInputStream();
            Log.d("FeedFetcher", "Got input stream");

            // we need to know how may bytes were read
            // to write them to the output stream
            int len = 0;
            while ((len = is.read(buffer)) != -1)
                fos.write(buffer, 0, len);
        
            // Makes sure that the InputStream is closed after the app is
            // finished using it.
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    // Reads an InputStream and converts it to a String.
    // http://stackoverflow.com/a/5445161
    public String readIt(InputStream stream)
        throws IOException, UnsupportedEncodingException {
        java.util.Scanner s = new java.util.Scanner(stream).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}
