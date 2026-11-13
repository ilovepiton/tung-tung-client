package dev.amethyst.finder.core;

/** A clear classification requires every underground column, not a sparse sample. */
public final class CaveCoverage {
    private final java.util.BitSet columns = new java.util.BitSet(256);
    private boolean cavity, invalid;
    public void finishColumn(int column, boolean validBand) {
        if(column<0||column>=256)throw new IllegalArgumentException("Column outside chunk");
        columns.set(column);
        invalid |= !validBand;
    }
    public void foundCavity() { cavity=true; }
    public boolean rejected() { return cavity||invalid; }
    public boolean clear() { return !rejected()&&columns.cardinality()==256; }
    public int columns() { return columns.cardinality(); }
}
