package com.wowza.wms.plugin.captions.caption;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebVttCaptionRecorderTest
{
    @Test
    void formatsTimestamps()
    {
        assertEquals("00:00:00.000", WebVttCaptionRecorder.formatTimestamp(0));
        assertEquals("00:00:01.500", WebVttCaptionRecorder.formatTimestamp(1500));
        assertEquals("01:02:03.004", WebVttCaptionRecorder.formatTimestamp(3723004));
    }

    @Test
    void languageCodes()
    {
        assertEquals("fr", WebVttCaptionRecorder.languageCode("fr"));
        assertEquals("fr", WebVttCaptionRecorder.languageCode("fr-FR"));
        assertEquals("und", WebVttCaptionRecorder.languageCode(""));
    }

    @Test
    void sanitizesStreamNames()
    {
        assertEquals("teststt", WebVttCaptionRecorder.sanitizeBaseName("teststt_delayed"));
        assertEquals("teststt", WebVttCaptionRecorder.sanitizeBaseName("teststt_resampled"));
        assertEquals("teststt", WebVttCaptionRecorder.sanitizeBaseName("teststt"));
    }

    @Test
    void escapesVttText()
    {
        assertEquals("a &amp; b &lt;c&gt;", WebVttCaptionRecorder.escapeVttText("a & b <c>"));
    }
}
