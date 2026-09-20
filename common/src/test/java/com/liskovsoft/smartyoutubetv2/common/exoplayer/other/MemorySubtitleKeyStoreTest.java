package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T08 slice: the memory-only key store of API 17-22. */
public class MemorySubtitleKeyStoreTest {
    @Test
    public void memoryStoreReportsThatItCannotPersist() {
        MemorySubtitleKeyStore store = new MemorySubtitleKeyStore();

        assertFalse(store.isPersistent());
        assertFalse(store.hasKey());
        assertNull(store.getApiKey());
    }

    @Test
    public void savingTrimsAndAuthorizesRequests() {
        MemorySubtitleKeyStore store = new MemorySubtitleKeyStore();

        assertTrue(store.save("  sk-test  "));

        assertTrue(store.hasKey());
        assertEquals("sk-test", store.getApiKey());
    }

    @Test
    public void blankKeysAreRefusedAndDoNotReplaceAStoredOne() {
        MemorySubtitleKeyStore store = new MemorySubtitleKeyStore();
        store.save("sk-test");

        assertFalse(store.save(null));
        assertFalse(store.save("   "));
        assertEquals("sk-test", store.getApiKey());
    }

    @Test
    public void clearRemovesTheKey() {
        MemorySubtitleKeyStore store = new MemorySubtitleKeyStore();
        store.save("sk-test");

        store.clear();

        assertFalse(store.hasKey());
        assertNull(store.getApiKey());
    }

    @Test
    public void thePrintedFormNeverCarriesTheKey() {
        MemorySubtitleKeyStore store = new MemorySubtitleKeyStore();
        store.save("sk-secret-value");

        String printed = store.toString();

        assertFalse(printed.contains("sk-secret-value"));
        assertTrue(printed.contains("configured=true"));
    }

    @Test
    public void storeFeedsTheTranslationServiceAsAKeyProvider() {
        MemorySubtitleKeyStore store = new MemorySubtitleKeyStore();
        store.save("sk-test");

        SubtitleTranslationService.KeyProvider provider = store;

        assertEquals("sk-test", provider.getApiKey());
    }
}
