package com.shallowsky.FeedViewer;

/**
 * This class exists to define a callback when the content is scrolled,
 * so the app can keep track of the scrolled position and save it in prefs.
  http://stackoverflow.com/questions/14752523/how-to-make-a-scroll-listener-for-webview-in-android
 */

import android.webkit.WebView;
import android.content.Context;
import android.util.AttributeSet;

//import android.util.Log;

public class ObservableWebView extends WebView
{
    private OnScrollChangedCallback mOnScrollChangedCallback;

    public ObservableWebView(final Context context)
    {
        super(context);
    }

    public ObservableWebView(final Context context, final AttributeSet attrs)
    {
        super(context, attrs);
    }

    public ObservableWebView(final Context context, final AttributeSet attrs, final int defStyle)
    {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onScrollChanged(final int l, final int t,
                                   final int oldl, final int oldt)
    {
        super.onScrollChanged(l, t, oldl, oldt);
        if (mOnScrollChangedCallback != null)
            mOnScrollChangedCallback.onScroll(l, t);
    }

    public OnScrollChangedCallback getOnScrollChangedCallback()
    {
        return mOnScrollChangedCallback;
    }

    public void setOnScrollChangedCallback(final OnScrollChangedCallback
                                           onScrollChangedCallback)
    {
        mOnScrollChangedCallback = onScrollChangedCallback;
    }

    /**
     * Implement in the activity/fragment/view
     * that you want to listen to the webview
     */
    public static interface OnScrollChangedCallback
    {
        public void onScroll(int l, int t);
    }


    /*
     * One of many futile attempts to get notified when the page is laid out:
    // http://stackoverflow.com/questions/23093513/android-webview-getcontentheight-always-returns-0
    // suggests that onSizeChanged can be used to determine when the WebView
    // is finally done laying out its content, but it doesn't work:
    // in practice it only gets called when the WebView is initially
    // created and height=0, and it never gets called again later.
    @Override
    public void onSizeChanged(int w, int h, int ow, int oh) {
        // don't forget this or things will break!
        super.onSizeChanged(w, h, ow, oh);

        Log.d("FeedViewer", "onSizeChanged: height = " + getContentHeight());
    }
    */
}

