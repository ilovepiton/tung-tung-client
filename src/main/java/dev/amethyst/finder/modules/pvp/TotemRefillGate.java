package dev.amethyst.finder.modules.pvp;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tracks a pop-triggered server swap separately from delayed inventory echoes. */
public final class TotemRefillGate {
    private record Reservation(long revision) {}
    private final Map<Integer,Reservation> sources=new LinkedHashMap<>();
    private int pendingSource=-1;
    private long handRevision,sentAt;
    private boolean sourceConfirmed;

    public void reset(){sources.clear();pendingSource=-1;sourceConfirmed=false;sentAt=0;}
    public boolean waiting(){return pendingSource>=0;}
    public int source(){return pendingSource;}
    public long sentAt(){return sentAt;}
    public boolean reserved(int source){return sources.containsKey(source);}
    public void begin(int source,long sourceRevision,long offhandRevision,long now){
        if(source<0||source>=36||reserved(source)||waiting())throw new IllegalStateException("Totem source already in flight");
        sources.put(source,new Reservation(sourceRevision));pendingSource=source;
        handRevision=offhandRevision;sentAt=now;sourceConfirmed=false;
    }
    /** A second actual pop proves that the preceding refill reached the server. */
    public boolean nextPop(){boolean had=waiting();pendingSource=-1;sourceConfirmed=false;return had;}
    public void sourceUpdate(int slot,long revision,boolean changed,boolean consumed){
        var reservation=sources.get(slot);
        if(reservation==null||revision<=reservation.revision)return;
        if(changed)sources.remove(slot);
        if(slot==pendingSource&&consumed)sourceConfirmed=true;
    }
    public boolean handUpdate(long revision,boolean protectedHand){
        if(!waiting()||revision<=handRevision||!protectedHand||!sourceConfirmed)return false;
        pendingSource=-1;sourceConfirmed=false;return true;
    }
    /** A consumed source plus an unchanged protected hand also covers suppressed matching echoes. */
    public boolean settle(boolean protectedHand){
        if(!waiting()||!sourceConfirmed||!protectedHand)return false;
        pendingSource=-1;sourceConfirmed=false;return true;
    }
    /** A restored snapshot can predate the click. Never retry its possibly spent source. */
    public boolean restored(boolean sourceRestored,boolean protectedHand){
        return waiting()&&sourceRestored&&!protectedHand;
    }
    /** Recovery may use another source, retaining reservations until authoritative changes. */
    public boolean retryElsewhere(long now,long minimumWait,boolean freshlyEmptyHand){
        if(!waiting()||now-sentAt<minimumWait||!freshlyEmptyHand)return false;
        pendingSource=-1;sourceConfirmed=false;return true;
    }
}
