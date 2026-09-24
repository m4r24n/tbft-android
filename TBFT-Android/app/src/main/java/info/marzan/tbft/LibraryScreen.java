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

/** A small personal library: shelves, tactile browsing and reading state. */
final class LibraryScreen {
    private final Activity activity;private final TbftRepository repo;private final Runnable refresh;
    private String shelf="all",status="all",search="";private int cabinetX,bookX;private boolean doorsOpen;
    private LinearLayout results;
    LibraryScreen(Activity activity,TbftRepository repo,Runnable refresh){this.activity=activity;this.repo=repo;this.refresh=refresh;doorsOpen=activity.getPreferences(0).getBoolean("library_open",false);}
    private int dp(int n){return Ui.dp(activity,n);}
    String location(){return status+":"+shelf+":"+cabinetX;}
    void saveState(Bundle b){b.putString("library_shelf",shelf);b.putString("library_status",status);b.putString("library_search",search);b.putInt("library_cabinet_x",cabinetX);b.putInt("library_book_x",bookX);}
    void restoreState(Bundle b){shelf=b.getString("library_shelf","all");status=b.getString("library_status","all");search=b.getString("library_search","");cabinetX=b.getInt("library_cabinet_x",0);bookX=b.getInt("library_book_x",0);}
    boolean back(){if(!shelf.equals("all")){shelf="all";search="";bookX=0;refresh.run();return true;}return false;}
    private void doors(boolean open){doorsOpen=open;activity.getPreferences(0).edit().putBoolean("library_open",open).apply();refresh.run();}
    void render(LinearLayout body){
        JSONObject doc=repo.wardrobe();LibraryRules.ensure(doc);
        LinearLayout title=new LinearLayout(activity);title.setGravity(Gravity.CENTER_VERTICAL);body.addView(title);
        TextView heading=Ui.heading(title,"Library",29);heading.setTypeface(Typeface.create("serif",Typeface.NORMAL));heading.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        Ui.button(title,"+ Add",()->edit(null));Ui.button(title,"Shelves",this::shelves).setTextSize(12);
        LinearLayout tabs=Ui.chips(body);String[][] choices={{"all","All"},{"reading","Reading"},{"unread","To read"},{"finished","Finished"},{"lent","Lent"}};
        for(String[] choice:choices){Button b=Ui.button(tabs,choice[1]+(choice[0].equals("all")?" · "+LibraryRules.count(doc,"all","all"):""),()->{status=choice[0];shelf="all";bookX=0;refresh.run();});b.setTextSize(12);Ui.selected(b,status.equals(choice[0]));}
        if(shelf.equals("all")){cabinet(body,doc);return;}
        Ui.button(body,"‹ All library shelves",()->{shelf="all";search="";bookX=0;refresh.run();});
        Ui.heading(body,LibraryRules.shelfName(doc,shelf),23);Ui.text(body,"Swipe along the shelf and tap a book for its details.",13,Ui.MUTED);
        EditText find=new EditText(activity);find.setSingleLine(true);find.setTextSize(14);find.setHint("Find by title or author");find.setText(search);find.setContentDescription("Find books");body.addView(find);
        results=Ui.column(activity);body.addView(results);collection(doc);
        find.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){search=s.toString();bookX=0;collection(repo.wardrobe());}public void afterTextChanged(Editable e){}});
        Ui.button(body,"+ Add to "+LibraryRules.shelfName(doc,shelf),()->edit(null));
    }
    private void cabinet(LinearLayout body,JSONObject doc){
        if(doorsOpen)Ui.text(body,"Swipe sideways through the bookcase, then open a shelf.",13,Ui.MUTED);else Ui.text(body,LibraryRules.count(doc,"all","all")+" books · a quiet place for what you want to keep.",13,Ui.MUTED);
        List<JSONObject> all=LibraryRules.shelves(doc);int pages=Math.max(1,(all.size()+5)/6),pageWidth=activity.getResources().getDisplayMetrics().widthPixels-dp(28);
        if(!doorsOpen){LibraryCabinet cabinet=new LibraryCabinet(activity,doc,all.subList(0,Math.min(6,all.size())),false,this::openShelf,()->doors(true));body.addView(cabinet,new LinearLayout.LayoutParams(-1,dp(365)));return;}
        HorizontalScrollView scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);scroll.setFillViewport(true);scroll.setContentDescription("Library shelves. Swipe horizontally to browse.");
        LinearLayout pagesRow=new LinearLayout(activity);scroll.addView(pagesRow);body.addView(scroll,new LinearLayout.LayoutParams(-1,dp(365)));
        for(int page=0;page<pages;page++){int start=page*6;LibraryCabinet cabinet=new LibraryCabinet(activity,doc,all.subList(start,Math.min(all.size(),start+6)),true,this::openShelf,()->{});pagesRow.addView(cabinet,new LinearLayout.LayoutParams(pageWidth,dp(365)));}
        TextView indicator=Ui.text(body,"Swipe shelves  ·  1 / "+pages,12,Ui.MUTED);indicator.setGravity(Gravity.CENTER);
        int saved=Math.min(cabinetX,Math.max(0,(pages-1)*pageWidth));scroll.post(()->scroll.scrollTo(saved,0));
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{cabinetX=x;int page=Math.min(pages-1,Math.max(0,Math.round((float)x/pageWidth)));indicator.setText("Swipe shelves  ·  "+(page+1)+" / "+pages);});
        Ui.button(body,"Close library doors",()->doors(false));
    }
    private void openShelf(String id){shelf=id;search="";bookX=0;refresh.run();}
    private boolean visible(JSONObject book){String needle=(Json.text(book,"title")+" "+Json.text(book,"author")).toLowerCase(Locale.ROOT);return shelf.equals(Json.text(book,"shelf"))&&(status.equals("all")||status.equals(Json.text(book,"status")))&&needle.contains(search.toLowerCase(Locale.ROOT));}
    private void collection(JSONObject doc){
        results.removeAllViews();List<JSONObject> books=new ArrayList<>();for(JSONObject book:LibraryRules.books(doc))if(visible(book))books.add(book);
        if(books.isEmpty()){LinearLayout empty=Ui.card(results);Ui.heading(empty,"An open shelf",20);Ui.text(empty,status.equals("all")?"Add a book here, or search another shelf.":"No books on this shelf have this reading status.",14,Ui.MUTED);return;}
        HorizontalScrollView scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(true);scroll.setBackground(Ui.shape(activity,0xffe2d5bf,12));scroll.setContentDescription("Books. Swipe horizontally to browse.");
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.BOTTOM);row.setPadding(dp(10),dp(8),dp(10),dp(10));scroll.addView(row);results.addView(scroll,new LinearLayout.LayoutParams(-1,dp(268)));
        for(JSONObject book:books){LinearLayout tile=Ui.column(activity);tile.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);tile.setPadding(dp(5),0,dp(5),0);row.addView(tile,new LinearLayout.LayoutParams(dp(136),-1));
            tile.addView(new BookView(activity,book),new LinearLayout.LayoutParams(dp(55),0,1));TextView name=Ui.heading(tile,Json.text(book,"title"),15);name.setGravity(Gravity.CENTER);name.setMaxLines(2);
            TextView author=Ui.text(tile,Json.text(book,"author").isEmpty()?"Unknown author":Json.text(book,"author"),11,Ui.MUTED);author.setGravity(Gravity.CENTER);author.setMaxLines(1);
            TextView state=Ui.text(tile,statusLabel(book),11,Ui.ACCENT);state.setGravity(Gravity.CENTER);tile.setFocusable(true);tile.setClickable(true);tile.setContentDescription(Json.text(book,"title")+", "+statusLabel(book));tile.setOnClickListener(v->details(Json.text(book,"id")));for(int n=0;n<tile.getChildCount();n++)tile.getChildAt(n).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        int saved=bookX;scroll.post(()->scroll.scrollTo(saved,0));scroll.setOnScrollChangeListener((v,x,y,ox,oy)->bookX=x);
    }
    private String statusLabel(JSONObject book){String value=Json.text(book,"status");if(value.equals("reading"))return "Reading · "+book.optInt("progress",0)+"%";if(value.equals("finished"))return "Finished";if(value.equals("lent"))return "Lent out";return "To read";}
    private void details(String id){JSONObject doc=repo.wardrobe(),book=LibraryRules.book(doc,id);Panel p=new Panel(Json.text(book,"title"));p.fields.addView(new BookView(activity,book),new LinearLayout.LayoutParams(-1,dp(170)));
        if(!Json.text(book,"author").isEmpty())Ui.text(p.fields,"by "+Json.text(book,"author"),14,Ui.MUTED);Ui.text(p.fields,LibraryRules.shelfName(doc,Json.text(book,"shelf"))+" · "+formatLabel(Json.text(book,"format"))+" · "+statusLabel(book),13,Ui.ACCENT);
        if(!Json.text(book,"note").isEmpty())Ui.text(p.fields,Json.text(book,"note"),14,Ui.MUTED);
        if(!Json.text(book,"status").equals("reading"))p.action("Start reading",()->change(v->{JSONObject b=LibraryRules.book(v,id);Json.put(b,"status","reading");},p.dialog));
        if(!Json.text(book,"status").equals("finished"))p.action("Mark finished",()->change(v->{JSONObject b=LibraryRules.book(v,id);Json.put(b,"status","finished");Json.put(b,"progress",100);},p.dialog));
        if(!Json.text(book,"status").equals("lent"))p.action("Mark as lent",()->change(v->Json.put(LibraryRules.book(v,id),"status","lent"),p.dialog));
        p.action("Edit book",()->{p.dialog.dismiss();edit(id);});p.show();}
    private void edit(String id){JSONObject doc=repo.wardrobe();List<JSONObject> shelves=LibraryRules.shelves(doc);if(shelves.isEmpty()){toast("Create a library shelf first.");newShelf(null);return;}JSONObject book=id==null?Json.of("color","#607D68","status","unread","format","paper","progress",0):LibraryRules.book(doc,id);Panel p=new Panel(id==null?"Add book":"Edit book");
        EditText title=p.input("Title",Json.text(book,"title")),author=p.input("Author",Json.text(book,"author"));List<String> ids=new ArrayList<>(),names=new ArrayList<>();for(JSONObject candidate:shelves){ids.add(Json.text(candidate,"id"));names.add(Json.text(candidate,"name"));}
        Spinner shelfChoice=p.select("Shelf",names,Math.max(0,ids.indexOf(id==null?(shelf.equals("all")?ids.get(0):shelf):Json.text(book,"shelf"))));
        List<String> formats=Arrays.asList("paper","ebook","audio");Spinner format=p.select("Format",Arrays.asList("Paper book","E-book","Audiobook"),Math.max(0,formats.indexOf(Json.text(book,"format"))));
        List<String> statuses=Arrays.asList("unread","reading","finished","lent");Spinner state=p.select("Reading status",Arrays.asList("To read","Reading","Finished","Lent out"),Math.max(0,statuses.indexOf(Json.text(book,"status"))));
        EditText progress=p.input("Progress · 0–100",String.valueOf(book.optInt("progress",0)));progress.setInputType(InputType.TYPE_CLASS_NUMBER);EditText color=p.input("Spine colour · hex",book.optString("color","#607D68"));
        LinearLayout palette=Ui.chips(p.fields);String[] colours={"#607D68","#A38B57","#47647A","#7A4E42","#725B78","#C7B287","#343C39"};for(String value:colours){Button swatch=Ui.button(palette,"●",()->color.setText(value));swatch.setTextColor(Color.parseColor(value));swatch.setContentDescription("Book colour "+value);}
        EditText note=p.input("Notes · optional",Json.text(book,"note"));p.action("Save book",()->{int percentage=Integer.parseInt(progress.getText().toString());String bookTitle=title.getText().toString(),bookAuthor=author.getText().toString(),bookShelf=ids.get(index(shelfChoice,ids.size())),bookFormat=formats.get(index(format,formats.size())),bookStatus=statuses.get(index(state,statuses.size())),bookColor=color.getText().toString(),bookNote=note.getText().toString();change(v->LibraryRules.saveBook(v,id==null?"":id,bookTitle,bookAuthor,bookShelf,bookFormat,bookStatus,percentage,bookColor,bookNote),p.dialog);});
        if(id!=null)p.action("Remove book",()->new AlertDialog.Builder(activity).setTitle("Remove this book?").setMessage("It will be removed from your Library.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->change(v->LibraryRules.removeBook(v,id),p.dialog)).show());p.show();}
    private void shelves(){JSONObject doc=repo.wardrobe();Panel p=new Panel("Library shelves");p.action("+ Add shelf",()->newShelf(p.dialog));for(JSONObject candidate:LibraryRules.shelves(doc)){String id=Json.text(candidate,"id");p.action(Json.text(candidate,"name")+" · "+LibraryRules.count(doc,id,"all")+" books",()->{p.dialog.dismiss();editShelf(id);});}p.show();}
    private void newShelf(AlertDialog parent){Panel p=new Panel("New library shelf");EditText name=p.input("Shelf name","");p.action("Add shelf",()->{String value=name.getText().toString();repo.wardrobe(v->LibraryRules.addShelf(v,value),error->activity.runOnUiThread(()->{if(error.isEmpty()){p.dialog.dismiss();if(parent!=null)parent.dismiss();refresh.run();toast("Shelf added");}else toast(error);}));});p.show();}
    private void editShelf(String id){JSONObject doc=repo.wardrobe();String current=LibraryRules.shelfName(doc,id);Panel p=new Panel(current+" shelf");EditText name=p.input("Shelf name",current);p.action("Save shelf name",()->change(v->LibraryRules.renameShelf(v,id,name.getText().toString()),p.dialog));
        int count=LibraryRules.count(doc,id,"all");List<String> ids=new ArrayList<>(),names=new ArrayList<>();for(JSONObject candidate:LibraryRules.shelves(doc))if(!id.equals(Json.text(candidate,"id"))){ids.add(Json.text(candidate,"id"));names.add(Json.text(candidate,"name"));}
        Ui.text(p.fields,count==0?"This shelf is empty.":count+" books are here. They can move together before this shelf is removed.",13,Ui.MUTED);Spinner destination=count>0&&!ids.isEmpty()?p.select("Move books to",names,0):null;
        if(count>0&&ids.isEmpty())p.action("+ Add another shelf first",()->newShelf(p.dialog));else p.action("Delete shelf",()->{String target=destination==null?"":ids.get(index(destination,ids.size()));new AlertDialog.Builder(activity).setTitle("Delete "+current+" shelf?").setMessage(count==0?"Remove this empty shelf?":"Move its books to "+LibraryRules.shelfName(doc,target)+" and remove it?").setNegativeButton("Cancel",null).setPositiveButton("Delete shelf",(d,w)->repo.wardrobe(v->LibraryRules.removeShelf(v,id,target),error->activity.runOnUiThread(()->{if(error.isEmpty()){p.dialog.dismiss();if(shelf.equals(id))shelf="all";refresh.run();toast("Shelf removed");}else toast(error);}))).show();});p.show();}
    private static int index(Spinner spinner,int size){return Math.max(0,Math.min(size-1,spinner.getSelectedItemPosition()));}
    private static String formatLabel(String value){return value.equals("ebook")?"E-book":value.equals("audio")?"Audiobook":"Paper book";}
    private void change(Consumer<JSONObject> action,AlertDialog dialog){repo.wardrobe(action,error->activity.runOnUiThread(()->{if(error.isEmpty()){if(dialog!=null)dialog.dismiss();refresh.run();toast("Saved on device");}else toast(error);}));}
    private void toast(String text){Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
    private final class Panel{final LinearLayout fields=Ui.column(activity);final AlertDialog dialog;Panel(String title){fields.setPadding(dp(20),dp(8),dp(20),dp(20));ScrollView scroll=new ScrollView(activity);scroll.addView(fields);dialog=new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setNegativeButton("Close",null).create();}EditText input(String label,String value){Ui.text(fields,label,13,Ui.MUTED);EditText input=new EditText(activity);input.setSingleLine(true);input.setTextSize(16);input.setText(value);input.setContentDescription(label);fields.addView(input);return input;}Spinner select(String label,List<String> values,int selected){Ui.text(fields,label,13,Ui.MUTED);Spinner spinner=new Spinner(activity);spinner.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,values));spinner.setSelection(selected);spinner.setMinimumHeight(dp(48));spinner.setContentDescription(label);fields.addView(spinner);return spinner;}void action(String label,Runnable action){Ui.button(fields,label,()->{try{action.run();}catch(Exception e){toast(e.getMessage()==null?"Check these values":e.getMessage());}});}void show(){dialog.show();}}
}
