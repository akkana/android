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

        /*
        // Scroll to the bottom. http://stackoverflow.com/a/4612082
        // (The easier and top-rated solution in that thread didn't work
        // because layout was null.)
           Mostly doesn't work, though.
        */
        mScrollView.post(new Runnable() {
            public void run() {
                mScrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
        /* Here are some more non-working solutions, from
         * http://stackoverflow.com/questions/19826693/how-can-i-make-a-textview-automatically-scroll-as-i-add-more-lines-of-text
         * The first one doesn't even compile because ScrollView has no
         * method setSelection().
         */
        //mTextView.setSelection(mTextView.getText().length());
        mTextView.scrollTo(0, Integer.MAX_VALUE);
        mScrollView.scrollTo(0, Integer.MAX_VALUE);
        /* Next attempt crashes with a NullPointerException on the first line:
        final int scrollAmount = mTextView.getLayout().getLineTop(mTextView.getLineCount()) - mTextView.getHeight();
        // if there is no need to scroll, scrollAmount will be <=0
        if (scrollAmount > 0)
            mTextView.scrollTo(0, scrollAmount);
        else
            mTextView.scrollTo(0, 0);
         */
    }
}

