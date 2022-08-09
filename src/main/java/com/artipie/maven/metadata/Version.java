/*
 * The MIT License (MIT) Copyright (c) 2020-2022 artipie.com
 * https://github.com/artipie/maven-adapter/blob/master/LICENSE.txt
 */
package com.artipie.maven.metadata;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Artifact version.
 * @since 0.5
 */
public final class Version implements Comparable<Version> {

    /**
     * Version value as string.
     */
    private final String value;

    /**
     * Ctor.
     * @param value Version as string
     */
    public Version(final String value) {
        this.value = value;
    }

    @Override
    public int compareTo(final Version another) {
        return Arrays.compare(
            stringVersionToIntArray(this.value), stringVersionToIntArray(another.value)
        );
    }

    /**
     * Transforms.
     * @param version Version to clean
     * @return Version without snapshot
     */
    private static int[] stringVersionToIntArray(final String version) {
        return Stream.of(version.replace("-SNAPSHOT", "").split("\\."))
            .mapToInt(Integer::parseInt).toArray();
    }

}
