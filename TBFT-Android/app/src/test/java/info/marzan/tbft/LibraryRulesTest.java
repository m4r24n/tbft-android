package info.marzan.tbft;

import org.json.JSONObject;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;

public class LibraryRulesTest {
    private JSONObject doc;
    @Before public void setup(){doc=WardrobeRules.fresh("workspace","owner");LibraryRules.ensure(doc);}
    private String add(String title,String shelf,String status){LibraryRules.saveBook(doc,"",title,"Author",shelf,"paper",status,25,"#607D68","note");List<JSONObject> books=LibraryRules.books(doc);return Json.text(books.get(books.size()-1),"id");}
    @Test public void booksKeepReadingStateAndFormat(){String id=add("The Quiet Book","learning","reading");JSONObject book=LibraryRules.book(doc,id);assertEquals("reading",Json.text(book,"status"));assertEquals(25,book.optInt("progress"));assertEquals("paper",Json.text(book,"format"));}
    @Test public void finishedBookAlwaysHasFullProgress(){String id=add("Finished","fiction","finished");assertEquals(100,LibraryRules.book(doc,id).optInt("progress"));}
    @Test public void deletingShelfMovesBooksFirst(){String id=add("Reference","reference","unread");LibraryRules.removeShelf(doc,"reference","learning");assertEquals("learning",Json.text(LibraryRules.book(doc,id),"shelf"));assertEquals(1,LibraryRules.count(doc,"learning","all"));}
    @Test public void occupiedShelfRequiresDestination(){add("Book","fiction","unread");assertThrows(IllegalArgumentException.class,()->LibraryRules.removeShelf(doc,"fiction",""));assertEquals(1,LibraryRules.count(doc,"fiction","all"));}
    @Test public void statusAndShelfFiltersAreIndependent(){add("One","fiction","reading");add("Two","fiction","unread");add("Three","work","reading");assertEquals(2,LibraryRules.count(doc,"fiction","all"));assertEquals(1,LibraryRules.count(doc,"fiction","reading"));assertEquals(2,LibraryRules.count(doc,"all","reading"));}
    @Test public void validationRejectsBadProgressAndColour(){assertThrows(IllegalArgumentException.class,()->LibraryRules.saveBook(doc,"","Book","","fiction","paper","reading",101,"#FFFFFF",""));assertThrows(IllegalArgumentException.class,()->LibraryRules.saveBook(doc,"","Book","","fiction","paper","unread",0,"white",""));}
}
