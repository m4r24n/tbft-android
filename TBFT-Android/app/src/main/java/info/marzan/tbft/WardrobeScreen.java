package info.marzan.tbft;

import android.app.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.function.Consumer;

/** The cabinet, rails and basket are different views of one offline collection. */
final class WardrobeScreen {
    private final Activity activity;
    private final TbftRepository repo;
    private final Runnable refresh;
    private String section="available", category="all", use="all", search="";
    private String room="wardrobe";
    private int shelfPage,railX;
    private boolean doorsOpen;
    private LinearLayout results;
    private final LibraryScreen library;
    WardrobeScreen(Activity a,TbftRepository r,Runnable refresh) {
        activity=a;repo=r;this.refresh=refresh;doorsOpen=a.getPreferences(0).getBoolean("wardrobe_open",false);library=new LibraryScreen(a,r,refresh);
    }
    private int dp(int n) { return Ui.dp(activity,n); }
    String location(){return room+":"+(room.equals("library")?library.location():section+":"+category+":"+shelfPage);}
    void saveState(Bundle b){b.putString("life_room",room);b.putString("wardrobe_section",section);b.putString("wardrobe_category",category);b.putString("wardrobe_use",use);b.putString("wardrobe_search",search);b.putInt("wardrobe_page",shelfPage);b.putInt("wardrobe_rail",railX);library.saveState(b);}
    void restoreState(Bundle b){room=b.getString("life_room","wardrobe");section=b.getString("wardrobe_section","available");category=b.getString("wardrobe_category","all");use=b.getString("wardrobe_use","all");search=b.getString("wardrobe_search","");shelfPage=b.getInt("wardrobe_page",0);railX=b.getInt("wardrobe_rail",0);library.restoreState(b);}
    boolean back(){if(room.equals("library"))return library.back();if(!category.equals("all")){category="all";search="";railX=0;refresh.run();return true;}if(!section.equals("available")){navigate("available");return true;}return false;}
    private void navigate(String value){section=value;category="all";search="";railX=0;refresh.run();}
    private void doors(boolean value){doorsOpen=value;activity.getPreferences(0).edit().putBoolean("wardrobe_open",value).apply();refresh.run();}
    void render(LinearLayout body) {
        LinearLayout roomTabs=new LinearLayout(activity);body.addView(roomTabs);
        Button wardrobe=Ui.button(roomTabs,"Wardrobe",()->{room="wardrobe";refresh.run();});wardrobe.setLayoutParams(new LinearLayout.LayoutParams(0,dp(50),1));Ui.selected(wardrobe,room.equals("wardrobe"));
        Button books=Ui.button(roomTabs,"Library",()->{room="library";refresh.run();});books.setLayoutParams(new LinearLayout.LayoutParams(0,dp(50),1));Ui.selected(books,room.equals("library"));
        if(room.equals("library")){library.render(body);return;}
        JSONObject doc=repo.wardrobe();
        LinearLayout title=new LinearLayout(activity);title.setGravity(Gravity.CENTER_VERTICAL);body.addView(title);
        TextView heading=Ui.heading(title,"Wardrobe",29);heading.setTypeface(Typeface.create("serif",Typeface.NORMAL));heading.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        Ui.button(title,"+ Add",()->edit(null));Ui.button(title,"Shelves",this::shelves).setTextSize(12);
        LinearLayout tabs=Ui.chips(body);
        String[][] sections={{"available","Closet"},{"in_use","In Use"},{"laundry","Laundry"},{"outfits","Outfits"}};
        for(String[] s:sections) {
            Button b=Ui.button(tabs,s[1]+(s[0].equals("outfits")?"":" · "+WardrobeRules.total(doc,s[0])),()->navigate(s[0]));
            b.setTextSize(12);b.setPadding(dp(9),dp(6),dp(9),dp(6));Ui.selected(b,section.equals(s[0]));
        }
        if(section.equals("available")&&category.equals("all")){cabinet(body,doc);return;}
        if(section.equals("outfits")){uses(body);outfits(body,doc);return;}
        if(section.equals("laundry")) {
            basket(body,doc,false);
            JSONObject batch=WardrobeRules.activeBatch(doc);
            if(batch!=null)Ui.text(body,"Do laundry is on your daily board. Completing it returns this load to the wardrobe.",13,Ui.MUTED);
            if(WardrobeRules.total(doc,"laundry")>0) Ui.button(body,"Laundry finished · return clothes",()->new AlertDialog.Builder(activity)
                .setTitle("Are these clothes clean and ready?").setMessage("This basket will move back to Available and its laundry task will be completed.")
                .setNegativeButton("Not yet",null).setPositiveButton("Finished",(d,w)->change(v->WardrobeRules.finishBasket(v,repo.today()),null)).show());
        } else if(section.equals("in_use")) {
            Ui.heading(body,"Out of the wardrobe",22);Ui.text(body,"What you are wearing or have taken out. Tap a piece to return it or put it in the basket.",13,Ui.MUTED);
        } else {
            Ui.button(body,"‹ All compartments",()->{category="all";search="";railX=0;refresh.run();});
            Ui.heading(body,WardrobeRules.categoryName(doc,category),23);
            Ui.text(body,"Slide along the rail. Tap a garment to take it out.",13,Ui.MUTED);
        }
        uses(body);
        EditText find=new EditText(activity);find.setSingleLine(true);find.setTextSize(14);find.setHint("Find clothes in this section");find.setText(search);find.setContentDescription("Find clothes");body.addView(find);
        results=Ui.column(activity);body.addView(results);collection(doc);
        find.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){search=s.toString();railX=0;collection(repo.wardrobe());}public void afterTextChanged(Editable e){}});
        if(section.equals("available")){Ui.button(body,"+ Add to "+WardrobeRules.categoryName(doc,category),()->edit(null));basket(body,doc,true);}
    }
    private void uses(LinearLayout body){
        LinearLayout uses=Ui.chips(body);
        for(String[] f:new String[][]{{"all","All clothes"},{"home","Home"},{"outdoor","Outdoor"}})
            Ui.selected(Ui.button(uses,f[1],()->{use=f[0];railX=0;refresh.run();}),use.equals(f[0]));
    }
    private void cabinet(LinearLayout body,JSONObject doc){
        if(doorsOpen){uses(body);Ui.text(body,"Swipe sideways through the wardrobe, then open a compartment.",13,Ui.MUTED);}
        else Ui.text(body,WardrobeRules.total(doc,"available")+" ready to wear · tucked away, together.",13,Ui.MUTED);
        List<JSONObject> all=WardrobeRules.list(WardrobeRules.state(doc),"categories");int pages=Math.max(1,(all.size()+5)/6),pageWidth=activity.getResources().getDisplayMetrics().widthPixels-dp(28);
        if(!doorsOpen){WardrobeCabinet cabinet=new WardrobeCabinet(activity,doc,all.subList(0,Math.min(all.size(),6)),use,false,id->{category=id;search="";railX=0;refresh.run();},()->doors(true));body.addView(cabinet,new LinearLayout.LayoutParams(-1,dp(365)));basket(body,doc,true);return;}
        HorizontalScrollView scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);scroll.setFillViewport(true);scroll.setContentDescription("Wardrobe compartments. Swipe horizontally to browse.");
        LinearLayout pagesRow=new LinearLayout(activity);scroll.addView(pagesRow);body.addView(scroll,new LinearLayout.LayoutParams(-1,dp(365)));
        for(int page=0;page<pages;page++){int start=page*6;WardrobeCabinet cabinet=new WardrobeCabinet(activity,doc,all.subList(start,Math.min(all.size(),start+6)),use,true,id->{category=id;search="";railX=0;refresh.run();},()->{});pagesRow.addView(cabinet,new LinearLayout.LayoutParams(pageWidth,dp(365)));}
        TextView indicator=Ui.text(body,"Swipe compartments  ·  1 / "+pages,12,Ui.MUTED);indicator.setGravity(Gravity.CENTER);
        int saved=Math.min(shelfPage,Math.max(0,(pages-1)*pageWidth));scroll.post(()->scroll.scrollTo(saved,0));
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{shelfPage=x;int page=Math.min(pages-1,Math.max(0,Math.round((float)x/pageWidth)));indicator.setText("Swipe compartments  ·  "+(page+1)+" / "+pages);});
        Ui.button(body,"Close wardrobe doors",()->doors(false)).setTextSize(12);
        basket(body,doc,true);
    }
    private void basket(LinearLayout body,JSONObject doc,boolean clickable){
        LinearLayout card=Ui.card(body);card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(9),dp(6),dp(12),dp(6));
        int count=WardrobeRules.total(doc,"laundry"),threshold=WardrobeRules.state(doc).optInt("threshold",8);
        card.addView(new WardrobeCabinet.Basket(activity,doc),new LinearLayout.LayoutParams(dp(100),dp(94)));
        LinearLayout caption=Ui.column(activity);card.addView(caption,new LinearLayout.LayoutParams(0,-2,1));
        Ui.heading(caption,"Laundry basket"+(clickable?"  ›":""),18);Ui.text(caption,count+" piece"+(count==1?"":"s")+" in the basket",13,Ui.MUTED);
        String progress=WardrobeRules.activeBatch(doc)!=null?"Laundry task is on your board":threshold==0?"Automatic task is off":Math.max(0,threshold-count)+" more until laundry day";
        Ui.text(caption,progress,12,Ui.ACCENT);
        if(clickable){card.setContentDescription("Open laundry basket, "+count+" pieces");card.setFocusable(true);card.setOnClickListener(v->navigate("laundry"));}
    }
    private boolean fits(JSONObject item){return WardrobeCabinet.matches(item,use);}
    private void collection(JSONObject doc){
        results.removeAllViews();List<JSONObject> clothes=new ArrayList<>();
        for(JSONObject item:WardrobeRules.list(WardrobeRules.state(doc),"items"))if(WardrobeRules.count(item,section)>0&&fits(item)
            &&(category.equals("all")||category.equals(Json.text(item,"category")))
            &&(Json.text(item,"name")+" "+Json.text(item,"color_name")).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))clothes.add(item);
        if(clothes.isEmpty()) {
            LinearLayout empty=Ui.card(results);Ui.heading(empty,section.equals("laundry")?"An empty basket":section.equals("in_use")?"Everything is put away":"An empty rail",20);
            Ui.text(empty,section.equals("available")?"Add something here, or try another filter. Clothes in use or in the basket return here when ready.":"Clothes you move here appear together, with their counts updated everywhere.",14,Ui.MUTED);return;
        }
        HorizontalScrollView scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(true);scroll.setFillViewport(true);
        scroll.setBackground(Ui.shape(activity,0xffe5dbca,12));scroll.setContentDescription("Clothes rail. Swipe horizontally to browse.");
        LinearLayout rail=new LinearLayout(activity);rail.setPadding(dp(8),0,dp(8),dp(10));scroll.addView(rail);results.addView(scroll);
        for(JSONObject item:clothes){
            LinearLayout tile=Ui.column(activity);tile.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);tile.setPadding(dp(5),0,dp(5),dp(6));
            rail.addView(tile,new LinearLayout.LayoutParams(dp(152),-2));
            View rod=new View(activity);rod.setBackgroundColor(WardrobeCabinet.EDGE);tile.addView(rod,new LinearLayout.LayoutParams(-1,dp(5)));
            tile.addView(new GarmentView(activity,doc,item,true),new LinearLayout.LayoutParams(-1,dp(187)));
            TextView name=Ui.heading(tile,Json.text(item,"name"),16);name.setGravity(Gravity.CENTER);name.setMaxLines(3);
            TextView count=Ui.text(tile,"×"+WardrobeRules.count(item,section)+"  ·  "+label(Json.text(item,"use")),12,Ui.ACCENT);count.setGravity(Gravity.CENTER);
            TextView color=Ui.text(tile,Json.text(item,"color_name")+" · "+appearanceLabel(doc,item),11,Ui.MUTED);color.setGravity(Gravity.CENTER);
            tile.setOnClickListener(v->details(Json.text(item,"id")));tile.setFocusable(true);tile.setContentDescription(Json.text(item,"name")+", "+WardrobeRules.count(item,section)+" "+section.replace('_',' ')+", "+appearanceLabel(doc,item));
            for(int n=0;n<tile.getChildCount();n++)tile.getChildAt(n).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        int position=railX;scroll.post(()->scroll.scrollTo(position,0));scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->railX=x);
        Ui.text(results,clothes.size()+" style"+(clothes.size()==1?"":"s")+" on this rail",12,Ui.MUTED);
    }
    private String appearanceLabel(JSONObject doc,JSONObject item){
        String shape=WardrobeRules.shape(doc,item);String description="";
        if(WardrobeRules.hasSleeves(shape))description=WardrobeRules.sleeve(doc,item).equals("long")?"Full sleeve":WardrobeRules.sleeve(doc,item).equals("short")?"Half sleeve":"Sleeveless";
        if(WardrobeRules.hood(doc,item))description+=(description.isEmpty()?"":" · ")+"Hooded";
        return description.isEmpty()?shape.substring(0,1).toUpperCase(Locale.ROOT)+shape.substring(1):description;
    }
    private static String label(String value) {return value.equals("home")?"Home":value.equals("outdoor")?"Outdoor":"Home & outdoor";}
    private void details(String id) {
        JSONObject doc=repo.wardrobe(),item;try{item=WardrobeRules.item(doc,id);}catch(Exception e){toast(e.getMessage());return;}
        Panel p=new Panel(Json.text(item,"name"));
        p.fields.addView(new GarmentView(activity,doc,item,true),new LinearLayout.LayoutParams(-1,dp(160)));
        Ui.text(p.fields,appearanceLabel(doc,item)+" · "+Json.text(item,"color"),13,Ui.MUTED);
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
        JSONObject doc=repo.wardrobe();
        if(WardrobeRules.list(WardrobeRules.state(doc),"categories").isEmpty()){toast("Create a shelf for your clothes first.");newShelf(null);return;}
        JSONObject item=id==null?Json.of("color","#FFFFFF"):WardrobeRules.item(doc,id);Panel p=new Panel(id==null?"Add clothes":"Edit clothes");
        EditText name=p.input("Name",Json.text(item,"name"));
        List<String> categoryIds=new ArrayList<>(),names=new ArrayList<>(); for(JSONObject c:WardrobeRules.list(WardrobeRules.state(doc),"categories")){categoryIds.add(Json.text(c,"id"));names.add(Json.text(c,"name"));}
        Spinner categories=p.select("Category",names,Math.max(0,categoryIds.indexOf(id==null?category:Json.text(item,"category"))));
        GarmentView preview=new GarmentView(activity,doc,item,true);p.fields.addView(preview,new LinearLayout.LayoutParams(-1,dp(140)));
        Spinner silhouette=p.select("Garment shape",Arrays.asList("Match category","T-shirt","Shirt","Sweater","Hoodie","Jacket","Jeans","Pants / trousers","Shorts","Skirt","Dress","Tank top","Briefs / underwear","Boxers","Shoes","Cap","Bag","Socks","Other"),Math.max(0,WardrobeRules.SHAPES.indexOf(item.optString("shape","auto"))));
        Spinner sleeves=p.select("Sleeve length",Arrays.asList("Match garment","Half sleeve","Full sleeve","Sleeveless"),Math.max(0,WardrobeRules.SLEEVES.indexOf(item.optString("sleeve","auto"))));
        List<String> hoodValues=Arrays.asList("auto","yes","no");
        Spinner hood=p.select("Hood",Arrays.asList("Match garment","With hood","Without hood"),Math.max(0,hoodValues.indexOf(item.optString("hood","auto"))));
        EditText colour=p.input("Colour · hex",item.optString("color","#FFFFFF")),colourName=p.input("Colour name",item.optString("color_name","White"));
        Runnable updatePreview=()->{
            JSONObject sample=Json.of("category",categoryIds.get(selectedIndex(categories,categoryIds.size())),"shape",WardrobeRules.SHAPES.get(selectedIndex(silhouette,WardrobeRules.SHAPES.size())),"sleeve",WardrobeRules.SLEEVES.get(selectedIndex(sleeves,WardrobeRules.SLEEVES.size())),"hood",hoodValues.get(selectedIndex(hood,hoodValues.size())));
            String shape=WardrobeRules.shape(doc,sample);sleeves.setEnabled(WardrobeRules.hasSleeves(shape));hood.setEnabled(WardrobeRules.hasSleeves(shape)||shape.equals("tank"));
            preview.update(shape,WardrobeRules.sleeve(doc,sample),WardrobeRules.hood(doc,sample),colour.getText().toString());
        };
        AdapterView.OnItemSelectedListener selected=new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int position,long row){updatePreview.run();}public void onNothingSelected(AdapterView<?> parent){}};
        categories.setOnItemSelectedListener(selected);silhouette.setOnItemSelectedListener(selected);sleeves.setOnItemSelectedListener(selected);hood.setOnItemSelectedListener(selected);
        colour.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){updatePreview.run();}public void afterTextChanged(Editable e){}});updatePreview.run();
        LinearLayout palette=Ui.chips(p.fields);String[][] colours={{"White","#FFFFFF"},{"Black","#242525"},{"Grey","#91958F"},{"Navy","#263C58"},{"Blue","#658AB6"},{"Green","#617D61"},{"Beige","#CCBA97"},{"Brown","#775C46"},{"Red","#A94F4F"},{"Pink","#DFA4AF"},{"Yellow","#D7B75D"}};
        for(String[] c:colours){Button b=Ui.button(palette,"●",()->{colour.setText(c[1]);colourName.setText(c[0]);});b.setTextColor(Color.parseColor(c[1]));b.setContentDescription(c[0]);b.setBackground(Ui.shape(activity,Ui.LINE,24));}
        List<String> uses=Arrays.asList("home","outdoor","both"); Spinner purpose=p.select("Wear at",Arrays.asList("Home","Outdoor","Home & outdoor"),Math.max(0,uses.indexOf(item.optString("use","both"))));
        EditText quantity=p.input("Total quantity",id==null?"1":String.valueOf(WardrobeRules.count(item,"all")));quantity.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText note=p.input("Care notes · optional",Json.text(item,"note"));
        p.action("Save clothes",()->{int n=Integer.parseInt(quantity.getText().toString());String cat=categoryIds.get(selectedIndex(categories,categoryIds.size())),u=uses.get(selectedIndex(purpose,uses.size()));
            String shape=WardrobeRules.SHAPES.get(selectedIndex(silhouette,WardrobeRules.SHAPES.size())),sleeve=WardrobeRules.SLEEVES.get(selectedIndex(sleeves,WardrobeRules.SLEEVES.size())),hoodValue=hoodValues.get(selectedIndex(hood,hoodValues.size()));
            String itemName=name.getText().toString(),hex=colour.getText().toString().trim(),colourLabel=colourName.getText().toString(),notes=note.getText().toString();
            change(v->{
                WardrobeRules.saveItem(v,id==null?"":id,itemName,cat,hex,colourLabel,u,notes,n);
                List<JSONObject> items=WardrobeRules.list(WardrobeRules.state(v),"items");String saved=id==null?Json.text(items.get(items.size()-1),"id"):id;
                WardrobeRules.appearance(v,saved,shape,sleeve,hoodValue);
            },p.dialog);});
        if(id!=null) p.action("Remove item",()->new AlertDialog.Builder(activity).setMessage("Remove this item from your wardrobe?").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->change(v->WardrobeRules.removeItem(v,id),p.dialog)).show());
        p.show();
    }
    private static int selectedIndex(Spinner spinner,int count){return Math.max(0,Math.min(count-1,spinner.getSelectedItemPosition()));}
    private void shelves() {
        JSONObject doc=repo.wardrobe();Panel p=new Panel("Your shelves");
        p.action("+ Add shelf",()->newShelf(p.dialog));
        for(JSONObject c:WardrobeRules.list(WardrobeRules.state(doc),"categories")) {
            String id=Json.text(c,"id");p.action(Json.text(c,"name")+" · "+WardrobeRules.categoryCount(doc,id)+" pieces",()->{p.dialog.dismiss();editShelf(id);});
        }
        p.action("Laundry settings",()->{p.dialog.dismiss();settings();});p.show();
    }
    private void newShelf(AlertDialog parent) {
        Panel p=new Panel("New shelf");EditText name=p.input("Shelf name","");
        List<String> kinds=Arrays.asList("top","shirt","bottom","shorts","layer","one_piece","shoes","other");
        Spinner shape=p.select("Default clothing shape",Arrays.asList("T-shirt / top","Shirt","Pants / jeans","Shorts","Jacket / layer","Dress / one piece","Shoes / footwear","Other / underwear"),0);
        p.action("Add shelf",()->{String title=name.getText().toString(),kind=kinds.get(selectedIndex(shape,kinds.size()));
            repo.wardrobe(v->WardrobeRules.category(v,title,kind),error->activity.runOnUiThread(()->{if(error.isEmpty()){p.dialog.dismiss();if(parent!=null)parent.dismiss();refresh.run();toast("Shelf added");}else toast(error);}));});p.show();
    }
    private void editShelf(String id) {
        JSONObject doc=repo.wardrobe();String title=WardrobeRules.categoryName(doc,id);Panel p=new Panel(title+" shelf");EditText name=p.input("Shelf name",title);
        p.action("Save shelf name",()->{String value=name.getText().toString();change(v->WardrobeRules.renameCategory(v,id,value),p.dialog);});
        int count=WardrobeRules.categoryCount(doc,id);List<String> destinations=new ArrayList<>(),names=new ArrayList<>();
        for(JSONObject c:WardrobeRules.list(WardrobeRules.state(doc),"categories"))if(!id.equals(Json.text(c,"id"))){destinations.add(Json.text(c,"id"));names.add(Json.text(c,"name"));}
        Ui.text(p.fields,count==0?"This shelf is empty.":count+" pieces are on this shelf, including clothes in use or in laundry. Move them to another shelf when removing it.",14,Ui.MUTED);
        Spinner target=count>0&&!destinations.isEmpty()?p.select("Move clothes to",names,0):null;
        if(count>0&&destinations.isEmpty())p.action("+ Add another shelf first",()->newShelf(p.dialog));
        else p.action("Delete shelf",()->{
            String destination=target==null?"":destinations.get(selectedIndex(target,destinations.size()));
            new AlertDialog.Builder(activity).setTitle("Delete "+title+" shelf?").setMessage(count==0?"Remove this empty shelf from the wardrobe?":"Move all its clothes to "+WardrobeRules.categoryName(doc,destination)+" and remove this shelf? Their availability and laundry status stay the same.")
                .setNegativeButton("Cancel",null).setPositiveButton("Delete shelf",(dialog,which)->{
                    repo.wardrobe(v->WardrobeRules.removeCategory(v,id,destination),error->activity.runOnUiThread(()->{if(error.isEmpty()){p.dialog.dismiss();if(category.equals(id)){category="all";search="";railX=0;}refresh.run();toast("Shelf removed");}else toast(error);}));
                }).show();
        });p.show();
    }
    private void settings() {
        JSONObject doc=repo.wardrobe();Panel p=new Panel("Wardrobe settings");
        EditText threshold=p.input("Create laundry task at this many pieces · 0 = off",String.valueOf(WardrobeRules.state(doc).optInt("threshold",8)));threshold.setInputType(InputType.TYPE_CLASS_NUMBER);
        p.action("Save laundry setting",()->{int n=Integer.parseInt(threshold.getText().toString());if(n<0||n>1000)throw new IllegalArgumentException("Choose 0–1,000 pieces.");change(v->Json.put(WardrobeRules.state(v),"threshold",n),p.dialog);});
        p.action("Manage shelves",()->{p.dialog.dismiss();shelves();});
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
        for(String id:ids) try{JSONObject item=WardrobeRules.item(doc,id);names.add(Json.text(item,"name"));icons.addView(new GarmentView(activity,doc,item,false),new LinearLayout.LayoutParams(0,dp(92),1));}catch(Exception ignored){names.add("Removed item");}
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
