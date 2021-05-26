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

    private void processStdIn() throws IOException {
        final Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();
        final LineReader lineReader
                = LineReaderBuilder.builder()
                        .terminal(terminal)
                        //.completer(new MyCompleter())
                        //.highlighter(new MyHighlighter())
                        //.parser(new MyParser())
                        .build();

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

        Optional<Boolean> handled = commandHandlers.stream().map(c -> c.apply(command))
                .filter(b -> Boolean.TRUE.equals(b)).findFirst();
        if (handled.isPresent()) {
            if (handled.get().booleanValue()) {
                log.info("" + (char) (new Random().nextInt(5) + 1));
            }
        }
    }

    class StdInProcessor extends Thread {

        private Spliterator<String> splt;
        private final Iterator<String> iterator;

        private StdInProcessor(Iterator<String> iterator) {
            splt = Spliterators.spliterator(iterator, Long.MAX_VALUE,
                    Spliterator.ORDERED | Spliterator.NONNULL);
            this.iterator = iterator;
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
                    }).forEach(CommandProcessor.this::processCommand);
                } catch (Exception x) {
                    log.info(x.getMessage() + " in cmd handler");
                    log.log(Level.WARNING, x.getMessage(), x);
                }
            }

            log.info(String.format("commandless"));

        }
    }
}
