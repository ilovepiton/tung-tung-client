package dev.amethyst.finder.core;

/** One use and one attack claim per physical gesture. Release never cancels an accepted placement. */
public final class ClickActionGate {
    private boolean down,armed;
    private long epoch;
    public void mouse(int action,boolean eligible) {
        if(action==0) {down=false;armed=false;}
        else if(action==1&&!down) {down=true;armed=eligible;}
    }
    public Ticket consume() {
        if(!armed)return null;
        armed=false;return new Ticket(epoch);
    }
    public void reset() {epoch++;down=false;armed=false;}
    public final class Ticket {
        private final long issued;
        private boolean attacked;
        private Ticket(long issued){this.issued=issued;}
        public boolean claimAttack(boolean eligible) {
            if(!eligible||attacked||issued!=epoch)return false;
            attacked=true;return true;
        }
    }
}
