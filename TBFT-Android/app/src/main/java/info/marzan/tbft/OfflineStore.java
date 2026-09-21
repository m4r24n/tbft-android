package info.marzan.tbft;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A row and its pending intent commit together. Network failures never delete pending work. */
final class OfflineStore extends SQLiteOpenHelper {
    static final class Record {
        final String table, id, error;
        final JSONObject body, base;
        final boolean dirty, removed;
        final long version;
        Record(Cursor cursor) {
            table = cursor.getString(0); id = cursor.getString(1);
            body = Json.object(cursor.getString(2));
            base = cursor.isNull(3) ? null : Json.object(cursor.getString(3));
            dirty = cursor.getInt(4) != 0; removed = cursor.getInt(5) != 0;
            version = cursor.getLong(6); error = cursor.getString(7);
        }
    }
    private static final String COLUMNS = "collection,id,body,base,dirty,removed,version,error";
    OfflineStore(Context context, String account) {
        super(context, "tbft-offline-" + java.util.UUID.fromString(account) + ".db", null, 1);
        setWriteAheadLoggingEnabled(true);
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE records (collection TEXT NOT NULL,id TEXT NOT NULL,body TEXT NOT NULL,base TEXT,dirty INTEGER NOT NULL DEFAULT 0,removed INTEGER NOT NULL DEFAULT 0,version INTEGER NOT NULL DEFAULT 0,error TEXT NOT NULL DEFAULT '',PRIMARY KEY(collection,id))");
        db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY,value TEXT NOT NULL)");
        db.execSQL("CREATE INDEX pending_records ON records(dirty,error)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("An explicit, non-destructive migration is required");
    }
    synchronized String meta(String key, String fallback) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM metadata WHERE key=?", new String[]{key})) {
            return c.moveToFirst() ? c.getString(0) : fallback;
        }
    }
    synchronized void setMeta(String key, String value) {
        ContentValues values = new ContentValues(); values.put("key", key); values.put("value", value);
        getWritableDatabase().replaceOrThrow("metadata", null, values);
    }
    synchronized Record get(String table, String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + COLUMNS + " FROM records WHERE collection=? AND id=?", new String[]{table, id})) {
            return c.moveToFirst() ? new Record(c) : null;
        }
    }
    synchronized List<Record> records(String table) {
        List<Record> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + COLUMNS + " FROM records WHERE collection=? AND removed=0 ORDER BY rowid", new String[]{table})) {
            while (c.moveToNext()) out.add(new Record(c));
        }
        return out;
    }
    synchronized List<Record> pending() {
        List<Record> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + COLUMNS + " FROM records WHERE dirty=1 ORDER BY CASE collection WHEN 'workspaces' THEN 0 WHEN 'projects' THEN 1 WHEN 'project_nodes' THEN 2 WHEN 'wardrobes' THEN 3 WHEN 'tasks' THEN 4 ELSE 5 END,rowid", null)) {
            while (c.moveToNext()) out.add(new Record(c));
        }
        return out;
    }
    private void write(String table, String id, JSONObject body, JSONObject base, boolean dirty, boolean removed, long version, String error) {
        ContentValues v = new ContentValues();
        v.put("collection", table); v.put("id", id); v.put("body", body.toString());
        if (base == null) v.putNull("base"); else v.put("base", base.toString());
        v.put("dirty", dirty ? 1 : 0); v.put("removed", removed ? 1 : 0); v.put("version", version); v.put("error", error);
        getWritableDatabase().replaceOrThrow("records", null, v);
    }
    synchronized void save(String table, JSONObject desired) {
        if (!SyncRules.WRITABLE.contains(table)) throw new IllegalArgumentException("Read-only collection");
        String id = Json.text(desired, "id"); java.util.UUID.fromString(id);
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            Record prior = get(table, id);
            // Keep the original server base through arbitrarily many offline edits.
            write(table, id, desired, prior == null ? null : prior.base, true, false,
                    prior == null ? 1 : prior.version + 1, prior == null ? "" : prior.error);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    synchronized void removeReminder(String id) {
        Record r = get("reminders", id);
        if (r != null) write(r.table, id, r.body, r.base, true, true, r.version + 1, r.error);
    }
    synchronized JSONObject wardrobe(String workspace,String account) {
        Record r=get("wardrobes",WardrobeRules.id(workspace,account));
        return r==null ? WardrobeRules.fresh(workspace,account) : Json.copy(r.body);
    }
    synchronized void changeWardrobe(String workspace,String account,String date,java.util.function.Consumer<JSONObject> action) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            JSONObject doc=wardrobe(workspace,account);
            action.accept(doc); WardrobeRules.reconcile(doc,date);
            saveWardrobe(doc);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private void saveWardrobe(JSONObject doc) {
        if(doc.toString().length()>350000) throw new IllegalArgumentException("The wardrobe is too large to save safely. Export a backup and reduce saved outfits.");
        Record previous=get("wardrobes",Json.text(doc,"id"));
        Json.put(doc,"change_id",java.util.UUID.randomUUID().toString());
        save("wardrobes",doc);
        for(JSONObject batch:WardrobeRules.list(WardrobeRules.state(doc),"batches")) {
            if(!Json.text(batch,"cancelled_at").isEmpty()) continue;
            String taskId=Json.text(batch,"task_id"); Record task=get("tasks",taskId);
            if(task==null) save("tasks",WardrobeRules.task(doc,batch));
            else {
                JSONObject prior=previous==null?null:WardrobeRules.batch(previous.body,taskId);
                if((prior==null||Json.text(prior,"completed_at").isEmpty())&&!Json.text(batch,"completed_at").isEmpty())
                    save("tasks",Json.merge(task.body,Json.of("completed_at",batch.opt("completed_at"))));
            }
        }
    }
    synchronized Record wardrobeForTask(String taskId) {
        for(Record r:records("wardrobes")) if(WardrobeRules.batch(r.body,taskId)!=null) return r;
        return null;
    }
    synchronized void saveTaskAndWardrobe(JSONObject task) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            String taskId=Json.text(task,"id"); Record prior=get("tasks",taskId),wardrobe=wardrobeForTask(taskId);
            save("tasks",task);
            if(wardrobe!=null) {
                JSONObject doc=Json.copy(wardrobe.body);
                boolean changed=false;
                if(prior!=null && Json.text(prior.body,"completed_at").isEmpty())
                    changed=WardrobeRules.complete(doc,taskId,Json.text(task,"completed_at"));
                JSONObject batch=WardrobeRules.batch(doc,taskId);
                if(prior!=null && Json.text(batch,"completed_at").isEmpty() && Json.text(batch,"cancelled_at").isEmpty()
                        && Json.text(prior.body,"deleted_at").isEmpty() && !Json.text(task,"deleted_at").isEmpty()) {
                    Json.put(WardrobeRules.batch(doc,taskId),"cancelled_at",task.opt("deleted_at")); changed=true;
                }
                if(changed) saveWardrobe(doc);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    /** Only call with an entire successfully paginated snapshot, never a partial/failed response. */
    synchronized void ingest(String table, List<JSONObject> rows) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            Set<String> ids = new HashSet<>();
            for (JSONObject body : rows) {
                String id = Json.text(body, "id");
                if (id.isEmpty()) throw new IllegalArgumentException("Snapshot lacks an ID");
                ids.add(id); Record local = get(table, id);
                if (local == null || !local.dirty) write(table, id, body, body, false, false, local == null ? 0 : local.version, "");
            }
            for (Record local : records(table)) {
                if (!local.dirty && !ids.contains(local.id)) db.delete("records", "collection=? AND id=?", new String[]{table, local.id});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    synchronized void acknowledge(Record sent, JSONObject remote) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            Record current = get(sent.table, sent.id);
            if (current == null) return;
            if (current.version == sent.version) {
                if (remote == null) db.delete("records", "collection=? AND id=?", new String[]{sent.table, sent.id});
                else write(sent.table, sent.id, remote, remote, false, false, current.version, "");
            } else if (remote != null) {
                JSONObject rebased = SyncRules.rebase(sent.body, current.body, remote);
                write(sent.table, sent.id, rebased, remote, true, current.removed, current.version, "");
            } else {
                write(current.table, current.id, current.body, current.base, true, current.removed, current.version,
                        "Record removed while a newer local edit was pending. Local copy preserved.");
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    synchronized void conflict(Record sent, String message) {
        getWritableDatabase().execSQL("UPDATE records SET error=? WHERE collection=? AND id=? AND dirty=1", new Object[]{message, sent.table, sent.id});
    }
    synchronized void acknowledgeManagedTask(Record sent,JSONObject generated,JSONObject remote) {
        Record current=get(sent.table,sent.id);if(current==null)return;
        JSONObject changes=SyncRules.delta(sent.base==null?generated:sent.base,sent.body);
        changes.remove("completed_at");changes.remove("deleted_at");
        JSONObject rebased=Json.merge(Json.merge(remote,changes),SyncRules.delta(sent.body,current.body));
        boolean dirty=SyncRules.delta(remote,rebased).length()>0;
        write(sent.table,sent.id,rebased,remote,dirty,false,current.version,"");
    }
    synchronized void retry(Record record) {
        getWritableDatabase().execSQL("UPDATE records SET error='' WHERE collection=? AND id=?", new Object[]{record.table, record.id});
    }
    synchronized void resolve(Record reviewed, JSONObject remote, boolean keepLocal) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            Record current = get(reviewed.table, reviewed.id);
            if (current == null || current.version != reviewed.version) throw new IllegalStateException("This item changed while you were reviewing it. Open review again.");
            if (keepLocal) {
                if (remote == null) throw new IllegalStateException("The server copy was deleted. Export the preserved local copy before creating a new item.");
                if(current.table.equals("wardrobes") && (current.base==null || !SyncRules.equal(WardrobeRules.state(current.base).opt("batches"),WardrobeRules.state(remote).opt("batches"))))
                    throw new IllegalStateException("Laundry changed on another device. Export this copy, then use the server copy to preserve its laundry history.");
                JSONObject patch = SyncRules.delta(current.base == null ? new JSONObject() : current.base, current.body);
                write(current.table,current.id,Json.merge(remote,patch),remote,true,current.removed,current.version+1,"");
            } else if (remote == null) db.delete("records","collection=? AND id=?",new String[]{current.table,current.id});
            else write(current.table,current.id,remote,remote,false,false,current.version+1,"");
            if(current.table.equals("wardrobes") && !keepLocal) {
                for(JSONObject batch:WardrobeRules.list(WardrobeRules.state(current.body),"batches")) {
                    Record task=get("tasks",Json.text(batch,"task_id"));
                    if(task!=null && task.base==null && (remote==null || WardrobeRules.batch(remote,task.id)==null))
                        db.delete("records","collection='tasks' AND id=?",new String[]{task.id});
                    else if(task!=null && task.dirty) write(task.table,task.id,task.body,task.base,true,false,task.version+1,"Laundry copy changed. Review this task before syncing it.");
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    synchronized JSONObject export() {
        org.json.JSONArray rows = new org.json.JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + COLUMNS + " FROM records", null)) {
            while (c.moveToNext()) {
                Record r = new Record(c);
                rows.put(Json.of("collection", r.table, "id", r.id, "body", r.body, "serverBase", r.base,
                        "pending", r.dirty, "removed", r.removed, "conflict", r.error));
            }
        }
        return Json.of("format", "tbft-offline-backup-v1", "exportedAt", java.time.Instant.now().toString(), "records", rows);
    }
    /** Imports only pending work, preserving the downloaded server base for explicit review. */
    synchronized int importBackup(JSONObject backup,String workspace,String account) {
        if(workspace.isEmpty() || !"tbft-offline-backup-v1".equals(Json.text(backup,"format")) || backup.optJSONArray("records")==null)
            throw new IllegalArgumentException("Connect this account first, then choose a TBFT backup.");
        java.util.Map<String,JSONObject> parents=new java.util.HashMap<>(); boolean sameWorkspace=false,sameAccount=false;
        for(JSONObject entry:Json.rows(backup.optJSONArray("records"))) {
            JSONObject body=entry.optJSONObject("body");if(body==null)throw new IllegalArgumentException("Invalid backup record");
            String table=Json.text(entry,"collection"),id=Json.text(body,"id");java.util.UUID.fromString(id);
            if(!id.equals(Json.text(entry,"id")))throw new IllegalArgumentException("Backup identity mismatch");
            parents.put(table+":"+id,body);
            if(table.equals("workspaces")&&workspace.equals(id))sameWorkspace=true;
            if(table.equals("profiles")&&account.equals(id))sameAccount=true;
        }
        if(!sameWorkspace||!sameAccount)throw new IllegalArgumentException("This backup belongs to a different workspace or account.");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();int count=0;
        try {
            for(JSONObject entry:Json.rows(backup.optJSONArray("records"))) {
                String table=Json.text(entry,"collection");if(!entry.optBoolean("pending")||!SyncRules.WRITABLE.contains(table))continue;
                JSONObject body=entry.optJSONObject("body");String id=Json.text(body,"id");
                if(body.toString().length()>350000)throw new IllegalArgumentException("A backup record is too large");
                String scoped=Json.text(body,"workspace_id");
                if(table.equals("workspaces"))scoped=id;
                if(table.equals("project_nodes")||table.equals("task_messages")) {
                    String parentTable=table.equals("project_nodes")?"projects":"tasks",parentId=Json.text(body,table.equals("project_nodes")?"project_id":"task_id");
                    JSONObject parent=parents.get(parentTable+":"+parentId);Record local=get(parentTable,parentId);if(parent==null&&local!=null)parent=local.body;
                    scoped=parent==null?"":Json.text(parent,"workspace_id");
                }
                if(!workspace.equals(scoped))throw new IllegalArgumentException("A pending item belongs to a different workspace.");
                if(table.equals("wardrobes")&&!account.equals(Json.text(body,"owner_user_id")))throw new IllegalArgumentException("This wardrobe belongs to another account.");
                Record local=get(table,id);
                if(local!=null&&local.dirty){if(!SyncRules.equal(local.body,body))throw new IllegalStateException("Pending work already exists here. Export and resolve it before importing.");continue;}
                JSONObject base=entry.optJSONObject("serverBase");
                write(table,id,body,base,true,entry.optBoolean("removed"),local==null?1:local.version+1,"Imported backup. Compare with the server, or retry after reviewing the local copy.");count++;
            }
            db.setTransactionSuccessful();return count;
        } finally {db.endTransaction();}
    }
}
