package rvn;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Scanner;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.SyntaxError;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import static rvn.Ansi.ANSI_RESET;
import static rvn.Ansi.ANSI_YELLOW;
import static rvn.Globals.thenFinished;
import static rvn.Globals.thenStarted;

/**
 *
 * @author wozza
 */
public class CommandProcessor {

    private Logger log = Logger.getLogger(Rvn.class.getName());

    public List<CommandHandler> commandHandlers;

    public CommandProcessor(final Rvn rvn) {
        commandHandlers = new ArrayList<>();
        commandHandlers.addAll(new Commands().createCommandHandlers(rvn, this));
        thenFinished = Instant.now();
        thenStarted = Instant.now();
    }

    public void processStdIn() throws IOException {
        final Terminal terminal = TerminalBuilder.builder()
                //.system(true)
                //.color(true)
                //.streams(System.in, System.out)
                .jna(true)
                .build();
        DefaultHistory history = null;

        final LineReader lineReader
                = LineReaderBuilder.builder()
                        .terminal(terminal)
                        .history(history = new DefaultHistory())
                        //.completer(new MyCompleter())
                        //.highlighter(new MyHighlighter())
                        //.parser(new MyParser())
                        .build();

        history.add("history test item");

        CloseableIterator<String> iterator = new CloseableIterator<String>() {
            String line;

            public boolean hasNext() {
                return (line = lineReader.readLine()) != null;
            }

            public String next() {
                log.info(line);
                return line;
            }

            @Override
            public void close() throws IOException {
                terminal.close();
            }
        };

        this.processStdIn(iterator);
    }

    void processStdInOld() throws IOException {
        Scanner scanner = new Scanner(System.in);

        scanner.useDelimiter(System.getProperty("line.separator"));
        this.processStdIn(scanner);
    }

    private void processStdIn(Iterator<String> iterator) throws IOException {
        new StdInProcessor(iterator).start();
    }

    public void processCommand(final String command2) {
        final String command = command2.trim();
        log.info(String.format(ANSI_YELLOW + "%1$s" + ANSI_RESET + " last build finished " + ANSI_YELLOW + "%2$s" + ANSI_RESET + " ago. last build started " + ANSI_YELLOW + "%3$s" + ANSI_RESET + " ago.",
                LocalTime.now(), Duration.between(thenFinished, Instant.now()).toString(), Duration.between(thenStarted, Instant.now()).toString()));

        Optional<Boolean> handled = commandHandlers.stream().map(
                c -> c.apply(command)
        )
                .filter(b -> Boolean.TRUE.equals(b)).findFirst();
        if (handled.isPresent()) {
            if (handled.get().booleanValue()) {
                log.info("" + (char) (new Random().nextInt(5) + 1));
            }
        }
    }

    private static class MyParser implements Parser {

        public MyParser() {
        }

        @Override
        public ParsedLine parse(String string, int i, ParseContext pc) throws SyntaxError {
            throw new UnsupportedOperationException("Not supported yet." + string);
        }
    }

    class StdInProcessor extends Thread {

        private Spliterator<String> splt;
        private final Iterator<String> iterator;
        private int count;

        private StdInProcessor(Iterator<String> iterator) {
            splt = Spliterators.spliterator(iterator, Long.MAX_VALUE,
                    Spliterator.ORDERED | Spliterator.NONNULL);
            this.iterator = iterator;
            this.setName(this.getClass().getSimpleName());
        }

        public void run() {
            log.info(String.format("commanded ? for help"));

            while (this.isAlive()) {
                Thread.yield();
                try {
                    StreamSupport.stream(splt, false).onClose(() -> {
                        try {
                            ((Closeable) iterator).close();
                        } catch (IOException ex) {
                            Logger.getLogger(Rvn.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    })
                            .forEach(CommandProcessor.this::processCommand);
                    this.count =0 ;
                } catch (UserInterruptException x) {
                    log.info("got break in cmd handler");
                    if (count++ > 2) {
                        log.info("okay kill me");
                        System.exit(1);
                    }
                } catch (Exception x) {
                    log.info(x.getMessage() + " in cmd handler");
                    log.log(Level.WARNING, x.getMessage(), x);
                } catch (Error x) {
                    log.info(x.getMessage() + " in cmd handler");
                    log.log(Level.WARNING, x.getMessage(), x);
                    log.severe("see ya l8r");
                    System.exit(1);
                }
            }

            log.info(String.format("commandless"));

        }
    }
}
