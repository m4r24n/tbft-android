package info.marzan.tbft;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class SyncEngine {
    private final OfflineStore store;
    private final SupabaseApi api;
    private final String account;
    SyncEngine(OfflineStore store, SupabaseApi api, String account) { this.store = store; this.api = api; this.account = account; }

    void sync() throws Exception {
        List<JSONObject> memberships = Json.rows(api.rest("workspace_members?user_id=eq." + SupabaseApi.value(account) + "&select=workspace_id&order=joined_at.asc,workspace_id.asc", "GET", null));
        String workspace = store.meta("workspace", "");
        if (workspace.isEmpty() && !memberships.isEmpty()) workspace = Json.text(memberships.get(0), "workspace_id");
        boolean member = false;
        for (JSONObject row : memberships) if (workspace.equals(Json.text(row, "workspace_id"))) member = true;
        if (!member) throw new SupabaseApi.ApiException(403, "Workspace membership unavailable. Local data is preserved; create/join a workspace on the web first.");
        store.setMeta("workspace", workspace);
        List<String> unavailable = new ArrayList<>();

        // Parent records before children. Conflict rows are retained for explicit review.
        for (OfflineStore.Record pending : store.pending()) {
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
            OfflineStore.Record current=store.get(pending.table,pending.id);
            if(current==null || !current.dirty || !current.error.isEmpty()) continue;
            if(current.table.equals("tasks")) {
                OfflineStore.Record wardrobe=store.wardrobeForTask(current.id);
                if(wardrobe!=null && wardrobe.dirty) continue;
            }
            try { if(current.table.equals("wardrobes")) pushWardrobe(current); else push(current); }
            catch (SupabaseApi.ApiException e) {
                if (e.status == 400 || e.status == 403 || e.status == 409 || e.status == 422) store.conflict(current, current.table.equals("wardrobes") ? "Wardrobe needs review before syncing. Your phone's copy is preserved." : e.getMessage());
                else if(current.table.equals("wardrobes") && (e.status==404 || e.status>=500)) unavailable.add("wardrobe upload");
                else throw e;
            } catch(java.io.IOException e) {
                if(current.table.equals("wardrobes")) unavailable.add("wardrobe upload"); else throw e;
            }
        }

        String filter = "&workspace_id=eq." + SupabaseApi.value(workspace);
        List<JSONObject> spaces = api.all("workspaces", "&id=eq." + SupabaseApi.value(workspace));
        if (spaces.size() != 1) throw new java.io.IOException("Workspace snapshot unavailable");
        store.ingest("workspaces", spaces);
        List<JSONObject> members = Json.rows(api.rest("workspace_members?workspace_id=eq." + SupabaseApi.value(workspace) + "&select=user_id,position&order=position.asc", "GET", null));
        List<JSONObject> profiles = new ArrayList<>();
        for (JSONObject memberRow : members) {
            JSONObject profile = api.one("profiles", Json.text(memberRow, "user_id"));
            if (profile == null) throw new java.io.IOException("Incomplete member profile snapshot");
            profiles.add(profile);
        }
        store.ingest("profiles", profiles);
        List<JSONObject> projects = api.all("projects", filter);
        List<JSONObject> tasks = api.all("tasks", filter);
        store.ingest("projects", projects);
        store.ingest("tasks", tasks);
        store.setMeta("core_sync", java.time.Instant.now().toString());

        // One optional collection failing must not suppress tasks or other collections.
        try { store.ingest("project_nodes", children("project_nodes", "project_id", projects)); } catch (Exception e) { unavailable.add("phases"); }
        try { store.ingest("task_messages", children("task_messages", "task_id", tasks)); } catch (Exception e) { unavailable.add("task notes"); }
        for (String table : new String[]{"reminders", "project_files", "activity_log", "wardrobes"}) {
            try { store.ingest(table, api.all(table, filter)); } catch (Exception e) { unavailable.add(table.replace('_', ' ')); }
        }
        if (!unavailable.isEmpty()) throw new java.io.IOException("Tasks synced. Retrying " + String.join(", ", unavailable) + "; existing local copies kept.");
        store.setMeta("last_sync", java.time.Instant.now().toString());
    }
    private void pushWardrobe(OfflineStore.Record queued) throws Exception {
        OfflineStore.Record sent;
        List<OfflineStore.Record> taskSnapshots=new ArrayList<>();
        synchronized(store) {
            sent=store.get("wardrobes",queued.id);
            if(sent==null || !sent.dirty) return;
            for(JSONObject batch:WardrobeRules.list(WardrobeRules.state(sent.body),"batches")) {
                OfflineStore.Record task=store.get("tasks",Json.text(batch,"task_id"));
                if(task!=null && task.dirty) taskSnapshots.add(task);
            }
        }
        long revision=sent.base==null?0:sent.base.optLong("revision",0);
        JSONArray result=api.rest("rpc/sync_wardrobe","POST",Json.of("document",sent.body,"expected_revision",revision));
        JSONObject acknowledged=result.optJSONObject(0);
        if(acknowledged==null) throw new java.io.IOException("Missing wardrobe acknowledgement");
        store.acknowledge(sent,acknowledged);
        for(OfflineStore.Record task:taskSnapshots) {
            JSONObject remote=api.one("tasks",task.id);
            if(remote!=null) {
                JSONObject batch=WardrobeRules.batch(sent.body,task.id);
                store.acknowledgeManagedTask(task,WardrobeRules.task(sent.body,batch),remote);
            }
        }
    }
    private List<JSONObject> children(String table, String key, List<JSONObject> parents) throws Exception {
        List<JSONObject> out = new ArrayList<>();
        for (int offset = 0; offset < parents.size(); offset += 50) {
            List<String> ids = new ArrayList<>();
            for (int i = offset; i < Math.min(offset + 50, parents.size()); i++) ids.add(Json.text(parents.get(i), "id"));
            out.addAll(api.all(table, "&" + key + "=in.(" + String.join(",", ids) + ")"));
        }
        return out;
    }
    private void push(OfflineStore.Record row) throws Exception {
        if (!SyncRules.WRITABLE.contains(row.table)) { store.conflict(row, "Read-only record"); return; }
        JSONObject remote = api.one(row.table, row.id);
        if (row.removed) {
            if (!row.table.equals("reminders")) throw new IllegalStateException("Only reminders use hard deletion");
            if (remote == null) { store.acknowledge(row, null); return; }
            JSONObject expected = row.base == null ? row.body : row.base;
            if (!SyncRules.matches(remote, SyncRules.delta(new JSONObject(), expected))) {
                store.conflict(row, "Reminder changed remotely before deletion. Both copies are preserved."); return;
            }
            String conditions = conditions(remote, SyncRules.delta(new JSONObject(), expected));
            JSONArray result = api.rest(row.table + "?id=eq." + row.id + conditions, "DELETE", null);
            if (result.length() > 0) store.acknowledge(row, null);
            return;
        }
        if (row.base == null) {
            if (remote == null) {
                JSONArray inserted = api.rest(row.table, "POST", row.body);
                if (inserted.optJSONObject(0) != null) store.acknowledge(row, inserted.optJSONObject(0));
            } else if (SyncRules.matches(remote, SyncRules.delta(new JSONObject(), row.body))) {
                // The server may have committed a previous POST whose response was lost.
                store.acknowledge(row, remote);
            } else store.conflict(row, "The same record ID already exists with different content.");
            return;
        }
        String conflict = SyncRules.conflict(row.base, row.body, remote);
        if (!conflict.isEmpty()) { store.conflict(row, conflict); return; }
        JSONObject patch = SyncRules.delta(row.base, row.body);
        if (row.table.equals("tasks") && (patch.has("completed_at") || patch.has("deleted_at"))
                && !account.equals(Json.text(remote,"owner_user_id"))) {
            store.conflict(row,"Task ownership changed. Only the current owner can change completion or archive status."); return;
        }
        if (row.table.equals("tasks") && patch.has("completed_at") && !Json.text(patch,"completed_at").isEmpty()) {
            List<OfflineStore.Record> spaces = store.records("workspaces");
            if (!spaces.isEmpty() && Json.text(remote,"original_date").compareTo(BoardRules.boardDate(
                    spaces.get(0).body.optString("timezone","Europe/Berlin"),spaces.get(0).body.optInt("rollover_hour",6),java.time.Instant.now())) > 0) {
                store.conflict(row,"This task is now scheduled for a future day. Review it before completion."); return;
            }
        }
        if (SyncRules.matches(remote, patch)) { store.acknowledge(row, remote); return; }
        // Atomic compare-and-set against the values just read, not a read-then-blind-write.
        String conditions = conditions(remote, patch);
        if (row.table.equals("reminders")) Json.put(patch, "updated_at", Json.now());
        JSONArray updated = api.rest(row.table + "?id=eq." + row.id + conditions, "PATCH", patch);
        if (updated.optJSONObject(0) != null) store.acknowledge(row, updated.optJSONObject(0));
        // Empty result is a race/RLS rejection, never proof of success. Keep it queued.
    }
    private String conditions(JSONObject remote, JSONObject patch) {
        StringBuilder query = new StringBuilder();
        if (remote.has("updated_at")) query.append(SupabaseApi.condition("updated_at", remote.opt("updated_at")));
        if (remote.has("deleted_at")) query.append(SupabaseApi.condition("deleted_at", remote.opt("deleted_at")));
        if (!remote.has("updated_at")) for (String key : Json.keys(patch)) query.append(SupabaseApi.condition(key, remote.opt(key)));
        return query.toString();
    }
}
