package com.asosiaciondeasis.animalesdeasis;

import javafx.event.Event;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code #handler} a view names has to exist on its controller.
 *
 * <p>{@code FXMLLoader} resolves handlers when the view is loaded, so a renamed
 * or re-typed handler compiles cleanly and fails only when someone opens that
 * screen. {@link FxmlMarkupTest} strips handlers to check markup alone, and the
 * screen tests do not open every view, so this is the check that covers all of
 * them.</p>
 */
class FxmlHandlersTest {

    private static final Path FXML_ROOT = Path.of("src", "main", "resources", "fxml");
    private static final Pattern CONTROLLER = Pattern.compile("fx:controller=\"([^\"]+)\"");
    private static final Pattern HANDLER = Pattern.compile("\\bon[A-Z]\\w*=\"#(\\w+)\"");

    static Stream<Arguments> handlers() throws IOException {
        List<Arguments> handlers = new ArrayList<>();
        try (Stream<Path> views = Files.walk(FXML_ROOT)) {
            for (Path view : views.filter(path -> path.toString().endsWith(".fxml")).toList()) {
                String markup = Files.readString(view, StandardCharsets.UTF_8);
                Matcher controller = CONTROLLER.matcher(markup);
                String controllerName = controller.find() ? controller.group(1) : null;
                Matcher handler = HANDLER.matcher(markup);
                while (handler.find()) {
                    handlers.add(Arguments.of(view.getFileName().toString(), controllerName, handler.group(1)));
                }
            }
        }
        return handlers.stream();
    }

    @ParameterizedTest(name = "{0} #{2}")
    @MethodSource("handlers")
    void handlerResolves(String view, String controllerName, String handler) throws Exception {
        assertTrue(controllerName != null, view + " names #" + handler + " but declares no controller");
        assertTrue(declaresHandler(Class.forName(controllerName), handler),
                controllerName + " has no handler method " + handler + " for " + view);
    }

    /** A handler takes nothing, or the event that triggered it. */
    private static boolean declaresHandler(Class<?> controller, String name) {
        for (Class<?> type = controller; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name)) {
                    continue;
                }
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length == 0
                        || (parameters.length == 1 && Event.class.isAssignableFrom(parameters[0]))) {
                    return true;
                }
            }
        }
        return false;
    }
}
