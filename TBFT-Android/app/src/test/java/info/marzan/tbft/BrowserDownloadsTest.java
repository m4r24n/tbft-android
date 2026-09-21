package info.marzan.tbft;
import org.junit.Test;
import static org.junit.Assert.*;
public class BrowserDownloadsTest {
    @Test public void namesCannotContainTraversalOrControlCharacters(){assertEquals("_report.pdf",BrowserDownloads.filename("../report.pdf"));assertEquals("a_b.txt",BrowserDownloads.filename("a\nb.txt"));assertEquals("download",BrowserDownloads.filename(".."));}
    @Test public void insecureAndCredentialUrlsAreRejected(){for(String url:new String[]{"http://example.org/file","file:///private/file","https://user:password@example.org/a"})assertThrows(java.io.IOException.class,()->BrowserDownloads.secure(url));}
    @Test public void secureLinkRetainsItsSignedQuery()throws Exception{assertEquals("https://example.org/file?token=abc",BrowserDownloads.secure("https://example.org/file?token=abc").toString());}
}
