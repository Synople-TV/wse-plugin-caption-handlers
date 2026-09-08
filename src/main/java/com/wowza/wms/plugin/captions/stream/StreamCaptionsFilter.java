/*
 * This code and all components (c) Copyright 2006 - 2025, Wowza Media Systems, LLC.  All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */

package com.wowza.wms.plugin.captions.stream;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.wowza.wms.plugin.captions.ModuleCaptionsBase.DELAYED_STREAM_SUFFIX;
import static com.wowza.wms.plugin.captions.ModuleCaptionsBase.RESAMPLED_STREAM_SUFFIX;

/**
 * Selects which published streams get captioned. The pattern is a regular expression matched
 * against the published stream name and against the application instance name, because the
 * name can land in either depending on how the encoder splits the RTMP URL:
 *
 *   Server rtmp://host/demo + stream key myStream_stt -> stream name  myStream_stt
 *   Server rtmp://host/demo/myStream_stt, no key      -> instance name myStream_stt
 *
 * So "_stt" captions myStream_stt but not myStream in both layouts. A null or blank pattern
 * captions every stream.
 */
public class StreamCaptionsFilter
{
    private static final String CLASS_NAME = StreamCaptionsFilter.class.getSimpleName();

    private final Pattern pattern;
    private final String instanceName;

    private StreamCaptionsFilter(Pattern pattern, String instanceName)
    {
        this.pattern = pattern;
        this.instanceName = instanceName;
    }

    public static StreamCaptionsFilter matchAll()
    {
        return new StreamCaptionsFilter(null, null);
    }

    public static StreamCaptionsFilter fromPattern(String pattern, IApplicationInstance appInstance, WMSLogger logger)
    {
        if (pattern == null || pattern.trim().isEmpty())
            return matchAll();
        try
        {
            return new StreamCaptionsFilter(Pattern.compile(pattern.trim()), appInstance == null ? null : appInstance.getName());
        }
        catch (PatternSyntaxException e)
        {
            logger.error(String.format("%s.fromPattern: invalid pattern [%s], captioning all streams", CLASS_NAME, pattern), e);
            return matchAll();
        }
    }

    public boolean matches(String streamName)
    {
        if (pattern == null)
            return true;
        if (instanceName != null && pattern.matcher(instanceName).find())
            return true;
        if (streamName == null)
            return false;
        return pattern.matcher(publishedName(streamName)).find();
    }

    /**
     * Maps the derived names the module generates back to the published name the pattern is
     * written against, so myStream_stt_delayed and myStream_stt_resampled both match "_stt".
     */
    public static String publishedName(String streamName)
    {
        String name = streamName.replace(".stream", "");
        if (name.endsWith(DELAYED_STREAM_SUFFIX))
            name = name.substring(0, name.length() - DELAYED_STREAM_SUFFIX.length());
        if (name.endsWith(RESAMPLED_STREAM_SUFFIX))
            name = name.substring(0, name.length() - RESAMPLED_STREAM_SUFFIX.length());
        return name;
    }

    public String getPatternStr()
    {
        return pattern == null ? "" : pattern.pattern();
    }
}
