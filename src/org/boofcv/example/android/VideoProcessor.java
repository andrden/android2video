package org.boofcv.example.android;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Camera;
import android.util.Log;
import boofcv.abst.filter.derivative.ImageGradient;
import boofcv.alg.misc.GImageMiscOps;
import boofcv.android.ConvertBitmap;
import boofcv.android.ConvertNV21;
import boofcv.factory.filter.derivative.FactoryDerivative;
import boofcv.struct.image.*;

import java.nio.ByteBuffer;

/**
 * Created by denny on 5/7/15.
 */
public class VideoProcessor {
    //private BufRW<ImageUInt8> gray;
    //private BufRW<MultiSpectral<ImageFloat32>> yuv;
    private BufRW<MultiSpectral<ImageUInt8>> rgbInt;

    volatile boolean showPixels = false;


    //private ImageSInt16 derivX,derivY;
    //MultiSpectral<ImageFloat32> rgb;

    // Object used for synchronizing output image
    private final Object lockOutput = new Object();

    // Android image data used for displaying the results
    private Bitmap output;
    // temporary storage that's needed when converting from BoofCV to Android image data types
    private byte[] storage;

    // computes the image gradient
    private ImageGradient<ImageUInt8,ImageSInt16> gradient = FactoryDerivative.three(ImageUInt8.class, ImageSInt16.class);

    Notific notific;

    public VideoProcessor(Camera.Size s, Notific notific) {
        this.notific = notific;
        // declare image data
//        gray = new BufRW<ImageUInt8>( new ImageUInt8(s.width,s.height), new ImageUInt8(s.width,s.height) );
//        yuv = new BufRW<MultiSpectral<ImageFloat32>>(
//                new MultiSpectral<ImageFloat32>(ImageFloat32.class,s.width,s.height,3),
//                new MultiSpectral<ImageFloat32>(ImageFloat32.class,s.width,s.height,3)
//        );
        rgbInt = new BufRW<MultiSpectral<ImageUInt8>>(
                new MultiSpectral<ImageUInt8>(ImageUInt8.class,s.width,s.height,3),
                new MultiSpectral<ImageUInt8>(ImageUInt8.class,s.width,s.height,3)
        );
        //rgb = new MultiSpectral<ImageFloat32>(ImageFloat32.class,s.width,s.height,3);
        //Arrays.fill( rgb.getBand(3).data, 1);
        //derivX = new ImageSInt16(s.width,s.height);
        //derivY = new ImageSInt16(s.width,s.height);

        output = Bitmap.createBitmap(s.width,s.height,Bitmap.Config.ARGB_8888 );
        storage = ConvertBitmap.declareStorage(output, storage);

    }

    class PixelStats{
        State state;
        //RGBi target;
        ColorSet colorSet;
        int numClose=0;

        PixelStats(State state, ColorSet colorSet) {
            this.state = state;
            this.colorSet = colorSet;
        }

        void update(RGBi pixel){
            //if( close(pixel, target) ){
            if( colorSet.match(pixel) ){
                numClose++;
            }
        }
    }

    public void backgroundProcess(boolean flipHorizontal) {
        //gray.syncSwapBufs();
        //yuv.syncSwapBufs();
        rgbInt.syncSwapBufs();

        MultiSpectral<ImageUInt8> imgBuf = rgbInt.readBuf;
        if( flipHorizontal ) {
          //  GImageMiscOps.flipHorizontal(gray.readBuf);
            //GImageMiscOps.flipHorizontal(yuv.readBuf);
            GImageMiscOps.flipHorizontal(imgBuf);
        }

        PixelStats[] colors = {
//            new PixelStats(State.RED, new RGBi(255,0,0)),
//            new PixelStats(State.GREEN, new RGBi(0,255,0)),
//            new PixelStats(State.BLUE, new RGBi(0,0,255)),

                new PixelStats(State.RED, ColorSet.RED),
                new PixelStats(State.GREEN, ColorSet.GREEN),
                new PixelStats(State.BLUE, ColorSet.BLUE),
        };
        RGBi pixel = new RGBi();

        int numAll = imgBuf.getBand(0).data.length;
        for( int i=0; i<numAll; i++ ){
            get(imgBuf, i, pixel);
            for( PixelStats ps : colors ){
                ps.update(pixel);
            }
            if( showPixels ){
                if( ColorSet.RED.match(pixel) ){
                    set(imgBuf, i, 255,0,0);
                }
                if( ColorSet.GREEN.match(pixel) ){
                    set(imgBuf, i, 0,255,0);
                }
                if( ColorSet.BLUE.match(pixel) ){
                    set(imgBuf, i, 0,0,255);
                }
            }
        }
        State state = null;
        int fitCount=0;
        for( PixelStats ps : colors ){
            boolean isColor = ps.numClose > numAll * 0.2;
            if( isColor ){
                state = ps.state;
                fitCount++;
            }
        }
        if( fitCount >= 2 ){
            state=null;
        }

        notific.state(state);

        // process the image and compute its gradient
       // gradient.process(gray.readBuf,derivX,derivY);

        // render the output in a synthetic color image
        synchronized ( lockOutput ) {
           ConvertBitmap.multiToBitmap(imgBuf, output, storage);
           if( state != null ) {
               int w = output.getWidth();
               for (int y = 100; y < 110; y++) {
                   for (int x = 0; x < w; x++) {
                      output.setPixel(x, y, state.color);
                   }
               }
           }
        }

    }

