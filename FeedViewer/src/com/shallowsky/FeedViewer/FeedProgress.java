package com.shallowsky.FeedViewer;

import android.widget.TextView;

// Since Java doesn't allow passing callback functions,
// here's an object to log progress to the FeedViewer's TextView widget.
public class FeedProgress {
    TextView mTextView;

    public FeedProgress(TextView tv) {
        mTextView = tv;
    }

    public void log(String s) {
        mTextView.append(s);
    }
}

