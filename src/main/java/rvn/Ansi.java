package rvn;

/**
 *
 * @author wozza
 */
public class Ansi {

    public static final boolean isAnsi() {

        String term = System.getenv().get("TERM");
        boolean isAnsi = System.console() != null && term != null && (term.contains("color") || term.contains("xterm"));
        return isAnsi;
    }

    public static final Boolean IS_ANSI = isAnsi();
    public static final String ANSI_RESET = IS_ANSI ? "\u001B[0m" : "";
    public static final String ANSI_BOLD = IS_ANSI ? "\u001B[1m" : "";
    public static final String ANSI_BLACK = IS_ANSI ? "\u001B[30m" : "";
    public static final String ANSI_RED = IS_ANSI ? "\u001B[31m" : "";
    public static final String ANSI_GREEN = IS_ANSI ? "\u001B[32m" : "";
    public static final String ANSI_YELLOW = IS_ANSI ? "\u001B[33m" : "";
    public static final String ANSI_BLUE = IS_ANSI ? "\u001B[34m" : "";
    public static final String ANSI_PURPLE = IS_ANSI ? "\u001B[35m" : "";
    public static final String ANSI_CYAN = IS_ANSI ? "\u001B[36m" : "";
    public static final String ANSI_WHITE = IS_ANSI ? "\u001B[37m" : "";

}
