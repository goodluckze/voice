/*
 * Copyright (C) 2013 gujicheng
 * 
 * Licensed under the GPL License Version 2.0;
 * you may not use this file except in compliance with the License.
 * 
 * If you have any question, please contact me.
 * 
 *************************************************************************
 **                   Author information                                **
 *************************************************************************
 ** Email: gujicheng197@126.com                                         **
 ** QQ   : 29600731                                                     **
 ** Weibo: http://weibo.com/gujicheng197                                **
 *************************************************************************
 */
package com.libra.sinvoice;

import com.libra.sinvoice.Buffer.BufferData;
import com.libra.sinvoice.LogHelper;

public class SinGenerator {
    private static final String TAG = "SinGenerator";

    private static final int STATE_START = 1;
    private static final int STATE_STOP = 2;

    public static final int BITS_8 = 128;
    public static final int BITS_16 = 32768;

    public static final int SAMPLE_RATE_8 = 8000;
    public static final int SAMPLE_RATE_11 = 11250;
    public static final int SAMPLE_RATE_16 = 16000;

    public static final int UNIT_ACCURACY_1 = 4;
    public static final int UNIT_ACCURACY_2 = 8;

    private int mState;
    private int mSampleRate;
    private int mBits;
    private int mDuration;
    private int mGenRate;

    private static final int DEFAULT_BITS = BITS_8;
    private static final int DEFAULT_SAMPLE_RATE = SAMPLE_RATE_8;
    private static final int DEFAULT_BUFFER_SIZE = 1024;

    private int mFilledSize;
    private int mBufferSize;
    private Listener mListener;
    private Callback mCallback;

    public static interface Listener {
        void onStartGen();

        void onStopGen();
    }

    public static interface Callback {
        BufferData getGenBuffer();

        void freeGenBuffer(BufferData buffer);
    }

    public SinGenerator(Callback callback) {
        this(callback, DEFAULT_SAMPLE_RATE, DEFAULT_BITS, DEFAULT_BUFFER_SIZE);
    }

    public SinGenerator(Callback callback, int sampleRate, int bits, int bufferSize) {
        mCallback = callback;

        mBufferSize = bufferSize;
        mSampleRate = sampleRate;
        mBits = bits;
        mDuration = 0;

        mFilledSize = 0;
        mState = STATE_STOP;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void stop() {
        if (STATE_START == mState) {
            mState = STATE_STOP;
        }
    }

    public void start() {
        if (STATE_STOP == mState) {
            mState = STATE_START;
        }
    }

    // genrate 频率
    // duration 持续时间

    /**
     *
     * NO1.每个音节持续时间(duration)

     如果把传输一个字符的正弦波看做是一个音节，
     那么每个音节的持续时间同样也影响到我们对声音信号的记录和还原。
     对识别声波的成功率有着重要的影响。
     当durantion变大时，音节持续时间变成，采样点变多，声音信号的还原度变高，解析出的音频中携带的频率信息出错率变低。
     同时，采样点变多，PCM文件变大，音节变长，传输过程变长，传输速率降低。

     NO2.每个音节的采样点unit_sample

     我们已经选定了采样率和每个音节的持续时间。

     很自然的就可以计算出存储每个音节信息的采样点数目unit_sample = duration * sample_rate。

     在发送端我们只要把每个采样点都通过energy公式计算出来，存储下来就OK了

     */
    public void gen(int genRate, int duration) {
        if (STATE_START == mState) {
            mGenRate = genRate;
            mDuration = duration;

            if (null != mListener) {
                mListener.onStartGen();
            }

            int n = mBits / 2;

            // 一帧时长*采样率 = 一帧采样个数
            int totalCount = (mDuration * mSampleRate) / 1000;
            // 正弦波频率 = 频率／采样率*2pi
            double per = (mGenRate / (double) mSampleRate) * 2 * Math.PI;
            double d = 0;

            LogHelper.d(TAG, "genRate:" + genRate);
            if (null != mCallback) {
                mFilledSize = 0;
                BufferData buffer = mCallback.getGenBuffer();
                if (null != buffer) {
                    // 取每个采样的正弦波的值
                    for (int i = 0; i < totalCount; ++i) {
                        if (STATE_START == mState) {
                            // 正弦波计算公式--可替换，方便解码即可
                            int out = (int) (Math.sin(d) * n) + 128;

                            if (mFilledSize >= mBufferSize - 1) {
                                // free buffer
                                buffer.setFilledSize(mFilledSize);
                                mCallback.freeGenBuffer(buffer);

                                mFilledSize = 0;
                                buffer = mCallback.getGenBuffer();
                                if (null == buffer) {
                                    LogHelper.e(TAG, "get null buffer");
                                    break;
                                }
                            }

                            buffer.mData[mFilledSize++] = (byte) (out & 0xff);
                            if (BITS_16 == mBits) {
                                buffer.mData[mFilledSize++] = (byte) ((out >> 8) & 0xff);
                            }

                            d += per;
                        } else {
                            LogHelper.d(TAG, "sin gen force stop");
                            break;
                        }
                    }
                } else {
                    LogHelper.e(TAG, "get null buffer");
                }

                if (null != buffer) {
                    buffer.setFilledSize(mFilledSize);
                    mCallback.freeGenBuffer(buffer);
                }
                mFilledSize = 0;

                if (null != mListener) {
                    mListener.onStopGen();
                }
            }
        }
    }
}
