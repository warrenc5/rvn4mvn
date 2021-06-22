package rvn;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Supplier;

/**
 *
 * @author wozza
 */
public class DefaultExceptionMessage implements Supplier<String> {

    StringBuilder bob = new StringBuilder();

    private DefaultExceptionMessage(Exception e) {

        bob.append(e.getMessage());
        StringWriter s = null;;
        e.printStackTrace(new PrintWriter(s = new StringWriter()));
        bob.append(s.getBuffer().toString());

    }

    @Override
    public String get() {
        return bob.toString();
    }

    public static DefaultExceptionMessage ofX(Exception e) {
        return new DefaultExceptionMessage(e);
    }

}
