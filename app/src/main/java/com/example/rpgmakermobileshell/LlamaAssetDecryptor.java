package com.example.rpgmakermobileshell;

import org.json.JSONException;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class LlamaAssetDecryptor {
    private static final byte[] SIGNATURE = "TCOAAL".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'
    };
    private static final byte[] OGG_HEADER = {'O', 'g', 'g', 'S'};

    static final class Result {
        final byte[] data;
        final boolean encrypted;
        final String extension;

        Result(byte[] data, boolean encrypted, String extension) {
            this.data = data;
            this.encrypted = encrypted;
            this.extension = extension;
        }
    }

    private LlamaAssetDecryptor() {
    }

    static Result decryptStream(InputStream input, OutputStream output, String fileName)
            throws IOException {
        byte[] prefix = new byte[SIGNATURE.length + 1];
        int prefixRead = readFully(input, prefix, 0, prefix.length);
        if (prefixRead < SIGNATURE.length || !isSignature(prefix, SIGNATURE.length)) {
            output.write(prefix, 0, prefixRead);
            copyStream(input, output);
            byte[] plainPrefix = Arrays.copyOf(prefix, Math.min(prefixRead, 64));
            return new Result(plainPrefix, false, guessExtension(plainPrefix));
        }

        int bodyLengthByte = prefixRead == prefix.length ? prefix[SIGNATURE.length] & 0xff : 0;
        byte[] firstBuffer = new byte[32 * 1024];
        ByteArrayCollector prefixCollector = new ByteArrayCollector(64);
        int rollingMask = (mask(fileName) + 1) & 0xff;
        boolean lengthUnlimited = bodyLengthByte == 0;
        int encryptedLeft = lengthUnlimited ? Integer.MAX_VALUE : bodyLengthByte;
        long plainBytes = 0;

        int bytesRead;
        while ((bytesRead = input.read(firstBuffer)) != -1) {
            for (int i = 0; i < bytesRead; i++) {
                byte cipher = firstBuffer[i];
                if (encryptedLeft > 0) {
                    firstBuffer[i] = (byte) ((cipher & 0xff) ^ rollingMask);
                    rollingMask = ((rollingMask << 1) ^ (cipher & 0xff)) & 0xff;
                    encryptedLeft--;
                }
            }
            prefixCollector.add(firstBuffer, bytesRead);
            output.write(firstBuffer, 0, bytesRead);
            plainBytes += bytesRead;
        }

        byte[] plainPrefix = prefixCollector.toByteArray();
        return new Result(plainPrefix, true, guessExtension(plainPrefix));
    }

    private static boolean isSignature(byte[] data, int length) {
        if (data.length < length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (data[i] != SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static int readFully(InputStream input, byte[] target, int offset, int length)
            throws IOException {
        int total = 0;
        while (total < length) {
            int read = input.read(target, offset + total, length - total);
            if (read == -1) {
                break;
            }
            total += read;
        }
        return total;
    }

    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    private static final class ByteArrayCollector {
        private final byte[] data;
        private int size;

        ByteArrayCollector(int maxSize) {
            data = new byte[maxSize];
        }

        void add(byte[] source, int length) {
            int count = Math.min(length, data.length - size);
            if (count > 0) {
                System.arraycopy(source, 0, data, size, count);
                size += count;
            }
        }

        byte[] toByteArray() {
            return Arrays.copyOf(data, size);
        }
    }

    static Result decrypt(byte[] input, String fileName) {
        if (!isEncrypted(input)) {
            return new Result(input, false, "");
        }

        int bodyStart = SIGNATURE.length + 1;
        int bodyLength = input.length - bodyStart;
        int encryptedLength = input[SIGNATURE.length] & 0xff;
        if (encryptedLength == 0) {
            encryptedLength = bodyLength;
        } else {
            encryptedLength = Math.min(encryptedLength, bodyLength);
        }

        byte[] body = Arrays.copyOfRange(input, bodyStart, input.length);
        int seed = (mask(fileName) + 1) & 0xff;
        byte[] plain = decryptBody(body, encryptedLength, seed);
        if (!isPlausible(plain)) {
            byte[] recovered = recoverSeed(body, encryptedLength, plain);
            if (recovered != null) {
                plain = recovered;
            }
        }
        return new Result(plain, true, guessExtension(plain));
    }

    private static boolean isEncrypted(byte[] input) {
        if (input.length <= SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (input[i] != SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static int mask(String name) {
        int value = 0;
        for (int i = 0; i < name.length(); i++) {
            value = (value << 1) ^ Character.toUpperCase(name.charAt(i));
        }
        return value;
    }

    private static byte[] decryptBody(byte[] body, int encryptedLength, int seed) {
        byte[] plain = body.clone();
        int rollingMask = seed;
        for (int i = 0; i < encryptedLength; i++) {
            plain[i] = (byte) (body[i] ^ rollingMask);
            rollingMask = ((rollingMask << 1) ^ (body[i] & 0xff)) & 0xff;
        }
        return plain;
    }

    private static boolean isPlausible(byte[] plain) {
        if (startsWith(plain, PNG_HEADER) || startsWith(plain, OGG_HEADER)) {
            return true;
        }
        return isText(plain, Math.min(8, plain.length));
    }

    private static byte[] recoverSeed(byte[] body, int encryptedLength, byte[] plain) {
        int checkedLength = Math.min(encryptedLength, 8);
        for (byte[] header : new byte[][]{PNG_HEADER, OGG_HEADER}) {
            int candidateSeed = (body[0] ^ header[0]) & 0xff;
            byte[] candidate = recoverPrefix(body, plain, checkedLength, candidateSeed);
            if (startsWith(candidate, header)) {
                return candidate;
            }
        }

        for (byte first : new byte[]{'{', '['}) {
            int candidateSeed = (body[0] ^ first) & 0xff;
            byte[] candidate = recoverPrefix(body, plain, checkedLength, candidateSeed);
            if (isText(candidate, Math.min(64, candidate.length)) && isJson(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static byte[] recoverPrefix(
            byte[] body, byte[] plain, int checkedLength, int candidateSeed) {
        byte[] candidate = plain.clone();
        int rollingMask = candidateSeed;
        for (int i = 0; i < checkedLength; i++) {
            candidate[i] = (byte) (body[i] ^ rollingMask);
            rollingMask = ((rollingMask << 1) ^ (body[i] & 0xff)) & 0xff;
        }
        return candidate;
    }

    private static boolean isText(byte[] bytes, int length) {
        for (int i = 0; i < length; i++) {
            int value = bytes[i] & 0xff;
            if (value != '\t' && value != '\n' && value != '\r'
                    && (value < 0x20 || value >= 0x7f)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isJson(byte[] bytes) {
        try {
            JSONTokener tokener = new JSONTokener(new String(bytes, StandardCharsets.UTF_8));
            tokener.nextValue();
            return tokener.nextClean() == 0;
        } catch (JSONException exception) {
            return false;
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String guessExtension(byte[] plain) {
        if (startsWith(plain, PNG_HEADER)) {
            return ".png";
        }
        if (startsWith(plain, OGG_HEADER)) {
            return ".ogg";
        }
        int first = 0;
        while (first < plain.length && isWhitespace(plain[first] & 0xff)) {
            first++;
        }
        if (first < plain.length && (plain[first] == '{' || plain[first] == '[')) {
            return ".json";
        }
        return ".bin";
    }

    private static boolean isWhitespace(int value) {
        return value == ' ' || value == '\t' || value == '\n'
                || value == '\r' || value == '\f';
    }
}
