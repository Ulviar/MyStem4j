package consumer;

import io.github.ulviar.mystem4j.MystemOutputFormat;
import io.github.ulviar.mystem4j.kotlin.MystemDslKt;
import kotlin.Unit;

public final class Smoke {
    public static void main(String[] args) {
        var options = MystemDslKt.mystemOptions(dsl -> {
            dsl.grammarInfo(true);
            return Unit.INSTANCE;
        });
        if (!options.grammarInfo() || options.format() != MystemOutputFormat.JSON) {
            throw new AssertionError(options);
        }
    }
}
