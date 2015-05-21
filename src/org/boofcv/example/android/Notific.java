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

    State said;
    long silentTill=0;

    public Notific(Sounds s) {
        sounds = s;
    }

    void state(final State state){
        if( state != newState ){
            newState = state;
            newStateT0 = System.currentTimeMillis();
        }
        if( System.currentTimeMillis() - newStateT0 > 300 && state!=said ) {
            // we are steady in this 'state'
            if( state!=null ) {
                if (System.currentTimeMillis() > silentTill) {
                    sounds.sound(state);
                    said = state;
                    silentTill = System.currentTimeMillis() + 2000;
                }
            }else{
                said=null; // reset
            }
        }
    }


}
