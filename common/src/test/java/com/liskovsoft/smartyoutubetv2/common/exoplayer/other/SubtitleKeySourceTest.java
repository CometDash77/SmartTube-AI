package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** T08 slice: the device key source, without any device keystore. */
public class SubtitleKeySourceTest {
    private static class FakeAccess implements SubtitleKeySource.KeyStoreAccess {
        private final List<String> requested = new ArrayList<>();
        private final List<String> deleted = new ArrayList<>();
        private SecretKey key;
        private boolean fails;

        @Override
        public SecretKey getOrCreate(String alias) {
            requested.add(alias);

            if (fails) {
                throw new IllegalStateException("keystore unavailable");
            }

            return key;
        }

        @Override
        public void delete(String alias) {
            deleted.add(alias);
            key = null;
        }
    }

    private static SecretKey newKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);

        return generator.generateKey();
    }

    @Test
    public void theKeyIsRequestedOnceAndCachedForTheSession() throws Exception {
        FakeAccess access = new FakeAccess();
        access.key = newKey();
        SubtitleKeySource source = new SubtitleKeySource(access);

        SecretKey first = source.getKey();
        SecretKey second = source.getKey();

        assertSame(first, second);
        assertEquals(1, access.requested.size());
        assertEquals(SubtitleKeySource.KEY_ALIAS, access.requested.get(0));
    }

    @Test
    public void createdAndExistingKeysAreBothAcceptableBecauseTheSeamDecides() throws Exception {
        FakeAccess access = new FakeAccess();
        access.key = newKey();
        SubtitleKeySource source = new SubtitleKeySource(access);

        assertEquals(access.key, source.getKey());
        assertTrue(source.getKey() instanceof SecretKey);
    }

    @Test
    public void aFailingKeystoreYieldsNoKeyInsteadOfThrowing() {
        FakeAccess access = new FakeAccess();
        access.fails = true;
        SubtitleKeySource source = new SubtitleKeySource(access);

        assertNull("the caller must fall back to the memory store", source.getKey());
        assertNull(source.getKey());
    }

    @Test
    public void aMissingAccessYieldsNoKey() {
        assertNull(new SubtitleKeySource(null).getKey());
    }

    @Test
    public void deletingForKeygetsBothTheCacheAndTheStoredEntry() throws Exception {
        FakeAccess access = new FakeAccess();
        access.key = newKey();
        SubtitleKeySource source = new SubtitleKeySource(access);
        SecretKey before = source.getKey();

        source.deleteKey();

        assertEquals(1, access.deleted.size());
        assertEquals(SubtitleKeySource.KEY_ALIAS, access.deleted.get(0));
        assertNull("the entry is gone, so nothing is cached either", source.getKey());
        assertNotEquals(before, source.getKey());
    }

    @Test
    public void theAliasIsStable() {
        assertEquals("ai_subtitle_encryption_key", SubtitleKeySource.KEY_ALIAS);
    }
}
