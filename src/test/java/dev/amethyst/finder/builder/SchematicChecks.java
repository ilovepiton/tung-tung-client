package dev.amethyst.finder.builder;
import java.nio.file.*;
import java.io.*;

public final class SchematicChecks {
    static Path fixtures;static int checks;
    static void check(String name,boolean ok){if(!ok)throw new AssertionError(name);checks++;System.out.println("PASS: "+name);}
    static SchematicFile load(String name)throws Exception{return SchematicFile.read(fixtures.resolve(name));}
    static void rejects(String name)throws Exception{try{load(name);throw new AssertionError("Accepted invalid file "+name);}catch(IOException expected){check("Rejects "+name,true);}}
    public static void main(String[] args)throws Exception {
        fixtures=Path.of(args[0]);
        var v2=load("wall.schem");check("Sponge v2 retains coordinates and skips air",v2.width()==3&&v2.height()==2&&v2.length()==2&&v2.cells().size()==6);
        check("State properties survive import",v2.cells().stream().anyMatch(c->c.state().contains("axis=x")));
        var v3=load("wrapped.schem");check("Sponge v3 named wrapper and nested block palette",v3.cells().equals(v2.cells()));
        var signed=load("signed.litematic");check("Signed Litematica region is normalized to minimum corner",signed.width()==2&&signed.length()==2&&signed.cells().size()==3&&signed.cells().getFirst().x()==1);
        var cross=load("cross-word.litematic");check("Litematica packed values crossing a 64-bit word survive",cross.cells().size()==23&&cross.cells().get(21).state().equals("minecraft:glass"));
        var multi=load("multi.litematic");check("Multiple separated Litematica regions retain their relative offsets",multi.width()==6&&multi.cells().size()==2);
        var structure=load("structure.nbt");check("Vanilla structure palette and sparse positions",structure.cells().size()==2&&structure.cells().get(1).x()==2);
        check("Block entity data is explicitly reported",!structure.warnings().isEmpty());
        check("Plain, uncompressed NBT is supported",load("plain.nbt").cells().equals(structure.cells()));
        for(String name:new String[]{"legacy.schematic","bad-index.schem","truncated.schem","oversized.schem","bad-varint.schem","extra-data.schem","bad-state.schem","duplicate-key.nbt","negative-array.nbt","deep.nbt","invalid-packed.litematic","conflict.litematic","empty.schem"})rejects(name);
        System.out.println("All "+checks+" schematic checks passed.");
    }
}
