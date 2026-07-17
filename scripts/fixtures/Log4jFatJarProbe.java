import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Log4jFatJarProbe {
    private static final Logger LOGGER = LogManager.getLogger();

    private Log4jFatJarProbe() {
    }

    public static void main(String[] args) {
        System.out.println(LOGGER.getName());
    }
}
