package info.marzan.tbft;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.time.*;
import java.util.*;

/** Every organiser screen is rendered from SQLite. WebView is confined to BrowserActivity. */
public class MainActivity extends Activity {
    private static final int INK=Ui.INK, MUTED=Ui.MUTED, BG=Ui.BG, CARD=Ui.CARD, ACCENT=Ui.ACCENT;
    private TbftRepository repo;
    private LinearLayout page, body;
    private TextView sync;
    private ScrollView contentScroll;
    private String renderedPage = "";
    private String tab = "Today", date = "", projectId = "";
    private boolean registered;
    private String moreSection="", projectSection="Tasks", month="";
    private WardrobeScreen wardrobeScreen;
    private final Handler clock = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() { public void run() { render(); clock.postDelayed(this, 60000); } };
    private final BroadcastReceiver receiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { render(); } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); repo = TbftRepository.get(this); wardrobeScreen=new WardrobeScreen(this,repo,this::render);
        if (state != null) { tab = state.getString("tab", "Today"); date = state.getString("date", ""); projectId = state.getString("project", ""); }
        SyncJobs.schedule(this); render();
    }
    @Override protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, new IntentFilter(TbftRepository.CHANGED), Context.RECEIVER_NOT_EXPORTED);
        else registerLegacyReceiver();
        registered = true; repo.requestSync(); clock.postDelayed(tick, 60000);
    }
    @Override protected void onStop() {
        clock.removeCallbacks(tick); if (registered) { unregisterReceiver(receiver); registered = false; } super.onStop();
    }
    // Before API 33 there is no NOT_EXPORTED flag; a signature permission restricts senders.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerLegacyReceiver() {
        registerReceiver(receiver,new IntentFilter(TbftRepository.CHANGED),getPackageName()+".permission.LOCAL_UPDATES",null);
    }
    @Override public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("tab", tab); out.putString("date", date); out.putString("project", projectId);
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(LinearLayout parent, String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(5), 0, dp(5)); parent.addView(view); return view;
    }
    private Button button(LinearLayout parent, String title, Runnable action) { return Ui.button(parent,title,action); }
    private LinearLayout card(LinearLayout parent) { return Ui.card(parent); }
    private void title(String title, String subtitle) {
        text(body, title, 28, INK).setTypeface(null, Typeface.BOLD); if (!subtitle.isEmpty()) text(body, subtitle, 14, MUTED);
    }
    private void render() {
        if (isFinishing() || isDestroyed()) return;
        String location = tab + ":" + projectId + ":" + moreSection + ":" + projectSection + ":" + (tab.equals("Today") ? repo.today() : date);
        int previousY = contentScroll != null && location.equals(renderedPage) ? contentScroll.getScrollY() : 0;
        renderedPage = location;
        page = column(); page.setBackgroundColor(BG);
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(14) + insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    dp(14) + insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets;
        });
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = new TextView(this); brand.setText("TBFT  /"); brand.setLetterSpacing(.1f); brand.setTextColor(INK); brand.setTextSize(22); brand.setTypeface(null, Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        button(header, "Browser", () -> startActivity(new Intent(this, BrowserActivity.class)));
        page.addView(header);
        sync = text(page, repo.summary(), 12, MUTED); sync.setPadding(0,dp(8),0,dp(8)); sync.setOnClickListener(v -> queue());
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        contentScroll = scroll;
        body = column(); body.setPadding(0, dp(10), 0, dp(20)); scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (repo.workspaceId().isEmpty()) {
            title("Your organiser, on your phone", "Connect once to download your TBFT workspace. Saved tasks and notes then work without internet.");
            button(body, "Connect TBFT account", this::login);
            text(body, "Saved locally · ready when you are", 13, MUTED);
        } else {
            switch (tab) {
                case "Projects": if (projectId.isEmpty()) projects(); else project(); break;
                case "Calendar": calendar(); break;
                case "Wardrobe": wardrobeScreen.render(body); break;
                case "More": more(); break;
                default: board(repo.today(), false);
            }
        }
        LinearLayout nav = new LinearLayout(this);
        for (String label : new String[]{"Today", "Projects", "Wardrobe", "Calendar", "More"}) {
            Button b = button(nav, label, () -> { tab = label; projectId = ""; moreSection=""; render(); });
            Ui.selected(b,tab.equals(label));
            b.setLayoutParams(new LinearLayout.LayoutParams(0, dp(54), 1)); b.setTextSize(11); b.setPadding(0,0,0,0);
        }
        page.addView(nav); setContentView(page); page.requestApplyInsets();
        if (previousY > 0) scroll.post(() -> scroll.scrollTo(0,previousY));
    }
    private void board(String selected, boolean calendar) {
        if (!calendar) {
            text(body,"THE DAILY BOARD",11,ACCENT).setLetterSpacing(.15f);
            title("Today",LocalDate.parse(selected).format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")));
            int remaining=0,done=0;
            for(OfflineStore.Record t:repo.rows("tasks")) if(repo.vault.account().equals(Json.text(t.body,"owner_user_id")) && BoardRules.appears(t.body,selected,repo.timezone(),repo.rollover(),Instant.now())) { if(Json.text(t.body,"completed_at").isEmpty())remaining++;else done++; }
            LinearLayout summary=card(body); summary.setBackground(Ui.shape(this,Ui.SAGE,18));
            text(summary,remaining+" to do   ·   "+done+" completed",18,INK).setTypeface(null,Typeface.BOLD);
            text(summary,"One thing at a time.",13,MUTED);
        } else text(body,LocalDate.parse(selected).format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")),20,INK).setTypeface(null,Typeface.BOLD);
        button(body, "+ Add task", () -> task(null, "", "", repo.vault.account(), selected));
        List<String> owners = new ArrayList<>(); owners.add(repo.vault.account());
        for (OfflineStore.Record r : repo.rows("profiles")) if (!owners.contains(r.id)) owners.add(r.id);
        for (String owner : owners) {
            text(body, repo.name(owner), 21, INK).setTypeface(null, Typeface.BOLD);
            int count = 0;
            for (OfflineStore.Record r : repo.rows("reminders")) if (selected.equals(Json.text(r.body,"reminder_date")) && owner.equals(Json.text(r.body,"owner_user_id"))) {
                LinearLayout c = card(body); text(c, "REMINDER", 11, ACCENT);
                text(c, Json.text(r.body,"title"), 18, INK); if (!Json.text(r.body,"note").isEmpty()) text(c, Json.text(r.body,"note"), 14, MUTED);
                c.setOnClickListener(v -> reminder(r, owner, selected)); count++;
            }
            List<OfflineStore.Record> tasks = repo.rows("tasks");
            tasks.sort(Comparator.comparing((OfflineStore.Record r) -> !Json.text(r.body,"completed_at").isEmpty())
                    .thenComparing(r -> Json.text(r.body,"original_date")).thenComparing(r -> Json.text(r.body,"deadline")));
            for (OfflineStore.Record r : tasks) if (owner.equals(Json.text(r.body,"owner_user_id")) && BoardRules.appears(r.body, selected, repo.timezone(), repo.rollover(), Instant.now())) {
                taskCard(body, r, selected); count++;
            }
            if (count == 0) text(body, "Nothing planned here.", 14, MUTED);
            button(body, "+ Add reminder", () -> reminder(null, owner, selected));
        }
    }
    private void taskCard(LinearLayout parent, OfflineStore.Record r, String selected) {
        JSONObject t = r.body; LinearLayout c = card(parent);
        String state=BoardRules.state(t, selected, repo.timezone(), repo.rollover(), Instant.now());
        if(Json.text(t,"completed_at").isEmpty()) c.setBackground(Ui.shape(this,Ui.SAGE,18));
        text(c, state.toUpperCase(Locale.ROOT) + pending(r), 11, ACCENT);
        text(c, Json.text(t,"title"), 18, INK);
        String time = Json.text(t,"deadline");
        text(c, repo.name(Json.text(t,"owner_user_id")) + (time.isEmpty() ? "" : " · " + time.substring(0, Math.min(5,time.length())))
                + (Json.text(t,"priority").equals("high") ? " · High priority" : ""), 13, MUTED);
        c.setOnClickListener(v -> task(r, "", "", "", selected));
    }
    private String pending(OfflineStore.Record r) { return !r.error.isEmpty() ? " · Needs review" : r.dirty ? " · Saved on device" : ""; }
    private void projects() {
        title("Projects", "Progress, next actions, and everything belonging to a project.");
        button(body, "+ New project", () -> editProject(null));
        int count = 0;
        for (OfflineStore.Record r : repo.rows("projects")) if (Json.text(r.body,"deleted_at").isEmpty()) {
            LinearLayout c = card(body); text(c, Json.text(r.body,"name"), 21, INK);
            int all = 0, done = 0; String next = "";
            for (OfflineStore.Record t : repo.rows("tasks")) if (r.id.equals(Json.text(t.body,"project_id")) && Json.text(t.body,"deleted_at").isEmpty()) {
                all++; if (!Json.text(t.body,"completed_at").isEmpty()) done++; else if (next.isEmpty()) next = Json.text(t.body,"title");
            }
            text(c, done + " / " + all + " tasks complete" + pending(r), 13, ACCENT);
            ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT)); progress.setProgress(all == 0 ? 0 : done * 100 / all); c.addView(progress);
            if (!next.isEmpty()) text(c, "Next · " + next, 15, INK);
            String target = Json.text(r.body,"target_date"); if (!target.isEmpty()) text(c, "Target " + target, 13, MUTED);
            c.setOnClickListener(v -> { projectId = r.id; projectSection="Tasks"; render(); }); count++;
        }
        if (count == 0) text(body, "Create a project to group tasks and phases.", 15, MUTED);
    }
    private void calendar() {
        String selected=date.isEmpty()?repo.today():date;
        YearMonth shown; try { shown=YearMonth.parse(month.isEmpty()?selected.substring(0,7):month); } catch(Exception e){shown=YearMonth.now();}
        final YearMonth current=shown;
        title("Calendar","A little perspective on your days.");
        LinearLayout controls=new LinearLayout(this);
        button(controls,"‹",()->{month=current.minusMonths(1).toString();render();}).setContentDescription("Previous month");
        TextView label=text(controls,current.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")),18,INK); label.setGravity(Gravity.CENTER);
        label.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        button(controls,"›",()->{month=current.plusMonths(1).toString();render();}).setContentDescription("Next month"); body.addView(controls);
        LinearLayout grid=card(body), days=new LinearLayout(this);grid.addView(days);
        for(String day:new String[]{"M","T","W","T","F","S","S"}){TextView t=text(days,day,12,MUTED);t.setGravity(Gravity.CENTER);t.setLayoutParams(new LinearLayout.LayoutParams(0,dp(32),1));}
        int first=current.atDay(1).getDayOfWeek().getValue()-1,total=current.lengthOfMonth();LinearLayout week=null;
        for(int cell=0;cell<((first+total+6)/7)*7;cell++){
            if(cell%7==0){week=new LinearLayout(this);grid.addView(week);}
            int day=cell-first+1;
            if(day<1||day>total){week.addView(new android.view.View(this),new LinearLayout.LayoutParams(0,dp(48),1));continue;}
            String value=current.atDay(day).toString();int count=0;
            for(OfflineStore.Record t:repo.rows("tasks"))if(BoardRules.appears(t.body,value,repo.timezone(),repo.rollover(),Instant.now()))count++;
            Button b=button(week,String.valueOf(day)+(count>0?" ·":""),()->{date=value;render();});
            b.setPadding(0,0,0,0);b.setTextSize(13);b.setLayoutParams(new LinearLayout.LayoutParams(0,dp(48),1));Ui.selected(b,value.equals(selected));
            b.setContentDescription(value+", "+count+" tasks");if(value.equals(repo.today())&&!value.equals(selected))b.setTextColor(ACCENT);
        }
        button(body,"Back to today",()->{date=repo.today();month="";render();});board(selected,true);
    }
    private void project() {
        OfflineStore.Record r=repo.store().get("projects",projectId);
        if(r==null){projectId="";projects();return;}
        button(body,"‹ All projects",()->{projectId="";render();});
        title(Json.text(r.body,"name"),Json.text(r.body,"description"));
        LinearLayout tabs=Ui.chips(body);
        for(String section:new String[]{"Tasks","Phases","Files","Activity","Details"})
            Ui.selected(button(tabs,section,()->{projectSection=section;render();}),projectSection.equals(section));
        switch(projectSection) {
            case "Tasks":
                button(body,"+ Add task",()->task(null,r.id,"",repo.vault.account(),repo.today()));
                int count=0;
                for(OfflineStore.Record t:repo.rows("tasks"))if(r.id.equals(Json.text(t.body,"project_id"))&&Json.text(t.body,"deleted_at").isEmpty()){taskCard(body,t,repo.today());count++;}
                if(count==0)text(body,"Give this project its first next action.",15,MUTED);break;
            case "Phases":
                button(body,"+ Add phase",()->phase(null,r.id));
                for(OfflineStore.Record p:repo.rows("project_nodes"))if(r.id.equals(Json.text(p.body,"project_id"))){
                    LinearLayout c=card(body);text(c,Json.text(p.body,"title"),18,INK);text(c,Json.text(p.body,"description"),14,MUTED);
                    button(c,"Edit phase",()->phase(p,r.id));button(c,"+ Task in this phase",()->task(null,r.id,p.id,repo.vault.account(),repo.today()));
                }break;
            case "Files":files(r.id);break;
            case "Activity":activity(r.id);break;
            default:
                LinearLayout c=card(body);text(c,"Owner · "+repo.name(Json.text(r.body,"owner_user_id")),15,INK);
                text(c,"Target · "+(Json.text(r.body,"target_date").isEmpty()?"Not set":Json.text(r.body,"target_date")),14,MUTED);
                button(c,"Edit project",()->editProject(r));
        }
    }
    private void more() {
        title(moreSection.isEmpty()?"Your space":moreSection,moreSection.isEmpty()?"A place for everything. Open only what you need.":"");
        if(moreSection.isEmpty()){
            menu("Workspace","People, timezone and daily rhythm",this::settings);
            menu("Archive","Finished chapters, ready to restore",()->{moreSection="Archive";render();});
            menu("Files","Project documents and links",()->{moreSection="Files";render();});
            menu("Activity","Recent changes in your workspace",()->{moreSection="Activity";render();});
            menu("Sync & backup","Connection, pending changes and local backup",()->{moreSection="Sync & backup";render();});
            menu("About","App version and available features",()->{moreSection="About";render();});return;
        }
        button(body,"‹ Your space",()->{moreSection="";render();});
        switch(moreSection){
            case "Archive":
                int count=0;
                for(String table:new String[]{"tasks","projects"})for(OfflineStore.Record r:repo.rows(table))if(!Json.text(r.body,"deleted_at").isEmpty()){
                    LinearLayout c=card(body);text(c,Json.text(r.body,table.equals("tasks")?"title":"name")+pending(r),16,INK);
                    button(c,"Restore",()->save(table,r,Json.merge(r.body,Json.of("deleted_at",null)),null));count++;
                }if(count==0)text(body,"Your archive is empty.",15,MUTED);break;
            case "Files":files("");break;
            case "Activity":activity("");break;
            case "Sync & backup":
                LinearLayout c=card(body);text(c,repo.status(),14,MUTED);button(c,"Sync & review pending changes",this::queue);
                button(body,"Reconnect account",this::login);button(body,"Export local backup",this::backup);button(body,"Import backup from previous app",this::importBackup);break;
            default:
                text(body,"TBFT Native · "+BuildConfig.VERSION_NAME,18,INK);
                text(body,"Tasks, notes, reminders, projects, calendar and Wardrobe save on this phone. Widgets use the same local data.",14,MUTED);
                text(body,"Files show metadata and online links. Recurring task generation and Google Drive PDF export still use the website.",14,MUTED);
        }
    }
    private void menu(String title,String subtitle,Runnable action){
        LinearLayout c=card(body);text(c,title+"  ›",18,INK).setTypeface(null,Typeface.BOLD);text(c,subtitle,13,MUTED);
        c.setOnClickListener(v->action.run());c.setFocusable(true);c.setContentDescription(title+", "+subtitle);
    }
    private void files(String project) {
        int count = 0;
        for (OfflineStore.Record r : repo.rows("project_files")) if (Json.text(r.body,"deleted_at").isEmpty() && (project.isEmpty() || project.equals(Json.text(r.body,"project_id")))) {
            LinearLayout c = card(body); text(c, Json.text(r.body,"original_name"), 16, INK);
            text(c, "File details available offline · contents require internet", 12, MUTED);
            String url = Json.text(r.body,"external_file_url");
            if (url.startsWith("https://")) button(c, "Open file", () -> startActivity(new Intent(this, BrowserActivity.class).setData(Uri.parse(url))));
            else text(c, "Open this attachment from the web app.", 12, MUTED);
            count++;
        }
        if (count == 0) text(body,"No downloaded file details.",14,MUTED);
    }
    private void activity(String project) {
        List<OfflineStore.Record> rows = repo.rows("activity_log"); rows.sort((a,b) -> Json.text(b.body,"created_at").compareTo(Json.text(a.body,"created_at")));
        int count = 0;
        Set<String> entities = new HashSet<>(); entities.add(project);
        if (!project.isEmpty()) for (String table : new String[]{"tasks","project_nodes","project_files"})
            for (OfflineStore.Record r : repo.rows(table)) if (project.equals(Json.text(r.body,"project_id"))) entities.add(r.id);
        for (OfflineStore.Record r : rows) if (project.isEmpty() || entities.contains(Json.text(r.body,"entity_id"))) {
            text(body, Json.text(r.body,"summary") + "\n" + Json.text(r.body,"created_at"), 13, MUTED); if (++count >= 20) break;
        }
        if (count == 0) text(body,"No downloaded activity yet.",14,MUTED);
    }
    private interface DateResult { void set(String value); }
    private void chooseDate(String initial, DateResult result) {
        LocalDate day; try { day = LocalDate.parse(initial); } catch (Exception e) { day = LocalDate.now(); }
        new DatePickerDialog(this,(picker,y,m,d) -> result.set(LocalDate.of(y,m+1,d).toString()),day.getYear(),day.getMonthValue()-1,day.getDayOfMonth()).show();
    }
    private final class Form {
        final LinearLayout fields = column();
        final Map<String,EditText> inputs = new LinkedHashMap<>();
        final Map<String,Spinner> spinners = new LinkedHashMap<>();
        final Map<String,List<String>> values = new HashMap<>();
        AlertDialog dialog;
        Form(String title) {
            fields.setPadding(dp(20),dp(8),dp(20),dp(20)); fields.setBackgroundColor(CARD);
            ScrollView scroll = new ScrollView(MainActivity.this); scroll.addView(fields);
            dialog = new AlertDialog.Builder(MainActivity.this).setTitle(title).setView(scroll).setNegativeButton("Close",null).create();
        }
        EditText input(String key, String label, String value, boolean multiline) {
            TextView l = new TextView(MainActivity.this); l.setText(label); fields.addView(l);
            EditText e = new EditText(MainActivity.this); e.setText(value); e.setTextSize(16);
            e.setSingleLine(!multiline); if (multiline) { e.setMinLines(3); e.setGravity(Gravity.TOP); }
            e.setInputType(InputType.TYPE_CLASS_TEXT | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES : InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
            fields.addView(e); inputs.put(key,e); return e;
        }
        void date(String key, String label, String value) {
            EditText e = input(key,label,value,false);
            button(fields,"Choose " + label.toLowerCase(Locale.ROOT),() -> chooseDate(e.getText().toString(),e::setText));
        }
        void select(String key, String label, List<String> ids, List<String> labels, String selected) {
            TextView l = new TextView(MainActivity.this); l.setText(label); fields.addView(l);
            Spinner spinner = new Spinner(MainActivity.this);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this,android.R.layout.simple_spinner_dropdown_item,labels);
            spinner.setAdapter(adapter); spinner.setSelection(Math.max(0,ids.indexOf(selected)));
            fields.addView(spinner); spinners.put(key,spinner); values.put(key,ids);
        }
        String value(String key) {
            if (inputs.containsKey(key)) return inputs.get(key).getText().toString();
            return values.get(key).get(spinners.get(key).getSelectedItemPosition());
        }
        void action(String label, Runnable action) {
            button(fields,label,() -> { try { action.run(); } catch (Exception e) { toast(e.getMessage() == null ? "Check the entered values" : e.getMessage()); } });
        }
        void show() { dialog.show(); dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE); }
        String required(String key) { String v = value(key).trim(); if (v.isEmpty()) throw new IllegalArgumentException("Please enter " + key.replace('_',' ')); return v; }
    }
    private JSONObject fresh(boolean workspace) {
        JSONObject b = Json.of("id",UUID.randomUUID().toString(),"created_by",repo.vault.account());
        if (workspace) Json.put(b,"workspace_id",repo.workspaceId()); return b;
    }
    private void owners(Form f, String selected) {
        List<String> ids = new ArrayList<>(), labels = new ArrayList<>();
        for (OfflineStore.Record r : repo.rows("profiles")) { ids.add(r.id); labels.add(repo.name(r.id)); }
        if (!ids.contains(repo.vault.account())) { ids.add(repo.vault.account()); labels.add("You"); }
        if (!selected.isEmpty() && !ids.contains(selected)) { ids.add(selected); labels.add(repo.name(selected)); }
        f.select("owner_user_id","Owner",ids,labels,selected);
    }
    private void task(OfflineStore.Record row, String project, String phase, String owner, String selected) {
        JSONObject initial = row == null ? Json.merge(fresh(true),Json.of("title","","description","","owner_user_id",owner,"original_date",selected,"priority","normal","project_id",project.isEmpty()?null:project,"project_node_id",phase.isEmpty()?null:phase)) : Json.copy(row.body);
        Form f = new Form(row == null ? "New task" : "Task notebook");
        f.input("title","Task",Json.text(initial,"title"),false); f.input("description","Description",Json.text(initial,"description"),true);
        owners(f,Json.text(initial,"owner_user_id")); f.date("original_date","Date",Json.text(initial,"original_date"));
        f.input("deadline","Time (HH:mm, optional)",Json.text(initial,"deadline"),false);
        f.select("priority","Priority",Arrays.asList("low","normal","high"),Arrays.asList("Low","Normal","High"),Json.text(initial,"priority"));
        List<String> ids = new ArrayList<>(Collections.singletonList("")), labels = new ArrayList<>(Collections.singletonList("No project"));
        for (OfflineStore.Record p : repo.rows("projects")) if (Json.text(p.body,"deleted_at").isEmpty() || p.id.equals(Json.text(initial,"project_id"))) { ids.add(p.id); labels.add(Json.text(p.body,"name")); }
        f.select("project_id","Project",ids,labels,Json.text(initial,"project_id"));
        List<String> phases = new ArrayList<>(Collections.singletonList("")), names = new ArrayList<>(Collections.singletonList("No phase"));
        for (OfflineStore.Record p : repo.rows("project_nodes")) { phases.add(p.id); names.add(Json.text(p.body,"title")); }
        f.select("project_node_id","Phase",phases,names,Json.text(initial,"project_node_id"));
        f.input("completion_note","Completion notes",Json.text(initial,"completion_note"),true);
        if (!Json.text(initial,"recurrence_type").isEmpty() && !Json.text(initial,"recurrence_type").equals("none")) {
            TextView note = new TextView(this); note.setText("Recurring task · new occurrences are currently generated by the web app."); f.fields.addView(note);
        }
        f.action("Save on device", () -> save("tasks",row,taskValues(f,initial),f));
        if (row != null && repo.vault.account().equals(Json.text(initial,"owner_user_id"))) {
            boolean complete = !Json.text(initial,"completed_at").isEmpty();
            f.action(complete ? "Reopen task" : "Mark completed", () -> {
                JSONObject desired = taskValues(f,initial);
                if (!repo.vault.account().equals(Json.text(desired,"owner_user_id"))) throw new IllegalArgumentException("Save the new owner before changing completion.");
                if (!complete && Json.text(desired,"original_date").compareTo(repo.today()) > 0) throw new IllegalArgumentException("A future task cannot be completed yet.");
                Json.put(desired,"completed_at",complete ? null : Json.now()); save("tasks",row,desired,f);
            });
            f.action("Archive task", () -> confirm("Archive this task?", () -> save("tasks",row,Json.merge(taskValues(f,initial),Json.of("deleted_at",Json.now())),f)));
        }
        if (row != null) {
            TextView heading = new TextView(this); heading.setText("Notebook messages"); f.fields.addView(heading);
            for (OfflineStore.Record m : repo.rows("task_messages")) if (row.id.equals(Json.text(m.body,"task_id")) && Json.text(m.body,"deleted_at").isEmpty()) {
                TextView message = new TextView(this); message.setText(repo.name(Json.text(m.body,"author_id")) + " · " + Json.text(m.body,"body") + pending(m));
                message.setPadding(0,dp(8),0,dp(8)); f.fields.addView(message);
            }
            f.action("+ Add notebook message", () -> message(row.id));
        }
        f.show();
    }
    private JSONObject taskValues(Form f, JSONObject initial) {
        JSONObject b = Json.copy(initial); Json.put(b,"title",f.required("title"));
        for (String k : new String[]{"description","completion_note","owner_user_id","priority"}) Json.put(b,k,f.value(k));
        Json.put(b,"original_date",BoardRules.validDate(f.required("original_date")));
        String time = f.value("deadline").trim(); Json.put(b,"deadline",time.isEmpty()?null:LocalTime.parse(time).toString());
        String project = f.value("project_id"), phase = f.value("project_node_id");
        if (!phase.isEmpty()) {
            OfflineStore.Record p = repo.store().get("project_nodes",phase);
            if (p == null || !project.equals(Json.text(p.body,"project_id"))) throw new IllegalArgumentException("Choose a phase belonging to the selected project, or No phase.");
        }
        Json.put(b,"project_id",project.isEmpty()?null:project); Json.put(b,"project_node_id",phase.isEmpty()?null:phase); return b;
    }
    private void reminder(OfflineStore.Record row, String owner, String date) {
        JSONObject b = row == null ? fresh(true) : Json.copy(row.body); Form f = new Form(row == null ? "New reminder" : "Reminder");
        f.input("title","Title",Json.text(b,"title"),false); f.input("note","Note",Json.text(b,"note"),true);
        owners(f,row == null ? owner : Json.text(b,"owner_user_id"));
        f.date("reminder_date","Date",row == null ? date : Json.text(b,"reminder_date"));
        f.action("Save on device",() -> { JSONObject desired = Json.merge(b,Json.of("title",f.required("title"),"note",f.value("note"),"owner_user_id",f.value("owner_user_id"),"reminder_date",BoardRules.validDate(f.required("reminder_date")))); save("reminders",row,desired,f); });
        if (row != null) f.action("Delete reminder",() -> confirm("Delete this reminder?",() -> repo.deleteReminder(row.id,error -> runOnUiThread(() -> { if (error.isEmpty()) { f.dialog.dismiss(); render(); } else toast(error); }))));
        f.show();
    }
    private void editProject(OfflineStore.Record row) {
        JSONObject b = row == null ? Json.merge(fresh(true),Json.of("preferred_view","folders","is_joint",true)) : Json.copy(row.body);
        Form f = new Form(row == null ? "New project" : "Edit project");
        f.input("name","Name",Json.text(b,"name"),false); f.input("description","Description",Json.text(b,"description"),true);
        owners(f,row == null ? repo.vault.account() : Json.text(b,"owner_user_id"));
        f.date("target_date","Target date (optional)",Json.text(b,"target_date"));
        f.select("is_joint","Ownership",Arrays.asList("true","false"),Arrays.asList("Shared project","Personal project"),String.valueOf(b.optBoolean("is_joint",true)));
        f.action("Save on device",() -> {
            String target = f.value("target_date").trim();
            save("projects",row,Json.merge(b,Json.of("name",f.required("name"),"description",f.value("description"),"owner_user_id",f.value("owner_user_id"),"is_joint",Boolean.parseBoolean(f.value("is_joint")),"target_date",target.isEmpty()?null:BoardRules.validDate(target))),f);
        });
        if (row != null) f.action("Archive project",() -> confirm("Archive this project?",() -> save("projects",row,Json.merge(b,Json.of("deleted_at",Json.now())),f)));
        f.show();
    }
    private void phase(OfflineStore.Record row, String project) {
        JSONObject b = row == null ? Json.merge(fresh(false),Json.of("project_id",project,"position",repo.rows("project_nodes").size(),"weight",1,"parent_node_id",null)) : Json.copy(row.body);
        Form f = new Form(row == null ? "New phase" : "Edit phase");
        f.input("title","Title",Json.text(b,"title"),false); f.input("description","Description",Json.text(b,"description"),true);
        f.action("Save on device",() -> save("project_nodes",row,Json.merge(b,Json.of("title",f.required("title"),"description",f.value("description"))),f)); f.show();
    }
    private void message(String taskId) {
        Form f = new Form("Notebook message"); f.input("body","Message","",true);
        f.action("Save on device",() -> save("task_messages",null,Json.of("id",UUID.randomUUID().toString(),"task_id",taskId,"author_id",repo.vault.account(),"body",f.required("body")),f)); f.show();
    }
    private void settings() {
        OfflineStore.Record row = repo.store().get("workspaces",repo.workspaceId()); if (row == null) return;
        Form f = new Form("Workspace settings"); f.input("name","Name",Json.text(row.body,"name"),false);
        f.input("timezone","Timezone",repo.timezone(),false); f.input("rollover_hour","Day starts at hour (0–23)",String.valueOf(repo.rollover()),false);
        f.action("Save on device",() -> {
            String zone = ZoneId.of(f.required("timezone")).getId(); int hour = Integer.parseInt(f.required("rollover_hour"));
            if (hour < 0 || hour > 23) throw new IllegalArgumentException("Hour must be between 0 and 23.");
            save("workspaces",row,Json.merge(row.body,Json.of("name",f.required("name"),"timezone",zone,"rollover_hour",hour)),f);
        }); f.show();
    }
    private void login() {
        Form f = new Form("Connect TBFT"); EditText email = f.input("email","Email","",false);
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password = f.input("password","Password","",false); password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        f.action("Sign in",() -> {
            String user = f.required("email"), pass = f.required("password"); toast("Connecting…");
            repo.signIn(user,pass,error -> runOnUiThread(() -> { if (error.isEmpty()) { password.setText(""); f.dialog.dismiss(); render(); } else toast(error); }));
        }); f.show();
    }
    private void save(String table, OfflineStore.Record original, JSONObject desired, Form f) {
        repo.edit(table,original == null ? null : original.body,desired,error -> runOnUiThread(() -> {
            if (error.isEmpty()) { if (f != null) f.dialog.dismiss(); toast("Saved on device"); render(); }
            else toast(error);
        }));
    }
    private void queue() {
        if (repo.store() == null) { login(); return; }
        Form f = new Form("Sync & pending changes");
        TextView status = new TextView(this); status.setText(repo.status()); f.fields.addView(status);
        for (OfflineStore.Record r : repo.store().pending()) {
            TextView label = new TextView(this); label.setText(r.table.replace('_',' ') + " · " + Json.text(r.body,r.table.equals("projects")?"name":"title") + "\n" + (r.error.isEmpty()?"Waiting to sync":r.error));
            label.setPadding(0,dp(12),0,dp(6)); f.fields.addView(label);
            if (!r.error.isEmpty()) {
                f.action("Compare and resolve",() -> review(r));
                f.action("Inspect preserved local copy",() -> new AlertDialog.Builder(this).setTitle("Local copy").setMessage(r.body.toString()).setPositiveButton("Close",null).show());
                f.action("Retry after review",() -> { repo.io.execute(() -> { repo.store().retry(r); repo.changed(); repo.requestSync(); }); f.dialog.dismiss(); });
            }
        }
        f.action("Sync now",() -> { repo.requestSync(); f.dialog.dismiss(); });
        f.action("Export backup of local work",this::backup); f.show();
    }
    private void backup() {
        if (repo.store() == null) return;
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"tbft-backup-" + repo.today() + ".json"),41);
    }
    private void importBackup() {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"),42);
    }
    private void review(OfflineStore.Record row) {
        toast("Loading the server copy…");
        repo.review(row,(remote,error) -> runOnUiThread(() -> {
            if (!error.isEmpty()) { toast(error); return; }
            Form f = new Form("Resolve conflict");
            TextView detail = new TextView(this);
            detail.setText("Saved on this phone:\n" + readable(row.body) + (row.removed ? "\nPending deletion" : "")
                    + "\n\nServer copy:\n" + (remote == null ? "Removed" : readable(remote)));
            f.fields.addView(detail);
            if (remote != null) f.action(row.removed ? "Keep my deletion" : "Keep my changes",() -> resolve(row,remote,true,f));
            f.action("Use server copy",() -> confirm("Replace the reviewed local changes with the server copy?",() -> resolve(row,remote,false,f)));
            f.action("Export before deciding",this::backup); f.show();
        }));
    }
    private String readable(JSONObject row) {
        if(row.has("state")) {
            StringBuilder wardrobe=new StringBuilder();
            wardrobe.append(WardrobeRules.total(row,"available")).append(" available · ").append(WardrobeRules.total(row,"in_use")).append(" in use · ").append(WardrobeRules.total(row,"laundry")).append(" in laundry\n");
            for(JSONObject g:WardrobeRules.list(WardrobeRules.state(row),"items"))wardrobe.append(Json.text(g,"name")).append(": ").append(WardrobeRules.count(g,"available")).append(" available, ").append(WardrobeRules.count(g,"in_use")).append(" in use, ").append(WardrobeRules.count(g,"laundry")).append(" laundry\n");
            return wardrobe.toString();
        }
        StringBuilder out = new StringBuilder();
        for (String key : Json.keys(row)) if (!key.equals("id") && !key.endsWith("_id") && !key.equals("created_at") && !key.equals("updated_at"))
            out.append(key.replace('_',' ')).append(": ").append(Json.text(row,key)).append("\n");
        return out.toString();
    }
    private void resolve(OfflineStore.Record row, JSONObject remote, boolean keepLocal, Form f) {
        repo.resolve(row,remote,keepLocal,error -> runOnUiThread(() -> { if (error.isEmpty()) { f.dialog.dismiss(); render(); toast("Decision saved on device"); } else toast(error); }));
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if(request==42 && result==RESULT_OK && data!=null && data.getData()!=null){
            Uri uri=data.getData();
            repo.io.execute(()->{
                try(java.io.InputStream in=getContentResolver().openInputStream(uri)){
                    if(in==null)throw new java.io.IOException("Cannot read this file");
                    java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
                    while((n=in.read(buffer))!=-1){if(out.size()+n>20000000)throw new java.io.IOException("Backup is too large");out.write(buffer,0,n);}
                    JSONObject backup=Json.object(out.toString("UTF-8"));int count=repo.store().importBackup(backup,repo.workspaceId(),repo.vault.account());
                    repo.changed();runOnUiThread(()->{toast(count+" pending changes restored. Review Sync before uploading.");render();});
                }catch(Exception e){runOnUiThread(()->toast("Backup was not imported: "+e.getMessage()));}
            });
        }
        if (request == 41 && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData(); repo.io.execute(() -> {
                try (java.io.OutputStream out = getContentResolver().openOutputStream(uri,"wt")) {
                    if (out == null) throw new java.io.IOException();
                    out.write(repo.store().export().toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    runOnUiThread(() -> toast("Backup exported"));
                } catch (Exception e) { runOnUiThread(() -> toast("Backup could not be exported. Local data is unchanged.")); }
            });
        }
    }
    private void confirm(String prompt, Runnable action) { new AlertDialog.Builder(this).setMessage(prompt).setNegativeButton("Cancel",null).setPositiveButton("Confirm",(d,w) -> { try { action.run(); } catch (Exception e) { toast(e.getMessage()); } }).show(); }
    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }
}
