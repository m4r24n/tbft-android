package info.marzan.tbft;

import android.app.*;
import android.graphics.Color;
import android.text.*;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.function.Consumer;

/** One focused collection at a time. All actions go through the local transaction. */
final class WardrobeScreen {
    private final Activity activity;
    private final TbftRepository repo;
    private final Runnable refresh;
    private String section="available", category="all", use="all", search="";
    private LinearLayout results;
    WardrobeScreen(Activity a,TbftRepository r,Runnable refresh) { activity=a;repo=r;this.refresh=refresh; }
    private int dp(int n) { return Ui.dp(activity,n); }
    void render(LinearLayout body) {
        JSONObject doc=repo.wardrobe();
        Ui.text(body,"YOUR EVERYDAY COLLECTION",11,Ui.ACCENT).setLetterSpacing(.13f);
        Ui.heading(body,"Wardrobe",30); Ui.text(body,"Less searching. More ready to wear.",14,Ui.MUTED);
        LinearLayout actions=new LinearLayout(activity);
        Ui.button(actions,"+ Add clothes",()->edit(null)); Ui.button(actions,"Settings",this::settings); body.addView(actions);
        LinearLayout tabs=Ui.chips(body);
        String[][] sections={{"available","Available"},{"in_use","In Use"},{"laundry","Laundry"},{"outfits","Outfits"}};
        for(String[] s:sections) {
            Button b=Ui.button(tabs,s[1]+(s[0].equals("outfits")?"":" · "+WardrobeRules.total(doc,s[0])),()->{section=s[0];refresh.run();});
            b.setTextSize(12);b.setPadding(dp(8),dp(6),dp(8),dp(6));Ui.selected(b,section.equals(s[0]));
        }
        LinearLayout uses=Ui.chips(body);
        for(String[] f:new String[][]{{"all","All clothes"},{"home","Home"},{"outdoor","Outdoor"}})
            Ui.selected(Ui.button(uses,f[1],()->{use=f[0];refresh.run();}),use.equals(f[0]));
        if(section.equals("outfits")) { outfits(body,doc); return; }
        if(section.equals("laundry")) {
            LinearLayout info=Ui.card(body); JSONObject batch=WardrobeRules.activeBatch(doc);
            int threshold=WardrobeRules.state(doc).optInt("threshold",8);
            Ui.heading(info,batch==null?"Your laundry basket":"Laundry is on your board",17);
            Ui.text(info,batch==null?(threshold==0?"Automatic tasks are off.":"A board task is added at "+threshold+" pieces."):"Complete Do laundry on your board, or finish the basket here.",13,Ui.MUTED);
            if(WardrobeRules.total(doc,"laundry")>0) Ui.button(info,"Laundry finished · return clothes",()->new AlertDialog.Builder(activity)
                .setTitle("Are these clothes clean and ready?").setMessage("This basket will move back to Available and its laundry task will be completed.")
                .setNegativeButton("Not yet",null).setPositiveButton("Finished",(d,w)->change(v->WardrobeRules.finishBasket(v,repo.today()),null)).show());
        } else if(section.equals("in_use")) Ui.text(body,"What you are wearing or have taken out. Return clean items, or move them to Laundry.",13,Ui.MUTED);
        LinearLayout categories=Ui.chips(body);
        Ui.selected(Ui.button(categories,"All categories",()->{category="all";refresh.run();}),category.equals("all"));
        for(JSONObject c:WardrobeRules.list(WardrobeRules.state(doc),"categories")) {
            String id=Json.text(c,"id"); Ui.selected(Ui.button(categories,Json.text(c,"name"),()->{category=id;refresh.run();}),category.equals(id));
        }
        EditText find=new EditText(activity); find.setSingleLine(true); find.setTextSize(15); find.setHint("Find clothes"); find.setText(search);
        find.setContentDescription("Find clothes"); body.addView(find);
        results=Ui.column(activity); body.addView(results); collection(doc);
        find.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int start,int count,int after){} public void onTextChanged(CharSequence s,int start,int before,int count){search=s.toString();collection(repo.wardrobe());} public void afterTextChanged(Editable e){} });
    }
    private boolean fits(JSONObject item) { return use.equals("all")||use.equals(Json.text(item,"use"))||"both".equals(Json.text(item,"use")); }
    private void collection(JSONObject doc) {
        results.removeAllViews(); LinearLayout row=null; int found=0;
        for(JSONObject item:WardrobeRules.list(WardrobeRules.state(doc),"items")) {
            int count=WardrobeRules.count(item,section);
            if(count==0||!fits(item)||(!category.equals("all")&&!category.equals(Json.text(item,"category")))||
                !(Json.text(item,"name")+" "+Json.text(item,"color_name")).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) continue;
            if(found%2==0) {row=new LinearLayout(activity);row.setBaselineAligned(false);results.addView(row);}
            LinearLayout tile=Ui.column(activity); tile.setPadding(dp(12),dp(10),dp(12),dp(14));tile.setBackground(Ui.shape(activity,Ui.CARD,18));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),dp(5),dp(3),dp(5));row.addView(tile,lp);
            GarmentView icon=new GarmentView(activity,WardrobeRules.kind(doc,item),Json.text(item,"color"),Json.text(item,"color_name")+" "+WardrobeRules.categoryName(doc,Json.text(item,"category")));
            tile.addView(icon,new LinearLayout.LayoutParams(-1,dp(108)));
            Ui.text(tile,WardrobeRules.categoryName(doc,Json.text(item,"category")).toUpperCase(Locale.ROOT),10,Ui.MUTED);
            Ui.heading(tile,Json.text(item,"name"),16); Ui.text(tile,"×"+count+"  ·  "+label(Json.text(item,"use")),12,Ui.ACCENT);
            String id=Json.text(item,"id"); tile.setOnClickListener(v->details(id)); tile.setFocusable(true);tile.setContentDescription(Json.text(item,"name")+", "+count+" "+section.replace('_',' '));
            found++;
        }
        if(found%2==1) row.addView(new android.view.View(activity),new LinearLayout.LayoutParams(0,1,1));
        if(found==0) {LinearLayout empty=Ui.card(results);Ui.heading(empty,section.equals("laundry")?"A fresh start":section.equals("in_use")?"Everything is put away":"Nothing here yet",18);
            Ui.text(empty,section.equals("available")?"Add clothes, or try another category or filter.":"Clothes you move here appear together, with accurate counts everywhere.",14,Ui.MUTED);}
    }
    private static String label(String value) {return value.equals("home")?"Home":value.equals("outdoor")?"Outdoor":"Home & outdoor";}
    private void details(String id) {
        JSONObject doc=repo.wardrobe(),item;try{item=WardrobeRules.item(doc,id);}catch(Exception e){toast(e.getMessage());return;}
        Panel p=new Panel(Json.text(item,"name"));
        p.fields.addView(new GarmentView(activity,WardrobeRules.kind(doc,item),Json.text(item,"color"),Json.text(item,"color_name")),new LinearLayout.LayoutParams(-1,dp(112)));
        Ui.text(p.fields,WardrobeRules.count(item,"available")+" available  ·  "+WardrobeRules.count(item,"in_use")+" in use  ·  "+WardrobeRules.count(item,"laundry")+" in laundry",14,Ui.MUTED);
        if(!Json.text(item,"note").isEmpty()) Ui.text(p.fields,Json.text(item,"note"),14,Ui.MUTED);
        if(WardrobeRules.count(item,"available")>0) {moveAction(p,item,"available","in_use","Wear / take out");moveAction(p,item,"available","laundry","Send to laundry");}
        if(WardrobeRules.count(item,"in_use")>0) {moveAction(p,item,"in_use","available","Return clean clothes");moveAction(p,item,"in_use","laundry","In Use → Laundry");}
        if(WardrobeRules.count(item,"laundry")>0) moveAction(p,item,"laundry","available","Remove from basket");
        p.action("Edit details & quantity",()->{p.dialog.dismiss();edit(id);}); p.show();
    }
    private void moveAction(Panel p,JSONObject item,String from,String to,String label) {
        p.action(label,()->{int max=WardrobeRules.count(item,from);String id=Json.text(item,"id");
            if(max==1) change(doc->WardrobeRules.move(doc,id,from,to,1),p.dialog);
            else {String[] choices=new String[max];for(int i=0;i<max;i++)choices[i]=(i+1)+" piece"+(i==0?"":"s");
                new AlertDialog.Builder(activity).setTitle(label+" · how many?").setItems(choices,(d,w)->change(doc->WardrobeRules.move(doc,id,from,to,w+1),p.dialog)).show();}
        });
    }
    private void edit(String id) {
        JSONObject doc=repo.wardrobe(),item=id==null?new JSONObject():WardrobeRules.item(doc,id);Panel p=new Panel(id==null?"Add clothes":"Edit clothes");
        EditText name=p.input("Name",Json.text(item,"name"));
        List<String> categoryIds=new ArrayList<>(),names=new ArrayList<>(); for(JSONObject c:WardrobeRules.list(WardrobeRules.state(doc),"categories")){categoryIds.add(Json.text(c,"id"));names.add(Json.text(c,"name"));}
        Spinner categories=p.select("Category",names,Math.max(0,categoryIds.indexOf(Json.text(item,"category"))));
        EditText colour=p.input("Colour · hex",item.optString("color","#FFFFFF")),colourName=p.input("Colour name",item.optString("color_name","White"));
        LinearLayout palette=Ui.chips(p.fields);String[][] colours={{"White","#FFFFFF"},{"Black","#242525"},{"Grey","#91958F"},{"Navy","#263C58"},{"Blue","#658AB6"},{"Green","#617D61"},{"Beige","#CCBA97"},{"Brown","#775C46"},{"Red","#A94F4F"},{"Pink","#DFA4AF"},{"Yellow","#D7B75D"}};
        for(String[] c:colours){Button b=Ui.button(palette,"●",()->{colour.setText(c[1]);colourName.setText(c[0]);});b.setTextColor(Color.parseColor(c[1]));b.setContentDescription(c[0]);b.setBackground(Ui.shape(activity,Ui.LINE,24));}
        List<String> uses=Arrays.asList("home","outdoor","both"); Spinner purpose=p.select("Wear at",Arrays.asList("Home","Outdoor","Home & outdoor"),Math.max(0,uses.indexOf(item.optString("use","both"))));
        EditText quantity=p.input("Total quantity",id==null?"1":String.valueOf(WardrobeRules.count(item,"all")));quantity.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText note=p.input("Care notes · optional",Json.text(item,"note"));
        p.action("Save clothes",()->{int n=Integer.parseInt(quantity.getText().toString());String cat=categoryIds.get(categories.getSelectedItemPosition()),u=uses.get(purpose.getSelectedItemPosition());
            change(v->WardrobeRules.saveItem(v,id==null?"":id,name.getText().toString(),cat,colour.getText().toString().trim(),colourName.getText().toString(),u,note.getText().toString(),n),p.dialog);});
        if(id!=null) p.action("Remove item",()->new AlertDialog.Builder(activity).setMessage("Remove this item from your wardrobe?").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->change(v->WardrobeRules.removeItem(v,id),p.dialog)).show());
        p.show();
    }
    private void settings() {
        JSONObject doc=repo.wardrobe();Panel p=new Panel("Wardrobe settings");
        EditText threshold=p.input("Create laundry task at this many pieces · 0 = off",String.valueOf(WardrobeRules.state(doc).optInt("threshold",8)));threshold.setInputType(InputType.TYPE_CLASS_NUMBER);
        p.action("Save laundry setting",()->{int n=Integer.parseInt(threshold.getText().toString());if(n<0||n>1000)throw new IllegalArgumentException("Choose 0–1,000 pieces.");change(v->Json.put(WardrobeRules.state(v),"threshold",n),p.dialog);});
        p.action("+ Add category",()->{Panel c=new Panel("New category");EditText name=c.input("Category name","");
            List<String> kinds=Arrays.asList("top","shirt","bottom","shorts","layer","one_piece","shoes","other");
            Spinner shape=c.select("Clothing shape",Arrays.asList("T-shirt / top","Shirt","Pants / jeans","Shorts","Jacket / layer","Dress / one piece","Shoes","Other"),0);
            c.action("Add category",()->change(v->WardrobeRules.category(v,name.getText().toString(),kinds.get(shape.getSelectedItemPosition())),c.dialog));c.show();});
        Ui.text(p.fields,"Your wardrobe is personal. Changes are saved on this phone first and sync with your account when connected.",13,Ui.MUTED);p.show();
    }
    private void outfits(LinearLayout body,JSONObject doc) {
        Ui.heading(body,"Ready-to-wear combinations",20);Ui.text(body,"Available pieces, paired by type and colour. Choose what feels right for the day.",13,Ui.MUTED);
        List<List<String>> suggestions=WardrobeRules.suggestions(doc,use);int n=0;for(List<String> ids:suggestions) outfitCard(body,doc,"Combination "+(++n),ids,null);
        if(n==0) Ui.text(body,"Add an available top and bottom, or a one-piece outfit, to see combinations.",14,Ui.MUTED);
        Ui.heading(body,"Saved outfits",20);
        for(JSONObject outfit:WardrobeRules.list(WardrobeRules.state(doc),"outfits")) outfitCard(body,doc,Json.text(outfit,"name"),WardrobeRules.outfitIds(outfit),Json.text(outfit,"id"));
    }
    private void outfitCard(LinearLayout body,JSONObject doc,String name,List<String> ids,String savedId) {
        LinearLayout c=Ui.card(body);Ui.heading(c,name,17); LinearLayout icons=new LinearLayout(activity);c.addView(icons);List<String> names=new ArrayList<>();
        for(String id:ids) try{JSONObject item=WardrobeRules.item(doc,id);names.add(Json.text(item,"name"));icons.addView(new GarmentView(activity,WardrobeRules.kind(doc,item),Json.text(item,"color"),Json.text(item,"name")),new LinearLayout.LayoutParams(0,dp(92),1));}catch(Exception ignored){names.add("Removed item");}
        Ui.text(c,android.text.TextUtils.join(" + ",names),13,Ui.MUTED);boolean available=WardrobeRules.available(doc,ids);
        Button wear=Ui.button(c,available?"Wear this outfit":"Some pieces are unavailable",()->change(v->WardrobeRules.wear(v,ids),null));wear.setEnabled(available);wear.setAlpha(available?1:.5f);
        if(savedId==null) Ui.button(c,"Save outfit",()->{Panel p=new Panel("Save outfit");EditText title=p.input("Outfit name","");p.action("Save",()->change(v->WardrobeRules.saveOutfit(v,title.getText().toString(),ids),p.dialog));p.show();});
        else Ui.button(c,"Remove saved outfit",()->change(v->{JSONArray a=WardrobeRules.state(v).optJSONArray("outfits");for(int i=0;i<a.length();i++)if(savedId.equals(Json.text(a.optJSONObject(i),"id"))){a.remove(i);break;}},null));
    }
    private void change(Consumer<JSONObject> action,AlertDialog dialog) {repo.wardrobe(action,error->activity.runOnUiThread(()->{if(error.isEmpty()){if(dialog!=null)dialog.dismiss();refresh.run();toast("Saved on device");}else toast(error);}));}
    private void toast(String s){Toast.makeText(activity,s,Toast.LENGTH_LONG).show();}
    private final class Panel {
        final LinearLayout fields=Ui.column(activity);final AlertDialog dialog;
        Panel(String title){fields.setPadding(dp(20),dp(8),dp(20),dp(20));ScrollView scroll=new ScrollView(activity);scroll.addView(fields);dialog=new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setNegativeButton("Close",null).create();}
        EditText input(String label,String value){Ui.text(fields,label,13,Ui.MUTED);EditText e=new EditText(activity);e.setSingleLine(true);e.setTextSize(16);e.setText(value);e.setContentDescription(label);fields.addView(e);return e;}
        Spinner select(String label,List<String> values,int selected){Ui.text(fields,label,13,Ui.MUTED);Spinner s=new Spinner(activity);s.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,values));s.setSelection(selected);s.setMinimumHeight(dp(48));s.setContentDescription(label);fields.addView(s);return s;}
        void action(String label,Runnable action){Ui.button(fields,label,()->{try{action.run();}catch(Exception e){toast(e.getMessage()==null?"Check these values":e.getMessage());}});}
        void show(){dialog.show();}
    }
}
