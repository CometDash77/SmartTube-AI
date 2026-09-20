package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

import java.nio.charset.Charset;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-GCM encryption of the API key, with the key itself supplied by the platform.
 *
 * <p>The plan requires the device keystore to hold the encryption key (API 23+) while this class owns
 * only the cryptography: a fresh 96-bit IV per encryption, the versioned
 * {@link SubtitleKeyEnvelope} text format, and a controlled failure path. Nothing here returns a
 * plaintext key on failure and nothing logs it; the caller decides what to do when the stored value
 * cannot be decrypted (for example after a device migration).
 */
public class SubtitleKeyCipher {
    /** API 1 charset: java.nio.charset.StandardCharsets is API 19 while this module supports 17. */
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    /** Supplies the encryption key: an Android keystore entry in production. */
    public interface KeySource {
        SecretKey getKey();
    }

    private static final int TAG_LENGTH_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final KeySource mKeySource;
    interface CipherFactory {
        Cipher create() throws GeneralSecurityException;
    }
    private final CipherFactory mCipherFactory;

    public SubtitleKeyCipher(KeySource keySource) {
        this(keySource, () -> Cipher.getInstance(TRANSFORMATION));
    }

    SubtitleKeyCipher(KeySource keySource, CipherFactory cipherFactory) {
        mKeySource = keySource;
        mCipherFactory = cipherFactory;
    }

    /**
     * @return the envelope text to store, or null when the key could not be encrypted
     */
    public String encrypt(String apiKey) {
        if (VERSION.SDK_INT < VERSION_CODES.KITKAT) {
            return null; // AES-GCM needs API 19; older devices keep the key in memory only
        }

        if (apiKey == null || apiKey.trim().isEmpty() || mKeySource == null || mKeySource.getKey() == null) {
            return null;
        }

        try {
            Cipher cipher = mCipherFactory.create();
            // AndroidKeyStore requires provider-generated IVs for randomized encryption (the
            // KeyGenParameterSpec default). A caller-supplied IV makes every save fail there,
            // even though ordinary JVM AES keys accept it. Keep that security policy enabled.
            cipher.init(Cipher.ENCRYPT_MODE, mKeySource.getKey());
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(apiKey.trim().getBytes(UTF_8));

            return SubtitleKeyEnvelope.pack(SubtitleKeyEnvelope.VERSION, iv, ciphertext);
        } catch (GeneralSecurityException | RuntimeException e) {
            return null; // never fall back to storing the key in the clear
        }
    }

    /**
     * @return the decrypted key, or null when the stored value is unusable (damaged, tampered with
     * or encrypted for another device)
     */
    public String decrypt(String stored) {
        if (VERSION.SDK_INT < VERSION_CODES.KITKAT) {
            return null; // AES-GCM needs API 19; older devices keep the key in memory only
        }

        SubtitleKeyEnvelope.Unpacked unpacked = SubtitleKeyEnvelope.unpack(stored);

        if (unpacked == null || mKeySource == null || mKeySource.getKey() == null) {
            return null;
        }

        try {
            Cipher cipher = mCipherFactory.create();
            cipher.init(Cipher.DECRYPT_MODE, mKeySource.getKey(), new GCMParameterSpec(TAG_LENGTH_BITS, unpacked.getIv()));
            byte[] plaintext = cipher.doFinal(unpacked.getCiphertext());

            return new String(plaintext, UTF_8);
        } catch (GeneralSecurityException | RuntimeException e) {
            return null;
        }
    }
}
