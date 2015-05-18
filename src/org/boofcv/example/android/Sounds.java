package org.boofcv.example.android;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;

import java.util.HashMap;

/**
 * Created by denny on 5/14/15.
 */
public class Sounds {
    SoundPool soundPool;
    HashMap<State, Integer> soundPoolMap;
    AudioManager audioManager;

    volatile boolean enabled;

    public Sounds(Context context) {
        audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        soundPool = new SoundPool(4, AudioManager.STREAM_MUSIC, 100);
        soundPoolMap = new HashMap<State, Integer>();
        for( State s : State.values() ){
            soundPoolMap.put(s, soundPool.load(context, s.resid, 1));
        }
    }

    void sound(State state){
        if( enabled ) {
            float curVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            float maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            float leftVolume = curVolume / maxVolume;
            float rightVolume = curVolume / maxVolume;
            int priority = 1;
            int no_loop = 0;
            float normal_playback_rate = 1f;
            soundPool.play(soundPoolMap.get(state), leftVolume, rightVolume, priority, no_loop, normal_playback_rate);
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
