package dev.amethyst.finder.history;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Identity supplied by the client: a server address or local world directory, plus dimension. */
public record ObservationScope(String identity, String dimension, String profile) {
    public ObservationScope {
        identity = required(identity, "identity");
        dimension = required(dimension, "dimension");
        profile = profile == null ? "" : profile;
        if (profile.length() > 2048) throw new IllegalArgumentException("profile is too long");
    }
    public ObservationScope(String identity, String dimension) { this(identity, dimension, ""); }

    /** File names contain only a digest; world paths and server addresses cannot become paths. */
    public String fileId() {
        try {
            String canonical = "tun-tun-observations-v1\n" + part(identity) + part(dimension) + part(profile);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
    private static String part(String value) { return value.length() + ":" + value; }
    private static String required(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 2048)
            throw new IllegalArgumentException(name + " is missing or too long");
        return value;
    }
}
