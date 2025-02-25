/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.spi.security.authentication.external.impl;

import com.google.common.collect.ImmutableSet;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalIdentity;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalIdentityRef;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalUser;
import org.apache.jackrabbit.oak.spi.security.authentication.external.basic.DefaultSyncConfig;
import org.apache.jackrabbit.oak.spi.security.authentication.external.basic.DefaultSyncContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import static org.apache.jackrabbit.oak.spi.security.authentication.external.impl.ExternalIdentityConstants.REP_EXTERNAL_PRINCIPAL_NAMES;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EnforceDynamicMembershipTest extends DynamicSyncContextTest {

    @Override
    @NotNull
    protected DefaultSyncConfig createSyncConfig() {
        DefaultSyncConfig sc = super.createSyncConfig();
        sc.user().setDynamicMembership(true).setEnforceDynamicMembership(true);
        return sc;
    }

    @Test
    public void testSyncMembershipWithChangedExistingGroups() throws Exception {
        long nesting = 1;
        syncConfig.user().setMembershipNestingDepth(nesting);

        ExternalUser externalUser = idp.getUser(USER_ID);

        DefaultSyncContext ctx = new DefaultSyncContext(syncConfig, idp, userManager, valueFactory);
        ctx.sync(externalUser);
        ctx.close();

        Authorizable a = userManager.getAuthorizable(externalUser.getId());
        assertSyncedMembership(userManager, a, externalUser);
        r.commit();

        // set with different groups than defined on IDP
        TestUserWithGroupRefs mod = new TestUserWithGroupRefs(externalUser, ImmutableSet.of(
                idp.getGroup("a").getExternalId(),
                idp.getGroup("aa").getExternalId(),
                idp.getGroup("secondGroup").getExternalId()));
        syncContext.syncMembership(mod, a, nesting);

        Tree t = r.getTree(a.getPath());
        assertTrue(t.hasProperty(REP_EXTERNAL_PRINCIPAL_NAMES));
        assertMigratedGroups(userManager, mod, null);
        assertMigratedGroups(userManager, externalUser, null);
    }

    @Test
    public void testSyncExternalUserExistingGroups() throws Exception {
        syncConfig.user().setMembershipNestingDepth(1);

        ExternalUser externalUser = idp.getUser(USER_ID);

        DefaultSyncContext ctx = new DefaultSyncContext(syncConfig, idp, userManager, valueFactory);
        ctx.sync(externalUser);
        ctx.close();

        Authorizable a = userManager.getAuthorizable(USER_ID);
        assertSyncedMembership(userManager, a, externalUser);
        // add an addition member to one group
        ExternalIdentityRef grRef = externalUser.getDeclaredGroups().iterator().next();
        Group gr = userManager.getAuthorizable(grRef.getId(), Group.class);
        gr.addMember(userManager.createGroup("someOtherMember"));
        r.commit();
        
        syncContext.setForceUserSync(true);
        syncConfig.user().setMembershipExpirationTime(-1);
        syncContext.sync(externalUser);

        // membership must have been migrated from group to rep:externalPrincipalNames
        // groups that have no other members left must be deleted.
        Tree t = r.getTree(a.getPath());
        assertTrue(t.hasProperty(REP_EXTERNAL_PRINCIPAL_NAMES));
        assertDynamicMembership(externalUser, 1);
        assertMigratedGroups(userManager, externalUser, grRef);
    }

    @Test
    public void testGroupFromDifferentIDP() throws Exception {
        syncConfig.user().setMembershipNestingDepth(1);

        ExternalUser externalUser = idp.getUser(USER_ID);

        DefaultSyncContext ctx = new DefaultSyncContext(syncConfig, idp, userManager, valueFactory);
        ctx.sync(externalUser);
        ctx.close();

        Authorizable a = userManager.getAuthorizable(USER_ID);
        assertSyncedMembership(userManager, a, externalUser);
        // add as member to a group from a different IDP
        Group gr = userManager.createGroup("anotherGroup");
        gr.addMember(a);
        r.commit();

        syncContext.setForceUserSync(true);
        syncConfig.user().setMembershipExpirationTime(-1);
        syncContext.sync(externalUser);

        // membership must have been migrated from group to rep:externalPrincipalNames
        // groups that have no other members left must be deleted.
        Tree t = r.getTree(a.getPath());
        assertTrue(t.hasProperty(REP_EXTERNAL_PRINCIPAL_NAMES));
        assertDynamicMembership(externalUser, 1);
        assertMigratedGroups(userManager, externalUser, null);
        
        gr = userManager.getAuthorizable("anotherGroup", Group.class);
        assertNotNull(gr);
        assertTrue(gr.isMember(a));
    }

    private static void assertMigratedGroups(@NotNull UserManager userManager,
                                             @NotNull ExternalIdentity externalIdentity, 
                                             @Nullable ExternalIdentityRef grRef) throws Exception {
        for (ExternalIdentityRef ref : externalIdentity.getDeclaredGroups()) {
            Group gr = userManager.getAuthorizable(ref.getId(), Group.class);
            if (ref.equals(grRef)) {
                assertNotNull(gr);
            } else {
                assertNull(gr);
            }
        }
    }
}