/*
 * The MIT License (MIT) Copyright (c) 2020-2022 artipie.com
 * https://github.com/artipie/maven-adapter/blob/master/LICENSE.txt
 */
package com.artipie.maven;

import com.artipie.asto.Key;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Maven front for artipie maven adaptor.
 * @since 0.5
 */
public interface Maven {

    /**
     * Updates the metadata of a maven package.
     * @param upload Uploading artifact location
     * @param artifact Artifact location
     * @return Completion stage
     */
    CompletionStage<Void> update(Key upload, Key artifact);

    /**
     * Fake {@link Maven} implementation.
     * @since 0.5
     */
    class Fake implements Maven {

        /**
         * Was maven updated?
         */
        private boolean updated;

        @Override
        public CompletionStage<Void> update(final Key upload, final Key artifact) {
            this.updated = true;
            return CompletableFuture.allOf();
        }

        /**
         * Was maven updated?
         * @return True is was, false - otherwise
         */
        public boolean wasUpdated() {
            return this.updated;
        }
    }
}
