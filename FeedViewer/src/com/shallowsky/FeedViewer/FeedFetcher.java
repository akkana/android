package com.shallowsky.FeedViewer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Reader;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.os.AsyncTask;

import java.net.URL;
import java.net.HttpURLConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import android.content.Context;

import android.util.Log;

//import android.app.AlertDialog;

// https://developer.android.com/training/basics/network-ops/index.html

public class FeedFetcher {

    Context mContext;
    String mServerUrl;

    public FeedFetcher(Context context, String serverurl) {
        mContext = context;
        mServerUrl = serverurl;
    }

    // Before attempting to fetch the URL, makes sure that there is a
    // network connection; then calls AsyncTask.
    // https://developer.android.com/training/basics/network-ops/connecting.html
    public void fetch(String url) {
        // Gets the URL from the UI's text field.
        ConnectivityManager connMgr = (ConnectivityManager) 
            mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            new DownloadWebpageTask().execute(url);
        } else {
            displayMessage("No network connection available.");
        }
    }

     // Uses AsyncTask to create a task away from the main UI thread.
     // This task takes a URL string and uses it to create an
     // HttpUrlConnection. Once the connection has been established,
     // the AsyncTask downloads the contents of the webpage as an
     // InputStream. Finally, the InputStream is converted into a
     // string, which is displayed in the UI by the AsyncTask's
     // onPostExecute method.
     private class DownloadWebpageTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            // params comes from the execute() call: params[0] is the url.
            try {
                return downloadUrl(urls[0]);
            } catch (IOException e) {
                return "Unable to retrieve web page. URL may be invalid.";
            }
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            logProgress(result);
       }
    }

    private void logProgress(String s) {
        // Should append to the textview in the FeedViewer dialog.
        //blah, blah;
    }

    // Given a URL, establishes an HttpUrlConnection and retrieves
    // the web page content as a InputStream, which it returns as
    // a string.
    private String downloadUrl(String myurl) throws IOException {
        InputStream is = null;
        // Only display the first 500 characters of the retrieved
        // web page content.
        int len = 500;
        
        try {
            URL url = new URL(myurl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000 /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            // Starts the query
            conn.connect();
            int response = conn.getResponseCode();
            Log.d("FeedFetcher", "The response is: " + response);
            is = conn.getInputStream();

            // Convert the InputStream into a string
            String contentAsString = readIt(is, len);
            return contentAsString;
        
            // Makes sure that the InputStream is closed after the app is
            // finished using it.
        } finally {
            if (is != null) {
                is.close();
            } 
        }
    }

    // Reads an InputStream and converts it to a String.
    public String readIt(InputStream stream, int len)
                         throws IOException, UnsupportedEncodingException {
        Reader reader = null;
        reader = new InputStreamReader(stream, "UTF-8");        
        char[] buffer = new char[len];
        reader.read(buffer);
        return new String(buffer);
    }

    public void displayMessage(String msg) {
        //textView.setText(msg);
        Log.d("FeedFetcher", msg);
    }
}
