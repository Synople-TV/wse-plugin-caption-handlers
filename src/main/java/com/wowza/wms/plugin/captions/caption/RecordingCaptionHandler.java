/*
 * This code and all components (c) Copyright 2006 - 2025, Wowza Media Systems, LLC.  All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */

package com.wowza.wms.plugin.captions.caption;

import com.wowza.wms.application.IApplicationInstance;

/**
 * Forwards captions to the live delayed-stream handler and archives WebVTT sidecars.
 */
public class RecordingCaptionHandler implements CaptionHandler, AutoCloseable
{
    private final CaptionHandler delegate;
    private final WebVttCaptionRecorder recorder;

    public RecordingCaptionHandler(CaptionHandler delegate, WebVttCaptionRecorder recorder)
    {
        this.delegate = delegate;
        this.recorder = recorder;
    }

    public static CaptionHandler wrap(IApplicationInstance appInstance, CaptionHandler delegate)
    {
        if (delegate == null || !WebVttCaptionRecorder.isEnabled(appInstance))
            return delegate;
        WebVttCaptionRecorder recorder = new WebVttCaptionRecorder(appInstance, delegate.getStreamName());
        return new RecordingCaptionHandler(delegate, recorder);
    }

    @Override
    public void handleCaption(Caption caption)
    {
        delegate.handleCaption(caption);
        if (recorder != null)
            recorder.append(caption);
    }

    @Override
    public int getWordsPerMinute()
    {
        return delegate.getWordsPerMinute();
    }

    @Override
    public void setWordsPerMinute(int wordsPerMinute)
    {
        delegate.setWordsPerMinute(wordsPerMinute);
    }

    @Override
    public CaptionTiming getCaptionTiming()
    {
        return delegate.getCaptionTiming();
    }

    @Override
    public long getStartOffset()
    {
        return delegate.getStartOffset();
    }

    @Override
    public String getStreamName()
    {
        return delegate.getStreamName();
    }

    @Override
    public void close()
    {
        if (recorder != null)
            recorder.close();
    }
}
