package com.example.spaul.simpledecoder;


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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

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

                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outIndex);
                    Log.d("SP", "size of the output buffer = " + outputBuffer.toString().length() + "," + outputBuffer.position()+","+outputBuffer.limit());
                    //save the bmp through a function


                    mediaCodec.releaseOutputBuffer(outIndex, false);
                    //need to add get output buffer here

                    Log.d(TAG,String.format("pts:%s",info.presentationTimeUs));


                }

                return true;
            }
        }
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
}
