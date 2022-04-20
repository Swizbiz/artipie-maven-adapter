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
package com.artipie.maven.asto;

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.ContentDigest;
import com.artipie.asto.ext.Digests;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.rx.RxStorageWrapper;
import hu.akarnokd.rxjava2.interop.SingleInterop;
import io.reactivex.Observable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * Checksums for Maven artifact.
 * @since 0.5
 */
public final class RepositoryChecksums {

    /**
     * Supported checksum algorithms.
     */
    private static final Set<String> SUPPORTED_ALGS = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList("sha512", "sha256", "sha1", "md5"))
    );

    /**
     * Repository storage.
     */
    private final Storage repo;

    /**
     * Repository checksums.
     * @param repo Repository storage
     */
    public RepositoryChecksums(final Storage repo) {
        this.repo = repo;
    }

    /**
     * Checksums of artifact.
     * @param artifact Artifact {@link Key}
     * @return Checksums future
     */
    public CompletionStage<? extends Map<String, String>> checksums(final Key artifact) {
        final RxStorageWrapper rxsto = new RxStorageWrapper(this.repo);
        return rxsto.list(artifact).flatMapObservable(Observable::fromIterable)
            .filter(key -> SUPPORTED_ALGS.contains(extension(key)))
            .flatMapSingle(
                item -> SingleInterop.fromFuture(
                    this.repo.value(item).thenCompose(pub -> new PublisherAs(pub).asciiString())
                        .thenApply(hash -> new ImmutablePair<>(extension(item), hash))
                )
            ).reduce(
                new HashMap<String, String>(),
                (map, hash) -> {
                    map.put(hash.getKey(), hash.getValue());
                    return map;
                }
            ).to(SingleInterop.get());
    }

    /**
     * Calculates and generates artifact checksum files.
     * @param artifact Artifact
     * @return Completable action
     */
    public CompletionStage<Void> generate(final Key artifact) {
        return CompletableFuture.allOf(
            SUPPORTED_ALGS.stream().map(
                alg -> this.repo.value(artifact).thenCompose(
                    content -> new ContentDigest(
                        content, Digests.valueOf(alg.toUpperCase(Locale.US))
                    ).hex().thenCompose(
                        hex -> this.repo.save(
                            new Key.From(String.format("%s.%s", artifact.string(), alg)),
                            new Content.From(hex.getBytes(StandardCharsets.UTF_8))
                        )
                    )
                )
            ).toArray(CompletableFuture[]::new)
        );
    }

    /**
     * Key extension.
     * @param key Key
     * @return Extension string
     */
    private static String extension(final Key key) {
        final String src = key.string();
        return src.substring(src.lastIndexOf('.') + 1).toLowerCase(Locale.US);
    }
}
