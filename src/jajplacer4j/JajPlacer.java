// Written in C++ by jaj22 2018
// Ported to BWAPI4J by Adakite 2018

package jajplacer4j;

import org.openbw.bwapi4j.BWMap;
import org.openbw.bwapi4j.Position;
import org.openbw.bwapi4j.TilePosition;
import org.openbw.bwapi4j.unit.MineralPatch;
import org.openbw.bwapi4j.unit.Unit;
import org.openbw.bwapi4j.unit.VespeneGeyser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JajPlacer {

    private static final int BASE_MIN = 400;		// mininum resource value to consider for a base
    private static final int MINERAL_MIN = 500;	// consider minerals smaller than this to be blockers
    private static final int INC_DIST = 9 * 32;		// mid-to-mid pixel distance for including resources in a base

    private final List<Base> bases = new ArrayList<>();
    private final int mapx;
    private final int mapy;
    private final int scanw;
    private boolean[] walkgrid;			// true if tile blocked by terrain
    private final boolean[] resblock;			// true if blocked (for depot top-left) by mineral proximity
    private final int[] resval;		// value of tile (for depot top-left)

    public JajPlacer(final BWMap bwMap, final List<MineralPatch> staticMinerals, List<VespeneGeyser> staticGeysers) {
        mapx = bwMap.mapWidth();
        mapy = bwMap.mapHeight();
        scanw = mapx + 2;
        MakeWalkGrid(bwMap);

        bases.clear();

        resblock = new boolean[(mapx + 2)*(mapy + 2)];
        for (int i = 0; i < resblock.length; ++i) {
            resblock[i] = false;
        }

        resval = new int[(mapx + 2)*(mapy + 2)];
        for (int i = 0; i < resval.length; ++i) {
            resval[i] = 0;
        }

        final List<Unit> res = new ArrayList<>(200);

        for (MineralPatch u : staticMinerals)
        {
            if (u.getInitialResources() < MINERAL_MIN) continue;
            TilePosition p = u.getInitialTilePosition();
            MarkResBlock(p, 2, 1);
            MarkBorderValue(p, 2, 1, 1);
            res.add(u);
        }
        for (VespeneGeyser u : staticGeysers)
        {
            if (u.getInitialResources() == 0) continue;
            TilePosition p = u.getInitialTilePosition();
            MarkResBlock(p, 4, 2);
            MarkBorderValue(p, 4, 2, 3);
            res.add(u);
        }

        // ok, then find all values above some threshold, sort them
        final List<Integer> potbase = new ArrayList<>(scanw*mapy / 8);
        for (int off = scanw; off<scanw*(mapy + 1); off++)
            if (resval[off] > BASE_MIN && !resblock[off]) potbase.add(off);
        potbase.sort(new PotBaseComparator(resval));

        // Make some bases
        for (int off : potbase)
        {
            if (resval[off] <= BASE_MIN || resblock[off]) continue;		// can get wiped

            bases.add(new Base());
            final Base base = bases.get(bases.size() - 1);
            base.tpos = new TilePosition((off - mapx - 3) % scanw, (off - mapx - 3) / scanw);
            base.pos = new Position(base.tpos.getX() * 32 + 64, base.tpos.getY() * 32 + 48);

            for (int i = 0; i < res.size(); ) {
                Position diff = base.pos.subtract(res.get(i).getInitialPosition());
                if (diff.getX()*diff.getX() + diff.getY()*diff.getY() > INC_DIST*INC_DIST) { ++i; continue; }

                if (res.get(i) instanceof MineralPatch) {
                    base.minerals.add((MineralPatch) res.get(i));
                    MarkBorderValue(res.get(i).getInitialTilePosition(), 2, 1, -1);
                }
				else if (res.get(i) instanceof VespeneGeyser) {
                    base.geysers.add((VespeneGeyser) res.get(i));
                    MarkBorderValue(res.get(i).getInitialTilePosition(), 4, 2, -3);
                } else {
                    throw new IllegalStateException("Unrecognized resource class: " + res.getClass().toString());
                }
                Collections.swap(res, i, res.size() - 1);
                res.remove(res.size() - 1);
            }


        }
    }

    private int TILEOFF(int x, int y) {
        return x + 1 + (y + 1)*(mapx + 2);
    }

    // simplified version for standalone, doesn't bother with bitmask
    private void MakeWalkGrid(final BWMap bwMap) {
        walkgrid = new boolean[scanw*(mapy + 2)];
        for (int i = 0; i < walkgrid.length; ++i) {
            walkgrid[i] = true;
        }

        int curoff = mapx + 3;			// 1,1 in walkgrid = 0,0 in bwapi
        for (int y = 0; y<mapy; y++, curoff += 2) {
            for (int x = 0; x<mapx; x++, curoff++) {
                walkgrid[curoff] = false;			// assume open unless otherwise
                for (int ym = 0; ym<4; ym++) for (int xm = 0; xm<4; xm++) {
                    if (bwMap.isWalkable(x * 4 + xm, y * 4 + ym)) continue;
                    walkgrid[curoff] = true; break;
                }
            }
        }
    }

    // mark area around resource for depot top-left blocking
    private void MarkResBlock(TilePosition p, int tw, int th) {
        TilePosition p1 = new TilePosition(Math.max(0, p.getX() - 6), Math.max(0, p.getY() - 5));					// offset by base size
        TilePosition p2 = new TilePosition(Math.min(mapx - 1, p.getX() + 2 + tw), Math.min(mapy - 1, p.getY() + 2 + th));

        for (int y = p1.getY(); y <= p2.getY(); y++) {
            int off = TILEOFF(p1.getX(), y);
            for (int x = p1.getX(); x <= p2.getX(); x++, off++) resblock[off] = true;
        }
    }

    // midoff is the offset of the starting tile
    // distrow is a distance lookup table, 0 = nothing extra
    // end is size of end sections
    // mid is size of mid section
    // inc is increment either downwards or right
    private int MarkRow(int midoff, int[] distrow, int mid, int end, int inc, int valmod) {
        int writes = 0;
        for (int i = 1, roff = midoff + inc; i <= end; i++, roff += inc, ++writes) {
            if (walkgrid[roff]) break;		// blocked tile, don't continue in this dir
            resval[roff] += valmod * distrow[i];
        }
        for (int i = 1 - mid, roff = midoff; i <= end; i++, roff -= inc, ++writes) {
            if (walkgrid[roff]) break;		// blocked tile, don't continue in this dir
            resval[roff] += valmod * distrow[Math.max(i, 0)];
        }
        return writes;
    }

    // inputs, p = top left tile, tw = width, th = height, valmod is multiplier for value.
    private void MarkBorderValue(TilePosition p, int tw, int th, int valmod) {
        // first 24 entries should be unused. formula sqrt(900/(x*x+y*y)), 3,0 gives val 10
		final int sqrtarr[] = { 0, 300, 150, 100, 75, 60, 50, 42, 300, 212, 134, 94, 72, 58, 49, 42, 150, 134, 106, 83, 67, 55, 47, 41,
            100, 94, 83, 70, 60, 51, 44, 39, 75, 72, 67, 60, 53, 46, 41, 37, 60, 58, 55, 51, 46, 42, 38, 34, 50, 49, 47, 44, 41, 38, 35, 32, 42, 42, 41, 39, 37, 34, 32, 30 };
        int coff = TILEOFF(p.getX() + tw - 1, p.getY() + th - 1);

        boolean c = false; for (int i = th; i<th + 6; i++) if (walkgrid[coff - i * scanw]) { c = true; break; }
        if (!c) for (int s = 3; s < 7; s++) if (MarkRow(coff - (s + 2 + th)*scanw, subarr(sqrtarr, s * 8), tw + 3, s, +1, valmod) == 0) break;		// top

        c = false; for (int i = 1; i<5; i++) if (walkgrid[coff + i * scanw]) { c = true; break; }
        if (!c) for (int s = 3; s < 7; s++) if (MarkRow(coff + (s + 1)*scanw, subarr(sqrtarr, s * 8), tw + 3, s, +1, valmod) == 0) break;		// bot

        c = false; for (int i = tw; i<tw + 7; i++) if (walkgrid[coff - i]) { c = true; break; }
        if (!c) for (int s = 3; s < 7; s++) if (MarkRow(coff - (s + 3 + tw), subarr(sqrtarr, s * 8), th + 2, s + 1, scanw, valmod) == 0) break;		// left

        c = false; for (int i = 1; i<5; i++) if (walkgrid[coff + i]) { c = true; break; }
        if (!c) for (int s = 3; s < 7; s++) if (MarkRow(coff + (s + 1), subarr(sqrtarr, s * 8), th + 2, s + 1, scanw, valmod) == 0) break;		// right
    }

    private int[] subarr(final int[] arr, final int offset) {
        final int[] subarr = new int[arr.length - offset];
        int subarr_idx = 0;

        for (int arr_idx = offset; arr_idx < arr.length; ++arr_idx) {
            subarr[subarr_idx++] = arr[arr_idx];
        }

        return subarr;
    }

    public List<Base> getBases() {
        return this.bases;
    }

}
