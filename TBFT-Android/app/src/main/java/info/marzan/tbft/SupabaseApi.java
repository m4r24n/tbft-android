package info.marzan.tbft;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Direct Supabase calls: the organiser never depends on Vercel or /api/widget/tasks. */
final class SupabaseApi {
    private static final String BASE = "https://wsshxdkuovzvidsubtjj.supabase.co";
    // Public client identifier, NOT a service-role/admin secret. RLS enforces user access.
    private static final String PUBLIC_KEY = "sb_publishable_HO9swyZlG_xl7usF5zhoOg_3UAxuCUc";
    static final class ApiException extends Exception {
        final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
    }
    private final SessionVault vault;
    SupabaseApi(SessionVault vault) { this.vault = vault; }
    static String value(String text) { return Uri.encode(text); }
    private String request(String path, String method, JSONObject body, String token) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setInstanceFollowRedirects(false); c.setRequestMethod(method);
        c.setConnectTimeout(12000); c.setReadTimeout(15000);
        c.setRequestProperty("apikey", PUBLIC_KEY); c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Prefer", "return=representation");
        if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (body != null) {
                c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
                try (java.io.OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int code = c.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (stream != null) try (InputStream in = stream) {
                byte[] buffer = new byte[8192]; int read;
                while ((read = in.read(buffer)) != -1) {
                    if (bytes.size() + read > 12 * 1024 * 1024) throw new java.io.IOException("Response too large; local data preserved");
                    bytes.write(buffer, 0, read);
                }
            }
            String result = bytes.toString("UTF-8");
            if (code < 200 || code >= 300) {
                // Never put raw server output or tokens in logs/UI.
                String message = code == 401 || code == 403 ? "Sign-in or permission needs attention. Local work is safe."
                        : code == 409 ? "Server record changed or already exists."
                        : "Cloud request failed (" + code + "). Local work is safe.";
                throw new ApiException(code, message);
            }
            return result.isEmpty() ? "[]" : result;
        } finally { c.disconnect(); }
    }
    synchronized void signIn(String email, String password) throws Exception {
        JSONObject session = Json.object(request("/auth/v1/token?grant_type=password", "POST", Json.of("email", email, "password", password), null));
        vault.save(session);
    }
    private synchronized String token(boolean force) throws Exception {
        JSONObject session = vault.read();
        if (Json.text(session, "refresh_token").isEmpty()) throw new ApiException(401, "Connect your TBFT account first.");
        if (force || session.optLong("expires_at", 0) < System.currentTimeMillis() / 1000 + 90) {
            session = Json.object(request("/auth/v1/token?grant_type=refresh_token", "POST", Json.of("refresh_token", Json.text(session, "refresh_token")), null));
            vault.save(session);
        }
        return Json.text(session, "access_token");
    }
    JSONArray rest(String path, String method, JSONObject body) throws Exception {
        try { return new JSONArray(request("/rest/v1/" + path, method, body, token(false))); }
        catch (ApiException e) {
            if (e.status != 401) throw e;
            return new JSONArray(request("/rest/v1/" + path, method, body, token(true)));
        }
    }
    List<JSONObject> all(String table, String filter) throws Exception {
        List<JSONObject> rows = new ArrayList<>();
        // Stable ID ordering and explicit pagination avoid silently truncating the offline copy.
        for (int offset = 0; ; offset += 200) {
            JSONArray page = rest(table + "?select=*&order=id.asc&limit=200&offset=" + offset + filter, "GET", null);
            rows.addAll(Json.rows(page));
            if (page.length() < 200) return rows;
            if (offset > 100000) throw new java.io.IOException("Snapshot limit reached; local data preserved");
        }
    }
    JSONObject one(String table, String id) throws Exception {
        JSONArray rows = rest(table + "?id=eq." + value(id) + "&select=*", "GET", null);
        return rows.optJSONObject(0);
    }
    static String condition(String key, Object v) {
        if (v == null || v == JSONObject.NULL) return "&" + value(key) + "=is.null";
        // Quoting prevents PostgREST syntax in user-entered text from becoming a filter.
        String literal = v.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        return "&" + value(key) + "=eq." + value("\"" + literal + "\"");
    }
}
