package dev.amethyst.finder.ui;

import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.FinderConfig;
import dev.amethyst.finder.HistoryContext;
import dev.amethyst.finder.core.ChunkAnalysis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;

/** Real observation controls and saved/current findings directly in Minecraft. */
public final class ChunkAnalysisScreen extends Screen {
    private final FinderConfig config=ChunkFinderClient.CONFIG;
    private int px,py,pw,ph,rows,page,filter,detailsScroll,detailsMaxScroll;
    private long lastRevision=-1;
    private long lastScope=-1;
    private Long selected;
    private EditBox profile;
    private String notice="";
    private boolean clearArmed;
    private List<Row> visible=List.of();
    private long playersCheckedAt;
    private record Row(long key,int x,int z,ChunkAnalysis analysis,int players) {
        boolean live(){return players>0 || analysis!=null&&analysis.status()==ChunkAnalysis.Status.LIVE;}
    }
    public ChunkAnalysisScreen() {
        super(Component.literal("Tung Tung Client / Chunk analysis"));
        lastScope=ChunkFinderClient.SCANNER.scopeGeneration();
    }

    @Override protected void init() {
        String draft=profile==null?config.historyProfile:profile.getValue();
        boolean editing=profile!=null&&profile.isFocused();
        pw=Math.min(740,width-16);ph=Math.min(430,height-16);px=(width-pw)/2;py=(height-ph)/2;
        rows=Math.max(1,(ph-202)/21);
        int tile=(pw-32)/3;
        String[] ids={"chest_chunks","amethyst_chunks","amethyst_growth","base_chunks","player_chunks","spawner_chunks"};
        String[] names={"Chests","Amethyst","Growth","Structures","Players","Spawners"};
        for(int i=0;i<ids.length;i++) {
            String id=ids[i];String caption=names[i];
            button(px+12+(i%3)*(tile+4),py+42+(i/3)*22,tile,20,
                    caption+": "+(config.chunkModuleEnabled(id)?"ON":"OFF"),()->{
                        config.setModuleEnabled(id,!config.chunkModuleEnabled(id));config.save();
                        ChunkFinderClient.SCANNER.requestRescan();rebuildWidgets();
                    },config.chunkModuleEnabled(id));
        }
        int listWidth=Math.max(110,(pw-34)/2);
        button(px+12,py+91,listWidth,20,"View: "+new String[]{"All observations","Currently verified","History / pending"}[filter],()->{
            filter=(filter+1)%3;page=0;rebuildWidgets();
        },false);
        refreshRows();
        int pages=Math.max(1,(visible.size()+rows-1)/rows);page=Math.clamp(page,0,pages-1);
        for(int i=0;i<rows;i++) {
            int index=page*rows+i;if(index>=visible.size())break;
            Row item=visible.get(index);
            String row=item.x()+", "+item.z()+" / "+(item.live()?"LIVE":item.analysis().status().name())+" / "+category(item);
            button(px+12,py+116+i*21,listWidth,19,row,()->{
                selected=item.key();detailsScroll=0;ChunkSelection.setOverride(selected);rebuildWidgets();
            },selected!=null&&selected==item.key());
        }
        int navY=py+116+rows*21;
        button(px+12,navY,30,19,"<",()->{page--;rebuildWidgets();},false).active=page>0;
        button(px+46,navY,30,19,">",()->{page++;rebuildWidgets();},false).active=page+1<pages;
        button(px+81,navY,listWidth-69,19,"Auto select",()->{selected=null;ChunkSelection.clearOverride();rebuildWidgets();},false);
        int bottom=py+ph-63,small=(pw-32)/3;
        button(px+12,bottom,small,20,"HUD: "+(config.chunkHud?"ON":"OFF"),()->{config.chunkHud=!config.chunkHud;changed();},config.chunkHud);
        button(px+16+small,bottom,small,20,"History outlines: "+(config.showHistoricalChunks?"ON":"OFF"),()->{config.showHistoricalChunks=!config.showHistoricalChunks;changed();},config.showHistoricalChunks);
        button(px+20+small*2,bottom,small,20,clearArmed?"Confirm clear scope":"Clear scope history",()->{
            if(!clearArmed){clearArmed=true;notice="Click again to clear this world/dimension/profile only.";rebuildWidgets();return;}
            ChunkFinderClient.SCANNER.clear();selected=null;ChunkSelection.clearOverride();clearArmed=false;
            notice="Current scope cleared. New real scans may add observations again.";rebuildWidgets();
        },false);
        profile=addRenderableWidget(new EditBox(font,px+12,bottom+26,Math.max(80,pw-130),18,Component.literal("History profile")));
        profile.setMaxLength(64);profile.setValue(draft);
        if(editing)setFocused(profile);
        button(px+pw-109,bottom+26,97,18,"Apply profile",()->{
            config.historyProfile=profile.getValue();config.sanitize();config.save();HistoryContext.invalidate();
            if(minecraft.level!=null)ChunkFinderClient.SCANNER.setScope(HistoryContext.resolve(minecraft,minecraft.level,config));
            selected=null;ChunkSelection.clearOverride();page=0;notice="History profile changed.";profile=null;rebuildWidgets();
        },false);
    }
    private FinderButton button(int x,int y,int w,int h,String title,Runnable action,boolean on) {
        return addRenderableWidget(new FinderButton(x,y,Math.max(20,w),h,title,action,()->on));
    }
    private void changed() { config.save();rebuildWidgets(); }
    private void refreshRows() {
        var merged=new LinkedHashMap<Long,Row>();
        for(ChunkAnalysis a:ChunkFinderClient.SCANNER.chunkObservations()) {
            if(a.storageCount()==0&&a.amethystCount()==0&&a.spawnerCount()==0&&a.evidence()==ChunkAnalysis.StructureEvidence.NONE
                    &&a.growthEvents()==0&&(selected==null||selected!=a.key()))continue;
            merged.put(a.key(),new Row(a.key(),a.chunkX(),a.chunkZ(),a,0));
        }
        for(var entry:ChunkWorldRenderer.currentPlayers(minecraft,config.chunkRangeBlocks).entrySet()) {
            long key=entry.getKey();var previous=merged.get(key);
            merged.put(key,new Row(key,(int)(key>>32),(int)key,previous==null?null:previous.analysis(),entry.getValue()));
        }
        List<Row> list=new ArrayList<>();
        for(var row:merged.values())if(filter==0 || (filter==1)==row.live())list.add(row);
        list.sort(Comparator.<Row>comparingInt(a->a.live()?0:1)
                .thenComparing(Comparator.comparingLong((Row a)->a.analysis()==null?Long.MAX_VALUE:a.analysis().observedAtMillis()).reversed()));
        visible=List.copyOf(list);lastRevision=ChunkFinderClient.SCANNER.revision();
    }
    private Row selectedRow() {
        Long key=selected!=null?selected:ChunkSelection.selectedKey();
        if(key==null)return null;
        var a=ChunkFinderClient.SCANNER.chunkObservations().stream().filter(item->item.key()==key).findFirst().orElse(null);
        int players=ChunkWorldRenderer.currentPlayers(minecraft,config.chunkRangeBlocks).getOrDefault(key,0);
        return a==null&&players==0?null:new Row(key,(int)(key>>32),(int)(long)key,a,players);
    }
    private static String category(Row row) {
        if(row.players()>0)return "Players "+row.players();
        ChunkAnalysis a=row.analysis();
        if(a.storageCount()>0)return "Storage "+a.storageCount();
        if(a.amethystCount()>0)return "Amethyst "+a.amethystCount();
        if(a.spawnerCount()>0)return "Spawner "+a.spawnerCount();
        return "Structure "+a.evidence().name();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0x65000000);Theme.card(g,px,py,pw,ph,0xF00D0B13);
        Theme.smallText(g,"TUNG TUNG CLIENT / CHUNK ANALYSIS",px+12,py+11,pw-24,0xFFFFDAB1);
        Theme.smallText(g,HistoryContext.label()+" / "+config.historyProfile,px+12,py+26,pw-24,Theme.MUTED);
        int x=px+(pw-34)/2+24,y=py+95,w=px+pw-12-x;
        Row item=selectedRow();
        if(item==null) {
            Theme.smallText(g,"Select a received or saved observation.",x,y,w,Theme.TEXT);
            Theme.smallText(g,ChunkHud.analysisKeyName()+" / Esc: return",x,y+16,w,Theme.MUTED);
        } else {
            ChunkAnalysis a=item.analysis();
            List<String> lines=new ArrayList<>();
            lines.add("Chunk: "+item.x()+", "+item.z());
            if(item.players()>0)lines.add("Currently observed players: "+item.players());
            if(a==null){lines.add("Player location: LIVE");lines.add("Player history: NOT STORED");}
            else {
            lines.add("Block observation: "+a.status());
            lines.add("Storage blocks: "+a.storageCount()+" / Spawners: "+a.spawnerCount());
            lines.add("Amethyst blocks: "+a.amethystCount());lines.add("Structure evidence: "+a.evidence());
            lines.add("Crafting: "+a.craftingCount()+" / Redstone: "+a.redstoneCount());
            lines.add("Growth: "+a.continuousGrowthEvents()+" continuous / "+a.gapGrowthEvents()+" across gap");
            if(a.growthEvents()>0) { lines.add("Previous: "+ChunkAnalysis.stageName(a.previousGrowthStage()));lines.add("Current: "+ChunkAnalysis.stageName(a.currentGrowthStage())); }
            lines.add("Last observed: "+DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(a.observedAtMillis())));
            lines.add("Simulation: "+(a.simulationActivityPossible()?"POSSIBLE":"UNVERIFIED"));
            lines.add("Server diagnostics: UNAVAILABLE");
            }
            List<String> wrapped=new ArrayList<>();
            for(String line:lines) {
                String remaining=line;
                while(!remaining.isEmpty()) {
                    String part=font.plainSubstrByWidth(remaining,Math.max(1,w));
                    if(part.isEmpty())part=remaining.substring(0,1);
                    wrapped.add(part);remaining=remaining.substring(part.length()).stripLeading();
                }
            }
            int capacity=Math.max(1,(ph-172)/12);
            detailsMaxScroll=Math.max(0,wrapped.size()-capacity);
            detailsScroll=Math.clamp(detailsScroll,0,detailsMaxScroll);
            for(int i=detailsScroll;i<Math.min(wrapped.size(),detailsScroll+capacity);i++) {
                Theme.smallText(g,wrapped.get(i),x,y,w,Theme.TEXT);y+=12;
            }
            if(detailsMaxScroll>0)Theme.smallText(g,"Scroll details: "+(detailsScroll+1)+"–"+Math.min(wrapped.size(),detailsScroll+capacity)+" / "+wrapped.size(),x,py+ph-74,w,Theme.MUTED);
        }
        String status=ChunkFinderClient.SCANNER.historyLoading()?"Loading saved observations…":ChunkFinderClient.SCANNER.historyError();
        if(status.isBlank())status=notice.isBlank()?ChunkHud.analysisKeyName()+" / Esc: return   P: desktop   History is not fresh verification.":notice;
        Theme.smallText(g,status,px+12,py+ph-12,pw-24,Theme.MUTED);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public void tick() {
        long scope=ChunkFinderClient.SCANNER.scopeGeneration();
        if(lastScope!=scope) {
            lastScope=scope;selected=null;ChunkSelection.clearOverride();clearArmed=false;page=0;detailsScroll=0;profile=null;
            rebuildWidgets();return;
        }
        long now=System.currentTimeMillis();
        if(lastRevision!=ChunkFinderClient.SCANNER.revision()){rebuildWidgets();playersCheckedAt=now;}
        else if(now-playersCheckedAt>=250){playersCheckedAt=now;var before=visible;refreshRows();if(!before.equals(visible))rebuildWidgets();}
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical) {
        int x=px+(pw-34)/2+24;
        if(mouseX>=x&&mouseX<px+pw-12&&mouseY>=py+91&&mouseY<py+ph-70) {
            detailsScroll=Math.clamp(detailsScroll-(int)Math.signum(vertical)*3,0,detailsMaxScroll);return true;
        }
        return super.mouseScrolled(mouseX,mouseY,horizontal,vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if(profile!=null&&profile.isFocused())return super.keyPressed(event);
        if(ChunkFinderClient.ANALYSIS.matches(event)){onClose();return true;}
        return super.keyPressed(event);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean isInGameUi(){return true;}
    @Override public void removed(){config.save();}
}
