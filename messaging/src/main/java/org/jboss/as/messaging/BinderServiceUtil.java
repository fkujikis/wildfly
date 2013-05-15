/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.messaging;

import javax.naming.InitialContext;

import org.jboss.as.naming.ContextListAndJndiViewManagedReferenceFactory;
import org.jboss.as.naming.ContextListManagedReferenceFactory;
import org.jboss.as.naming.ManagedReference;
import org.jboss.as.naming.ServiceBasedNamingStore;
import org.jboss.as.naming.ValueManagedReference;
import org.jboss.as.naming.ValueManagedReferenceFactory;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.naming.service.BinderService;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.ImmediateValue;
import org.jboss.msc.value.Values;

/**
 * Utility class to install BinderService (either to bind actual objects or create alias on another binding).
 *
 * @author <a href="http://jmesnil.net">Jeff Mesnil</a> (c) 2013 Red Hat Inc.
 */
public class BinderServiceUtil {

    public static void installBinderService(final ServiceTarget serviceTarget,
                                                 final String name,
                                                 final Object obj) {
        final ContextNames.BindInfo bindInfo = ContextNames.bindInfoFor(name);
        final BinderService binderService = new BinderService(bindInfo.getBindName());

        serviceTarget.addService(bindInfo.getBinderServiceName(), binderService)
                .addDependency(bindInfo.getParentContextServiceName(), ServiceBasedNamingStore.class, binderService.getNamingStoreInjector())
                .addInjection(binderService.getManagedObjectInjector(), new ValueManagedReferenceFactory(Values.immediateValue(obj)))
                .setInitialMode(ServiceController.Mode.ACTIVE)
                .install();
    }

    public static void installAliasBinderService(final ServiceTarget serviceTarget,
                                                 final ServiceName namingStoreServiceName,
                                                 final String name,
                                                 final ServiceName aliasServiceName,
                                                 final String alias) {
        final BinderService aliasBinderService = new BinderService(alias);
        aliasBinderService.getManagedObjectInjector().inject(new AliasManagedReferenceFactory(name));

        serviceTarget.addService(aliasServiceName, aliasBinderService)
                .addDependency(namingStoreServiceName, ServiceBasedNamingStore.class, aliasBinderService.getNamingStoreInjector())
                .addDependency(ContextNames.bindInfoFor(name).getBinderServiceName())
                .install();
    }

    private static final class AliasManagedReferenceFactory implements ContextListAndJndiViewManagedReferenceFactory {

        private final String name;

        /**
         * @param name original JNDI name
         */
        public AliasManagedReferenceFactory(String name) {
            this.name = name;
        }

        @Override
        public ManagedReference getReference() {
            try {
                final Object value = new InitialContext().lookup(name);
                return new ValueManagedReference(new ImmediateValue<Object>(value));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String getInstanceClassName() {
            final Object value = getReference().getInstance();
            return value != null ? value.getClass().getName() : ContextListManagedReferenceFactory.DEFAULT_INSTANCE_CLASS_NAME;
        }

        @Override
        public String getJndiViewInstanceValue() {
            return String.valueOf(getReference().getInstance());
        }
    }
}