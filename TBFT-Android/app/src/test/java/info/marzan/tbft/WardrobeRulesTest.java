package info.marzan.tbft;

import org.json.*;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;

public class WardrobeRulesTest {
    private JSONObject doc;
    private static final String W="053dbac3-7105-4423-a9a4-4f21d3a5e583",U="cd820422-7a93-43f7-8c9e-7b67e620af66",DATE="2026-09-21",NOW="2026-09-21T12:00:00Z";
    @Before public void setup(){doc=WardrobeRules.fresh(W,U);}
    private String add(String name,String category,int quantity){WardrobeRules.saveItem(doc,"",name,category,"#FFFFFF","White","both","",quantity);List<JSONObject> items=WardrobeRules.list(WardrobeRules.state(doc),"items");return Json.text(items.get(items.size()-1),"id");}
    @Test public void eachPieceBelongsToExactlyOnePlace(){String id=add("White tee","tshirts",3);WardrobeRules.move(doc,id,"available","in_use",1);WardrobeRules.move(doc,id,"available","laundry",1);assertEquals(1,WardrobeRules.total(doc,"available"));assertEquals(1,WardrobeRules.total(doc,"in_use"));assertEquals(1,WardrobeRules.total(doc,"laundry"));assertEquals(3,WardrobeRules.total(doc,"all"));}
    @Test public void thresholdMakesOnlyOneDeterministicTask(){String id=add("Tee","tshirts",10);WardrobeRules.move(doc,id,"available","laundry",8);WardrobeRules.reconcile(doc,DATE);JSONObject batch=WardrobeRules.activeBatch(doc);assertNotNull(batch);String task=Json.text(batch,"task_id");WardrobeRules.move(doc,id,"available","laundry",1);WardrobeRules.reconcile(doc,DATE);assertEquals(task,Json.text(WardrobeRules.activeBatch(doc),"task_id"));assertEquals(1,WardrobeRules.list(WardrobeRules.state(doc),"batches").size());assertEquals(9,WardrobeRules.list(batch,"entries").size());}
    @Test public void reopeningOldTaskDoesNotWashTheNextLoad(){String id=add("Tee","tshirts",8);WardrobeRules.move(doc,id,"available","laundry",8);WardrobeRules.reconcile(doc,DATE);String task=Json.text(WardrobeRules.activeBatch(doc),"task_id");assertTrue(WardrobeRules.complete(doc,task,NOW));WardrobeRules.move(doc,id,"available","laundry",8);WardrobeRules.reconcile(doc,DATE);assertFalse(WardrobeRules.complete(doc,task,NOW));assertEquals(8,WardrobeRules.total(doc,"laundry"));assertNotEquals(task,Json.text(WardrobeRules.activeBatch(doc),"task_id"));}
    @Test public void oldSnapshotCannotClearAChangedLaundryToken(){String id=add("Tee","tshirts",1);Json.put(WardrobeRules.state(doc),"threshold",1);WardrobeRules.move(doc,id,"available","laundry",1);WardrobeRules.reconcile(doc,DATE);String task=Json.text(WardrobeRules.activeBatch(doc),"task_id");WardrobeRules.move(doc,id,"laundry","available",1);WardrobeRules.move(doc,id,"available","laundry",1);WardrobeRules.complete(doc,task,NOW);assertEquals(1,WardrobeRules.total(doc,"laundry"));}
    @Test public void finishBelowThresholdCreatesCompletedBatch(){String id=add("Tee","tshirts",1);WardrobeRules.move(doc,id,"available","laundry",1);WardrobeRules.finishBasket(doc,DATE);assertEquals(1,WardrobeRules.total(doc,"available"));assertNull(WardrobeRules.activeBatch(doc));assertEquals(8,WardrobeRules.state(doc).optInt("threshold"));}
    @Test public void zeroThresholdDisablesAutomaticTask(){String id=add("Tee","tshirts",10);Json.put(WardrobeRules.state(doc),"threshold",0);WardrobeRules.move(doc,id,"available","laundry",10);WardrobeRules.reconcile(doc,DATE);assertNull(WardrobeRules.activeBatch(doc));}
    @Test public void outfitNeedsAvailableTopAndBottom(){String top=add("Tee","tshirts",1),bottom=add("Jeans","jeans",1);assertEquals(1,WardrobeRules.suggestions(doc,"home").size());WardrobeRules.wear(doc,Arrays.asList(top,bottom));assertEquals(2,WardrobeRules.total(doc,"in_use"));assertTrue(WardrobeRules.suggestions(doc,"all").isEmpty());assertThrows(IllegalArgumentException.class,()->WardrobeRules.wear(doc,Arrays.asList(top,bottom)));}
    @Test public void cannotShrinkAwayClothesInUse(){String id=add("Tee","tshirts",2);WardrobeRules.move(doc,id,"available","in_use",2);assertThrows(IllegalArgumentException.class,()->WardrobeRules.saveItem(doc,id,"Tee","tshirts","#FFFFFF","White","both","",1));}
    @Test public void appearanceDefaultsWorkWithOlderItems(){
        String tee=add("Tee","tshirts",1),hoodie=add("Hoodie","hoodies",1),tank=add("Tank","tanks",1),underwear=add("Briefs","underwear",1);
        assertEquals("short",WardrobeRules.sleeve(doc,WardrobeRules.item(doc,tee)));
        assertEquals("long",WardrobeRules.sleeve(doc,WardrobeRules.item(doc,hoodie)));assertTrue(WardrobeRules.hood(doc,WardrobeRules.item(doc,hoodie)));
        assertEquals("tank",WardrobeRules.shape(doc,WardrobeRules.item(doc,tank)));assertEquals("none",WardrobeRules.sleeve(doc,WardrobeRules.item(doc,tank)));
        assertEquals("briefs",WardrobeRules.shape(doc,WardrobeRules.item(doc,underwear)));
    }
    @Test public void sleeveAndHoodAreIndependentAndSurviveOlderEdits(){
        String id=add("Light jacket","jackets",2);WardrobeRules.appearance(doc,id,"jacket","short","yes");
        JSONObject g=WardrobeRules.item(doc,id);assertEquals("short",WardrobeRules.sleeve(doc,g));assertTrue(WardrobeRules.hood(doc,g));
        WardrobeRules.saveItem(doc,id,"Edited jacket","jackets","#AB19EF","Purple","both","",2);
        assertTrue(WardrobeRules.hood(doc,g));assertEquals("short",WardrobeRules.sleeve(doc,g));
        WardrobeRules.appearance(doc,id,"hoodie","long","no");assertFalse(WardrobeRules.hood(doc,g));assertEquals("long",WardrobeRules.sleeve(doc,g));
        assertThrows(IllegalArgumentException.class,()->WardrobeRules.appearance(doc,id,"jacket","broken","yes"));
    }
    @Test public void newCompartmentsAreAddedOnceWithoutChangingExistingOnes(){
        JSONArray categories=WardrobeRules.state(doc).optJSONArray("categories");
        for(int i=categories.length()-1;i>=0;i--)if(Arrays.asList("underwear","tanks").contains(Json.text(categories.optJSONObject(i),"id")))categories.remove(i);
        WardrobeRules.category(doc,"Camping layers","layer");int before=categories.length();
        WardrobeRules.ensureCategories(doc);WardrobeRules.ensureCategories(doc);assertEquals(before+2,categories.length());assertEquals("Tank tops",WardrobeRules.categoryName(doc,"tanks"));
    }
    @Test public void removingShelfMovesClothesWithoutChangingTheirStateOrLook(){
        String id=add("Tee","tshirts",3);WardrobeRules.move(doc,id,"available","in_use",1);WardrobeRules.move(doc,id,"available","laundry",1);WardrobeRules.reconcile(doc,DATE);
        JSONObject pieces=Json.of("pieces",Json.copy(WardrobeRules.item(doc,id)).optJSONArray("pieces"));
        WardrobeRules.removeCategory(doc,"tshirts","jeans");JSONObject item=WardrobeRules.item(doc,id);
        assertEquals("jeans",Json.text(item,"category"));assertEquals("tshirt",WardrobeRules.shape(doc,item));assertEquals("short",WardrobeRules.sleeve(doc,item));
        assertEquals(pieces.toString(),Json.of("pieces",item.optJSONArray("pieces")).toString());
        assertEquals(1,WardrobeRules.count(item,"available"));assertEquals(1,WardrobeRules.count(item,"in_use"));assertEquals(1,WardrobeRules.count(item,"laundry"));
    }
    @Test public void deletedDefaultShelfDoesNotReturnOnRefresh(){
        WardrobeRules.removeCategory(doc,"underwear","");WardrobeRules.removeCategory(doc,"tanks","");WardrobeRules.ensureCategories(doc);
        assertFalse(WardrobeRules.list(WardrobeRules.state(doc),"categories").stream().anyMatch(c->Arrays.asList("underwear","tanks").contains(Json.text(c,"id"))));
    }
    @Test public void occupiedShelfNeedsValidDestinationAndNamesStayUnique(){
        add("Tee","tshirts",1);assertThrows(IllegalArgumentException.class,()->WardrobeRules.removeCategory(doc,"tshirts",""));
        assertThrows(IllegalArgumentException.class,()->WardrobeRules.removeCategory(doc,"tshirts","tshirts"));assertEquals(1,WardrobeRules.total(doc,"all"));
        assertThrows(IllegalArgumentException.class,()->WardrobeRules.renameCategory(doc,"tshirts","Shirts"));
        WardrobeRules.renameCategory(doc,"tshirts","Everyday tops");assertEquals("Everyday tops",WardrobeRules.categoryName(doc,"tshirts"));
    }
    @Test public void documentEqualityIgnoresObjectKeyOrder(){assertTrue(SyncRules.equal(Json.object("{\"a\":1,\"b\":[{\"x\":2,\"y\":3}]}"),Json.object("{\"b\":[{\"y\":3,\"x\":2}],\"a\":1.0}")));}
}
