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

import java.util.ArrayList;
import java.util.List;

import android.media.AudioFormat;
import android.text.TextUtils;

import com.libra.sinvoice.Buffer.BufferData;

public class SinVoicePlayer implements Encoder.Listener, Encoder.Callback, PcmPlayer.Listener, PcmPlayer.Callback {
    private final static String TAG = "SinVoicePlayer";

    private final static int STATE_START = 1;
    private final static int STATE_STOP = 2;
    private final static int STATE_PENDING = 3;

    private final static int DEFAULT_GEN_DURATION = 100;

    // 编码范围
    private String mCodeBook;
    private List<Integer> mCodes = new ArrayList<Integer>();

    private Encoder mEncoder;
    private PcmPlayer mPlayer;
    private Buffer mBuffer;

    private int mState;
    private Listener mListener;
    private Thread mPlayThread;
    private Thread mEncodeThread;

    public static interface Listener {
        void onPlayStart();

        void onPlayEnd();
    }

    public SinVoicePlayer() {
        this(Common.DEFAULT_CODE_BOOK);
    }

    public SinVoicePlayer(String codeBook) {
        this(codeBook, Common.DEFAULT_SAMPLE_RATE, Common.DEFAULT_BUFFER_SIZE, Common.DEFAULT_BUFFER_COUNT);
    }

    public SinVoicePlayer(String codeBook, int sampleRate, int bufferSize, int buffCount) {
        mState = STATE_STOP;
        mBuffer = new Buffer(buffCount, bufferSize);

        mEncoder = new Encoder(this, sampleRate, SinGenerator.BITS_16, bufferSize);
        mEncoder.setListener(this);
        mPlayer = new PcmPlayer(this, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        mPlayer.setListener(this);

        setCodeBook(codeBook);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setCodeBook(String codeBook) {
        if (!TextUtils.isEmpty(codeBook) && codeBook.length() < Encoder.getMaxCodeCount() - 1) {
            mCodeBook = codeBook;
        }
    }

    /**
     *
     * @param text 编码内容
     * @return
     */

    private boolean convertTextToCodes(String text) {
        boolean ret = true;

        if (!TextUtils.isEmpty(text)) {
            // 清空
            mCodes.clear();
            // mCode指频率列表，增加开始标志
            mCodes.add(Common.START_TOKEN);
            int len = text.length();
            // 遍历text每个字符，在CodeBook如果找到，则增加到mCode，如果没有则报错
            for (int i = 0; i < len; ++i) {
                char ch = text.charAt(i);
                int index = mCodeBook.indexOf(ch);
                if (index > -1) {
                    mCodes.add(index + 1);
                } else {
                    ret = false;
                    LogHelper.d(TAG, "invalidate char:" + ch);
                    break;
                }
            }
            // 添加结束标志
            if (ret) {
                mCodes.add(Common.STOP_TOKEN);
            }
        } else {
            ret = false;
        }

        return ret;
    }

    public void play(final String text) {
        play(text, false, 0);
    }


    public void play(final String text, final boolean repeat, final int muteInterval) {

        // 判断状态、编码范围不为空、编码内容合法
        if (STATE_STOP == mState && null != mCodeBook && convertTextToCodes(text)) {
            mState = STATE_PENDING;
            //
            // pcm播放线程开始执行
            mPlayThread = new Thread() {
                @Override
                public void run() {
                    mPlayer.start();
                }
            };
            if (null != mPlayThread) {
                mPlayThread.start();
            }
            // 编码转频率线程开始执行
            mEncodeThread = new Thread() {
                @Override
                public void run() {
                    do {
                        LogHelper.d(TAG, "encode start");
                        /**
                         * 待编码数组，含开始标志+内容+结束标志
                         * 每个频率播放时长 100ms
                         * 间隔时间
                         */
                        mEncoder.encode(mCodes, DEFAULT_GEN_DURATION, muteInterval);
                        LogHelper.d(TAG, "encode end");

                        mEncoder.stop();
                    } while (repeat && STATE_PENDING != mState);
                    stopPlayer();
                }
            };
            if (null != mEncodeThread) {
                mEncodeThread.start();
            }

            LogHelper.d(TAG, "play");
            // 将状态改为开始
            mState = STATE_START;
        }
    }

    public void stop() {
        if (STATE_START == mState) {
            mState = STATE_PENDING;

            LogHelper.d(TAG, "force stop start");
            mEncoder.stop();
            if (null != mEncodeThread) {
                try {
                    mEncodeThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    mEncodeThread = null;
                }
            }

            LogHelper.d(TAG, "force stop end");
        }
    }

    private void stopPlayer() {
        if (mEncoder.isStoped()) {
            mPlayer.stop();
        }

        // put end buffer
        mBuffer.putFull(BufferData.getEmptyBuffer());

        if (null != mPlayThread) {
            try {
                mPlayThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                mPlayThread = null;
            }
        }

        mBuffer.reset();
        mState = STATE_STOP;
    }

    @Override
    public void onStartEncode() {
        LogHelper.d(TAG, "onStartGen");
    }

    @Override
    public void freeEncodeBuffer(BufferData buffer) {
        if (null != buffer) {
            mBuffer.putFull(buffer);
        }
    }

    @Override
    public BufferData getEncodeBuffer() {
        return mBuffer.getEmpty();
    }

    @Override
    public void onEndEncode() {
    }

    @Override
    public BufferData getPlayBuffer() {
        return mBuffer.getFull();
    }

    @Override
    public void freePlayData(BufferData data) {
        mBuffer.putEmpty(data);
    }

    @Override
    public void onPlayStart() {
        if (null != mListener) {
            mListener.onPlayStart();
        }
    }

    @Override
    public void onPlayStop() {
        if (null != mListener) {
            mListener.onPlayEnd();
        }
    }

}
