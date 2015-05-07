package org.boofcv.example.android;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Camera;
import boofcv.abst.filter.derivative.ImageGradient;
import boofcv.alg.color.ColorYuv;
import boofcv.alg.misc.GImageMiscOps;
import boofcv.android.ConvertBitmap;
import boofcv.android.ConvertNV21;
import boofcv.android.VisualizeImageData;
import boofcv.factory.filter.derivative.FactoryDerivative;
import boofcv.struct.image.*;

/**
 * Created by denny on 5/7/15.
 */
public class VideoProcessor {
    private BufRW<ImageUInt8> gray;
    private BufRW<MultiSpectral<ImageFloat32>> yuv;


    private ImageSInt16 derivX,derivY;
    MultiSpectral<ImageFloat32> rgb;

    // Object used for synchronizing output image
    private final Object lockOutput = new Object();

    // Android image data used for displaying the results
    private Bitmap output;
    // temporary storage that's needed when converting from BoofCV to Android image data types
    private byte[] storage;

    // computes the image gradient
    private ImageGradient<ImageUInt8,ImageSInt16> gradient = FactoryDerivative.three(ImageUInt8.class, ImageSInt16.class);

    public VideoProcessor(Camera.Size s) {
        // declare image data
        gray = new BufRW<ImageUInt8>( new ImageUInt8(s.width,s.height), new ImageUInt8(s.width,s.height) );
        yuv = new BufRW<MultiSpectral<ImageFloat32>>(
                new MultiSpectral<ImageFloat32>(ImageFloat32.class,s.width,s.height,3),
                new MultiSpectral<ImageFloat32>(ImageFloat32.class,s.width,s.height,3)
        );
        rgb = new MultiSpectral<ImageFloat32>(ImageFloat32.class,s.width,s.height,3);
        derivX = new ImageSInt16(s.width,s.height);
        derivY = new ImageSInt16(s.width,s.height);

        output = Bitmap.createBitmap(s.width,s.height,Bitmap.Config.ARGB_8888 );
        storage = ConvertBitmap.declareStorage(output, storage);

    }

    public void backgroundProcess(boolean flipHorizontal) {
        gray.swapBufs();

        if( flipHorizontal ) {
            GImageMiscOps.flipHorizontal(gray.readBuf);
            //GImageMiscOps.flipHorizontal(yuv.readBuf);
        }

//        ColorYuv.yuvToRgb_F32(yuv.readBuf, rgb);
//        RGBf pixel = new RGBf();
//        RGBf blue = new RGBf(0,0,1);
//        int numClose=0;
//        int numAll = rgb.getBand(0).data.length;
//        for( int i=0; i<numAll; i++ ){
//            get(rgb, i, pixel);
//            if( close(pixel, blue) ){
//                numClose++;
//            }
//        }
//        boolean isBlue = numClose > numAll * 0.5;

        // process the image and compute its gradient
        gradient.process(gray.readBuf,derivX,derivY);

        // render the output in a synthetic color image
        synchronized ( lockOutput ) {
            VisualizeImageData.colorizeGradient(derivX, derivY, -1, output, storage);
            //ConvertBitmap.multiToBitmap(yuv.readBuf, output, storage);
            int w = output.getWidth();
            for( int x=0; x<w-1; x++ ){
//                if( isBlue ) {
                    output.setPixel(x, 100, Color.BLUE);
  //              }
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


        public void swapBufs() {
            // process the most recently converted image by swapping image buffered
            synchronized (lock) {
                T tmp = readBuf;
                readBuf = writeBuf;
                writeBuf = tmp;
            }

        }
    }

    public void onPreviewFrame(byte[] bytes){
        // convert from NV21 format into gray scale
        synchronized (gray.lock) {
            ConvertNV21.nv21ToGray(bytes, gray.writeBuf.width, gray.writeBuf.height, gray.writeBuf);
            ConvertNV21.nv21ToMsYuv_F32(bytes, yuv.writeBuf.width, yuv.writeBuf.height, yuv.writeBuf);
        }

    }

    void get(MultiSpectral<ImageFloat32> img, int x, int y, RGBf dest){
        dest.r = img.getBand(0).get(x,y);
        dest.g = img.getBand(1).get(x,y);
        dest.b = img.getBand(2).get(x,y);
    }
    void get(MultiSpectral<ImageFloat32> img, int idx, RGBf dest){
        dest.r = img.getBand(0).data[idx];
        dest.g = img.getBand(1).data[idx];
        dest.b = img.getBand(2).data[idx];
    }

    boolean close(RGBf c1 , RGBf c2){
        if( Math.abs(c1.r-c2.r)>0.5 ) return false;
        if( Math.abs(c1.g-c2.g)>0.5 ) return false;
        if( Math.abs(c1.b-c2.b)>0.5 ) return false;
        return true;
    }

    public Bitmap getOutput() {
        return output;
    }

    public Object getLockOutput() {
        return lockOutput;
    }
/*
    public static void toBitmap( MultiSpectral<ImageFloat32> img , Bitmap output , byte[] storage ) {
        shapeShape(img, output);

        if( storage == null )
            storage = declareStorage(output,null);

        int indexDst = 0;

        if( invert ) {
            for (int y = 0; y < binary.height; y++) {
                int indexSrc = binary.startIndex + y * binary.stride;
                for (int x = 0; x < binary.width; x++) {
                    int value = (1-binary.data[indexSrc++]) * 255;

                    storage[indexDst++] = (byte) value;
                    storage[indexDst++] = (byte) value;
                    storage[indexDst++] = (byte) value;
                    storage[indexDst++] = (byte) 0xFF;
                }
            }
        } else {
            for (int y = 0; y < binary.height; y++) {
                int indexSrc = binary.startIndex + y * binary.stride;
                for (int x = 0; x < binary.width; x++) {
                    int value = binary.data[indexSrc++] * 255;

                    storage[indexDst++] = (byte) value;
                    storage[indexDst++] = (byte) value;
                    storage[indexDst++] = (byte) value;
                    storage[indexDst++] = (byte) 0xFF;
                }
            }
        }

        output.copyPixelsFromBuffer(ByteBuffer.wrap(storage));
    }
    private static void shapeShape(ImageBase input, Bitmap output) {
        if( output.getConfig() != Bitmap.Config.ARGB_8888 )
            throw new IllegalArgumentException("Only ARGB_8888 is supported");
        if( input.width != output.getWidth() || input.height != output.getHeight() )
            throw new IllegalArgumentException("Input and output must have the same shape");
    }*/


}
