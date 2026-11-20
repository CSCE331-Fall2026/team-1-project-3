// AI generated, lets login read the encrypted passwords from PR #73
package backend.auth;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// Reads passwords the generator encrypted (src/sql-scripts/generator/security.cpp, AES-256-GCM)
// stored as base64 of [1 byte version][12 byte iv][encrypted password][16 byte tag], key is GEN_KEY in set-env.sh
// anything that doesnt decrypt is treated as a plaintext password, so the old database still works
public final class PasswordCipher {
    private static final int VERSION = 1;
    private static final int IV_SIZE = 12;
    private static final int TAG_SIZE = 16;
    private static final int KEY_SIZE = 32;

    private final byte[][] keys;

    public PasswordCipher(String genKey) {
        keys = readKeys(genKey);
    }

    public static PasswordCipher fromEnvironment() {
        return new PasswordCipher(System.getenv("GEN_KEY"));
    }

    public String readable(String stored) {
        if (stored == null) {
            return null;
        }
        byte[] blob;
        try {
            blob = Base64.getDecoder().decode(stored);
        }
        catch (IllegalArgumentException notBase64) {
            return stored;
        }
        if (blob.length < 1 + IV_SIZE + TAG_SIZE || blob[0] != VERSION) {
            return stored;
        }
        for (byte[] key : keys) {
            String password = decrypt(blob, key);
            if (password != null) {
                return password;
            }
        }
        return stored;
    }

    // null if the key is wrong or it isnt one of ours, gcm checks the tag so a wrong key cant give a fake password
    private static String decrypt(byte[] blob, byte[] key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec iv = new GCMParameterSpec(TAG_SIZE * 8, blob, 1, IV_SIZE);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), iv);
            byte[] password = cipher.doFinal(blob, 1 + IV_SIZE, blob.length - 1 - IV_SIZE);
            return new String(password, StandardCharsets.UTF_8);
        }
        catch (Exception wrongKey) {
            return null;
        }
    }

    // GEN_KEY is 32 numbers 0-255 separated by spaces
    // the generator reads them in backwards (first number is the last byte) but prints a new key forwards,
    // so try both orders, whichever one decrypts is the right one
    private static byte[][] readKeys(String genKey) {
        if (genKey == null || genKey.isBlank()) {
            return new byte[0][];
        }
        String[] numbers = genKey.trim().split("\\s+");
        if (numbers.length != KEY_SIZE) {
            throw new IllegalStateException("GEN_KEY should be 32 numbers, it has " + numbers.length);
        }
        byte[] forwards = new byte[KEY_SIZE];
        byte[] backwards = new byte[KEY_SIZE];
        for (int i = 0; i < KEY_SIZE; i++) {
            int value = Integer.parseInt(numbers[i]);
            if (value < 0 || value > 255) {
                throw new IllegalStateException("GEN_KEY numbers have to be 0-255, got " + value);
            }
            forwards[i] = (byte) value;
            backwards[KEY_SIZE - 1 - i] = (byte) value;
        }
        if (Arrays.equals(forwards, backwards)) {
            return new byte[][] { backwards };
        }
        return new byte[][] { backwards, forwards };
    }
}
