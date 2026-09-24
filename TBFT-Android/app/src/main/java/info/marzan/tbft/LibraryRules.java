package info.marzan.tbft;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Offline library data stored inside the owner's versioned Life collection. */
final class LibraryRules {
    static JSONObject state(JSONObject doc) { return WardrobeRules.state(doc); }
    static List<JSONObject> shelves(JSONObject doc) { ensure(doc); return WardrobeRules.list(state(doc),"library_shelves"); }
    static List<JSONObject> books(JSONObject doc) { ensure(doc); return WardrobeRules.list(state(doc),"books"); }
    static void ensure(JSONObject doc) {
        JSONObject state=state(doc);
        if(state.optJSONArray("library_shelves")==null) {
            JSONArray shelves=new JSONArray();
            String[][] defaults={{"fiction","Fiction"},{"learning","Learning"},{"work","Work & study"},{"reference","Reference"},{"life","Life & ideas"},{"to_read","To read"}};
            for(String[] shelf:defaults)shelves.put(Json.of("id",shelf[0],"name",shelf[1]));
            Json.put(state,"library_shelves",shelves);
        }
        if(state.optJSONArray("books")==null)Json.put(state,"books",new JSONArray());
        if(state.optJSONArray("removed_library_shelves")==null)Json.put(state,"removed_library_shelves",new JSONArray());
    }
    static JSONObject book(JSONObject doc,String id) {
        for(JSONObject book:books(doc))if(id.equals(Json.text(book,"id")))return book;
        throw new IllegalArgumentException("This book was removed. Reopen Library.");
    }
    static String shelfName(JSONObject doc,String id) {
        for(JSONObject shelf:shelves(doc))if(id.equals(Json.text(shelf,"id")))return Json.text(shelf,"name");
        return "Books";
    }
    static int count(JSONObject doc,String shelf,String status) {
        int total=0;for(JSONObject book:books(doc))if((shelf.equals("all")||shelf.equals(Json.text(book,"shelf")))&&(status.equals("all")||status.equals(Json.text(book,"status"))))total++;
        return total;
    }
    static void saveBook(JSONObject doc,String id,String title,String author,String shelf,String format,String status,int progress,String color,String note) {
        ensure(doc);title=title.trim();author=author.trim();note=note.trim();
        if(title.isEmpty()||title.length()>160)throw new IllegalArgumentException("Book titles need 1–160 characters.");
        if(author.length()>120)throw new IllegalArgumentException("Keep the author under 120 characters.");
        if(note.length()>2000)throw new IllegalArgumentException("Keep notes under 2,000 characters.");
        if(!Arrays.asList("paper","ebook","audio").contains(format))throw new IllegalArgumentException("Choose a book format.");
        if(!Arrays.asList("unread","reading","finished","lent").contains(status))throw new IllegalArgumentException("Choose a reading status.");
        if(progress<0||progress>100)throw new IllegalArgumentException("Progress must be between 0 and 100.");
        if(!color.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Use a colour such as #607D68.");
        boolean valid=false;for(JSONObject candidate:shelves(doc))if(shelf.equals(Json.text(candidate,"id")))valid=true;
        if(!valid)throw new IllegalArgumentException("Choose an existing shelf.");
        JSONObject book;
        if(id.isEmpty()){book=Json.of("id",UUID.randomUUID().toString());state(doc).optJSONArray("books").put(book);}else book=book(doc,id);
        Json.put(book,"title",title);Json.put(book,"author",author);Json.put(book,"shelf",shelf);Json.put(book,"format",format);
        Json.put(book,"status",status);Json.put(book,"progress",status.equals("finished")?100:progress);Json.put(book,"color",color.toUpperCase(Locale.ROOT));Json.put(book,"note",note);
        if(books(doc).size()>2000)throw new IllegalArgumentException("This library supports up to 2,000 books.");
    }
    static void removeBook(JSONObject doc,String id) {
        JSONArray books=state(doc).optJSONArray("books");
        for(int i=0;i<books.length();i++)if(id.equals(Json.text(books.optJSONObject(i),"id"))){books.remove(i);return;}
    }
    static void addShelf(JSONObject doc,String name) {
        ensure(doc);name=name.trim();if(name.isEmpty()||name.length()>50)throw new IllegalArgumentException("Shelf names need 1–50 characters.");
        for(JSONObject shelf:shelves(doc))if(name.equalsIgnoreCase(Json.text(shelf,"name")))throw new IllegalArgumentException("That shelf already exists.");
        state(doc).optJSONArray("library_shelves").put(Json.of("id",UUID.randomUUID().toString(),"name",name));
    }
    static void renameShelf(JSONObject doc,String id,String name) {
        name=name.trim();if(name.isEmpty()||name.length()>50)throw new IllegalArgumentException("Shelf names need 1–50 characters.");
        JSONObject selected=null;for(JSONObject shelf:shelves(doc)){if(id.equals(Json.text(shelf,"id")))selected=shelf;else if(name.equalsIgnoreCase(Json.text(shelf,"name")))throw new IllegalArgumentException("That shelf already exists.");}
        if(selected==null)throw new IllegalArgumentException("This shelf was removed.");Json.put(selected,"name",name);
    }
    static void removeShelf(JSONObject doc,String id,String destination) {
        JSONObject source=null,target=null;for(JSONObject shelf:shelves(doc)){if(id.equals(Json.text(shelf,"id")))source=shelf;if(destination.equals(Json.text(shelf,"id")))target=shelf;}
        if(source==null)throw new IllegalArgumentException("This shelf was removed.");int count=count(doc,id,"all");
        if(count>0&&(target==null||id.equals(destination)))throw new IllegalArgumentException("Choose another shelf for these books first.");
        for(JSONObject book:books(doc))if(id.equals(Json.text(book,"shelf")))Json.put(book,"shelf",destination);
        JSONArray shelves=state(doc).optJSONArray("library_shelves");for(int i=0;i<shelves.length();i++)if(id.equals(Json.text(shelves.optJSONObject(i),"id"))){shelves.remove(i);break;}
        state(doc).optJSONArray("removed_library_shelves").put(id);
    }
}
