package dev.amethyst.finder;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AntiLeakSelectionTest {
    private static final String N="file/Anti-Leak-Netherite.zip",S="file/Anti-Leak-Stone.zip",D="file/Anti-Leak-Slate.zip";
    @Test void exactlyThreeSeparateThemes(){
        var themes=TexturePacks.VARIANTS.stream().filter(AntiLeak::variant).toList();
        assertEquals(3,themes.size());assertTrue(themes.stream().allMatch(v->v.group().equals("anti-leak")));assertEquals(3,themes.stream().map(TexturePacks.Variant::file).distinct().count());
    }
    @Test void switchingThemesIsExclusiveAndPreservesOtherPacks(){
        var base=List.of("vanilla","server/required","file/MyPack.zip",WhiteGlint.PACK,MediaPack.ID,"file/TUNG-Totem-frog.zip");
        for(String old:List.of(N,S,D))for(String next:List.of(N,S,D)){
            var current=new ArrayList<>(base);current.add(old);var expected=new ArrayList<>(base);expected.add(next);assertEquals(expected,AntiLeak.selection(current,next));
        }
    }
    @Test void offRemovesOnlyMasks(){assertEquals(List.of("vanilla","server/required",MediaPack.ID),AntiLeak.selection(List.of(N,"vanilla",S,"server/required",MediaPack.ID,D),null));}
    @Test void packChangesKeepOneMaskAtHighestPriority(){
        var before=List.of("vanilla",N,"file/Tunk-Black.zip",MediaPack.ID,WhiteGlint.PACK);
        var after=TexturePacks.selection(before,"file/Tunk-White.zip",null);
        assertEquals(List.of("vanilla","file/Tunk-White.zip",MediaPack.ID,WhiteGlint.PACK,N),after);
        assertEquals(List.of("vanilla",D),AntiLeak.onTop(List.of(N,"vanilla",S,N,D)));
    }
    @Test void normalizationNeverEnablesAnOffMask(){var off=List.of("vanilla",MediaPack.ID);assertEquals(off,AntiLeak.onTop(off));assertThrows(IllegalArgumentException.class,()->AntiLeak.selection(off,"file/Unknown.zip"));}
}
