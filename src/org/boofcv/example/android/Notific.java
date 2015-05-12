package org.boofcv.example.android;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;

import java.util.HashMap;

/**
 * Created by denny on 5/12/15.
 */
public class Notific {
    SoundPool soundPool;
    HashMap<Integer, Integer> soundPoolMap;
    AudioManager audioManager;

    long silentTill=0;
    long lastState=0;

    enum State{
        RED(R.raw.red),
        GREEN(R.raw.blue),
        BLUE(R.raw.blue)
        ;
        final int resid;

        State(int resid) {
            this.resid = resid;
        }
    }

    public Notific(Context context) {
        audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        soundPool = new SoundPool(4, AudioManager.STREAM_MUSIC, 100);
        soundPoolMap = new HashMap<Integer, Integer>();
        for( State s : State.values() ){
            soundPoolMap.put(s.resid, soundPool.load(context, s.resid, 1));
        }
    }

    void state(State state){
        if( System.currentTimeMillis() > silentTill && System.currentTimeMillis() - lastState > 2000 ){
            sound(state.resid);
            silentTill = System.currentTimeMillis() + 3000;
        }
        lastState = System.currentTimeMillis();
    }

    void sound(int id){
        float curVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        float maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float leftVolume = curVolume/maxVolume;
        float rightVolume = curVolume/maxVolume;
        int priority = 1;
        int no_loop = 0;
        float normal_playback_rate = 1f;
        soundPool.play(id, leftVolume, rightVolume, priority, no_loop, normal_playback_rate);

    }

}
