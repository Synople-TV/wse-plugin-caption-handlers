/*
 * This code and all components (c) Copyright 2006 - 2025, Wowza Media Systems, LLC.  All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */

package com.wowza.wms.plugin.captions.stream;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.LiveStreamPacketizerCupertino;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.module.ModuleItem;
import com.wowza.wms.module.ModuleList;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.stream.livepacketizer.*;
import com.wowza.wms.timedtext.model.ITimedTextConstants;

import java.util.Collection;
import java.util.function.Predicate;

import static com.wowza.wms.plugin.captions.ModuleCaptionsBase.CAPTION_DELIVERY_CEA;
import static com.wowza.wms.plugin.captions.ModuleCaptionsBase.CAPTION_DELIVERY_WEBVTT;
import static com.wowza.wms.plugin.captions.ModuleCaptionsBase.DELAYED_STREAM_SUFFIX;
import static com.wowza.wms.plugin.captions.ModuleCaptionsBase.PROP_CAPTION_DELIVERY;
import static com.wowza.wms.plugin.captions.ModuleCaptionsBase.RESAMPLED_STREAM_SUFFIX;

public class LiveStreamPacketizerListener extends LiveStreamPacketizerActionNotifyBase
{
    private final IApplicationInstance appInstance;
    private final StreamCaptionsFilter captionsFilter;

    public LiveStreamPacketizerListener(IApplicationInstance appInstance)
    {
        this(appInstance, StreamCaptionsFilter.matchAll());
    }

    public LiveStreamPacketizerListener(IApplicationInstance appInstance, StreamCaptionsFilter captionsFilter)
    {
        this.appInstance = appInstance;
        this.captionsFilter = captionsFilter;
    }

    @Override
    public void onLiveStreamPacketizerCreate(ILiveStreamPacketizer packetizer, String streamName)
    {
        if (!captionsFilter.matches(streamName))
            return;
        IMediaStream stream = appInstance.getStreams().getStream(streamName);
        // Default delivery=cea: do not force WebVTT sidecars so ModuleOnTextDataToCEA can
        // embed CEA-608 in video (required for native playlist.m3u8?DVR captions).
        // Set speechToTextCaptionDelivery=webvtt for the old live-sidecar behavior.
        String delivery = appInstance.getProperties().getPropertyStr(PROP_CAPTION_DELIVERY, CAPTION_DELIVERY_CEA).trim().toLowerCase();
        boolean wantWebVtt = CAPTION_DELIVERY_WEBVTT.equals(delivery) && !isCEAModuleInstalled();
        if (wantWebVtt
                && packetizer instanceof LiveStreamPacketizerCupertino
                && (streamName.endsWith(DELAYED_STREAM_SUFFIX)
                        || (stream.isTranscodeResult() && !streamName.endsWith(RESAMPLED_STREAM_SUFFIX))))
        {
            packetizer.getProperties().setProperty(ITimedTextConstants.PROP_CUPERTINO_LIVE_USE_WEBVTT, true);
        }
    }


    protected boolean isCEAModuleInstalled()
    {
        boolean isInstalled = false;
        try
        {
            ModuleList moduleList = appInstance.getModuleList();
            Collection<ModuleItem> modules = moduleList.getModuleItems().values();
            Predicate<ModuleItem> predicate = module -> module.getBaseClass().contains("ModuleOnTextDataToCEA");
            isInstalled = modules.stream().anyMatch(predicate);
        }
        catch (Exception e)
        {
            WMSLoggerFactory.getLoggerObj(getClass(), appInstance).error(getClass().getSimpleName() + ".isCEAModuleInstalled: exception: " + e, e);
        }
        return isInstalled;
    }

}
