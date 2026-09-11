/*
 * This code and all components (c) Copyright 2006 - 2025, Wowza Media Systems, LLC.  All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */

package com.wowza.wms.plugin.captions.caption;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static com.wowza.wms.plugin.captions.ModuleCaptionsBase.MODULE_NAME;
import static com.wowza.wms.plugin.captions.ModuleCaptionsBase.PROP_CAPTIONS_DEBUG_LOG;
import static com.wowza.wms.plugin.captions.ModuleCaptionsBase.PROP_SAVE_WEBVTT;
import static com.wowza.wms.plugin.captions.ModuleCaptionsBase.PROP_WEBVTT_OUTPUT_DIR;

/**
 * Appends final STT cues for all languages into one WebVTT sidecar next to live-record MP4s
 * (e.g. content/teststt.vtt alongside content/teststt.mp4).
 */
public class WebVttCaptionRecorder implements AutoCloseable
{
    private static final Class<WebVttCaptionRecorder> CLASS = WebVttCaptionRecorder.class;
    private static final String CLASS_NAME = CLASS.getSimpleName();

    private final WMSLogger logger;
    private final boolean debugLog;
    private final Path outputDir;
    private final String streamName;
    private final AtomicInteger nextIndex = new AtomicInteger(1);
    private BufferedWriter writer;
    private Path path;
    private volatile boolean closed;

    public WebVttCaptionRecorder(IApplicationInstance appInstance, String streamName)
    {
        this.logger = WMSLoggerFactory.getLoggerObj(CLASS, appInstance);
        this.debugLog = appInstance.getProperties().getPropertyBoolean(PROP_CAPTIONS_DEBUG_LOG, false);
        this.streamName = sanitizeBaseName(streamName);
        String configured = appInstance.getProperties().getPropertyStr(PROP_WEBVTT_OUTPUT_DIR, "").trim();
        if (!configured.isEmpty())
            this.outputDir = Path.of(configured);
        else
            this.outputDir = Path.of(appInstance.getStreamStorageDir());
    }

    public static boolean isEnabled(IApplicationInstance appInstance)
    {
        return appInstance.getProperties().getPropertyBoolean(PROP_SAVE_WEBVTT, true);
    }

    public synchronized void append(Caption caption)
    {
        if (closed || caption == null)
            return;
        String text = caption.getText();
        if (text == null || text.isBlank())
            return;

        String lang = languageCode(caption.getLanguage());
        try
        {
            ensureOpen();
            long begin = Math.max(0, caption.getBegin());
            long end = Math.max(begin + 1, caption.getEnd());
            int index = nextIndex.getAndIncrement();
            // One file for all languages; tag each cue with WebVTT <lang>.
            String cue = index + "\n"
                    + formatTimestamp(begin) + " --> " + formatTimestamp(end) + "\n"
                    + "<lang " + lang + ">" + escapeVttText(text.trim()) + "\n\n";
            writer.write(cue);
            writer.flush();
            if (debugLog)
                logger.info(MODULE_NAME + "::" + CLASS_NAME + ".append [" + streamName + " lang=" + lang
                        + "] " + formatTimestamp(begin) + " --> " + formatTimestamp(end) + " " + text.replace('\n', ' '));
        }
        catch (IOException e)
        {
            logger.error(MODULE_NAME + "::" + CLASS_NAME + ".append failed for " + streamName + " lang=" + lang, e);
        }
    }

    private void ensureOpen() throws IOException
    {
        if (writer != null)
            return;
        Files.createDirectories(outputDir);
        path = outputDir.resolve(streamName + ".vtt");
        boolean exists = Files.exists(path) && Files.size(path) > 0;
        writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        if (!exists)
        {
            writer.write("WEBVTT\n");
            writer.write("NOTE Synople Azure STT archive (all languages) for stream " + streamName + "\n\n");
            writer.flush();
        }
        logger.info(MODULE_NAME + "::" + CLASS_NAME + ".open [" + path + "]");
    }

    static String formatTimestamp(long millis)
    {
        long total = Math.max(0, millis);
        long hours = total / 3_600_000;
        long minutes = (total % 3_600_000) / 60_000;
        long seconds = (total % 60_000) / 1_000;
        long ms = total % 1_000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);
    }

    static String escapeVttText(String text)
    {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    static String languageCode(String language)
    {
        if (language == null || language.isBlank())
            return "und";
        String trimmed = language.trim().replace('_', '-');
        try
        {
            Locale locale = Locale.forLanguageTag(trimmed);
            String lang = locale.getLanguage();
            if (lang != null && !lang.isBlank())
                return lang.toLowerCase(Locale.ROOT);
        }
        catch (Exception ignored)
        {
        }
        String safe = trimmed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "");
        return safe.isEmpty() ? "und" : safe;
    }

    static String sanitizeBaseName(String streamName)
    {
        if (streamName == null || streamName.isBlank())
            return "unknown";
        String base = streamName.trim();
        // Prefer the ingest name when given a delayed/resampled publish name.
        if (base.endsWith("_delayed"))
            base = base.substring(0, base.length() - "_delayed".length());
        if (base.endsWith("_resampled"))
            base = base.substring(0, base.length() - "_resampled".length());
        return base.replaceAll("[\\\\/:*?\"<>|]+", "_");
    }

    @Override
    public synchronized void close()
    {
        if (closed)
            return;
        closed = true;
        if (writer == null)
            return;
        try
        {
            writer.flush();
            writer.close();
            logger.info(MODULE_NAME + "::" + CLASS_NAME + ".close [" + path + "]");
        }
        catch (IOException e)
        {
            logger.error(MODULE_NAME + "::" + CLASS_NAME + ".close failed [" + path + "]", e);
        }
        finally
        {
            writer = null;
        }
    }
}
