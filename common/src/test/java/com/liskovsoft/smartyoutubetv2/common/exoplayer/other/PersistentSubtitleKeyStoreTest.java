package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Before;
import org.junit.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T08 slice: encrypted persistence, session caching and unusable-state cleanup. */
import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;
import org.junit.runner.RunWith;
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28) // AES-GCM needs API 19; the module supports minSdk 17
public class PersistentSubtitleKeyStoreTest {
    private static class FakeStorage implements PersistentSubtitleKeyStore.Storage {
        String value;
        int deletes;

        @Override
        public String read() {
            return value;
        }

        @Override
        public void write(String newValue) {
            value = newValue;
        }

        @Override
        public void delete() {
            value = null;
            deletes++;
        }
    }

    private SecretKey mKey;
    private FakeStorage mStorage;

    @Before
    public void setUp() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        mKey = generator.generateKey();
        mStorage = new FakeStorage();
    }

    private PersistentSubtitleKeyStore store(SecretKey key) {
        return new PersistentSubtitleKeyStore(new SubtitleKeyCipher(() -> key), mStorage);
    }

    @Test
    public void persistentStoreSavesAndReadsBackTheKey() {
        PersistentSubtitleKeyStore store = store(mKey);

        assertTrue(store.isPersistent());
        assertFalse(store.hasKey());
        assertTrue(store.save("sk-test-value"));
        assertTrue(store.hasKey());
        assertEquals("sk-test-value", store.getApiKey());
    }

    @Test
    public void theStoredValueIsCiphertextNotTheKey() {
        store(mKey).save("sk-secret-value");

        assertFalse(mStorage.value.contains("sk-secret-value"));
        assertTrue(mStorage.value.startsWith(SubtitleKeyEnvelope.VERSION + ":"));
    }

    @Test
    public void aNewStoreInstanceDecryptsTheStoredKey() {
        store(mKey).save("sk-test-value");

        PersistentSubtitleKeyStore reopened = store(mKey);

        assertTrue(reopened.hasKey());
        assertEquals("sk-test-value", reopened.getApiKey());
    }

    @Test
    public void aValueThatCannotBeDecryptedIsDroppedAndReportedAsMissing() {
        store(mKey).save("sk-test-value");
        KeyGenerator generator = null;

        try {
            generator = KeyGenerator.getInstance("AES");
            generator.init(256);
        } catch (Exception e) {
            org.junit.Assert.fail("AES must be available");
        }

        PersistentSubtitleKeyStore migratedDevice = store(generator.generateKey());

        assertNull("a restored secret from another device is unusable", migratedDevice.getApiKey());
        assertFalse(migratedDevice.hasKey());
        assertNull("the unusable saved value is removed", mStorage.value);
        assertEquals(1, mStorage.deletes);
    }

    @Test
    public void damagedStoredTextIsAlsoDropped() {
        mStorage.value = "not an envelope";

        assertNull(store(mKey).getApiKey());
        assertNull(mStorage.value);
    }

    @Test
    public void clearingRemovesBothTheSessionCopyAndTheStoredValue() {
        PersistentSubtitleKeyStore store = store(mKey);
        store.save("sk-test-value");

        store.clear();

        assertFalse(store.hasKey());
        assertNull(store.getApiKey());
        assertNull(mStorage.value);
    }

    @Test
    public void blankKeysAreRefusedAndNothingIsWritten() {
        PersistentSubtitleKeyStore store = store(mKey);

        assertFalse(store.save(null));
        assertFalse(store.save("   "));
        assertNull(mStorage.value);
    }

    @Test
    public void thePrintedFormNeverCarriesTheKey() {
        PersistentSubtitleKeyStore store = store(mKey);
        store.save("sk-secret-value");

        String printed = store.toString();

        assertFalse(printed.contains("sk-secret-value"));
        assertTrue(printed.contains("configured=true"));
    }
}
