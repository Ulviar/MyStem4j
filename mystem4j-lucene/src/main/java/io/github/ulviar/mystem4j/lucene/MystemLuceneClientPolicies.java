package io.github.ulviar.mystem4j.lucene;

import io.github.ulviar.mystem4j.MystemClient;
import io.github.ulviar.mystem4j.MystemClientExecutionProfile;
import java.util.Objects;

final class MystemLuceneClientPolicies {
    private static final System.Logger LOGGER = System.getLogger(MystemLuceneAnalyzer.class.getName());

    private MystemLuceneClientPolicies() {}

    static void apply(MystemClient client, MystemLuceneClientPolicy policy) {
        MystemClientExecutionProfile profile =
                Objects.requireNonNull(client.executionProfile(), "client.executionProfile()");
        if (profile == MystemClientExecutionProfile.UNKNOWN
                || profile == MystemClientExecutionProfile.POOLED_SESSIONS
                || policy == MystemLuceneClientPolicy.ALLOW_ANY) {
            return;
        }

        String message = message(profile);
        if (policy == MystemLuceneClientPolicy.REQUIRE_POOLED_OR_UNKNOWN) {
            throw new IllegalArgumentException(message);
        }
        LOGGER.log(System.Logger.Level.WARNING, message);
    }

    private static String message(MystemClientExecutionProfile profile) {
        return switch (profile) {
            case ONE_SHOT_PROCESS_PER_REQUEST ->
                "Lucene analysis received a one-shot MyStem client; indexing will start a native MyStem process "
                        + "per analyzed field. Use a pooled client for indexing or set "
                        + "MystemLuceneClientPolicy.ALLOW_ANY.";
            case REUSABLE_SESSION ->
                "Lucene analysis received a reusable-session MyStem client; concurrent Lucene indexing will "
                        + "serialize requests through one MyStem process. Use a pooled client for indexing or set "
                        + "MystemLuceneClientPolicy.ALLOW_ANY.";
            case UNKNOWN, POOLED_SESSIONS ->
                throw new IllegalArgumentException("No Lucene policy message for " + profile);
        };
    }
}
