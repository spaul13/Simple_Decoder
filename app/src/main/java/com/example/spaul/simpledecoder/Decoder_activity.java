package com.example.spaul.simpledecoder;


import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import android.media.Image;
import android.graphics.ImageFormat;
import java.io.ByteArrayOutputStream;
import android.graphics.Rect;
import android.graphics.BitmapFactory;
import android.graphics.YuvImage;


import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED;
import static android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED;
import static android.media.MediaCodec.INFO_TRY_AGAIN_LATER;


/**
 * Created by spaul on 8/14/2019.
 */

public class Decoder_activity {

    private static final File FILES_DIR = Environment.getExternalStorageDirectory();
    private static final String INPUT_FILE = "source.mp4";

    private static final String TAG = "SP_MediaDecoder";
    //bmp configuration
    private static int m_width = 4096;
    private static int m_height = 512;
    private ByteBuffer outputBuffer;
    private static int decode_count = 0;
    public static int MAX_FRAMES = 10;
    private ByteBuffer[] outputBuf_array;
    private Image decoded_image;


    public boolean feedInputBuffer(MediaExtractor source, MediaCodec codec) {

        if (source == null || codec == null) return false;
        Log.d(TAG, "Inside feedInputBuffer");

        int inIndex = codec.dequeueInputBuffer(0);
        if (inIndex < 0)  return false;

        ByteBuffer codecInputBuffer = codec.getInputBuffers()[inIndex];
        codecInputBuffer.position(0);
        int sampleDataSize = source.readSampleData(codecInputBuffer,0);
        Log.d(TAG, "input index = " +inIndex);

        if (sampleDataSize <=0 ) {


            if (inIndex >= 0)
                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            return false;
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        bufferInfo.offset = 0;
        bufferInfo.presentationTimeUs = source.getSampleTime();
        bufferInfo.size = sampleDataSize;
        bufferInfo.flags = source.getSampleFlags();
        Log.d(TAG, "bufferInfo size = " +bufferInfo.size );

        switch (inIndex)
        {
            case INFO_TRY_AGAIN_LATER: return true;
            default:
            {

                codec.queueInputBuffer(inIndex,
                        bufferInfo.offset,
                        bufferInfo.size,
                        bufferInfo.presentationTimeUs,
                        bufferInfo.flags
                );

                source.advance();

                return true;
            }
        }

    }

    public boolean drainOutputBuffer(MediaCodec mediaCodec) {

        if (mediaCodec == null) return false;

        Log.d(TAG, "Inside drainoutput Buffer");
        final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outIndex =  mediaCodec.dequeueOutputBuffer(info, 0);

        if ((info.flags & BUFFER_FLAG_END_OF_STREAM) != 0) {
            mediaCodec.releaseOutputBuffer(outIndex, false);
            return false;
        }
        //Info: INFO_OUTPUT_BUFFERS_CHANGED and getOutputBuffers() got deprecated at API 21, introduced at API 17
        //for getOutputBuffer(), getOutputImage() are also introduced at API 21
        switch (outIndex)
        {
            case INFO_OUTPUT_BUFFERS_CHANGED:
                Log.d(TAG, "output buffers changed" );
                return true;
            case INFO_TRY_AGAIN_LATER:
                Log.d(TAG, "try again later" );
                return true;
            case INFO_OUTPUT_FORMAT_CHANGED:
                Log.d(TAG, "output format changed" );
                return true;
            default:
            {
                if (outIndex >= 0 && info.size > 0)
                {
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    bufferInfo.presentationTimeUs = info.presentationTimeUs;
                    bufferInfo.size = info.size;
                    bufferInfo.flags = info.flags;
                    bufferInfo.offset = info.offset;
                    Log.d(TAG, "output index= " +outIndex + ", info size = "+ info.size );

                    //need to add get output buffer here
                    //Do we need to allocate this bytebuffer??
                    //option 1: (API 21)
                    outputBuffer = mediaCodec.getOutputBuffer(outIndex);
                    //option 2:
                    /*
                    outputBuf_array = mediaCodec.getOutputBuffers();
                    Log.d("SP","outputbuf length = " + outputBuf_array.length);
                    for(int i=0; i<outputBuf_array.length; i++)
                    {
                        Log.d("SP", " i = " + i + ", outputBuffer size = " + outputBuf_array[i].toString().length() + "," + outputBuf_array[i].position()+"," +outputBuf_array[i].limit());
                    }
                    */
                    //opt 3:(API 21)
                    decoded_image = mediaCodec.getOutputImage(outIndex);
                    Log.d("SP", "output image size = " + decoded_image.toString().length() + ",["+decoded_image.getPlanes() + "," + decoded_image.getWidth() +"," + decoded_image.getHeight()+"]");

                    //added from stackoverflow
                    //while using getoutputimage() outbuffer becomes inaccessible (showing error)
                    /*
                    outputBuffer.position(info.offset);
                    outputBuffer.limit(info.offset + info.size);
                    byte[] ba = new byte[outputBuffer.remaining()];
                    outputBuffer.get(ba);

                    Log.d("SP", "size of the output buffer = " + outputBuffer.toString().length() + "," + outputBuffer.position()+","+outputBuffer.limit());
                    */
                    //save the bmp through a function

                    File outputFile = new File(FILES_DIR,
                            String.format("frame-%02d.png", decode_count));
                    //long startWhen = System.nanoTime();
                    try {
                        Log.d(TAG, "Before calling save frame");
                        if(decode_count<MAX_FRAMES)
                        {
                            //saveFrame(outputFile.toString());
                            saveFrame_image(outputFile.toString());
                        }
                    }
                    catch(IOException e)
                    {

                    }

                    decode_count++;
                    mediaCodec.releaseOutputBuffer(outIndex, false);


                    Log.d(TAG,String.format("pts:%s",info.presentationTimeUs));


                }

                return true;
            }
        }
    }
    //for saving the frame from output Image to bitmap (still not working)--> problem still with copyPixelsFromBuffer()
    public void saveFrame_image(String filename) throws IOException {
        Log.d(TAG, "Inside saveFrame_image");
        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(filename));
            //save the image into bitmap
            if(decoded_image == null)
            {
                Log.d(TAG, "decoded image is null");
            }
            int width = decoded_image.getWidth();
            int height = decoded_image.getHeight();
            final Image.Plane[] planes = decoded_image.getPlanes();
            Log.d("SP", "width, height = " +width + "," + height +", planes length = " + planes.length);
            /*
            final ByteBuffer buffer = planes[0].getBuffer();
            Log.d("SP", " plane[0]: bytebuffer size = " + buffer.limit() + "," +buffer.position()); //buffer limit = 4096*512
            final ByteBuffer buffer1 = planes[1].getBuffer();
            Log.d("SP", " plane[1]: bytebuffer size = " + buffer1.limit() + "," +buffer1.position()); //buffer limit = 4096*512
            final ByteBuffer buffer2 = planes[2].getBuffer();
            Log.d("SP", " plane[2]: bytebuffer size = " + buffer2.limit() + "," +buffer2.position()); //buffer limit = 4096*512
            //I can get YUV plane data in three bytebuffer
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            Log.d("SP", "ps, rs, rp" + pixelStride + "," +rowStride + "," +rowPadding);
            Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);  //width + rowPadding / pixelStride  = width
            Log.d(TAG, "before copy pixels from buffer, before rewind() size = " +buffer.position() + "," + buffer.limit());
            buffer.rewind();
            Log.d(TAG, "before copy pixels from buffer, after rewind() size = " +buffer.position() + "," + buffer.limit());
            bitmap.copyPixelsFromBuffer(buffer);
            Log.d(TAG, "before saving the bmp");
            */

            //new way of getting bitmap
            /*start*/
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            //decoded_image.compressToJpeg(new Rect(0, 0, width, height), 50, out);
            byte[] imageBytes = NV21toJPEG(
                    YUV_420_888toNV21(decoded_image),
                    decoded_image.getWidth(), decoded_image.getHeight());
            //byte[] imageBytes = out.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, bos);
            /*end*/
            Log.d(TAG, "bmp recycle");
            bitmap.recycle();
        } finally {
            if (bos != null) bos.close();
        }


    }

    private static byte[] YUV_420_888toNV21(Image image) {
        byte[] nv21;
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        nv21 = new byte[ySize + uSize + vSize];

        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return nv21;
    }


    private static byte[] NV21toJPEG(byte[] nv21, int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);
        return out.toByteArray();
    }

    //for saving the frame from outbytebuffer to bitmap (not working)
    public void saveFrame(String filename) throws IOException {

        Log.d(TAG, "Inside saveFrame");
        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(filename));
            Log.d(TAG, "before creating bmp");
            Bitmap bmp = Bitmap.createBitmap(m_width, m_height, Bitmap.Config.ARGB_8888);

            Log.d(TAG, "before outbuf rewind");
            outputBuffer.rewind();
            Log.d(TAG, "copy pixels from buffer");
            bmp.copyPixelsFromBuffer(outputBuffer);//causing error here, telling buffer is not large enough for pixels
            Log.d(TAG, "compress and save it");
            bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
            Log.d(TAG, "recycle()");
            bmp.recycle();
        } finally {
            if (bos != null) bos.close();
        }
        Log.d(TAG, "Saved " + m_width + "x" + m_height + " frame as '" + filename + "'");


    }

    private void doDecoder(){


        File inputFile = new File(FILES_DIR, INPUT_FILE);   // must be an absolute path
        // The MediaExtractor error messages aren't very useful.  Check to see if the input
        // file exists so we can throw a better one if it's not there.
        Log.d(TAG, "Inside dodecoder");
        try {
            if (!inputFile.canRead()) {
                throw new FileNotFoundException("Unable to read " + inputFile);
            }
        }
        catch(FileNotFoundException ex)
        {

        }

        MediaExtractor extractor = new MediaExtractor();
        Log.d(TAG, "Inside MediaExtractor");

        //Uri videoPathUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.demo_video);
        try {
            extractor.setDataSource(inputFile.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }


        int trackCount = extractor.getTrackCount();
        Log.d(TAG, "trackcount = " + trackCount);

        String extractMimeType = "video/";
        MediaFormat trackFormat = null;

        int trackID = -1;
        for (int i = 0; i < trackCount; i++) {
            trackFormat = extractor.getTrackFormat(i);
            Log.d(TAG, "trackformat = " + trackFormat);
            if (trackFormat.getString(MediaFormat.KEY_MIME).startsWith(extractMimeType)) {
                trackID = i;
                break;
            }
        }

        if (trackID != -1)
            extractor.selectTrack(trackID);


        MediaCodec mediaCodec = null;
        try {
            mediaCodec = MediaCodec.createDecoderByType(trackFormat.getString(MediaFormat.KEY_MIME));
            mediaCodec.configure(trackFormat,null,null,0);
            Log.d(TAG, "Before starting the mediacodec = " + mediaCodec.getName());
            mediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }


        while (true) {
            Log.d(TAG, "Before feeding decoder");
            boolean ret = feedInputBuffer(extractor,mediaCodec);
            Log.d(TAG, "Before draining decoder");
            boolean decRet = drainOutputBuffer(mediaCodec);
            if (!ret && !decRet)break;;
        }

        extractor.release();

        mediaCodec.release();



    }

    public void startDecode() {

        Log.d(TAG, "Creating a thread to start decoding");
        new Thread(new Runnable() {
            @Override
            public void run() {
                doDecoder();
            }
        }).start();

    }

    private void showSupportedColorFormat(android.media.MediaCodecInfo.CodecCapabilities caps)
    {
        System.out.print("supported color format: ");
        for (int c : caps.colorFormats) {
            System.out.print(c + "\t");
        }
        System.out.println();
    }
}
