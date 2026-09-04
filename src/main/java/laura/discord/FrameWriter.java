package laura.discord;

import laura.lib.javassist.Frame;

import java.io.IOException;
import java.io.OutputStream;

final class FrameWriter {
    private FrameWriter() {
    }

    static void a(OutputStream output, Frame frame) throws IOException {
        output.write(frame.a());
        output.flush();
    }
}
