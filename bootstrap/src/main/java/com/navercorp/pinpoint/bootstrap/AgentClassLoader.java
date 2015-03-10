/*
 * Copyright 2014 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.bootstrap;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.Callable;

import com.navercorp.pinpoint.bootstrap.config.ProfilerConfig;
import com.navercorp.pinpoint.common.service.ServiceTypeRegistryService;

/**
 * @author emeroad
 */
public class AgentClassLoader {

    private static final SecurityManager SECURITY_MANAGER = System.getSecurityManager();

    private final URLClassLoader classLoader;

    private String bootClass;

    private final ContextClassLoaderExecuteTemplate<Object> executeTemplate;

    public AgentClassLoader(URL[] urls) {
        if (urls == null) {
            throw new NullPointerException("urls");
        }

        ClassLoader bootStrapClassLoader = AgentClassLoader.class.getClassLoader();
        this.classLoader = createClassLoader(urls, bootStrapClassLoader);

        this.executeTemplate = new ContextClassLoaderExecuteTemplate<Object>(classLoader);
    }

    private PinpointURLClassLoader createClassLoader(final URL[] urls, final ClassLoader bootStrapClassLoader) {
        if (SECURITY_MANAGER != null) {
            return AccessController.doPrivileged(new PrivilegedAction<PinpointURLClassLoader>() {
                public PinpointURLClassLoader run() {
                    return new PinpointURLClassLoader(urls, bootStrapClassLoader);
                }
            });
        } else {
            return new PinpointURLClassLoader(urls, bootStrapClassLoader);
        }
    }

    public void setBootClass(String bootClass) {
        this.bootClass = bootClass;
    }

    public Agent boot(final String agentArgs, final Instrumentation instrumentation, final ProfilerConfig profilerConfig, final URL[] pluginJars, final ServiceTypeRegistryService serviceTypeRegistryService) {

        final Class<?> bootStrapClazz = getBootStrapClass();

        final Object agent = executeTemplate.execute(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                try {
                    Constructor<?> constructor = bootStrapClazz.getConstructor(String.class, Instrumentation.class, ProfilerConfig.class, URL[].class, ServiceTypeRegistryService.class);
                    return constructor.newInstance(agentArgs, instrumentation, profilerConfig, pluginJars, serviceTypeRegistryService);
                } catch (InstantiationException e) {
                    throw new BootStrapException("boot create failed. Error:" + e.getMessage(), e);
                } catch (IllegalAccessException e) {
                    throw new BootStrapException("boot method invoke failed. Error:" + e.getMessage(), e);
                }
            }
        });

        if (agent instanceof Agent) {
            return (Agent) agent;
        } else {
            String agentClassName;
            if (agent == null) {
                agentClassName = "Agent is null";
            } else {
                agentClassName = agent.getClass().getName();
            }
            throw new BootStrapException("Invalid AgentType. boot failed. AgentClass:" + agentClassName);
        }
    }

    private Class<?> getBootStrapClass() {
        try {
            return this.classLoader.loadClass(bootClass);
        } catch (ClassNotFoundException e) {
            throw new BootStrapException("boot class not found. bootClass:" + bootClass + " Error:" + e.getMessage(), e);
        }
    }

}
