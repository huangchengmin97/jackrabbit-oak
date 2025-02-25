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
package org.apache.jackrabbit.oak.jcr.delegate;

import org.apache.jackrabbit.oak.api.Root;
import org.apache.jackrabbit.oak.spi.security.authorization.permission.PermissionAware;
import org.apache.jackrabbit.oak.spi.security.authorization.permission.PermissionProvider;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class SessionDelegateTest extends AbstractDelegatorTest {

    @Test
    public void testRefreshAware() {
        PermissionProvider pp = mock(PermissionProvider.class);
        Root r = mockRoot(pp, true);
        SessionDelegate delegate = mockSessionDelegate(r, pp);

        PermissionAware pa = (PermissionAware) r;
        verify(pa, never()).getPermissionProvider();

        // calling refresh without permissionprovider field being assigned
        delegate.refresh(true);
        delegate.refresh(false);
        verify(pp, times(2)).refresh();

        // calling refresh with permissionprovider field being assigned
        delegate.getPermissionProvider();
        delegate.refresh(true);
        delegate.refresh(false);
        verify(pp, times(4)).refresh();

        verify(pa, times(1)).getPermissionProvider();
    }

    @Test
    public void testRefreshUnaware() {
        PermissionProvider pp = mock(PermissionProvider.class);
        Root r = mockRoot(pp, false);
        SessionDelegate delegate = mockSessionDelegate(r, pp);

        // calling refresh without permissionprovider field being assigned
        delegate.refresh(true);
        delegate.refresh(false);
        verify(pp, times(2)).refresh();

        // calling refresh with permissionprovider field being assigned
        delegate.getPermissionProvider();
        delegate.refresh(true);
        delegate.refresh(false);
        verify(pp, times(6)).refresh();
    }
}