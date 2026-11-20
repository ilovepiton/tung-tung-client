package dev.amethyst.finder.builder;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Bounded, game-independent reader for Sponge v1–3, Litematica and vanilla structure NBT. */
public record SchematicFile(String name, String format, int width, int height, int length,
                            List<Cell> cells, List<String> warnings) {
    public static final int MAX_VOLUME=1_000_000, MAX_BLOCKS=200_000, MAX_BYTES=16*1024*1024;
    public record Cell(int x,int y,int z,String state) {}
    public static SchematicFile read(Path path) throws IOException {
        if(Files.size(path)>MAX_BYTES)throw bad("File exceeds 16 MB");
        try(var stream=new BufferedInputStream(Files.newInputStream(path))) {
            stream.mark(2);int a=stream.read(),b=stream.read();stream.reset();
            InputStream decoded=a==31&&b==139?new GZIPInputStream(stream):stream;
            var in=new DataInputStream(new LimitedInput(decoded,64*1024*1024));
            if(in.readUnsignedByte()!=10)throw bad("Expected a named NBT compound");
            in.readUTF();
            Map<String,Object> root=map(new Reader(in).tag(10,0));
            if(root.containsKey("Schematic"))root=map(root.get("Schematic"));
            if(root.containsKey("Regions"))return litematic(root,path.getFileName().toString());
            if(root.containsKey("palette")&&root.containsKey("blocks"))return structure(root,path.getFileName().toString());
            if(root.get("Blocks") instanceof byte[])throw bad("Legacy numeric .schematic: export it as Sponge .schem or .litematic first");
            return sponge(root,path.getFileName().toString());
        } catch(ClassCastException|ArithmeticException ex) { throw bad("Invalid NBT field type or dimensions"); }
    }
    private static SchematicFile sponge(Map<String,Object> r,String name)throws IOException {
        int version=integer(r,"Version");if(version<1||version>3)throw bad("Supported Sponge versions: 1, 2, 3");
        int w=integer(r,"Width")&65535,h=integer(r,"Height")&65535,l=integer(r,"Length")&65535;
        int volume=volume(w,h,l);Map<String,Object> blocks=version==3?map(r.get("Blocks")):r;
        Map<String,Object> raw=map(blocks.get("Palette"));
        if(raw.isEmpty()||raw.size()>4096)throw bad("Invalid block palette (maximum 4096 states)");
        Map<Integer,String> palette=new HashMap<>();
        for(var e:raw.entrySet()) { int id=num(e.getValue());if(id<0||id>=4096||palette.put(id,state(e.getKey()))!=null)throw bad("Invalid or duplicate palette index"); }
        byte[] data=bytes(blocks.get(version==3?"Data":"BlockData"));
        int offset=0;List<Cell> cells=new ArrayList<>();
        for(int i=0;i<volume;i++) {
            int value=0,shift=0,n;
            do {if(offset>=data.length||shift>=35)throw bad("Truncated or oversized block VarInt");n=data[offset++]&255;if(shift==28&&(n&240)!=0)throw bad("Invalid block VarInt");value|=(n&127)<<shift;shift+=7;}while((n&128)!=0);
            String s=palette.get(value);if(s==null)throw bad("Missing palette index "+value);
            add(cells,i%w,i/(w*l),i/w%l,s);
        }
        if(offset!=data.length)throw bad("Block data length does not match dimensions");
        return finish(name,"Sponge "+version,w,h,l,cells,hasData(r)||hasData(blocks));
    }
    private static SchematicFile litematic(Map<String,Object> r,String filename)throws IOException {
        int version=integer(r,"Version");if(version<4||version>7)throw bad("Supported Litematica versions: 4–7");
        Map<String,Object> regions=map(r.get("Regions"));if(regions.isEmpty()||regions.size()>64)throw bad("Choose 1–64 regions");
        List<Cell> all=new ArrayList<>();boolean extra=false;long total=0;
        int minX=Integer.MAX_VALUE,minY=minX,minZ=minX,maxX=Integer.MIN_VALUE,maxY=maxX,maxZ=maxX;
        for(Object region:regions.values()) {
            var reg=map(region);var size=map(reg.get("Size"));var pos=map(reg.get("Position"));
            int sx=integer(size,"x"),sy=integer(size,"y"),sz=integer(size,"z"),w=Math.abs(sx),h=Math.abs(sy),l=Math.abs(sz);
            int count=volume(w,h,l);total+=count;if(total>MAX_VOLUME)throw bad("Combined regions exceed 1,000,000 voxels");
            int x=coordinate(integer(pos,"x")),y=coordinate(integer(pos,"y")),z=coordinate(integer(pos,"z"));
            x+=Math.min(0,sx+1);y+=Math.min(0,sy+1);z+=Math.min(0,sz+1);
            minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x+w);maxY=Math.max(maxY,y+h);maxZ=Math.max(maxZ,z+l);
            List<?> raw=list(reg.get("BlockStatePalette"));if(raw.isEmpty()||raw.size()>4096)throw bad("Invalid Litematica palette");
            List<String> palette=new ArrayList<>();for(Object p:raw)palette.add(paletteState(map(p)));
            int bits=Math.max(2,32-Integer.numberOfLeadingZeros(palette.size()-1));
            long[] data=longs(reg.get("BlockStates"));if(data.length!=((long)count*bits+63)/64)throw bad("Invalid packed block data length");
            long mask=(1L<<bits)-1;
            for(int i=0;i<count;i++) {
                long start=(long)i*bits;int word=(int)(start>>>6),bit=(int)(start&63);long id=data[word]>>>bit;
                if(bit+bits>64)id|=data[word+1]<<(64-bit);id&=mask;
                if(id>=palette.size())throw bad("Missing Litematica palette index");
                add(all,x+i%w,y+i/(w*l),z+i/w%l,palette.get((int)id));
            }
            extra|=hasData(reg);
        }
        int w=maxX-minX,h=maxY-minY,l=maxZ-minZ;volume(w,h,l);
        Map<String,Cell> unique=new LinkedHashMap<>();
        for(Cell c:all) {
            Cell normalized=new Cell(c.x-minX,c.y-minY,c.z-minZ,c.state);
            String key=normalized.x+","+normalized.y+","+normalized.z;
            Cell old=unique.putIfAbsent(key,normalized);
            if(old!=null&&!old.state.equals(normalized.state))throw bad("Overlapping regions contain conflicting blocks");
        }
        String name=r.get("Metadata") instanceof Map<?,?> m&&m.get("Name") instanceof String s?s:filename;
        return finish(name,"Litematica "+version,w,h,l,new ArrayList<>(unique.values()),extra);
    }
    private static SchematicFile structure(Map<String,Object> r,String name)throws IOException {
        List<?> size=list(r.get("size"));if(size.size()!=3)throw bad("Invalid structure size");
        int w=num(size.get(0)),h=num(size.get(1)),l=num(size.get(2));volume(w,h,l);
        List<?> raw=list(r.get("palette"));if(raw.isEmpty()||raw.size()>4096)throw bad("Invalid structure palette");
        List<String> palette=new ArrayList<>();for(Object p:raw)palette.add(paletteState(map(p)));
        List<Cell> cells=new ArrayList<>();Set<String> seen=new HashSet<>();boolean extra=false;
        for(Object value:list(r.get("blocks"))) {
            var block=map(value);var p=list(block.get("pos"));if(p.size()!=3)throw bad("Invalid block position");
            int x=num(p.get(0)),y=num(p.get(1)),z=num(p.get(2)),id=integer(block,"state");
            if(x<0||x>=w||y<0||y>=h||z<0||z>=l||id<0||id>=palette.size()||!seen.add(x+","+y+","+z))throw bad("Invalid or duplicate structure block");
            add(cells,x,y,z,palette.get(id));extra|=block.containsKey("nbt");
        }
        return finish(name,"Structure NBT",w,h,l,cells,extra||hasData(r));
    }
    private static SchematicFile finish(String name,String format,int w,int h,int l,List<Cell> cells,boolean extra)throws IOException {
        if(cells.isEmpty())throw bad("Schematic contains no solid blocks");
        cells.sort(Comparator.comparingInt(Cell::y).thenComparingInt(Cell::z).thenComparingInt(Cell::x));
        return new SchematicFile(name.substring(0,Math.min(name.length(),120)),format,w,h,l,List.copyOf(cells),extra?List.of("Entities, inventories and block-entity data are not copied; only block states are built."):List.of());
    }
    private static void add(List<Cell> cells,int x,int y,int z,String state)throws IOException {
        if(Set.of("minecraft:air","minecraft:cave_air","minecraft:void_air","minecraft:structure_void").contains(state))return;
        if(cells.size()>=MAX_BLOCKS)throw bad("Maximum 200,000 non-air blocks");cells.add(new Cell(x,y,z,state));
    }
    private static String paletteState(Map<String,Object> p)throws IOException {
        if(!(p.get("Name") instanceof String s))throw bad("Missing palette block name");
        StringBuilder out=new StringBuilder(s);
        if(p.get("Properties") instanceof Map<?,?> props&&!props.isEmpty()) {
            var pairs=new TreeMap<String,String>();for(var e:props.entrySet())pairs.put(String.valueOf(e.getKey()),String.valueOf(e.getValue()));
            out.append('[');boolean first=true;for(var e:pairs.entrySet()){if(!first)out.append(',');first=false;out.append(e.getKey()).append('=').append(e.getValue());}out.append(']');
        }
        return state(out.toString());
    }
    private static String state(String s)throws IOException {
        if(s.length()>512||!s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+(\\[[a-z0-9_=,.-]+\\])?"))throw bad("Invalid block-state name");return s;
    }
    private static boolean hasData(Map<String,Object> m){for(String k:List.of("Entities","BlockEntities","TileEntities","entities"))if(m.get(k) instanceof List<?> l&&!l.isEmpty())return true;return false;}
    public static int volume(int w,int h,int l)throws IOException {if(w<1||h<1||l<1||w>512||h>512||l>512||(long)w*h*l>MAX_VOLUME)throw bad("Maximum bounds: 512 per side and 1,000,000 voxels");return w*h*l;}
    private static int coordinate(int n)throws IOException {if(Math.abs((long)n)>1_000_000)throw bad("Region position exceeds supported bounds");return n;}
    private static int integer(Map<String,Object> m,String k)throws IOException {return num(m.get(k));}
    private static int num(Object v)throws IOException {if(!(v instanceof Byte||v instanceof Short||v instanceof Integer))throw bad("Expected integer NBT field");return ((Number)v).intValue();}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object v)throws IOException {if(!(v instanceof Map))throw bad("Expected NBT compound");return (Map<String,Object>)v;}
    private static List<?> list(Object v)throws IOException {if(!(v instanceof List<?> l))throw bad("Expected NBT list");return l;}
    private static byte[] bytes(Object v)throws IOException {if(!(v instanceof byte[] b))throw bad("Expected byte array");return b;}
    private static long[] longs(Object v)throws IOException {if(!(v instanceof long[] b))throw bad("Expected long array");return b;}
    private static IOException bad(String message){return new IOException(message);}
    private static final class LimitedInput extends FilterInputStream {
        int left;LimitedInput(InputStream stream,int limit){super(stream);left=limit;}
        @Override public int read()throws IOException {if(left<=0)throw bad("Decompressed NBT exceeds 64 MB");int b=super.read();if(b>=0)left--;return b;}
        @Override public int read(byte[] b,int off,int len)throws IOException {if(left<=0)throw bad("Decompressed NBT exceeds 64 MB");int n=super.read(b,off,Math.min(left,len));if(n>0)left-=n;return n;}
    }
    private static final class Reader {
        final DataInputStream in;int count;Reader(DataInputStream in){this.in=in;}
        int length(int bytes)throws IOException {int n=in.readInt();if(n<0||n>16*1024*1024/bytes)throw bad("NBT array exceeds bounds");return n;}
        Object tag(int type,int depth)throws IOException {
            if(depth>48||++count>2_000_000)throw bad("NBT nesting or tag count exceeds bounds");
            return switch(type) {
                case 1->in.readByte();case 2->in.readShort();case 3->in.readInt();case 4->in.readLong();case 5->in.readFloat();case 6->in.readDouble();
                case 7->{int n=length(1);byte[] a=new byte[n];in.readFully(a);yield a;}
                case 8->in.readUTF();
                case 9->{int t=in.readUnsignedByte(),n=length(8);if(t==0&&n>0)throw bad("Invalid list type");var a=new ArrayList<>(n);for(int i=0;i<n;i++)a.add(tag(t,depth+1));yield a;}
                case 10->{var m=new LinkedHashMap<String,Object>();for(int t;(t=in.readUnsignedByte())!=0;){String key=in.readUTF();if(m.putIfAbsent(key,tag(t,depth+1))!=null)throw bad("Duplicate NBT key");}yield m;}
                case 11->{int n=length(4);int[] a=new int[n];for(int i=0;i<n;i++)a[i]=in.readInt();yield a;}
                case 12->{int n=length(8);long[] a=new long[n];for(int i=0;i<n;i++)a[i]=in.readLong();yield a;}
                default->throw bad("Unsupported NBT tag "+type);
            };
        }
    }
}
