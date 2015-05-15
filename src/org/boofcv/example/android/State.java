package org.boofcv.example.android;

import android.graphics.Color;

/**
* Created by denny on 5/14/15.
*/
enum State {
    RED(R.raw.red, Color.RED),
    GREEN(R.raw.green, Color.GREEN),
    BLUE(R.raw.blue, Color.BLUE),

    CLICK(R.raw.sndclick1, Color.WHITE)
    ;
    final int resid;
    int color;

    State(int resid, int color) {
        this.resid = resid;
        this.color = color;
    }
}
