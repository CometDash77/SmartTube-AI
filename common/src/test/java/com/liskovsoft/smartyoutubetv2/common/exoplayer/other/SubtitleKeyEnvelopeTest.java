package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/** T08 slice: the encrypted-key storage format, without any device cryptography. */
public class SubtitleKeyEnvelopeTest {
    private static final byte[] IV = {1, 2, 3, 4};
    private static final byte[] CIPHERTEXT = {10, 11, 12, 13, 14};

    @Test
    public void packAndUnpackRoundTrip() {
        SubtitleKeyEnvelope.Unpacked unpacked = SubtitleKeyEnvelope.unpack(SubtitleKeyEnvelope.pack(1, IV, CIPHERTEXT));

        assertEquals(SubtitleKeyEnvelope.VERSION, unpacked.getVersion());
        assertArrayEquals(IV, unpacked.getIv());
        assertArrayEquals(CIPHERTEXT, unpacked.getCiphertext());
    }

    @Test
    public void packedFormIsVersionedHexWithThreeFields() {
        String packed = SubtitleKeyEnvelope.pack(SubtitleKeyEnvelope.VERSION, IV, CIPHERTEXT);

        assertEquals("1:01020304:0a0b0c0d0e", packed);
    }

    @Test
    public void unsupportedVersionIsRefused() {
        assertNull("another layout must never be reinterpreted", SubtitleKeyEnvelope.unpack("2:01020304:0a0b0c0d0e"));
        assertNull(SubtitleKeyEnvelope.unpack("x:01020304:0a0b0c0d0e"));
    }

    @Test
    public void malformedValuesAreRefused() {
        assertNull(SubtitleKeyEnvelope.unpack(null));
        assertNull(SubtitleKeyEnvelope.unpack(""));
        assertNull(SubtitleKeyEnvelope.unpack("1:01020304"));
        assertNull(SubtitleKeyEnvelope.unpack("1::0a0b"));
        assertNull(SubtitleKeyEnvelope.unpack("1:0102:"));
        assertNull(SubtitleKeyEnvelope.unpack("1:zz:0a0b"));
        assertNull(SubtitleKeyEnvelope.unpack("1:010:0a0b")); // odd hex length
    }

    @Test
    public void emptyFieldsCannotBePacked() {
        try {
            SubtitleKeyEnvelope.pack(1, new byte[0], CIPHERTEXT);
            org.junit.Assert.fail("expected an IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // an empty IV would be a silently weak encryption, so packing must fail loudly
        }
    }

    @Test
    public void theStoredTextNeverContainsThePlainKey() {
        String plainKey = "sk-secret-value";
        byte[] ciphertext = plainKey.getBytes(StandardCharsets.UTF_8); // stand-in for real ciphertext

        String packed = SubtitleKeyEnvelope.pack(1, IV, ciphertext);

        assertFalse("hex-encoded bytes, so the key is not readable as text", packed.contains(plainKey));
        assertFalse(packed.contains("sk-"));
    }
}
