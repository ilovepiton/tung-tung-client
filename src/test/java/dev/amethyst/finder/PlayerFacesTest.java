package dev.amethyst.finder;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.google.common.collect.ArrayListMultimap;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerFacesTest {
    private GameProfile profile(String url) {
        var properties = ArrayListMultimap.<String,Property>create();
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        properties.put("textures", new Property("textures", Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8))));
        return new GameProfile(UUID.randomUUID(), "FaceTest", new PropertyMap(properties));
    }
    @Test void onlyOfficialTextureHashesAreAccepted() {
        String hash = "a".repeat(64);
        assertEquals("https://textures.minecraft.net/texture/"+hash, PlayerFaces.skinUrl(profile("http://textures.minecraft.net/texture/"+hash)));
        for (String bad : new String[]{"https://example.com/texture/"+hash,"https://textures.minecraft.net.evil.test/texture/"+hash,"file:///tmp/skin.png","https://textures.minecraft.net/texture/../../private"})
            assertEquals("",PlayerFaces.skinUrl(profile(bad)));
    }
    @Test void faceUsesActualFrontAndTransparentHatLayer() throws Exception {
        BufferedImage skin=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for(int x=8;x<16;x++)for(int y=8;y<16;y++)skin.setRGB(x,y,0xffcc8855);
        skin.setRGB(42,11,0xff3366ff);
        ByteArrayOutputStream data=new ByteArrayOutputStream();ImageIO.write(skin,"png",data);
        String encoded=PlayerFaces.crop(data.toByteArray());
        BufferedImage face=ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(encoded.substring(encoded.indexOf(',')+1))));
        assertEquals(8,face.getWidth());assertEquals(8,face.getHeight());
        assertEquals(0xffcc8855,face.getRGB(0,0));assertEquals(0xff3366ff,face.getRGB(2,3));
    }
    @Test void malformedImagesAreRejected() {
        assertThrows(java.io.IOException.class,()->PlayerFaces.crop(new byte[]{1,2,3}));
    }
}
