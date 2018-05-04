// Written in C++ by jaj22 2018
// Ported to BWAPI4J by Adakite 2018

package jajplacer4j;

import org.openbw.bwapi4j.Position;
import org.openbw.bwapi4j.TilePosition;
import org.openbw.bwapi4j.unit.MineralPatch;
import org.openbw.bwapi4j.unit.VespeneGeyser;

import java.util.ArrayList;
import java.util.List;

public class Base {

    public TilePosition tpos;
    public Position pos;
    public List<MineralPatch> minerals;
    public List<VespeneGeyser> geysers;

    public Base() {
        this.minerals = new ArrayList<>();
        this.geysers = new ArrayList<>();
    }

}
