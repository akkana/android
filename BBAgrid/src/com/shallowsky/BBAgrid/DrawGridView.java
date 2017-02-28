package com.shallowsky.BBAgrid;

import android.content.Context;

import android.view.View;

import android.util.AttributeSet;
import android.util.Log;

import android.widget.TextView;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;

public class DrawGridView extends View {
    // The size of a grid block in meters:
    private static final double BLOCK_SIZE = 2500.;

    int mWidth = 0;
    int mHeight = 0;

    // Grid information that we can display:
    // Current grid block
    int mCurRow;
    int mCurCol;
    // distances to adjacent blocks, in meters:
    double mWestDist;
    double mEastDist;
    double mNorthDist;
    double mSouthDist;

    String mCommentString = "";

    int mBlockFontSize = 150;
    int mMediumFontSize = 100;
    int mCommentFontSize = 80;

    public DrawGridView(Context context) {
        super(context);
    }

    // Also need this constructor to avoid "Error inflating class"
    public DrawGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected void onSizeChanged (int w,
                                            int h,
                                            int oldw,
                                            int oldh) {
        mWidth = w;
        mHeight = h;
        //Log.d("BBAgrid", "***** onSizeChanged *****" + w + ", " + h);
    }

    public void setRowCol(int row, int col) {
        mCurRow = row;
        mCurCol = col;
    }

    public void setDistances(double w, double e, double n, double s) {
        mWestDist = w;
        mEastDist = e;
        mNorthDist = n;
        mSouthDist = s;
    }

    public void setCommentString(String us) {
        mCommentString = us;
    }

    /**
     * Force a call to onDraw() to update changes.
     */
    public void redraw() {
        postInvalidate();
    }

    // Android can only draw from one routine, onDraw.
    // There's apparently no way to go in later and add another line,
    // or different text; you must keep track of everything and force
    // a redraw of the whole View.
    /**
     * Redisplay everything.
     */
    @Override protected void onDraw(Canvas canvas) {
        //Log.d("BBAgrid", "***** onDraw *****");
        super.onDraw(canvas);

        if (mWidth == 0 || mHeight == 0)
            return;

        Paint paint = new Paint();

        // Is this needed?
        paint.setStyle(Paint.Style.FILL);

        // clear the background:
        paint.setColor(Color.BLACK);
        canvas.drawPaint(paint);

        // draw the grid here
        final int margin = 10;
        int gridLeftX = margin;
        int gridRightX = mWidth - gridLeftX;
        int gridTopY = (mHeight - mWidth) / 2 + margin;
        int gridBottomY = mHeight - gridTopY;
        int firstlinedist = (gridRightX - gridLeftX) / 5;
        int gridLeftLineX = gridLeftX + firstlinedist;
        int gridRightLineX = gridRightX - gridLeftLineX;
        int gridTopLineY = gridTopY + firstlinedist;
        int gridBottomLineY = gridBottomY - firstlinedist;
        paint.setColor(Color.YELLOW);
        paint.setStrokeWidth(4);
        canvas.drawLine(gridLeftLineX, gridTopY, gridLeftLineX, gridBottomY,
                        paint);
        canvas.drawLine(gridRightLineX, gridTopY, gridRightLineX, gridBottomY,
                        paint);
        canvas.drawLine(gridLeftX, gridTopLineY, gridRightX, gridTopLineY,
                        paint);
        canvas.drawLine(gridLeftX, gridBottomLineY, gridRightX, gridBottomLineY,
                        paint);

        int textx = 10;
        int texty = mBlockFontSize;

        // The comment string:
        paint.setColor(Color.WHITE);
        paint.setTextSize((float)mCommentFontSize);
        canvas.drawText(mCommentString,
                        textx, mBlockFontSize + mMediumFontSize,
                        paint);

        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        //paint.setTextSize(16 * getResources().getDisplayMetrics().density);
        //float height = -paint.ascent() + paint.descent();
        //Log.d("BBAgrid", "text height is " + height + " = " + height * getResources().getDisplayMetrics().density);
        paint.setTextSize((float)mBlockFontSize);

        // If we don't have row and column set for some reason,
        // don't draw any of the rest except upper and lower string.
        if (mCurRow < 0 || mCurCol < 0) {
            canvas.drawText("Outside the grid", textx, texty, paint);
            return;
        }

        // The grid block we're in:
        String text = "Block: " + mCurRow + mCurCol;
        canvas.drawText(text, textx, texty, paint);
        texty += mBlockFontSize;

        // Done with text. Time for graphics.

        // Draw a red circle representing our current position.
        // Distances are floating point in fractions of a grid square.
        //paint.setAntiAlias(false);
        paint.setColor(Color.RED);
        int gridwidth = gridRightLineX - gridLeftLineX;
        canvas.drawCircle(gridLeftLineX
                          + (int)(mWestDist / BLOCK_SIZE * gridwidth),
                          gridTopLineY
                          + (int)(mNorthDist / BLOCK_SIZE * gridwidth),
                          30, paint);

        // Draw numbers for the current and adjacent grid squares:
        paint.setTextSize((float)mBlockFontSize);
        paint.setColor(Color.GRAY);
        int middleTextX = mWidth/2 - mBlockFontSize * 5/8;
        int middleTextY = mHeight/2 + mBlockFontSize * 4/10;
        int partgridwidth = gridwidth * 7/10;
        canvas.drawText("" + mCurRow + mCurCol,
                        middleTextX, middleTextY, paint);
        canvas.drawText("" + (mCurRow-1) + mCurCol,
                        middleTextX, middleTextY - partgridwidth, paint);
        canvas.drawText("" + (mCurRow+1) + mCurCol,
                        middleTextX, middleTextY + partgridwidth, paint);
        canvas.drawText("" + mCurRow + (mCurCol-1),
                        middleTextX - partgridwidth, middleTextY, paint);
        canvas.drawText("" + mCurRow + (mCurCol+1),
                        middleTextX + partgridwidth, middleTextY, paint);

        // Distances, drawn on the right:

        paint.setColor(Color.WHITE);
        paint.setTextSize((float)mMediumFontSize);
        if (mCurRow >= 0 && mCurCol >= 0) {
            if (mWestDist < mEastDist)
                text = String.format("\n%1$dm W to %2$d%3$d",
                                     (int)mWestDist, mCurRow, mCurCol-1);
            else
                text = String.format("\n%1$dm E to %2$d%3$d",
                                     (int)mEastDist, mCurRow, mCurCol+1);
            float textwidth = paint.measureText(text);
            canvas.drawText(text, mWidth - textwidth,
                            mHeight - mMediumFontSize - margin, paint);

            if (mNorthDist < mSouthDist)
                text = String.format("\n%1$dm N to %2$d%3$d",
                                     (int)mNorthDist, mCurRow-1, mCurCol);
            else
                text = String.format("\n%1$dm S to %2$d%3$d",
                                     (int)mSouthDist, mCurRow+1, mCurCol);
            canvas.drawText(text, mWidth - textwidth,
                            mHeight - margin, paint);
        }
    }
}
