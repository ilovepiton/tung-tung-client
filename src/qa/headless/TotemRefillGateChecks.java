import dev.amethyst.finder.modules.pvp.TotemRefillGate;

/** Packet-order scenarios; no Minecraft process, window, account or user profile. */
public final class TotemRefillGateChecks {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    public static void main(String[] args){
        var gate=new TotemRefillGate();
        gate.begin(9,7,12,100);
        check(gate.waiting()&&gate.reserved(9),"Pop immediately reserves its spare source");
        check(!gate.handUpdate(13,false)&&gate.waiting(),"Late empty-offhand echo cannot start another swap");
        gate.sourceUpdate(9,8,false,false);
        check(gate.waiting()&&gate.reserved(9),"Unchanged stale source snapshot is not confirmation");
        gate.sourceUpdate(9,9,true,true);
        check(!gate.reserved(9)&&gate.waiting(),"Consumed-source reply waits while local hand is empty");
        check(!gate.settle(false)&&gate.waiting(),"Partial slot batches cannot create inverse swaps");
        check(gate.handUpdate(14,true)&&!gate.waiting(),"Server totem reply confirms the refill");
        gate.begin(10,2,14,200);
        gate.sourceUpdate(10,3,true,true);
        check(gate.settle(true)&&!gate.waiting(),"Source confirmation covers suppressed matching hand echo");
        gate.begin(11,1,14,300);
        check(gate.nextPop()&&!gate.waiting()&&gate.reserved(11),"A rapid second real pop advances without reusing a stale source");
        gate.begin(12,1,14,301);
        gate.sourceUpdate(11,2,true,true);
        check(gate.waiting()&&gate.source()==12&&!gate.reserved(11),"Old source echo cannot confirm the newer refill");
        check(!gate.handUpdate(14,true)&&gate.waiting(),"Old hand revision cannot acknowledge a newer request");
        check(!gate.handUpdate(15,true)&&gate.waiting(),"Late protected echo from an earlier refill cannot confirm the newer source");
        check(!gate.restored(true,true)&&gate.waiting(),"A protected hand cannot be treated as a rejection");
        check(gate.restored(true,false)&&gate.waiting()&&gate.reserved(12),"A full restored snapshot never releases a possibly spent source");
        check(!gate.retryElsewhere(500,1000,true)&&gate.waiting(),"Recovery never floods swaps within its wait interval");
        check(!gate.retryElsewhere(2000,1000,false)&&gate.waiting(),"Recovery requires a fresh server-empty hand");
        check(gate.retryElsewhere(2000,1000,true)&&!gate.waiting()&&gate.reserved(12),"Recovery can choose another source without an inverse swap");
        gate.begin(13,4,20,400);
        boolean rejected=false;try{gate.begin(13,4,20,401);}catch(IllegalStateException expected){rejected=true;}
        check(rejected,"Duplicate in-flight source cannot be sent again");
        check(!gate.handUpdate(19,true)&&gate.waiting(),"Outdated revision remains ignored");
        gate.reset();check(!gate.waiting()&&!gate.reserved(13),"World changes clear all pending reservations");
        for(int source=0;source<36;source++){gate.begin(source,0,0,500+source);gate.nextPop();}
        check(gate.reserved(0)&&gate.reserved(35),"Burst bookkeeping is bounded to the 36 actual inventory slots");
        System.out.println("{\"ok\":true,\"checks\":"+checks+",\"minecraftStarted\":false,\"userProfileTouched\":false}");
    }
}
