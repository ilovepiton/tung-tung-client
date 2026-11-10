package dev.amethyst.finder;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TexturePackSelectionTest {
    @Test void downloadedWhiteGlintIsAnIndependentAddon(){
        var variant=TexturePacks.variant(WhiteGlint.ID);
        assertEquals("addon",variant.group());assertEquals(WhiteGlint.FILE,variant.file());
    }
    @Test void whiteGlintStaysAboveMainPersonalAndTotemPacks(){
        var before=List.of("vanilla",WhiteGlint.PACK,"file/Tunk-Black.zip",MediaPack.ID,"file/TUNG-Totem-frog.zip");
        var result=TexturePacks.selection(before,"file/SevenStars-cold-white-30ffb592.zip",null);
        assertEquals(List.of("vanilla","file/SevenStars-cold-white-30ffb592.zip",MediaPack.ID,"file/TUNG-Totem-frog.zip",WhiteGlint.PACK),result);
        assertEquals(result,WhiteGlint.onTop(result));
    }
    @Test void whiteGlintOrderingDoesNotEnableADisabledAddon(){
        var before=List.of("vanilla",MediaPack.ID,"server/required");assertEquals(before,WhiteGlint.onTop(before));
        assertEquals(List.of("vanilla",WhiteGlint.PACK),WhiteGlint.onTop(List.of(WhiteGlint.PACK,"vanilla",WhiteGlint.PACK)));
    }
    @Test void sevenStarsAreSevenDistinctMainPacks(){
        var packs=TexturePacks.VARIANTS.stream().filter(v->v.id().startsWith("seven-")).toList();
        assertEquals(7,packs.size());assertEquals(7,packs.stream().map(TexturePacks.Variant::file).distinct().count());
        assertTrue(packs.stream().allMatch(v->v.group().equals("pack")));
    }
    @Test void everyMainPackReplacesEveryOtherManagedMainPack(){
        var variants=TexturePacks.VARIANTS.stream().filter(v->v.group().equals("pack")).toList();
        for(var old:variants)for(var next:variants){
            var result=TexturePacks.selection(List.of("vanilla","server/required","file/My-own-pack",old.aliases().getFirst()),next.aliases().getFirst(),null);
            assertEquals(List.of("vanilla","server/required","file/My-own-pack",next.aliases().getFirst()),result);
        }
    }
    @Test void selectingMainPreservesExplicitPersonalAndTotemPriority(){
        var result=TexturePacks.selection(List.of("vanilla","file/Tunk-Black.zip","file/Tunk-Extra-pvp-crosshair.zip",MediaPack.ID,"file/TUNG-Totem-frog.zip"),"file/SevenStars-cold-white-30ffb592.zip",null);
        assertEquals(List.of("vanilla","file/SevenStars-cold-white-30ffb592.zip","file/Tunk-Extra-pvp-crosshair.zip",MediaPack.ID,"file/TUNG-Totem-frog.zip"),result);
    }
    @Test void oldMouseChoicesFallBackWithoutChangingVolumes(){
        var config=new FinderConfig();config.mouseSoundProfile="click20";config.mouseButtonProfiles.put("1","click19");config.mouseButtonProfiles.put("0","click02");config.mouseButtonVolumes.put("1",.4);
        config.sanitize();assertEquals("click01",config.mouseSoundProfile);assertFalse(config.mouseButtonProfiles.containsKey("1"));assertEquals("click02",config.mouseButtonProfiles.get("0"));assertEquals(.4,config.mouseButtonVolumes.get("1"));
        assertEquals(7,MouseSounds.PROFILES.size());
    }
    @Test void removedOrInvalidPackCannotCrashStartup(){
        var config=new FinderConfig();config.pigPackVariant="removed-pack";config.sanitize();assertEquals("black",config.pigPackVariant);
        config.pigPackVariant=null;config.sanitize();assertEquals("black",config.pigPackVariant);
    }
}
