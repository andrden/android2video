package org.boofcv.example.android;

/**
* Created by denny on 5/14/15.
*/
enum State {
    RED(R.raw.red),
    GREEN(R.raw.green),
    BLUE(R.raw.blue),

    CLICK(R.raw.sndclick1)
    ;
    final int resid;

    State(int resid) {
        this.resid = resid;
    }
}
