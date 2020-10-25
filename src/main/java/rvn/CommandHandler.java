package rvn;

import java.util.function.Function;
import java.util.logging.Logger;

/**
 *
 * @author wozza
 */
public class CommandHandler implements Function<String, Boolean> {
    
    final String verb;
    final String format;
    final String description;
    private final Function<String, Boolean> fun;
    private Logger log = Logger.getLogger(this.getClass().getName());

    public CommandHandler(String verb, String format, String description, Function<String, Boolean> fun) {
        this.verb = verb;
        this.format = format;
        this.description = description;
        this.fun = fun;
    }

    @Override
    public Boolean apply(String t) {
        boolean applied = this.fun.apply(t);
        if (applied) {
            log.info("handled by " + verb);
        }
        return applied;
    }
    
}
