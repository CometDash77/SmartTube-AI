package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Storage format of an encrypted API key: version, IV and ciphertext.
 *
 * <p>Only the encoding lives here; the encryption itself is the platform keystore's job on API 23+
 * (T08). Keeping the format separate makes it testable without any device cryptography and makes a
 * future format change explicit: an unknown version is refused instead of being interpreted as a
 * different layout, and every field is hex so the text never depends on a platform Base64 API.
 *
 * <p>The packed form contains ciphertext only; there is no code path that writes the plaintext key.
 */
public final class SubtitleKeyEnvelope {
    public static final int VERSION = 1;
    private static final String SEPARATOR = ":";

    public static final class Unpacked {
        private final int mVersion;
        private final byte[] mIv;
        private final byte[] mCiphertext;

        Unpacked(int version, byte[] iv, byte[] ciphertext) {
            mVersion = version;
            mIv = iv;
            mCiphertext = ciphertext;
        }

        public int getVersion() {
            return mVersion;
        }

        public byte[] getIv() {
            return mIv.clone();
        }

        public byte[] getCiphertext() {
            return mCiphertext.clone();
        }
    }

    private SubtitleKeyEnvelope() {
    }

    public static String pack(int version, byte[] iv, byte[] ciphertext) {
        if (iv == null || iv.length == 0 || ciphertext == null || ciphertext.length == 0) {
            throw new IllegalArgumentException("iv and ciphertext must not be empty");
        }

        return version + SEPARATOR + toHex(iv) + SEPARATOR + toHex(ciphertext);
    }

    /**
     * @return the fields, or null when the stored text is malformed or of an unsupported version
     */
    public static Unpacked unpack(String stored) {
        if (stored == null) {
            return null;
        }

        String[] parts = stored.trim().split(SEPARATOR);

        if (parts.length != 3) {
            return null;
        }

        int version;

        try {
            version = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return null;
        }

        if (version != VERSION) {
            return null; // never reinterpret another layout
        }

        byte[] iv = fromHex(parts[1]);
        byte[] ciphertext = fromHex(parts[2]);

        if (iv == null || iv.length == 0 || ciphertext == null || ciphertext.length == 0) {
            return null;
        }

        return new Unpacked(version, iv, ciphertext);
    }

    static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);

        for (byte value : bytes) {
            hex.append(Character.forDigit((value >> 4) & 0xF, 16)).append(Character.forDigit(value & 0xF, 16));
        }

        return hex.toString();
    }

    static byte[] fromHex(String hex) {
        if (hex == null || hex.length() == 0 || hex.length() % 2 != 0) {
            return null;
        }

        byte[] bytes = new byte[hex.length() / 2];

        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);

            if (high < 0 || low < 0) {
                return null;
            }

            bytes[i] = (byte) ((high << 4) | low);
        }

        return bytes;
    }
}
