package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;

/** Real JVM AES-GCM plus AndroidKeyStore's randomized-encryption IV restriction. */
public final class RandomizedEncryptionTestProvider extends Provider {
    public RandomizedEncryptionTestProvider() {
        super("SubtitleRandomizedEncryptionTest", 1.0, "Reject caller IVs during encryption");
        put("Cipher.AES/GCM/NoPadding", Gcm.class.getName());
    }

    public static final class Gcm extends CipherSpi {
        private final Cipher mDelegate;

        public Gcm() throws Exception {
            mDelegate = Cipher.getInstance("AES/GCM/NoPadding", "SunJCE");
        }

        @Override protected void engineSetMode(String mode) { }
        @Override protected void engineSetPadding(String padding) { }
        @Override protected int engineGetBlockSize() { return mDelegate.getBlockSize(); }
        @Override protected int engineGetOutputSize(int length) { return mDelegate.getOutputSize(length); }
        @Override protected byte[] engineGetIV() { return mDelegate.getIV(); }
        @Override protected AlgorithmParameters engineGetParameters() { return mDelegate.getParameters(); }

        @Override
        protected void engineInit(int mode, Key key, SecureRandom random) throws InvalidKeyException {
            mDelegate.init(mode, key, random);
        }

        @Override
        protected void engineInit(int mode, Key key, AlgorithmParameterSpec params, SecureRandom random)
                throws InvalidKeyException, InvalidAlgorithmParameterException {
            refuseEncryptionParameters(mode);
            mDelegate.init(mode, key, params, random);
        }

        @Override
        protected void engineInit(int mode, Key key, AlgorithmParameters params, SecureRandom random)
                throws InvalidKeyException, InvalidAlgorithmParameterException {
            refuseEncryptionParameters(mode);
            mDelegate.init(mode, key, params, random);
        }

        private void refuseEncryptionParameters(int mode) throws InvalidAlgorithmParameterException {
            if (mode == Cipher.ENCRYPT_MODE) {
                throw new InvalidAlgorithmParameterException("Caller-provided IV not permitted");
            }
        }

        @Override protected byte[] engineUpdate(byte[] input, int offset, int length) {
            return mDelegate.update(input, offset, length);
        }
        @Override protected int engineUpdate(byte[] input, int offset, int length, byte[] output, int outputOffset)
                throws ShortBufferException {
            return mDelegate.update(input, offset, length, output, outputOffset);
        }
        @Override protected byte[] engineDoFinal(byte[] input, int offset, int length)
                throws IllegalBlockSizeException, BadPaddingException {
            return mDelegate.doFinal(input, offset, length);
        }
        @Override protected int engineDoFinal(byte[] input, int offset, int length, byte[] output, int outputOffset)
                throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
            return mDelegate.doFinal(input, offset, length, output, outputOffset);
        }
    }
}
