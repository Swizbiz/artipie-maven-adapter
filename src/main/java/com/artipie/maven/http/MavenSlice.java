/*
 * The MIT License (MIT) Copyright (c) 2020-2022 artipie.com
 * https://github.com/artipie/maven-adapter/blob/master/LICENSE.txt
 */
package com.artipie.maven.http;

import com.artipie.asto.Storage;
import com.artipie.http.Slice;
import com.artipie.http.auth.Action;
import com.artipie.http.auth.Authentication;
import com.artipie.http.auth.BasicAuthSlice;
import com.artipie.http.auth.Permission;
import com.artipie.http.auth.Permissions;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rs.StandardRs;
import com.artipie.http.rt.ByMethodsRule;
import com.artipie.http.rt.RtRule;
import com.artipie.http.rt.RtRulePath;
import com.artipie.http.rt.SliceRoute;
import com.artipie.http.slice.SliceSimple;
import com.artipie.maven.asto.AstoMaven;
import com.artipie.maven.asto.AstoValidUpload;

/**
 * Maven API entry point.
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
public final class MavenSlice extends Slice.Wrap {

    /**
     * Ctor.
     * @param storage The storage and default parameters for free access.
     */
    public MavenSlice(final Storage storage) {
        this(storage, Permissions.FREE, Authentication.ANONYMOUS);
    }

    /**
     * Private ctor since Artipie doesn't know about `Identities` implementation.
     * @param storage The storage.
     * @param perms Access permissions.
     * @param users Concrete identities.
     */
    public MavenSlice(final Storage storage, final Permissions perms, final Authentication users) {
        super(
            new SliceRoute(
                new RtRulePath(
                    new RtRule.Any(
                        new ByMethodsRule(RqMethod.GET),
                        new ByMethodsRule(RqMethod.HEAD)
                    ),
                    new BasicAuthSlice(
                        new LocalMavenSlice(storage),
                        users,
                        new Permission.ByName(perms, Action.Standard.READ)
                    )
                ),
                new RtRulePath(
                    new RtRule.All(
                        new ByMethodsRule(RqMethod.PUT),
                        new RtRule.ByPath(".*SNAPSHOT.*")
                    ),
                    new BasicAuthSlice(
                        new UploadSlice(storage),
                        users,
                        new Permission.ByName(perms, Action.Standard.WRITE)
                    )
                ),
                new RtRulePath(
                    new RtRule.All(
                        new ByMethodsRule(RqMethod.PUT),
                        new RtRule.ByPath(PutMetadataSlice.PTN_META)
                    ),
                    new BasicAuthSlice(
                        new PutMetadataSlice(storage),
                        users,
                        new Permission.ByName(perms, Action.Standard.WRITE)
                    )
                ),
                new RtRulePath(
                    new RtRule.All(
                        new ByMethodsRule(RqMethod.PUT),
                        new RtRule.ByPath(PutMetadataChecksumSlice.PTN)
                    ),
                    new BasicAuthSlice(
                        new PutMetadataChecksumSlice(
                            storage, new AstoValidUpload(storage), new AstoMaven(storage)
                        ),
                        users,
                        new Permission.ByName(perms, Action.Standard.WRITE)
                    )
                ),
                new RtRulePath(
                    new ByMethodsRule(RqMethod.PUT),
                    new BasicAuthSlice(
                        new UploadSlice(storage),
                        users,
                        new Permission.ByName(perms, Action.Standard.WRITE)
                    )
                ),
                new RtRulePath(
                    RtRule.FALLBACK, new SliceSimple(StandardRs.NOT_FOUND)
                )
            )
        );
    }
}
