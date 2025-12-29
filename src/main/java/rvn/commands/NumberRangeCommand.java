package rvn.commands;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import rvn.BuildIt;
import rvn.CommandHandler;
import rvn.Globals;
import static rvn.Globals.buildIndex;
import rvn.NVV;

public class NumberRangeCommand extends CommandHandler {

    public NumberRangeCommand() {
        super("(\\d+)-(\\d+)([!`])?", "1`,3-5", "Builds the given project with the commands. To rebuild last use `,  To list commands omit the second argument.", (command) -> {
            BuildIt buildIt = BuildIt.getInstance();
            Scanner scanner = new Scanner(command);
            scanner.useDelimiter(Pattern.compile(","));

            Logger log = Logger.getLogger(NumberRangeCommand.class.getName());
            log.info(command);

            Integer cmdIndex = null;
            Set<NVV> nvvs = new HashSet<>();

            while (scanner.hasNext()) {
                String token = scanner.next().trim();

                Matcher matcher = null;

                int start = 0, end = 0;
                String directive = "";
                if ((matcher = Pattern.compile("(\\d+)([!`])?").matcher(token)).matches()) {
                    start = Integer.parseInt(matcher.group(1));
                    end = start;
                    directive = matcher.group(2);
                } else if ((matcher = Pattern.compile("(\\d+)-(\\d+)([!`])?").matcher(token)).matches()) {
                    start = Integer.parseInt(matcher.group(1));
                    end = Integer.parseInt(matcher.group(2));
                    directive = matcher.group(3);
                }
                if (Globals.buildIndex.size() <= start) {
                    log.warning("not enough projects max is " + (start = Globals.buildIndex.size()));
                    return TRUE;
                }

                for (int index = start; index <= end; index++) {
                    NVV nvv = buildIndex.get(index);

                    if (nvv != null) {
                        nvvs.add(nvv);
                    }
                }
            }
            try {
                buildIt.lock.acquire();
            } catch (InterruptedException ex) {
                return FALSE;
            }
            nvvs.stream().forEach(n -> buildIt.buildACommand(n, "-"));

            buildIt.lock.release();

            return TRUE;
        }); 
    }
}
/**
                String cmd = null;

                if ("`".equals(directive)) {
                    cmd = previousCmdIdx.get(index);
                } else {
                    cmd = directive;
                    previousCmdIdx.put(index, cmd);
                }

                List<Integer> rangeIdx = rangeToIndex(cmd);
 for (Integer cmdIdx : rangeIdx) {
                    cmd = commands.get(cmdIdx);
                    log.info(cmd);
                    if ("!".equals(matcher.group(2))) {
                        ConfigFactory.getInstance().toggleCommand(nvv, cmd);
                    } else {
                        buildIt.buildACommand(nvv, cmd);
                    }
                }
                buildIt.lock.release();
            } else {

                AtomicInteger i = new AtomicInteger();
                commands.stream()
                        .forEach(s -> log.info(i.getAndIncrement() + " " + s));
            }

            return TRUE;
        }
        return FALSE;
    }

);
    }
         }
 */
