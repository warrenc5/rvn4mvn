
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rvn.BuildIt;
import rvn.Globals;
import rvn.NVV;
import rvn.commands.NumberRangeCommand;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import java.util.stream.IntStream;
import static org.mockito.Mockito.mock;

/**
 *
 * @author wozza
 */
@ExtendWith(MockitoExtension.class)
public class CommandTests {

    @Mock
    BuildIt mock;

    @Test
    public void testNumberRangeCommand() {
        Globals.buildIndex.clear();
        IntStream.rangeClosed(1, 101)
                .mapToObj(i -> new NVV(Integer.toString(i), Integer.toString(i), Integer.toString(i)))
                .forEach(Globals.buildIndex::add);
        BuildIt.instance = mock;
        mock.lock = mock(Semaphore.class);
        NumberRangeCommand cmd = new NumberRangeCommand();
        cmd.apply("10-20,2`,3!,4-5`,7-7!");
        verify(mock, atLeastOnce()).buildACommand(any(NVV.class), anyString());
    }
}
