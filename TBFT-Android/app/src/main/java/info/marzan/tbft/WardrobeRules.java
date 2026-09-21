package info.marzan.tbft;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Deterministic wardrobe rules shared by offline actions and tested against the cloud contract. */
final class WardrobeRules {
    static final String TABLE = "wardrobes";
    static String stableId(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format(Locale.ROOT,"%02x",b & 255));
            String h = hex.toString();
            return h.substring(0,8)+"-"+h.substring(8,12)+"-"+h.substring(12,16)+"-"+h.substring(16,20)+"-"+h.substring(20);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    static String id(String workspace,String owner) { return stableId("wardrobe:"+workspace+":"+owner); }
    static JSONObject fresh(String workspace,String owner) {
        JSONArray categories = new JSONArray();
        String[][] defaults = {{"tshirts","T-shirts","top"},{"shirts","Shirts","shirt"},{"shorts","Shorts","shorts"},
                {"jeans","Jeans","bottom"},{"pants","Pants","bottom"},{"jackets","Jackets","layer"},
                {"hoodies","Hoodies","layer"},{"sweaters","Sweaters","top"},{"dresses","Dresses","one_piece"},
                {"shoes","Shoes","shoes"},{"other","Other","other"}};
        for (String[] c : defaults) categories.put(Json.of("id",c[0],"name",c[1],"kind",c[2]));
        return Json.of("id",id(workspace,owner),"workspace_id",workspace,"owner_user_id",owner,"revision",0,
                "state",Json.of("categories",categories,"items",new JSONArray(),"outfits",new JSONArray(),
                        "batches",new JSONArray(),"threshold",8,"sequence",0));
    }
    static JSONObject state(JSONObject doc) { return doc.optJSONObject("state"); }
    static List<JSONObject> list(JSONObject state,String key) { JSONArray a=state.optJSONArray(key); return a == null ? new ArrayList<>() : Json.rows(a); }
    static JSONObject item(JSONObject doc,String id) {
        for (JSONObject item : list(state(doc),"items")) if (id.equals(Json.text(item,"id"))) return item;
        throw new IllegalArgumentException("This item was removed. Reopen Wardrobe.");
    }
    static int count(JSONObject item,String status) {
        int n=0; for (JSONObject p:list(item,"pieces")) if (status.equals("all") || status.equals(Json.text(p,"status"))) n++;
        return n;
    }
    static int total(JSONObject doc,String status) {
        int n=0; for (JSONObject item:list(state(doc),"items")) n+=count(item,status); return n;
    }
    static String categoryName(JSONObject doc,String id) {
        for(JSONObject c:list(state(doc),"categories")) if(id.equals(Json.text(c,"id"))) return Json.text(c,"name");
        return "Other";
    }
    static String kind(JSONObject doc,JSONObject item) {
        for(JSONObject c:list(state(doc),"categories")) if(Json.text(item,"category").equals(Json.text(c,"id"))) return Json.text(c,"kind");
        return "other";
    }
    static void category(JSONObject doc,String name,String kind) {
        name=name.trim();
        if(name.isEmpty() || name.length()>50) throw new IllegalArgumentException("Category names need 1–50 characters.");
        if(!Arrays.asList("top","shirt","bottom","shorts","layer","one_piece","shoes","other").contains(kind)) throw new IllegalArgumentException("Choose a clothing shape.");
        for(JSONObject c:list(state(doc),"categories")) if(name.equalsIgnoreCase(Json.text(c,"name"))) throw new IllegalArgumentException("That category already exists.");
        state(doc).optJSONArray("categories").put(Json.of("id",UUID.randomUUID().toString(),"name",name,"kind",kind));
    }
    static void saveItem(JSONObject doc,String id,String name,String category,String color,String colorName,String use,String note,int quantity) {
        if(name.trim().isEmpty() || name.trim().length()>120) throw new IllegalArgumentException("Item names need 1–120 characters.");
        if(quantity<1 || quantity>100) throw new IllegalArgumentException("Choose between 1 and 100 pieces.");
        if(!color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Use a colour such as #FFFFFF.");
        if(!Arrays.asList("home","outdoor","both").contains(use)) throw new IllegalArgumentException("Choose Home, Outdoor or Both.");
        if(note.length()>2000) throw new IllegalArgumentException("Keep care notes under 2,000 characters.");
        boolean found=false; for(JSONObject c:list(state(doc),"categories")) if(category.equals(Json.text(c,"id"))) found=true;
        if(!found) throw new IllegalArgumentException("Choose an existing category.");
        JSONObject garment;
        if(id.isEmpty()) {
            garment=Json.of("id",UUID.randomUUID().toString(),"pieces",new JSONArray());
            state(doc).optJSONArray("items").put(garment);
        } else garment=item(doc,id);
        int existing=count(garment,"all"), remove=existing-quantity;
        if(remove>count(garment,"available")) throw new IllegalArgumentException("Return or wash the in-use pieces before reducing this quantity.");
        JSONArray pieces=garment.optJSONArray("pieces");
        for(int i=pieces.length()-1; i>=0 && remove>0; i--) if("available".equals(Json.text(pieces.optJSONObject(i),"status"))) { pieces.remove(i); remove--; }
        for(int i=existing;i<quantity;i++) pieces.put(Json.of("id",UUID.randomUUID().toString(),"status","available","wears",0));
        for(String key:new String[]{"name","category","color","color_name","use","note"}) {
            String value=key.equals("name")?name.trim():key.equals("category")?category:key.equals("color")?color.toUpperCase(Locale.ROOT):key.equals("color_name")?colorName.trim():key.equals("use")?use:note.trim();
            Json.put(garment,key,value);
        }
        if(total(doc,"all")>1000) throw new IllegalArgumentException("This wardrobe supports up to 1,000 pieces.");
    }
    static void removeItem(JSONObject doc,String id) {
        JSONObject garment=item(doc,id);
        if(count(garment,"available")!=count(garment,"all")) throw new IllegalArgumentException("Return or wash this item's pieces before removing it.");
        JSONArray items=state(doc).optJSONArray("items");
        for(int i=0;i<items.length();i++) if(id.equals(Json.text(items.optJSONObject(i),"id"))) { items.remove(i); break; }
    }
    static void move(JSONObject doc,String itemId,String from,String to,int quantity) {
        List<String> states=Arrays.asList("available","in_use","laundry");
        if(!states.contains(from)||!states.contains(to)||from.equals(to)||quantity<1) throw new IllegalArgumentException("Choose a valid move.");
        JSONObject garment=item(doc,itemId);
        if(count(garment,from)<quantity) throw new IllegalArgumentException("The quantity changed. Reopen this item.");
        for(JSONObject p:list(garment,"pieces")) if(quantity>0 && from.equals(Json.text(p,"status"))) {
            Json.put(p,"status",to);
            if(to.equals("laundry")) Json.put(p,"laundry_token",UUID.randomUUID().toString());
            if(to.equals("in_use")) { Json.put(p,"wears",p.optInt("wears",0)+1); Json.put(p,"last_worn_at",Json.now()); }
            quantity--;
        }
    }
    static JSONObject activeBatch(JSONObject doc) {
        for(JSONObject b:list(state(doc),"batches")) if(Json.text(b,"completed_at").isEmpty() && Json.text(b,"cancelled_at").isEmpty()) return b;
        return null;
    }
    static JSONObject batch(JSONObject doc,String taskId) {
        for(JSONObject b:list(state(doc),"batches")) if(taskId.equals(Json.text(b,"task_id"))) return b;
        return null;
    }
    static void reconcile(JSONObject doc,String date) {
        JSONObject s=state(doc), batch=activeBatch(doc);
        int threshold=s.optInt("threshold",8);
        if(batch==null && threshold>0 && total(doc,"laundry")>=threshold) {
            int seq=s.optInt("sequence",0)+1; Json.put(s,"sequence",seq);
            batch=Json.of("sequence",seq,"task_id",stableId(Json.text(doc,"id")+":laundry:"+seq),"date",date,"completed_at",null,"entries",new JSONArray());
            s.optJSONArray("batches").put(batch);
        }
        if(batch!=null) {
            JSONArray entries=new JSONArray();
            for(JSONObject garment:list(s,"items")) for(JSONObject p:list(garment,"pieces")) if("laundry".equals(Json.text(p,"status")))
                entries.put(Json.of("piece_id",Json.text(p,"id"),"token",Json.text(p,"laundry_token")));
            Json.put(batch,"entries",entries);
        }
    }
    static boolean complete(JSONObject doc,String taskId,String completedAt) {
        JSONObject batch=batch(doc,taskId);
        if(batch==null || !Json.text(batch,"completed_at").isEmpty() || !Json.text(batch,"cancelled_at").isEmpty() || completedAt.isEmpty()) return false;
        Map<String,String> tokens=new HashMap<>();
        for(JSONObject entry:list(batch,"entries")) tokens.put(Json.text(entry,"piece_id"),Json.text(entry,"token"));
        for(JSONObject garment:list(state(doc),"items")) for(JSONObject p:list(garment,"pieces"))
            if("laundry".equals(Json.text(p,"status")) && tokens.containsKey(Json.text(p,"id"))
                    && tokens.get(Json.text(p,"id")).equals(Json.text(p,"laundry_token"))) {
                Json.put(p,"status","available"); Json.put(p,"washed_at",completedAt); Json.put(p,"wears",0);
            }
        Json.put(batch,"completed_at",completedAt); return true;
    }
    static void finishBasket(JSONObject doc,String date) {
        if(total(doc,"laundry")==0) throw new IllegalArgumentException("The basket is already empty.");
        int threshold=state(doc).optInt("threshold",8);
        Json.put(state(doc),"threshold",1); reconcile(doc,date); Json.put(state(doc),"threshold",threshold);
        complete(doc,Json.text(activeBatch(doc),"task_id"),Json.now());
    }
    static JSONObject task(JSONObject doc,JSONObject batch) {
        return Json.of("id",Json.text(batch,"task_id"),"wardrobe_id",Json.text(doc,"id"),"workspace_id",Json.text(doc,"workspace_id"),"owner_user_id",Json.text(doc,"owner_user_id"),
                "created_by",Json.text(doc,"owner_user_id"),"title","Do laundry","description","Wardrobe laundry. Complete this task when the clothes are washed and ready to wear.",
                "original_date",Json.text(batch,"date"),"priority","normal","completed_at",batch.opt("completed_at"));
    }
    static boolean available(JSONObject doc,List<String> ids) {
        if(ids.isEmpty() || new HashSet<>(ids).size()!=ids.size()) return false;
        try { for(String id:ids) if(count(item(doc,id),"available")==0) return false; } catch(IllegalArgumentException e) { return false; }
        return true;
    }
    static void wear(JSONObject doc,List<String> ids) {
        if(!available(doc,ids)) throw new IllegalArgumentException("An item in this outfit is no longer available.");
        for(String id:ids) move(doc,id,"available","in_use",1);
    }
    static void saveOutfit(JSONObject doc,String name,List<String> ids) {
        if(name.trim().isEmpty() || name.length()>80 || ids.isEmpty()) throw new IllegalArgumentException("Give this outfit a short name.");
        state(doc).optJSONArray("outfits").put(Json.of("id",UUID.randomUUID().toString(),"name",name.trim(),"items",new JSONArray(ids)));
    }
    static List<String> outfitIds(JSONObject outfit) {
        List<String> ids=new ArrayList<>(); JSONArray a=outfit.optJSONArray("items");
        if(a!=null) for(int i=0;i<a.length();i++) ids.add(a.optString(i)); return ids;
    }
    static List<List<String>> suggestions(JSONObject doc,String use) {
        List<JSONObject> tops=new ArrayList<>(),bottoms=new ArrayList<>(),one=new ArrayList<>(),shoes=new ArrayList<>();
        for(JSONObject g:list(state(doc),"items")) if(count(g,"available")>0 && (use.equals("all")||use.equals(Json.text(g,"use"))||"both".equals(Json.text(g,"use")))) {
            String k=kind(doc,g);
            if(k.equals("top")||k.equals("shirt")) tops.add(g);
            else if(k.equals("bottom")||k.equals("shorts")) bottoms.add(g);
            else if(k.equals("one_piece")) one.add(g);
            else if(k.equals("shoes")) shoes.add(g);
        }
        List<List<String>> result=new ArrayList<>();
        for(JSONObject t:tops) for(JSONObject b:bottoms) result.add(Arrays.asList(Json.text(t,"id"),Json.text(b,"id")));
        result.sort(Comparator.comparingInt(ids -> -colourScore(item(doc,ids.get(0)),item(doc,ids.get(1)))));
        for(JSONObject g:one) result.add(new ArrayList<>(Collections.singletonList(Json.text(g,"id"))));
        List<List<String>> out=new ArrayList<>();
        for(List<String> ids:result) {
            List<String> outfit=new ArrayList<>(ids);
            if(!shoes.isEmpty()) {
                shoes.sort(Comparator.comparingInt(s -> -colourScore(item(doc,ids.get(0)),s)));
                outfit.add(Json.text(shoes.get(0),"id"));
            }
            out.add(outfit); if(out.size()==6) break;
        }
        return out;
    }
    private static int colourScore(JSONObject a,JSONObject b) {
        String x=Json.text(a,"color"),y=Json.text(b,"color");
        int score=neutral(x)||neutral(y)?10:0;
        try {
            int c=Integer.parseInt(x.substring(1),16),d=Integer.parseInt(y.substring(1),16);
            int l=((c>>16)&255)+((c>>8)&255)+(c&255),m=((d>>16)&255)+((d>>8)&255)+(d&255);
            score+=Math.min(5,Math.abs(l-m)/90);
        } catch(RuntimeException ignored) {}
        return score;
    }
    private static boolean neutral(String color) {
        try { int c=Integer.parseInt(color.substring(1),16),r=(c>>16)&255,g=(c>>8)&255,b=c&255;
            return Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<40 || (r<85&&g<100&&b<140) || (r>130&&g>100&&b>65&&r>=g&&g>=b);
        } catch(RuntimeException e) { return false; }
    }
}
