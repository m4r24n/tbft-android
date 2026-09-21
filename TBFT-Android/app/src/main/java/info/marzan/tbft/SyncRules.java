package info.marzan.tbft;

import org.json.JSONObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Pure, deterministic merge rules. Never silently choose a winner for overlapping edits. */
final class SyncRules {
    static final Set<String> WRITABLE = new HashSet<>(Arrays.asList(
            "projects", "project_nodes", "tasks", "task_messages", "reminders", "workspaces", "wardrobes"));
    private static final Set<String> GENERATED = new HashSet<>(Arrays.asList("created_at", "updated_at", "edited_at"));

    static boolean equal(Object a, Object b) {
        if (a == null || a == JSONObject.NULL) return b == null || b == JSONObject.NULL;
        if (b == null || b == JSONObject.NULL) return false;
        if (a instanceof JSONObject && b instanceof JSONObject) {
            JSONObject x=(JSONObject)a,y=(JSONObject)b;
            if(x.length()!=y.length()) return false;
            for(String key:Json.keys(x)) if(!y.has(key)||!equal(x.opt(key),y.opt(key))) return false;
            return true;
        }
        if (a instanceof org.json.JSONArray && b instanceof org.json.JSONArray) {
            org.json.JSONArray x=(org.json.JSONArray)a,y=(org.json.JSONArray)b;
            if(x.length()!=y.length()) return false;
            for(int i=0;i<x.length();i++) if(!equal(x.opt(i),y.opt(i))) return false;
            return true;
        }
        if (a instanceof Number && b instanceof Number) return new java.math.BigDecimal(a.toString()).compareTo(new java.math.BigDecimal(b.toString())) == 0;
        return a.toString().equals(b.toString());
    }
    static boolean equal(String key, Object a, Object b) {
        if (equal(a, b)) return true;
        if (a == null || a == JSONObject.NULL || b == null || b == JSONObject.NULL) return false;
        try {
            if (key.endsWith("_at")) return java.time.OffsetDateTime.parse(a.toString()).toInstant()
                    .equals(java.time.OffsetDateTime.parse(b.toString()).toInstant());
            if (key.equals("deadline")) return java.time.LocalTime.parse(a.toString()).equals(java.time.LocalTime.parse(b.toString()));
        } catch (java.time.DateTimeException ignored) { }
        return false;
    }
    static JSONObject delta(JSONObject base, JSONObject desired) {
        JSONObject patch = new JSONObject();
        for (String key : Json.keys(desired)) {
            if (!GENERATED.contains(key) && !equal(key, base.opt(key), desired.opt(key))) Json.put(patch, key, desired.opt(key));
        }
        return patch;
    }
    static boolean matches(JSONObject remote, JSONObject patch) {
        for (String key : Json.keys(patch)) if (!equal(key, remote.opt(key), patch.opt(key))) return false;
        return true;
    }
    static String conflict(JSONObject base, JSONObject desired, JSONObject remote) {
        if (remote == null) return "This record was removed remotely. Your local copy is preserved.";
        // Archiving remotely must not be accidentally undone by an unrelated offline edit.
        if (!equal("deleted_at", base.opt("deleted_at"), remote.opt("deleted_at"))
                && !equal("deleted_at", desired.opt("deleted_at"), remote.opt("deleted_at"))) return "Archive status changed on another device.";
        for (String key : Json.keys(delta(base, desired))) {
            if (!equal(key, base.opt(key), remote.opt(key)) && !equal(key, desired.opt(key), remote.opt(key)))
                return "Both devices changed " + key.replace('_', ' ') + ".";
        }
        return "";
    }
    /** Preserve edits made while an earlier version was travelling over the network. */
    static JSONObject rebase(JSONObject sent, JSONObject current, JSONObject acknowledged) {
        return Json.merge(acknowledged, delta(sent, current));
    }
}
