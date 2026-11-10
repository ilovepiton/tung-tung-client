package dev.amethyst.finder.ui;

import dev.amethyst.finder.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;
import dev.amethyst.finder.core.MineralType;

/** Resource controls and an explicit, frozen player-position bookmark. */
public final class DiscoveryScreen extends Screen {
    private int px,py,pw,ph,page,rows,ticks;
    private boolean players;
    private EditBox search;
    private String query="",renderedQuery="",notice="";
    private List<PlayerLocator.Row> roster=List.of();
    public DiscoveryScreen(boolean players) {super(Component.literal("Tung Tung / Find Player & Highlights"));this.players=players;}
    @Override protected void init() {
        if(search!=null)query=search.getValue();
        pw=Math.min(700,width-20);ph=Math.min(382,height-20);px=(width-pw)/2;py=(height-ph)/2;
        button(px+14,py+47,Math.min(150,(pw-44)/2),22,"Highlights",()->{players=false;search=null;rebuildWidgets();},!players);
        button(px+22+Math.min(150,(pw-44)/2),py+47,Math.min(150,(pw-44)/2),22,"Find Player",()->{players=true;rebuildWidgets();},players);
        button(px+pw-74,py+13,60,20,"Done",this::onClose,false);
        if(players) playerWidgets();else highlightWidgets();
    }
    private FinderButton button(int x,int y,int w,int h,String text,Runnable action,boolean on){return addRenderableWidget(new FinderButton(x,y,w,h,text,action,()->on,true));}
    private void highlightWidgets() {
        var config=ChunkFinderClient.CONFIG;
        button(px+pw-122,py+74,50,18,"All",()->selectAll(true),false);
        button(px+pw-66,py+74,50,18,"None",()->selectAll(false),false);
        int gap=8,w=(pw-44)/3,step=(ph-154)/4;
        var minerals=MineralType.values();
        for(int i=0;i<minerals.length;i++) {
            var mineral=minerals[i];
            addRenderableWidget(new MineralButton(px+14+(i%3)*(w+gap),py+98+(i/3)*step,w,step-4,mineral,()->{
                var next=config.activeMinerals();next.put(mineral.id,!config.mineralEnabled(mineral));config.selectMinerals(next);config.save();rebuildWidgets();
            }));
        }
        int bottomWidth=(pw-44)/3;
        button(px+14,py+ph-44,bottomWidth,20,"Range: "+config.resourceRange,()->{
            config.resourceRange=config.resourceRange>=128?32:config.resourceRange+32;config.save();rebuildWidgets();
        },false);
        button(px+22+bottomWidth,py+ph-44,bottomWidth,20,"Lava: "+(config.isModuleEnabled("lava_esp")?"ON":"OFF"),()->{
            config.setModuleEnabled("lava_esp",!config.isModuleEnabled("lava_esp"));config.save();rebuildWidgets();
        },config.isModuleEnabled("lava_esp"));
        button(px+30+bottomWidth*2,py+ph-44,bottomWidth,20,"Player list",()->{players=true;rebuildWidgets();},false);
    }
    private void selectAll(boolean value) {
        Map<String,Boolean> next=new LinkedHashMap<>();for(var m:MineralType.values())next.put(m.id,value);
        ChunkFinderClient.CONFIG.selectMinerals(next);ChunkFinderClient.CONFIG.save();rebuildWidgets();
    }
    private static ItemStack mineralIcon(MineralType type) {
        return new ItemStack(switch(type) {
            case DIAMOND->Items.DIAMOND;case EMERALD->Items.EMERALD;case LAPIS->Items.LAPIS_LAZULI;
            case IRON->Items.RAW_IRON;case GOLD->Items.RAW_GOLD;case COPPER->Items.RAW_COPPER;
            case REDSTONE->Items.REDSTONE;case COAL->Items.COAL;case QUARTZ->Items.QUARTZ;
            case ANCIENT_DEBRIS->Items.ANCIENT_DEBRIS;case AMETHYST->Items.AMETHYST_SHARD;case SULFUR->Items.SULFUR;
        });
    }
    private static final class MineralButton extends net.minecraft.client.gui.components.Button {
        final MineralType mineral;
        MineralButton(int x,int y,int w,int h,MineralType mineral,Runnable action) {
            super(x,y,w,h,Component.literal(mineral.label),b->action.run(),DEFAULT_NARRATION);this.mineral=mineral;
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(mineral.label+" · click to toggle")));
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta) {
            boolean on=ChunkFinderClient.CONFIG.mineralEnabled(mineral);
            Theme.card(g,getX(),getY(),width,height,on?0xFF2A1D11:0xFF171009);
            if(on||isHoveredOrFocused())g.outline(getX(),getY(),width,height,0xFFC89155);
            g.item(mineralIcon(mineral),getX()+6,getY()+(height-16)/2);
            Theme.smallText(g,mineral.label,getX()+28,getY()+(height-8)/2,width-52,on?0xFFF2EBE1:0xFFB5A28C);
            Theme.smallText(g,on?"ON":"OFF",getX()+width-22,getY()+(height-8)/2,20,on?0xFFC89155:0xFF7A654E);
        }
    }
    private void playerWidgets() {
        int left=Math.max(145,(pw-44)/2);
        search=addRenderableWidget(new EditBox(font,px+16,py+82,left,20,Component.literal("Search players")));
        search.setMaxLength(64);search.setValue(query);search.setHint(Component.literal("Search players…"));
        search.setResponder(value->{if(!query.equals(value)){query=value;page=0;}});
        roster=ChunkFinderClient.PLAYERS.rows(minecraft).stream().filter(r->r.name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        renderedQuery=query;
        rows=Math.max(1,(ph-171)/27);int pages=Math.max(1,(roster.size()+rows-1)/rows);page=Math.clamp(page,0,pages-1);
        for(int i=0;i<rows&&page*rows+i<roster.size();i++) {
            var row=roster.get(page*rows+i);String suffix=row.observable()?"  ·  "+row.distance()+"m":"  ·  out of view";
            var action=button(px+16,py+111+i*27,left,24,row.name()+suffix,()->{
                try {var saved=ChunkFinderClient.PLAYERS.pin(minecraft,row.id());notice="Saved "+saved.name()+" at "+saved.coordinates();}
                catch(Exception error){notice=error.getMessage();}rebuildWidgets();
            },ChunkFinderClient.PLAYERS.saved()!=null&&ChunkFinderClient.PLAYERS.saved().playerId().equals(row.id()));
            action.active=row.observable();
        }
        button(px+16,py+ph-48,28,20,"<",()->{page--;rebuildWidgets();},false).active=page>0;
        button(px+50,py+ph-48,28,20,">",()->{page++;rebuildWidgets();},false).active=page+1<pages;
        button(px+pw/2+14,py+ph-64,Math.max(100,pw/2-32),23,"Clear saved marker",()->{
            try {ChunkFinderClient.PLAYERS.clear();notice="Saved marker cleared.";}catch(Exception error){notice=error.getMessage();}rebuildWidgets();
        },false).active=ChunkFinderClient.PLAYERS.saved()!=null;
    }
    @Override public void tick() {
        ticks++;
        if(players&&(!query.equals(renderedQuery)||ticks%20==0&&(search==null||!search.isFocused()))) {
            boolean focused=search!=null&&search.isFocused();int cursor=search==null?0:search.getCursorPosition();
            rebuildWidgets();if(focused){setFocused(search);search.setCursorPosition(cursor);}
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0x80090604);Theme.card(g,px,py,pw,ph,0xF2100C09);
        g.fill(px+14,py+9,px+42,py+11,0xFFC89155);
        Theme.smallText(g,"TUNG TUNG / DISCOVERY",px+14,py+20,pw-100,0xFFF2EBE1);
        Theme.smallText(g,players?"Choose a player. Save a place.":"Make the important blocks stand out.",px+14,py+34,pw-28,0xFF93897D);
        if(!players) {
            long selected=ChunkFinderClient.CONFIG.activeMinerals().values().stream().filter(Boolean.TRUE::equals).count();
            Theme.smallText(g,"MINERALS · "+selected+" / "+MineralType.values().length,px+16,py+79,pw-150,0xFF93897D);
            Theme.smallText(g,ChunkFinderClient.RESOURCES.status()+" · received blocks only",px+16,py+ph-16,pw-32,0xFF7A654E);
        } else {
            int x=px+pw/2+14,w=pw/2-30;var pin=ChunkFinderClient.PLAYERS.saved();
            Theme.card(g,x,py+82,w,ph-154,0xFF171009);
            Theme.smallText(g,"SAVED POSITION",x+12,py+96,w-24,0xFFC89155);
            if(pin==null) {
                Theme.smallText(g,"No marker yet",x+12,py+121,w-24,Theme.TEXT);
                Theme.smallText(g,"Click a nearby player to save",x+12,py+141,w-24,0xFF93897D);
                Theme.smallText(g,"their current XYZ position.",x+12,py+155,w-24,0xFF93897D);
            } else {
                Theme.smallText(g,pin.name(),x+12,py+121,w-24,0xFFF6E4B9);
                if(ph<300) {
                    Theme.smallText(g,pin.coordinates(),x+12,py+146,w-24,Theme.TEXT);
                    Theme.smallText(g,"Fixed saved position",x+12,py+163,w-24,0xFFC89155);
                } else {
                    Theme.smallText(g,"X  "+pin.x(),x+12,py+146,w-24,Theme.TEXT);
                    Theme.smallText(g,"Y  "+pin.y(),x+12,py+162,w-24,Theme.TEXT);
                    Theme.smallText(g,"Z  "+pin.z(),x+12,py+178,w-24,Theme.TEXT);
                    Theme.smallText(g,ChunkFinderClient.PLAYERS.active(minecraft)==null?"Saved in another world / dimension":"Marker fixed at these coordinates",x+12,py+201,w-24,0xFFC89155);
                }
            }
            Theme.smallText(g,"Names outside view have no received coordinates.",px+16,py+ph-20,pw-32,0xFF7A654E);
        }
        if(!notice.isEmpty())Theme.smallText(g,notice,px+16,py+ph-34,pw-32,0xFF8FE9C3);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public boolean isPauseScreen(){return false;}
}
