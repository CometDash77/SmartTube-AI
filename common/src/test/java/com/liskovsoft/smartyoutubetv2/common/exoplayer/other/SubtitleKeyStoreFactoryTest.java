package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** T08 slice: the API-level decision and its fallback. */
public class SubtitleKeyStoreFactoryTest {
    @Test
    public void persistenceStartsAtApi23() {
        assertFalse(SubtitleKeyStoreFactory.canPersist(17));
        assertFalse(SubtitleKeyStoreFactory.canPersist(21));
        assertFalse(SubtitleKeyStoreFactory.canPersist(22));
        assertTrue(SubtitleKeyStoreFactory.canPersist(23));
        assertTrue(SubtitleKeyStoreFactory.canPersist(34));
    }

    @Test
    public void oldDevicesUseTheMemoryStoreAndSaySo() {
        SubtitleKeyStore store = SubtitleKeyStoreFactory.create(17, () -> null);

        assertFalse("the menu must be able to explain the session-only rule", store.isPersistent());
        assertTrue(store.save("sk-test"));
        assertTrue(store.hasKey());
    }

    @Test
    public void newDevicesUseThePlatformStoreWhenItIsAvailable() {
        PersistentSubtitleKeyStore persistent = new PersistentSubtitleKeyStore(
                new SubtitleKeyCipher(() -> null), new PersistentSubtitleKeyStore.Storage() {
            @Override
            public String read() {
                return null;
            }

            @Override
            public void write(String value) {
            }

            @Override
            public void delete() {
            }
        });

        SubtitleKeyStore store = SubtitleKeyStoreFactory.create(23, () -> persistent);

        assertSame(persistent, store);
        assertTrue(store.isPersistent());
    }

    @Test
    public void anUnavailablePlatformStoreFallsBackToMemory() {
        SubtitleKeyStore store = SubtitleKeyStoreFactory.create(23, () -> null);

        assertFalse(store.isPersistent());
        assertTrue(store.save("sk-test"));
    }

    @Test
    public void aPlatformStoreThatCannotPersistIsNotAccepted() {
        SubtitleKeyStore store = SubtitleKeyStoreFactory.create(29, MemorySubtitleKeyStore::new);

        assertFalse("a non-persistent store must not be used on a modern device", store.isPersistent());
    }

    @Test
    public void aMissingFactoryOnAnOldDeviceIsStillSafe() {
        SubtitleKeyStore store = SubtitleKeyStoreFactory.create(19, null);

        assertFalse(store.isPersistent());
        assertNull(store.getApiKey());
    }
}
