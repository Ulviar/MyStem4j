package io.github.ulviar.mystem4j;

import com.github.ulviar.icli.session.ProtocolAdapter;
import com.github.ulviar.icli.session.ProtocolReaders;
import com.github.ulviar.icli.session.ProtocolWriter;

final class JsonLineMystemAdapter implements ProtocolAdapter<String, String> {
    private final int maxResponseChars;

    JsonLineMystemAdapter(int maxResponseChars) {
        this.maxResponseChars = maxResponseChars;
    }

    @Override
    public void writeRequest(String request, ProtocolWriter writer) {
        if (request.indexOf('\n') >= 0 || request.indexOf('\r') >= 0) {
            throw new MystemInvalidOptionsException("Reusable MyStem JSON line protocol rejects multiline input.");
        }
        writer.writeLine(request);
        writer.flush();
    }

    @Override
    public String readResponse(ProtocolReaders readers) {
        return readers.stdout().readLine(maxResponseChars) + "\n";
    }
}
