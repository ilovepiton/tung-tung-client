package dev.amethyst.finder;

import dev.amethyst.finder.history.ObservationScope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.storage.LevelResource;
import java.util.Locale;
import java.util.UUID;

/** Resolve known world identity once per level/profile, without probing the server. */
public final class HistoryContext {
    private static ClientLevel cachedLevel;
    private static String cachedProfile;
    private static ObservationScope scope;
    private static String label="No world";
    private static Object unknownConnection;
    private static String unknownSession;
    private HistoryContext() {}
    public static ObservationScope resolve(Minecraft client,ClientLevel level,FinderConfig config) {
        if (level==null) { invalidate();return null; }
        if (level==cachedLevel && config.historyProfile.equals(cachedProfile) && scope!=null) return scope;
        String identity;
        var local=client.getSingleplayerServer();
        if (local!=null) {
            var path=local.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            identity="singleplayer:"+path;
            label="Singleplayer / "+path.getFileName();
        } else if (client.getCurrentServer()!=null && !client.getCurrentServer().ip.isBlank()) {
            identity="server:"+client.getCurrentServer().ip.trim().toLowerCase(Locale.ROOT);
            label=client.getCurrentServer().ip;
        } else {
            Object connection=client.getConnection()!=null?client.getConnection():level;
            if(connection!=unknownConnection || unknownSession==null) {
                unknownConnection=connection;
                unknownSession=UUID.randomUUID().toString();
            }
            identity="unidentified-session:"+unknownSession;
            label="Server identity unavailable / session only";
        }
        scope=new ObservationScope(identity,level.dimension().toString(),config.historyProfile);
        cachedLevel=level;cachedProfile=config.historyProfile;
        return scope;
    }
    public static String label() { return label; }
    public static void invalidate() { cachedLevel=null;cachedProfile=null;scope=null;label="No world"; }
}
