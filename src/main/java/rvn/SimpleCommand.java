package rvn;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author wozza
 */
public abstract class SimpleCommand implements Function<String, Boolean> {

    private final Pattern pattern;
    private Logger log = Logger.getLogger(this.getClass().getName());

    public SimpleCommand(String pattern) {
        this(Pattern.compile(pattern));
    }

    public SimpleCommand(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public Boolean apply(String command) {
        Matcher matcher = pattern.matcher(command);
        if (matcher.matches()) {
            List<String> matches = new ArrayList<>(matcher.groupCount());
            for (int i = 0; i <= matcher.groupCount(); i++) {
                if (!matcher.group(i).isBlank()) {
                    matches.add(matcher.group(i));
                }
            }
            log.finest(matcher.groupCount() + " " + matches.toString());
            try {
                return this.configure(matches.iterator());
            } catch (Exception ex) {
                log.severe(matcher.groupCount() + " " + matches.toString() + " " + ex.getClass().getName() + " " + ex.getMessage());
                return Boolean.FALSE;
            }
        }
        return Boolean.FALSE;
    }

    public abstract Boolean configure(Iterator<String> i) throws Exception;

}
