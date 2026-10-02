package com.asosiaciondeasis.animalesdeasis.Config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.FileAppender;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The log file sits in the same folder as the database, so a person at the
 * shelter can be asked for one folder and send both.
 */
class LogLocationTest {

    @Test
    void theLogFileFollowsTheDataDirectory() throws Exception {
        String dataDir = System.getProperty("animalesdeasis.data.dir");
        assumeTrue(dataDir != null, "run through Maven: the surefire configuration sets the data directory");

        LoggerContext context = new LoggerContext();
        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            // The application's own configuration, not logback-test.xml.
            configurator.doConfigure(LogLocationTest.class.getResource("/logback.xml"));

            FileAppender<?> file = (FileAppender<?>) context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("FILE");

            assertEquals(Database.DATA_DIR.resolve("logs").resolve("animalesdeasis.log"), Path.of(file.getFile()));
        } finally {
            context.stop();
        }
    }
}
