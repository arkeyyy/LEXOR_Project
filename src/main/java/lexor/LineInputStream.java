package lexor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Prevents a reader from buffering subsequent LF/CRLF input lines. Does not own its input. */
final class LineInputStream extends InputStream {
    private final InputStream input;

    LineInputStream(InputStream input) {
        this.input = Objects.requireNonNull(input);
    }

    @Override
    public int read() throws IOException {
        return input.read();
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        if (length == 0) return 0;
        int count = 0;
        while (count < length) {
            // Propagate even a partial read failure: a SCAN line is accepted atomically.
            int next = input.read();
            if (next == -1) return count == 0 ? -1 : count;
            bytes[offset + count++] = (byte) next;
            if (next == '\n') break;
        }
        return count;
    }

    // available() stays zero so InputStreamReader cannot prefetch the next line.
    // close() stays a no-op so caller streams remain open.
}
