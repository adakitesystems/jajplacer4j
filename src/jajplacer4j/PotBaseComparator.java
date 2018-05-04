// Written in C++ by jaj22 2018
// Ported to BWAPI4J by Adakite 2018

package jajplacer4j;

import java.util.Comparator;

public class PotBaseComparator implements Comparator<Integer> {

    final int[] resval;

    public PotBaseComparator(final int[] resval) {
        this.resval = resval;
    }

    @Override
    public int compare(final Integer a, final Integer b) {
        return Integer.compare(this.resval[b], this.resval[a]);
    }

}
