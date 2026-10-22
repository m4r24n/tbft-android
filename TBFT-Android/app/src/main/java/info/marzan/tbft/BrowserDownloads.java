package info.marzan.tbft;

import java.io.*;
import java.net.*;
import java.util.function.*;

/** Streams to the Android document picker destination; cookies are selected anew per host. */
final class BrowserDownloads {
    static String filename(String suggested) {
        String name=suggested==null?"download":suggested.replaceAll("[\\\\/\\p{Cntrl}]","_").replaceAll("^\\.+","").trim();
        if(name.isEmpty())name="download";
        return name.length()>180?name.substring(0,180):name;
    }
    static URL secure(String url) throws IOException {
        URL parsed=new URL(url);
        if(!parsed.getProtocol().equals("https")||parsed.getHost().isEmpty()||parsed.getUserInfo()!=null)throw new IOException("Only secure HTTPS downloads are supported.");
        return parsed;
    }
    static void save(String source,String agent,Function<String,String> cookies,OutputStream output,BooleanSupplier cancelled,LongConsumer progress) throws IOException {
        save(source,agent,cookies,output,cancelled,progress,url->(HttpURLConnection)url.openConnection());
    }
    interface ConnectionFactory { HttpURLConnection open(URL url) throws IOException; }
    static void save(String source,String agent,Function<String,String> cookies,OutputStream output,BooleanSupplier cancelled,LongConsumer progress,ConnectionFactory factory) throws IOException {
        URL url=secure(source);
        for(int redirects=0;redirects<=8;redirects++) {
            if(cancelled.getAsBoolean())throw new InterruptedIOException("Cancelled");
            HttpURLConnection connection=factory.open(url);connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(20000);
            try {
                connection.setRequestProperty("User-Agent",agent);
                String cookie=cookies.apply(url.toString());if(cookie!=null&&!cookie.isEmpty())connection.setRequestProperty("Cookie",cookie);
                int code=connection.getResponseCode();
                if(code==301||code==302||code==303||code==307||code==308){String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Invalid download redirect");url=secure(new URL(url,location).toString());continue;}
                if(code<200||code>=300)throw new IOException(code==401||code==403?"This website did not allow the download. Sign in there and try again.":"Download failed (HTTP "+code+").");
                long expected=connection.getContentLengthLong(),total=0,last=0;
                try(InputStream input=connection.getInputStream()) {byte[] buffer=new byte[65536];int n;
                    while((n=input.read(buffer))!=-1){if(cancelled.getAsBoolean())throw new InterruptedIOException("Cancelled");output.write(buffer,0,n);total+=n;
                        if(total-last>262144){progress.accept(total);last=total;}}
                }
                if(expected>=0&&total!=expected)throw new IOException("Connection ended before the file was complete. Please retry.");
                output.flush();progress.accept(total);return;
            } finally {connection.disconnect();}
        }
        throw new IOException("Too many download redirects.");
    }
}
