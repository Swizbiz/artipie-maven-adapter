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
package com.artipie.maven.repository;

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Maven proxy cache.
 * @since 0.5
 */
public interface ProxyCache {

    /**
     * Cache that does nothing.
     */
    ProxyCache NOP = (key, remote) -> remote.get();

    /**
     * Try to load content from cache or fallback to remote publisher if cached key doesn't exist.
     * When loading remote item, the cache may save its content to the cache storage.
     * @param key Cached item key
     * @param remote Remote source
     * @return Content for key
     */
    CompletionStage<? extends Content> load(
        Key key, Supplier<? extends CompletionStage<? extends Content>> remote
    );
}
