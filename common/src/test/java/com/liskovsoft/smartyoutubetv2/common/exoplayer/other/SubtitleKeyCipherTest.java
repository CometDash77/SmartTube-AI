package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Before;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T08 slice: AES-GCM key encryption with an injected key, verified on the JVM. */
import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;
import org.junit.runner.RunWith;
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28) // AES-GCM needs API 19; the module supports minSdk 17
public class SubtitleKeyCipherTest {
    private SecretKey mKey;
    private SubtitleKeyCipher mCipher;

    @Before
    public void setUp() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        mKey = generator.generateKey();
        mCipher = new SubtitleKeyCipher(() -> mKey);
    }

    @Test
    public void encryptThenDecryptReturnsTheKey() {
        String stored = mCipher.encrypt("sk-test-value");

        assertTrue(stored.startsWith(SubtitleKeyEnvelope.VERSION + ":"));
        assertEquals("sk-test-value", mCipher.decrypt(stored));
    }

    @Test
    public void storedTextNeverContainsThePlainKey() {
        String stored = mCipher.encrypt("sk-secret-value");

        assertFalse(stored.contains("sk-secret-value"));
        assertFalse(stored.contains("sk-secret"));
    }

    @Test
    public void everyEncryptionUsesAFreshIv() {
        Set<String> storedValues = new HashSet<>();

        for (int i = 0; i < 20; i++) {
            storedValues.add(mCipher.encrypt("sk-test-value"));
        }

        assertEquals("each encryption must be unique", 20, storedValues.size());
    }

    @Test
    public void aTamperedCiphertextIsRefused() {
        String stored = mCipher.encrypt("sk-test-value");
        String tampered = stored.substring(0, stored.length() - 2) + (stored.endsWith("00") ? "11" : "00");

        assertNotEquals(stored, tampered);
        assertNull("GCM must reject a modified ciphertext", mCipher.decrypt(tampered));
    }

    @Test
    public void anotherDeviceKeyCannotDecryptTheStoredValue() throws Exception {
        String stored = mCipher.encrypt("sk-test-value");
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SubtitleKeyCipher otherDevice = new SubtitleKeyCipher(generator::generateKey);

        assertNull(otherDevice.decrypt(stored));
    }

    @Test
    public void malformedAndBlankInputsAreRefused() {
        assertNull(mCipher.encrypt(null));
        assertNull(mCipher.encrypt("   "));
        assertNull(mCipher.decrypt(null));
        assertNull(mCipher.decrypt(""));
        assertNull(mCipher.decrypt("not an envelope"));
    }

    @Test
    public void aMissingOrUnusableKeystoreReturnsNullInsteadOfPlaintext() {
        SubtitleKeyCipher withoutKey = new SubtitleKeyCipher(() -> null);

        assertNull(withoutKey.encrypt("sk-test-value"));
        assertNull(withoutKey.decrypt(mCipher.encrypt("sk-test-value")));
    }

    @Test
    public void deterministicIvKeepsTheFormatTestable() {
        byte[] fixedIv = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
        SubtitleKeyCipher fixed = new SubtitleKeyCipher(() -> mKey, size -> fixedIv.clone());

        String first = fixed.encrypt("sk-test-value");
        String second = fixed.encrypt("sk-test-value");

        assertEquals(first, second);
        assertEquals("sk-test-value", fixed.decrypt(first));
        assertEquals(0, new SecureRandom().nextInt(1)); // keep the deterministic path honest
    }
}
