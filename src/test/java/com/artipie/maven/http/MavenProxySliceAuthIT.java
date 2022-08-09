/*
 * The MIT License (MIT) Copyright (c) 2020-2022 artipie.com
 * https://github.com/artipie/maven-adapter/blob/master/LICENSE.txt
 */
package com.artipie.maven.http;

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.cache.Cache;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.http.Headers;
import com.artipie.http.Slice;
import com.artipie.http.auth.Authentication;
import com.artipie.http.client.auth.BasicAuthenticator;
import com.artipie.http.client.jetty.JettyClientSlices;
import com.artipie.http.hm.RsHasStatus;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.slice.LoggingSlice;
import com.artipie.vertx.VertxSliceServer;
import io.vertx.reactivex.core.Vertx;
import java.net.URI;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link MavenProxySlice} to verify it works with target requiring authentication.
 *
 * @since 0.7
 * @todo #222:30min This test is failing after changes by #222, figure out why and fix it.
 *  Also, this test should be extended to be sure proxy does not work when auth is required nut not
 *  provided.
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@Disabled
final class MavenProxySliceAuthIT {

    /**
     * Vertx instance.
     */
    private static final Vertx VERTX = Vertx.vertx();

    /**
     * Jetty client.
     */
    private final JettyClientSlices client = new JettyClientSlices();

    /**
     * Maven proxy.
     */
    private Slice proxy;

    /**
     * Vertx slice server instance.
     */
    private VertxSliceServer server;

    @BeforeEach
    void setUp() throws Exception {
        final Storage storage = new InMemoryStorage();
        new TestResource("com/artipie/helloworld").addFilesTo(
            storage,
            new Key.From("com", "artipie", "helloworld")
        );
        final String username = "alice";
        final String password = "qwerty";
        this.server = new VertxSliceServer(
            MavenProxySliceAuthIT.VERTX,
            new LoggingSlice(
                new MavenSlice(
                    storage,
                    (user, action) -> user.name().equals(username),
                    new Authentication.Single(username, password)
                )
            )
        );
        final int port = this.server.start();
        this.client.start();
        this.proxy = new LoggingSlice(
            new MavenProxySlice(
                this.client,
                URI.create(String.format("http://localhost:%d", port)),
                new BasicAuthenticator(username, password),
                Cache.NOP
            )
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        this.client.stop();
        this.server.stop();
    }

    @Test
    void shouldGet() {
        MatcherAssert.assertThat(
            this.proxy.response(
                new RequestLine(
                    RqMethod.GET,
                    "/com/artipie/helloworld/0.1/helloworld-0.1.pom"
                ).toString(),
                Headers.EMPTY,
                Content.EMPTY
            ),
            new RsHasStatus(RsStatus.OK)
        );
    }
}
