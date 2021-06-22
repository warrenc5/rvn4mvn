package rvn;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author wozza
 */
public class SafeConsumer<T> implements Consumer<T> {
    
    protected Consumer<T> f;
    private Logger log = Logger.getLogger(this.getClass().getName());

    public SafeConsumer(Consumer<T> f) {
        this.f = f;
    }

    @Override
    public void accept(T t) {
        try {
            f.accept(t);
        } catch (Throwable ex) {
            log.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }
    
}
