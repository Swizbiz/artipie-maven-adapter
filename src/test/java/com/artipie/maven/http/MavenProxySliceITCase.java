/*
 * MIT License
 *
 * Copyright (c) 2020 Artipie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.maven.http;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.cache.FromStorageCache;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.http.client.auth.Authenticator;
import com.artipie.http.client.jetty.JettyClientSlices;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.slice.LoggingSlice;
import com.artipie.vertx.VertxSliceServer;
import io.vertx.reactivex.core.Vertx;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link MavenProxySlice} to verify it can work with central.
 * @since 0.6
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class MavenProxySliceITCase {

    /**
     * Vertx instance.
     */
    private static final Vertx VERTX = Vertx.vertx();

    /**
     * Jetty client.
     */
    private final JettyClientSlices client = new JettyClientSlices();

    /**
     * Test storage.
     */
    private Storage storage;

    /**
     * Server port.
     */
    private int port;

    /**
     * Vertx slice server instance.
     */
    private VertxSliceServer server;

    @BeforeEach
    void setUp() throws Exception {
        this.client.start();
        this.storage = new InMemoryStorage();
        this.server = new VertxSliceServer(
            MavenProxySliceITCase.VERTX,
            new LoggingSlice(
                new MavenProxySlice(
                    this.client,
                    URI.create("https://repo.maven.apache.org/maven2"),
                    Authenticator.ANONYMOUS,
                    new FromStorageCache(this.storage)
                )
            )
        );
        this.port = this.server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        this.client.stop();
        this.server.stop();
    }

    @Test
    void downloadsJarFromCentralAndCachesIt() throws Exception {
        final HttpURLConnection con = (HttpURLConnection) new URL(
            String.format("http://localhost:%s/args4j/args4j/2.32/args4j-2.32.jar", this.port)
        ).openConnection();
        con.setRequestMethod("GET");
        MatcherAssert.assertThat(
            "Response status is 200",
            con.getResponseCode(),
            new IsEqual<>(Integer.parseInt(RsStatus.OK.code()))
        );
        MatcherAssert.assertThat(
            "Jar was saved to storage",
            this.storage.exists(new Key.From("args4j/args4j/2.32/args4j-2.32.jar")).join(),
            new IsEqual<>(true)
        );
        con.disconnect();
    }

    @Test
    void downloadsJarFromCache() throws Exception {
        new TestResource("com/artipie/helloworld")
            .addFilesTo(this.storage, new Key.From("com", "artipie", "helloworld"));
        final HttpURLConnection con = (HttpURLConnection) new URL(
            String.format(
                "http://localhost:%s/com/artipie/helloworld/0.1/helloworld-0.1.jar", this.port
            )
        ).openConnection();
        con.setRequestMethod("GET");
        MatcherAssert.assertThat(
            "Response status is 200",
            con.getResponseCode(),
            new IsEqual<>(Integer.parseInt(RsStatus.OK.code()))
        );
        con.disconnect();
    }

    @Test
    @Disabled
    void downloadJarFromCentralAndCacheFailsWithNotFound() throws Exception {
        final HttpURLConnection con = (HttpURLConnection) new URL(
            String.format("http://localhost:%s/notfoundexample", this.port)
        ).openConnection();
        con.setRequestMethod("GET");
        MatcherAssert.assertThat(
            con.getResponseCode(),
            new IsEqual<>(Integer.parseInt(RsStatus.NOT_FOUND.code()))
        );
        con.disconnect();
    }

    @Test
    void headRequestWorks() throws Exception {
        final HttpURLConnection con = (HttpURLConnection) new URL(
            String.format("http://localhost:%s/args4j/args4j/2.32/args4j-2.32.pom", this.port)
        ).openConnection();
        con.setRequestMethod("HEAD");
        MatcherAssert.assertThat(
            "Response status is 200",
            con.getResponseCode(),
            new IsEqual<>(Integer.parseInt(RsStatus.OK.code()))
        );
        MatcherAssert.assertThat(
            "Headers are returned",
            con.getHeaderFields(),
            Matchers.allOf(
                Matchers.hasKey("Content-Type"),
                Matchers.hasKey("Last-Modified"),
                Matchers.hasKey("ETag"),
                Matchers.hasKey("X-Checksum-MD5"),
                Matchers.hasKey("X-Checksum-SHA1")
            )
        );
        con.disconnect();
    }

    @Test
    void checksumRequestWorks() throws Exception {
        final HttpURLConnection con = (HttpURLConnection) new URL(
            String.format("http://localhost:%s/args4j/args4j/2.32/args4j-2.32.pom.md5", this.port)
        ).openConnection();
        con.setRequestMethod("GET");
        MatcherAssert.assertThat(
            "Response status is 200",
            con.getResponseCode(),
            new IsEqual<>(Integer.parseInt(RsStatus.OK.code()))
        );
        con.disconnect();
    }

}
