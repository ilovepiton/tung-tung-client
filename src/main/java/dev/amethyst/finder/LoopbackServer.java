package dev.amethyst.finder;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.*;
import java.util.function.Function;

/** Minimal bounded HTTP transport using only Java SE, including trimmed Minecraft Java runtimes. */
public final class LoopbackServer implements AutoCloseable {
    public record Request(String method,String path,String token,byte[] body) {}
    public record Response(int status,String json) {}
    private final ServerSocket listener;
    private final ThreadPoolExecutor workers;
    private volatile boolean closed;
    public LoopbackServer(Function<Request,Response> handler) throws IOException {
        listener=new ServerSocket();listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),8);
        workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"Tung Tung Client IPC");t.setDaemon(true);return t;});
        Thread acceptor=new Thread(()->{
            while(!closed)try {
                Socket socket=listener.accept();
                try {workers.execute(()->serve(socket,handler));}catch(RejectedExecutionException ex){socket.close();}
            }catch(IOException ignored){if(closed)return;}
        },"Tung Tung Client IPC listener");acceptor.setDaemon(true);acceptor.start();
    }
    public int port(){return listener.getLocalPort();}
    private static void serve(Socket socket,Function<Request,Response> handler) {
        try(socket){
            socket.setSoTimeout(4000);InputStream input=new BufferedInputStream(socket.getInputStream());
            String[] start=line(input,4096).split(" ");if(start.length!=3)throw new IOException("Invalid request");
            Map<String,String> headers=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for(int n=0;n<64;n++){
                String h=line(input,4096);if(h.isEmpty())break;if(n==63)throw new IOException("Too many headers");
                int colon=h.indexOf(':');if(colon<=0)throw new IOException("Invalid header");headers.put(h.substring(0,colon),h.substring(colon+1).trim());
            }
            if(headers.containsKey("Transfer-Encoding"))throw new IOException("Chunked requests unsupported");
            int length=Integer.parseInt(headers.getOrDefault("Content-Length","0"));
            if(length<0||length>65536)throw new IOException("Request too large");
            byte[] body=input.readNBytes(length);if(body.length!=length)throw new IOException("Incomplete request");
            Response response=handler.apply(new Request(start[0],start[1].split("\\?",2)[0],headers.get("X-Tun-Token"),body));
            byte[] bytes=response.json.getBytes(StandardCharsets.UTF_8);
            String head="HTTP/1.1 "+response.status+" "+(response.status==200?"OK":"Request rejected")+"\r\nContent-Type: application/json; charset=utf-8\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Length: "+bytes.length+"\r\n\r\n";
            OutputStream output=socket.getOutputStream();output.write(head.getBytes(StandardCharsets.US_ASCII));output.write(bytes);output.flush();
        }catch(IOException|RuntimeException ignored){}
    }
    private static String line(InputStream input,int maximum)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        for(int n=0;n<maximum;n++){int ch=input.read();if(ch<0)throw new EOFException();if(ch=='\n')return bytes.toString(StandardCharsets.US_ASCII).replace("\r","");bytes.write(ch);}
        throw new IOException("Header line too long");
    }
    @Override public void close(){closed=true;try{listener.close();}catch(IOException ignored){}workers.shutdownNow();}
}
