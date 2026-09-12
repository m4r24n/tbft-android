package info.marzan.tbft;

import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class TbftRepository {
    static final String CHANGED = "info.marzan.tbft.LOCAL_CHANGED";
    private static TbftRepository instance;
    final Context context;
    final SessionVault vault;
    final SupabaseApi api;
    final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private OfflineStore local;
    volatile String transientError = "";
    static synchronized TbftRepository get(Context context) {
        if (instance == null) instance = new TbftRepository(context.getApplicationContext());
        return instance;
    }
    private TbftRepository(Context context) { this.context = context; vault = new SessionVault(context); api = new SupabaseApi(vault); }
    synchronized OfflineStore store() {
        if (vault.account().isEmpty()) return null;
        if (local == null) local = new OfflineStore(context, vault.account());
        return local;
    }
    List<OfflineStore.Record> rows(String table) { return store() == null ? new ArrayList<>() : store().records(table); }
    JSONObject workspace() {
        List<OfflineStore.Record> rows = rows("workspaces");
        return rows.isEmpty() ? Json.of("timezone", "Europe/Berlin", "rollover_hour", 6) : rows.get(0).body;
    }
    String timezone() { return workspace().optString("timezone", "Europe/Berlin"); }
    int rollover() { return workspace().optInt("rollover_hour", 6); }
    String today() { return BoardRules.boardDate(timezone(), rollover(), Instant.now()); }
    String workspaceId() { return store() == null ? "" : store().meta("workspace", ""); }
    String name(String id) {
        for (OfflineStore.Record profile : rows("profiles")) if (profile.id.equals(id)) return Json.text(profile.body, "display_name");
        return id.equals(vault.account()) ? "You" : "Partner";
    }
    String status() {
        if (syncing.get()) return "Syncing · local editing stays available";
        if (store() == null) return "Connect once to download your workspace";
        List<OfflineStore.Record> pending = store().pending();
        int conflicts = 0;
        for (OfflineStore.Record row : pending) if (!row.error.isEmpty()) conflicts++;
        String error = store().meta("sync_error", "");
        String last = store().meta("last_sync", "");
        return (conflicts > 0 ? conflicts + " need review · " : "") + pending.size() + " pending · "
                + (!error.isEmpty() ? error : last.isEmpty() ? "Initial download needed" : "Last synced " + last.replace('T', ' ').substring(0, 16) + " UTC");
    }
    void changed() {
        context.sendBroadcast(new Intent(CHANGED).setPackage(context.getPackageName()));
        TbftWidgetProvider.updateAll(context);
    }
    interface Completion { void done(String error); }
    interface RemoteCompletion { void done(JSONObject remote, String error); }
    void review(OfflineStore.Record row, RemoteCompletion done) {
        network.execute(() -> {
            try { done.done(api.one(row.table,row.id),""); }
            catch (Exception e) { done.done(null,"Connect to review the server copy. Your local copy is preserved."); }
        });
    }
    void resolve(OfflineStore.Record row, JSONObject remote, boolean keepLocal, Completion done) {
        io.execute(() -> {
            try { store().resolve(row,remote,keepLocal); changed(); SyncJobs.request(context); done.done(""); }
            catch (Exception e) { done.done(e.getMessage()); }
        });
    }
    void signIn(String email, String password, Completion done) {
        network.execute(() -> {
            String error = "";
            try { api.signIn(email, password); SyncJobs.schedule(context); }
            catch (Exception e) { error = e.getMessage() == null ? "Could not sign in" : e.getMessage(); }
            done.done(error); changed();
            if (error.isEmpty()) requestSync();
        });
    }
    void edit(String table, JSONObject opened, JSONObject body, Completion done) {
        io.execute(() -> {
            String error = "";
            try {
                if (store() == null || workspaceId().isEmpty()) throw new IllegalStateException("Download your workspace first");
                synchronized (store()) {
                    JSONObject desired = body;
                    if (opened != null) {
                        OfflineStore.Record latest = store().get(table, Json.text(body, "id"));
                        JSONObject changes = SyncRules.delta(opened,body);
                        if (table.equals("tasks") && !vault.account().equals(Json.text(opened,"owner_user_id"))
                                && (changes.has("completed_at") || changes.has("deleted_at"))) throw new IllegalStateException("Only the task owner can change completion or archive status.");
                        String overlap = SyncRules.conflict(opened, body, latest == null ? null : latest.body);
                        if (!overlap.isEmpty()) throw new IllegalStateException(overlap + " Reopen this item to review the latest copy; this form is still here.");
                        desired = Json.merge(latest.body, SyncRules.delta(opened, body));
                    }
                    store().save(table, desired);
                }
                changed(); SyncJobs.request(context);
            } catch (Exception e) { error = "Could not save locally: " + e.getMessage(); }
            done.done(error);
        });
    }
    void deleteReminder(String id, Completion done) {
        io.execute(() -> {
            try { store().removeReminder(id); changed(); SyncJobs.request(context); done.done(""); }
            catch (Exception e) { done.done("Could not save deletion locally"); }
        });
    }
    void requestSync() { syncAsync(success -> {}); }
    interface SyncCompletion { void done(boolean success); }
    void syncAsync(SyncCompletion done) {
        if (store() == null) { done.done(true); return; }
        if (!syncing.compareAndSet(false, true)) { done.done(false); return; }
        changed();
        network.execute(() -> {
            boolean success = false;
            try {
                new SyncEngine(store(), api, vault.account()).sync();
                store().setMeta("sync_error", "");
                success = store().pending().stream().noneMatch(r -> r.error.isEmpty());
            } catch (Exception e) {
                store().setMeta("sync_error", e instanceof SupabaseApi.ApiException ? e.getMessage()
                        : "Offline or service unavailable. Local work is safe.");
            } finally { syncing.set(false); changed(); done.done(success); }
        });
    }
    List<String> widgetLines() {
        List<String> lines = new ArrayList<>();
        String date = today(), account = vault.account();
        for (OfflineStore.Record row : rows("reminders")) if (account.equals(Json.text(row.body, "owner_user_id"))
                && date.equals(Json.text(row.body, "reminder_date"))) lines.add("REMINDER · " + Json.text(row.body, "title"));
        for (OfflineStore.Record row : rows("tasks")) if (account.equals(Json.text(row.body, "owner_user_id"))
                && Json.text(row.body, "completed_at").isEmpty() && BoardRules.appears(row.body, date, timezone(), rollover(), Instant.now())) {
            String deadline = Json.text(row.body, "deadline");
            lines.add((date.compareTo(Json.text(row.body, "original_date")) > 0 ? "↪ " : "") + Json.text(row.body, "title")
                    + (deadline.isEmpty() ? "" : " · " + deadline.substring(0, Math.min(5, deadline.length()))));
        }
        return lines;
    }
}