    static class RGBf{
        float r,g,b;

        RGBf() {
        }

        RGBf(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    static class BufRW<T>{
        // Two images are needed to store the converted preview image to prevent a thread conflict from occurring
        T readBuf;
        T writeBuf;

        // Object used for synchronizing swap
        private final Object lock = new Object();
        BufRW(T b1, T b2){
            readBuf = b1;
            writeBuf = b2;
        }


        public void syncSwapBufs() {
            // process the most recently converted image by swapping image buffered
            synchronized (lock) {
                T tmp = readBuf;
                readBuf = writeBuf;
                writeBuf = tmp;
            }

        }
    }

    public void onPreviewFrame(byte[] bytes){
        try {
//            synchronized (gray.lock) {
//                // convert from NV21 format into gray scale
//                ConvertNV21.nv21ToGray(bytes, gray.writeBuf.width, gray.writeBuf.height, gray.writeBuf);
//            }
//            ConvertNV21.nv21ToMsYuv_F32(bytes, yuv.writeBuf.width, yuv.writeBuf.height, yuv.writeBuf);
            synchronized (rgbInt.lock) {
                ConvertNV21.nv21ToMsRgb_U8(bytes, rgbInt.writeBuf.width, rgbInt.writeBuf.height, rgbInt.writeBuf);
            }
        }catch(Throwable t){
            Log.w("VideoProcessor","onPreviewFrame",t);
        }
    }

    void get(MultiSpectral<ImageFloat32> img, int x, int y, RGBf dest){
        dest.r = img.getBand(0).get(x,y);
        dest.g = img.getBand(1).get(x,y);
        dest.b = img.getBand(2).get(x,y);
    }
    void get(MultiSpectral<ImageUInt8> img, int x, int y, RGBi dest){
        dest.r = img.getBand(0).get(x,y);
        dest.g = img.getBand(1).get(x,y);
        dest.b = img.getBand(2).get(x,y);
    }
    void get(MultiSpectral<ImageFloat32> img, int idx, RGBf dest){
        dest.r = img.getBand(0).data[idx];
        dest.g = img.getBand(1).data[idx];
        dest.b = img.getBand(2).data[idx];
    }
    void get(MultiSpectral<ImageUInt8> img, int idx, RGBi dest){
        dest.r = img.getBand(0).data[idx] & 0xFF;
        dest.g = img.getBand(1).data[idx] & 0xFF;
        dest.b = img.getBand(2).data[idx] & 0xFF;
    }
    void set(MultiSpectral<ImageUInt8> img, int idx, int r, int g, int b){
        img.getBand(0).data[idx] = (byte)(r & 0xFF) ;
        img.getBand(1).data[idx] = (byte)(g & 0xFF);
        img.getBand(2).data[idx] = (byte)(b & 0xFF);
    }

    boolean close(RGBf c1 , RGBf c2){
        if( Math.abs(c1.r-c2.r)>0.5 ) return false;
        if( Math.abs(c1.g-c2.g)>0.5 ) return false;
        if( Math.abs(c1.b-c2.b)>0.5 ) return false;
        return true;
    }

    boolean close(RGBi c1 , RGBi c2){
        if( Math.abs(c1.r-c2.r)>128 ) return false;
        if( Math.abs(c1.g-c2.g)>128 ) return false;
        if( Math.abs(c1.b-c2.b)>128 ) return false;
        return true;
    }

    public Bitmap getOutput() {
        return output;
    }

    public Object getLockOutput() {
        return lockOutput;
    }

    public static void multiToBitmap_F32(MultiSpectral<ImageFloat32> input, byte[] storage , Bitmap output ) {
        final int h = input.height;
        final int w = input.width;

        ImageFloat32 R = input.getBand(0);
        ImageFloat32 G = input.getBand(1);
        ImageFloat32 B = input.getBand(2);

        int indexDst = 0;

        for (int y = 0; y < h; y++) {
            int indexSrc = input.startIndex + y * input.stride;
            for (int x = 0; x < w; x++, indexSrc++) {
                storage[indexDst++] = (byte) (R.data[indexSrc]);
                storage[indexDst++] = (byte) (G.data[indexSrc]);
                storage[indexDst++] = (byte) (B.data[indexSrc]);
                storage[indexDst++] = (byte) 255;
            }
        }

        output.copyPixelsFromBuffer(ByteBuffer.wrap(storage));

    }

    public void setShowPixels(boolean showPixels) {
        this.showPixels = showPixels;
    }
}
