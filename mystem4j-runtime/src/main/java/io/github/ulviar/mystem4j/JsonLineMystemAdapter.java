package io.github.ulviar.mystem4j;

import io.github.ulviar.procwright.session.ProtocolAdapter;
import io.github.ulviar.procwright.session.ProtocolReaders;
import io.github.ulviar.procwright.session.ProtocolWriter;

final class JsonLineMystemAdapter implements ProtocolAdapter<String, String> {
    private final int maxResponseChars;

    JsonLineMystemAdapter(int maxResponseChars) {
        this.maxResponseChars = maxResponseChars;
    }

    @Override
    public void writeRequest(String request, ProtocolWriter writer) {
        MystemJsonLineProtocol.validateRequest(request);
        writer.writeLine(request);
        writer.flush();
    }

    @Override
    public String readResponse(ProtocolReaders readers) {
        return readers.stdout().readLine(maxResponseChars) + "\n";
    }
}
