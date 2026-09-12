package info.marzan.tbft;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Tokens are encrypted using a non-exportable Android Keystore key. No passwords on disk. */
final class SessionVault {
    private final SharedPreferences prefs;
    SessionVault(Context context) { prefs = context.getSharedPreferences("tbft_session_v1", Context.MODE_PRIVATE); }
    String account() { return prefs.getString("account", ""); }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias("tbft.session.v1")) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder("tbft.session.v1", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey("tbft.session.v1", null);
    }
    synchronized JSONObject read() throws Exception {
        String encrypted = prefs.getString("ciphertext", "");
        if (encrypted.isEmpty()) return new JSONObject();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)));
        return Json.object(new String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), StandardCharsets.UTF_8));
    }
    synchronized void save(JSONObject session) throws Exception {
        JSONObject user = session.optJSONObject("user");
        String id = Json.text(user, "id");
        if (id.isEmpty()) throw new IllegalStateException("Session has no user identity");
        java.util.UUID.fromString(id);
        if (!account().isEmpty() && !account().equals(id)) throw new IllegalStateException("Sign in with the same TBFT account. This preview does not switch accounts.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        JSONObject stored = Json.copy(session);
        if (!stored.has("expires_at")) Json.put(stored, "expires_at", System.currentTimeMillis() / 1000 + stored.optLong("expires_in", 3600));
        String encoded = Base64.encodeToString(cipher.doFinal(stored.toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!prefs.edit().putString("account", id).putString("ciphertext", encoded)
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).commit()) throw new IllegalStateException("Could not persist session");
    }
}
