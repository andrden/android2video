/*
 * Copyright (c) 2011-2014, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.boofcv.example.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.hardware.Camera;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.FrameLayout;

import java.util.List;
import java.util.Locale;

/**
 * Demonstration of how to process a video stream on an Android device using BoofCV.  Most of the code below
 * is deals with handling Android and all of its quirks.  Video streams can be accessed in Android by processing
 * a camera preview.  Data from a camera preview comes in an NV21 image format, which needs to be converted.
 * After it has been converted it needs to be processed and then displayed.  Note that several locks are required
 * to avoid the three threads (GUI, camera preview, and processing) from interfering with each other.
 *
 * @author Peter Abeles
 */
public class VideoActivity extends Activity implements Camera.PreviewCallback {
    // camera and display objects
    private Camera mCamera;
    Camera.Parameters cameraParms;
    private Visualization mDraw;
    private CameraPreview mPreview;
    Sounds sounds;
    Notific notific;
    VideoProcessor videoProcessor;
    private TextToSpeech tts;


    // Thread where image data is processed
    private ThreadProcess thread;


    // if true the input image is flipped horizontally
    // Front facing cameras need to be flipped to appear correctly
    boolean flipHorizontal;



    public void click1(View view) {
        sounds.sound(State.CLICK);
    }

    public void pixelsCheckboxClicked(View view){
        videoProcessor.setShowPixels( ((CheckBox)view).isChecked() );
    }

    public void soundCheckboxClicked(View view){
        sounds.setEnabled(((CheckBox) view).isChecked());
    }

    public void flashCheckboxClicked(View view){
        boolean checked = ((CheckBox) view).isChecked();

        //Check Whether device supports AutoFlash, If you YES then set AutoFlash
        List<String> flashModes = cameraParms.getSupportedFlashModes();
        if (flashModes!=null && flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)){
            if( checked ) {
                cameraParms.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                tts.speak("Flashlight", TextToSpeech.QUEUE_FLUSH, null);
            }else{
                cameraParms.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            }
        }else{
            tts.speak("No flashlight in this phone", TextToSpeech.QUEUE_FLUSH, null);
        }
        mCamera.setParameters(cameraParms);
        mCamera.startPreview();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {

                    int result = tts.setLanguage(Locale.US);

                    if (result == TextToSpeech.LANG_MISSING_DATA
                            || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "This Language is not supported");
                    } else {
                        //btnSpeak.setEnabled(true);
                        //speakOut();
                    }

                } else {
                    Log.e("TTS", "Initilization Failed!");
                }
            }
        });
        sounds = new Sounds(this, tts);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.video);

        // Used to visualize the results
        mDraw = new Visualization(this);

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this,this,true);

        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);

        preview.addView(mPreview);
        preview.addView(mDraw);
    }

    @Override
    public void onDestroy() {
        // Don't forget to shutdown tts!
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.w("VideoActivity", "onResume");
        setUpAndConfigureCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // stop the camera preview and all processing
        if (mCamera != null){
            mPreview.setCamera(null);
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;

            thread.stopThread();
            thread = null;
        }
    }

    /**
     * Sets up the camera if it is not already setup.
     */
    private void setUpAndConfigureCamera() {
        // Open and configure the camera
        mCamera = selectAndOpenCamera();

        cameraParms = mCamera.getParameters();

        // Select the preview size closest to 320x240
        // Smaller images are recommended because some computer vision operations are very expensive
        List<Camera.Size> sizes = cameraParms.getSupportedPreviewSizes();
        Camera.Size s = sizes.get(closest(sizes,320,240));
        cameraParms.setPreviewSize(s.width, s.height);
        mCamera.setParameters(cameraParms);

        Log.w("VideoActivity", "chosen preview size "+s.width + " x "+s.height);

        notific = new Notific(sounds);
        videoProcessor = new VideoProcessor(s, notific);

        // start image processing thread
        thread = new ThreadProcess();
        thread.start();

        // Start the video feed by passing it to mPreview
        mPreview.setCamera(mCamera);
    }

    /**
     * Step through the camera list and select a camera.  It is also possible that there is no camera.
     * The camera hardware requirement in AndroidManifest.xml was turned off so that devices with just
     * a front facing camera can be found.  Newer SDK's handle this in a more sane way, but with older devices
     * you need this work around.
     */
    private Camera selectAndOpenCamera() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        int numberOfCameras = Camera.getNumberOfCameras();

        int selected = -1;

        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, info);

            if( info.facing == Camera.CameraInfo.CAMERA_FACING_BACK ) {
                selected = i;
                flipHorizontal = false;
                break;
            } else {
                // default to a front facing camera if a back facing one can't be found
                selected = i;
                flipHorizontal = true;
            }
        }

        if( selected == -1 ) {
            dialogNoCamera();
            return null; // won't ever be called
        } else {
            return Camera.open(selected);
        }
    }

    /**
     * Gracefully handle the situation where a camera could not be found
     */
    private void dialogNoCamera() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your device has no cameras!")
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        System.exit(0);
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Goes through the size list and selects the one which is the closest specified size
     */
    public static int closest( List<Camera.Size> sizes , int width , int height ) {
        int best = -1;
        int bestScore = Integer.MAX_VALUE;

        for( int i = 0; i < sizes.size(); i++ ) {
            Camera.Size s = sizes.get(i);

            int dx = s.width-width;
            int dy = s.height-height;

            int score = dx*dx + dy*dy;
            if( score < bestScore ) {
                best = i;
                bestScore = score;
            }
        }

        return best;
    }

    /**
     * Called each time a new image arrives in the data stream.
     */
    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {

        videoProcessor.onPreviewFrame(bytes);

        // Can only do trivial amounts of image processing inside this function or else bad stuff happens.
        // To work around this issue most of the processing has been pushed onto a thread and the call below
        // tells the thread to wake up and process another image
        thread.interrupt();
    }

    /**
     * Draws on top of the video stream for visualizing computer vision results
     */
    private class Visualization extends SurfaceView {

        Activity activity;

        public Visualization(Activity context ) {
            super(context);
            this.activity = context;

            // This call is necessary, or else the
            // draw method will not be called.
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas){

            synchronized ( videoProcessor.getLockOutput() ) {
                int w = canvas.getWidth();
                int h = canvas.getHeight();

                Bitmap output = videoProcessor.getOutput();

                // fill the window and center it
                double scaleX = w/(double)output.getWidth();
                double scaleY = h/(double)output.getHeight();

                double scale = Math.min(scaleX,scaleY);
                double tranX = (w-scale*output.getWidth())/2;
                double tranY = (h-scale*output.getHeight())/2;

                canvas.translate((float)tranX,(float)tranY);
                canvas.scale((float)scale,(float)scale);

                // draw the image
                canvas.drawBitmap(output,0,0,null);
            }
        }
    }

    /**
     * External thread used to do more time consuming image processing
     */
    private class ThreadProcess extends Thread {

        // true if a request has been made to stop the thread
        volatile boolean stopRequested = false;
        // true if the thread is running and can process more data
        volatile boolean running = true;

        /**
         * Blocks until the thread has stopped
         */
        public void stopThread() {
            stopRequested = true;
            while( running ) {
                thread.interrupt();
                Thread.yield();
            }
        }

        @Override
        public void run() {

            while( !stopRequested ) {

                // Sleep until it has been told to wake up
                synchronized ( Thread.currentThread() ) {
                    try {
                        wait();
                    } catch (InterruptedException ignored) {}
                }

                videoProcessor.backgroundProcess(flipHorizontal);

                mDraw.postInvalidate();
            }
            running = false;
        }
    }

}
