package myhttp;

import java.io.IOException;

@FunctionalInterface
public interface IHandlerInterface {

    void handler(Request req, Response res) throws IOException;
}
