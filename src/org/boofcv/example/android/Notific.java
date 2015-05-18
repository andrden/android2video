package org.boofcv.example.android;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;

import java.util.HashMap;

/**
 * Created by denny on 5/12/15.
 */
public class Notific {
    Sounds sounds;

    State newState=null;
    long newStateT0;

    long silentTill=0;
    long lastState=0;

    public Notific(Sounds s) {
        sounds = s;
    }

    void state(final State state){
        if( state != newState ){
            newState = state;
            newStateT0 = System.currentTimeMillis();
        }
        if( state!=null && System.currentTimeMillis() - newStateT0 > 300 ) {
            if (System.currentTimeMillis() > silentTill && System.currentTimeMillis() - lastState > 1000) {
                sounds.sound(state);
                silentTill = System.currentTimeMillis() + 3000;
            }
            lastState = System.currentTimeMillis();
        }
    }


}
