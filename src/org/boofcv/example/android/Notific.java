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

    long silentTill=0;
    long lastState=0;

    public Notific(Sounds s) {
        sounds = s;
    }

    void state(final State state){
        if( System.currentTimeMillis() > silentTill && System.currentTimeMillis() - lastState > 2000 ){
            sounds.sound(state);
//            handler.post(new Runnable(){
//                public void run(){
//                    sound(state.resid);
//                }
//            });
            silentTill = System.currentTimeMillis() + 3000;
        }
        lastState = System.currentTimeMillis();
    }


}
