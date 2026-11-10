package dev.amethyst.finder.audio;

/** Stateful 48 kHz mono speech filter. No capture, encoding, or network access. */
public final class VoicePolish {
    private double previousInput,highPass,envelope;
    public void reset(){previousInput=highPass=envelope=0;}
    public short[] process(short[] input,double gain){
        short[] out=new short[input.length];
        for(int i=0;i<input.length;i++){
            double x=input[i]/32768d;
            highPass=.9896*(highPass+x-previousInput);previousInput=x;
            double level=Math.abs(highPass*gain),speed=level>envelope?.007:.00015;
            envelope+=speed*(level-envelope);
            double compression=envelope>.20?Math.pow(.20/envelope,.60):1;
            double y=highPass*gain*compression;
            // Keep headroom for a normal voice plus input effects; hard ceiling never wraps.
            out[i]=(short)Math.round(Math.clamp(y,-.92,.92)*32767);
        }
        return out;
    }
}
