package rvn;

/**
 *
 * @author wozza
 */
public class FlexiTuple {

    Object[][] f = new Object[30000][3];
    int y = 0;

    void put(Object... v) {

        for (int x = 0; x < v.length; x++) {
            f[y++][x] = v[x];
        }
    }

}
