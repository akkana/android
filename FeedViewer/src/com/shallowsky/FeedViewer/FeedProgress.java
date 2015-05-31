package com.shallowsky.FeedViewer;

import android.view.View;
import android.widget.TextView;
import android.widget.ScrollView;

// Since Java doesn't allow passing callback functions,
// here's an object to log progress to the FeedViewer's TextView widget.
public class FeedProgress {
    TextView mTextView;
    ScrollView mScrollView;

    public FeedProgress(TextView tv, ScrollView sv) {
        mTextView = tv;
        mScrollView = sv;
    }

    public void log(String s) {
        mTextView.append(s);

        // Scroll to the bottom. http://stackoverflow.com/a/4612082
        // (The easier and top-rated solution in that thread didn't work
        // because layout was null.)
        mScrollView.post(new Runnable() {
            public void run() {
                mScrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }
}

