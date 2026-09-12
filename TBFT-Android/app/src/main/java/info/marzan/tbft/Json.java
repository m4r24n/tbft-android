package info.marzan.tbft;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class Json {
    static String now() { return java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS).toString(); }
    static JSONObject object(String raw) {
        try { return new JSONObject(raw); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid saved record", e); }
    }
    static JSONObject copy(JSONObject value) { return object(value.toString()); }
    static JSONObject put(JSONObject object, String key, Object value) {
        try { object.put(key, value == null ? JSONObject.NULL : value); return object; }
        catch (Exception e) { throw new IllegalArgumentException(e); }
    }
    static JSONObject of(Object... pairs) {
        JSONObject out = new JSONObject();
        for (int i = 0; i < pairs.length; i += 2) put(out, (String) pairs[i], pairs[i + 1]);
        return out;
    }
    static String text(JSONObject row, String key) {
        return row == null || row.isNull(key) ? "" : row.optString(key, "");
    }
    static List<String> keys(JSONObject row) {
        List<String> keys = new ArrayList<>();
        Iterator<String> iterator = row.keys();
        while (iterator.hasNext()) keys.add(iterator.next());
        return keys;
    }
    static List<JSONObject> rows(JSONArray array) {
        List<JSONObject> rows = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) if (array.optJSONObject(i) != null) rows.add(array.optJSONObject(i));
        return rows;
    }
    static JSONObject merge(JSONObject base, JSONObject patch) {
        JSONObject out = copy(base);
        for (String key : keys(patch)) put(out, key, patch.opt(key));
        return out;
    }
}
