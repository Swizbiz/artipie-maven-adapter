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

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.cache.Cache;
import com.artipie.asto.cache.CacheControl;
import com.artipie.asto.cache.DigestVerification;
import com.artipie.asto.cache.Remote;
import com.artipie.asto.ext.Digests;
import com.artipie.http.Headers;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.async.AsyncResponse;
import com.artipie.http.headers.Header;
import com.artipie.http.rq.RequestLineFrom;
import com.artipie.http.rs.RsWithBody;
import com.artipie.http.rs.StandardRs;
import com.artipie.http.slice.KeyFromPath;
import io.reactivex.Flowable;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.reactivestreams.Publisher;

/**
 * Maven proxy slice with cache support.
 * @since 0.5
 * @todo #146:30min Create integration test for cached proxy:
 *  the test starts new server instance and serves HEAD requests for artifact with checksum
 *  headers, cache contains some artifact, test requests this artifact from `CachedProxySlice`
 *  with injected `Cache` and client `Slice` instances and verifies that target slice
 *  doesn't invalidate the cache if checksums headers matches and invalidates cache if
 *  checksums doesn't match.
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
final class CachedProxySlice implements Slice {

    /**
     * Checksum header pattern.
     */
    private static final Pattern CHECKSUM_PATTERN =
        Pattern.compile("x-checksum-(sha1|sha256|sha512|md5)", Pattern.CASE_INSENSITIVE);

    /**
     * Translation of checksum headers to digest algorithms.
     */
    private static final Map<String, String> DIGEST_NAMES = Map.of(
        "sha1", "SHA-1",
        "sha256", "SHA-256",
        "sha512", "SHA-512",
        "md5", "MD5"
    );

    /**
     * Origin slice.
     */
    private final Slice client;

    /**
     * Cache.
     */
    private final Cache cache;

    /**
     * Wraps origin slice with caching layer.
     * @param client Client slice
     * @param cache Cache
     */
    CachedProxySlice(final Slice client, final Cache cache) {
        this.client = client;
        this.cache = cache;
    }

    @Override
    public Response response(final String line, final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body) {
        final RequestLineFrom req = new RequestLineFrom(line);
        final Key key = new KeyFromPath(req.uri().getPath());
        return new AsyncResponse(
            new RepoHead(this.client)
                .head(req.uri().getPath()).thenCompose(
                    head -> this.cache.load(
                        key,
                        new Remote.WithErrorHandling(
                            () -> {
                                final CompletableFuture<Optional<? extends Content>> promise =
                                    new CompletableFuture<>();
                                this.client.response(line, Headers.EMPTY, Content.EMPTY).send(
                                    (rsstatus, rsheaders, rsbody) -> {
                                        final CompletableFuture<Void> term =
                                            new CompletableFuture<>();
                                        if (rsstatus.success()) {
                                            final Flowable<ByteBuffer> res =
                                                Flowable.fromPublisher(rsbody)
                                                .doOnError(term::completeExceptionally)
                                                .doOnTerminate(() -> term.complete(null));
                                            promise.complete(Optional.of(new Content.From(res)));
                                        } else {
                                            promise.complete(Optional.empty());
                                        }
                                        return term;
                                    }
                                );
                                return promise;
                            }
                        ),
                        new CacheControl.All(
                            StreamSupport.stream(
                                head.orElse(Headers.EMPTY).spliterator(),
                                false
                            ).map(Header::new)
                            .map(CachedProxySlice::checksumControl)
                            .collect(Collectors.toUnmodifiableList())
                        )
                    ).handle(
                        (content, throwable) -> {
                            final Response result;
                            if (throwable == null && content.isPresent()) {
                                result = new RsWithBody(
                                    StandardRs.OK, new Content.From(content.get())
                                );
                            } else {
                                result = StandardRs.NOT_FOUND;
                            }
                            return result;
                        }
                    )
            )
        );
    }

    /**
     * Checksum cache control verification.
     * @param header Checksum header
     * @return Cache control with digest
     */
    private static CacheControl checksumControl(final Header header) {
        final Matcher matcher = CachedProxySlice.CHECKSUM_PATTERN.matcher(header.getKey());
        final CacheControl res;
        if (matcher.matches()) {
            try {
                res = new DigestVerification(
                    new Digests.FromString(
                        CachedProxySlice.DIGEST_NAMES.get(
                            matcher.group(1).toLowerCase(Locale.US)
                        )
                    ).get(),
                    Hex.decodeHex(header.getValue().toCharArray())
                );
            } catch (final DecoderException err) {
                throw new IllegalStateException("Invalid digest hex", err);
            }
        } else {
            res = CacheControl.Standard.ALWAYS;
        }
        return res;
    }
}
