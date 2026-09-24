package info.marzan.tbft;
import org.junit.Test;
import static org.junit.Assert.*;
public class BrowserDownloadsTest {
    @Test public void namesCannotContainTraversalOrControlCharacters(){assertEquals("_report.pdf",BrowserDownloads.filename("../report.pdf"));assertEquals("a_b.txt",BrowserDownloads.filename("a\nb.txt"));assertEquals("download",BrowserDownloads.filename(".."));}
    @Test public void insecureAndCredentialUrlsAreRejected(){for(String url:new String[]{"http://example.org/file","file:///private/file","https://user:password@example.org/a"})assertThrows(java.io.IOException.class,()->BrowserDownloads.secure(url));}
    @Test public void secureLinkRetainsItsSignedQuery()throws Exception{assertEquals("https://example.org/file?token=abc",BrowserDownloads.secure("https://example.org/file?token=abc").toString());}
    @Test public void streamingPreservesBytesAndSelectsCookiesForEachHost()throws Exception{
        java.util.List<Fake> connections=new java.util.ArrayList<>();java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        BrowserDownloads.save("https://first.example/file","TBFT",url->url.contains("first.example")?"first=secret":"second=other",out,()->false,n->{},url->{Fake f=new Fake(url);connections.add(f);return f;});
        assertArrayEquals(new byte[]{1,2,3},out.toByteArray());assertEquals(2,connections.size());assertEquals("first=secret",connections.get(0).getRequestProperty("Cookie"));assertEquals("second=other",connections.get(1).getRequestProperty("Cookie"));assertTrue(connections.stream().allMatch(f->f.closed));
    }
    @Test public void incompleteDownloadIsReportedInsteadOfSuccess()throws Exception{
        assertThrows(java.io.IOException.class,()->BrowserDownloads.save("https://second.example/file","TBFT",u->null,new java.io.ByteArrayOutputStream(),()->false,n->{},url->new Fake(url){@Override public long getContentLengthLong(){return 50;}}));
    }
    @Test public void cancellationStopsBeforeNetworkConnection()throws Exception{
        assertThrows(java.io.InterruptedIOException.class,()->BrowserDownloads.save("https://second.example/file","TBFT",u->null,new java.io.ByteArrayOutputStream(),()->true,n->{},url->{throw new AssertionError("Must not open connection");}));
    }
    private static class Fake extends java.net.HttpURLConnection{
        boolean closed;Fake(java.net.URL url){super(url);}
        @Override public int getResponseCode(){return url.getHost().equals("first.example")?302:200;}
        @Override public String getHeaderField(String key){return key.equals("Location")?"https://second.example/file":null;}
        @Override public long getContentLengthLong(){return 3;}
        @Override public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(new byte[]{1,2,3});}
        @Override public void disconnect(){closed=true;}
        @Override public boolean usingProxy(){return false;}
        @Override public void connect(){}
    }
}
