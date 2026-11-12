package dev.amethyst.finder.modules;

import java.util.Map;

/** Run with assertions enabled; no game launch or server connection required. */
public final class FarmChecks {
    public static void main(String[] args) {
        var defaults=FarmItems.sanitize(null);
        assert defaults.size()==24&&defaults.values().stream().filter(Boolean.TRUE::equals).count()==1;
        assert FarmItems.fullStack("cobblestone",64,64,false,defaults);
        assert !FarmItems.fullStack("cobblestone",63,64,false,defaults):"Keep partial stacks";
        assert !FarmItems.fullStack("cobblestone",64,64,true,defaults):"Protect named/enhanced stacks";
        assert !FarmItems.fullStack("diamond",64,64,false,Map.of("diamond",true)):"Ignore resources outside the allowlist";
        assert !FarmItems.fullStack("iron_ingot",64,64,false,defaults):"Unselected resources must stay";
        assert FarmItems.sanitize(Map.of("diamond",true)).values().stream().noneMatch(Boolean.TRUE::equals);
        assert FarmItems.receipt(7,7,64,0)==FarmItems.Receipt.WAIT:"Client prediction is not a receipt";
        assert FarmItems.receipt(7,8,64,64)==FarmItems.Receipt.WAIT:"Server correction rejected the move";
        assert FarmItems.receipt(7,8,64,32)==FarmItems.Receipt.PARTIAL:"Stop rather than count a partial deposit";
        assert FarmItems.receipt(7,8,64,0)==FarmItems.Receipt.ACCEPTED;
        assert FarmAutomation.confirmLabel("Confirm & Sell");
        assert FarmAutomation.confirmLabel("SELL ITEMS");
        assert !FarmAutomation.confirmLabel("Buy");
        assert !FarmAutomation.confirmLabel("Seller profile");
        assert FarmAutomation.titleMatches("Sell Inventory","");
        assert FarmAutomation.titleMatches(" Sell ","sell");
        assert !FarmAutomation.titleMatches("Auction House","Sell");
        System.out.println("PASS: farm allowlist, full/plain stacks, authoritative receipts, rejected/partial moves and Confirm/Sell matching");
    }
}
