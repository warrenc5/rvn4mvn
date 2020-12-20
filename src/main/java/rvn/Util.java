package rvn;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author wozza
 */
public class Util {

    private static Logger slog = Logger.getLogger(Rvn.class.getName());

    static void logX(Logger log, IOException ex) {
        log.log(Level.SEVERE, ex.getMessage(), ex);
    }

    static void log(Logger log, IOException ex) {
        log.log(Level.SEVERE, ex.getMessage());
    }

    static String prettyDuration(Duration d) {
        if (d.toHours() > 24) {
            return d.toDays() + " days";
        }
        return d.toString();
    }

    public static List<Integer> rangeToIndex(String range) {
        slog.info(range);
        List<Integer> list = new ArrayList<>();

        String[] split = range.split(",");

        for (String s : split) {

            int b = 0;
            int e = 0;
            if (s.indexOf('-') > 0) {
                String[] r = s.split("-");
                switch (r.length) {
                    case 0:
                        list.add(-1);
                        break;
                    case 1:
                        b = Integer.parseInt(r[0]);
                        e = list.size();
                        break;
                    case 2:
                        b = Integer.parseInt(r[0]);
                        e = Integer.parseInt(r[1]);
                        break;
                    default:

                }
                for (int i = b; i <= e; i++) {
                    list.add(i);
                }

            } else {
                b = Integer.parseInt(s);
                list.add(b);
            }
        }
        return list;
    }

    public static boolean between(int i, int min, int max) {
        return i >= min && i <= max;
    }
    
    public static Stream<Node> toStream(NodeList nodeList) {
        Spliterator<Node> splt = Spliterators.spliterator(new NodeListIterator(nodeList), nodeList.getLength(),
                Spliterator.ORDERED | Spliterator.NONNULL);
        return StreamSupport.stream(splt, true);
    }

}
