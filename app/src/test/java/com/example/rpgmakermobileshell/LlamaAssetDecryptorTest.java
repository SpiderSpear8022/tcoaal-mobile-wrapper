package com.example.rpgmakermobileshell;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LlamaAssetDecryptorTest {
    @Test
    public void decryptsPngUsingOriginalFilename() {
        byte[] plain = pngData();
        LlamaAssetDecryptor.Result result = LlamaAssetDecryptor.decrypt(
                encrypt(plain, "a1b2c3d4e5f60708", 0), "a1b2c3d4e5f60708");

        assertArrayEquals(plain, result.data);
        assertEquals(".png", result.extension);
        assertTrue(result.encrypted);
    }

    @Test
    public void recoversPngSeedWhenFilenameDoesNotMatch() {
        byte[] plain = pngData();
        LlamaAssetDecryptor.Result result = LlamaAssetDecryptor.decrypt(
                encrypt(plain, "original-name", 0), "renamed-file");

        assertArrayEquals(plain, result.data);
        assertEquals(".png", result.extension);
    }

    @Test
    public void leavesTheUnencryptedTailUntouched() {
        byte[] plain = pngData();
        LlamaAssetDecryptor.Result result = LlamaAssetDecryptor.decrypt(
                encrypt(plain, "asset-name", 8), "asset-name");

        assertArrayEquals(plain, result.data);
        assertEquals(".png", result.extension);
    }

    @Test
    public void decryptsStreamWithoutLoadingTheWholeFile() throws IOException {
        byte[] plain = pngData();
        ByteArrayInputStream input = new ByteArrayInputStream(
                encrypt(plain, "asset-name", 0));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        LlamaAssetDecryptor.Result result = LlamaAssetDecryptor.decryptStream(
                input, output, "asset-name");

        assertArrayEquals(plain, output.toByteArray());
        assertEquals(".png", result.extension);
        assertTrue(result.encrypted);
    }

    @Test
    public void preservesUnencryptedFilesWithoutAddingAnExtension() {
        byte[] plain = "LANGDATA{}".getBytes(StandardCharsets.UTF_8);
        LlamaAssetDecryptor.Result result = LlamaAssetDecryptor.decrypt(plain, "language-data");

        assertArrayEquals(plain, result.data);
        assertEquals("", result.extension);
        assertFalse(result.encrypted);
    }

    private static byte[] pngData() {
        byte[] data = new byte[80];
        byte[] header = {
                (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'
        };
        System.arraycopy(header, 0, data, 0, header.length);
        for (int i = header.length; i < data.length; i++) {
            data[i] = (byte) (i * 13);
        }
        return data;
    }

    private static byte[] encrypt(byte[] plain, String name, int length) {
        int encryptedLength = length == 0 ? plain.length : Math.min(length, plain.length);
        byte[] body = plain.clone();
        int rollingMask = (mask(name) + 1) & 0xff;
        for (int i = 0; i < encryptedLength; i++) {
            int cipher = (plain[i] & 0xff) ^ rollingMask;
            body[i] = (byte) cipher;
            rollingMask = ((rollingMask << 1) ^ cipher) & 0xff;
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] signature = "TCOAAL".getBytes(StandardCharsets.US_ASCII);
        output.write(signature, 0, signature.length);
        output.write(length);
        output.write(body, 0, body.length);
        return output.toByteArray();
    }

    private static int mask(String name) {
        int value = 0;
        for (int i = 0; i < name.length(); i++) {
            value = (value << 1) ^ Character.toUpperCase(name.charAt(i));
        }
        return value;
    }
}
