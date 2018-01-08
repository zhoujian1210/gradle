/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.project.ant;

import groovy.util.AntBuilder;
import org.apache.tools.ant.ComponentHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.gradle.api.Transformer;
import org.gradle.api.internal.file.ant.AntFileResource;
import org.gradle.api.internal.file.ant.BaseDirSelector;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public class BasicAntBuilder extends org.gradle.api.AntBuilder implements Closeable {
    // These are used to discard references to tasks so they can be garbage collected
    private static final Field NODE_FIELD;
    private static final Field COLLECTOR_FIELD;
    private static final Field CHILDREN_FIELD;

    static {
        try {
            NODE_FIELD = AntBuilder.class.getDeclaredField("lastCompletedNode");
            NODE_FIELD.setAccessible(true);
            COLLECTOR_FIELD = AntBuilder.class.getDeclaredField("collectorTarget");
            COLLECTOR_FIELD.setAccessible(true);
            CHILDREN_FIELD = Target.class.getDeclaredField("children");
            CHILDREN_FIELD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List children;

    public BasicAntBuilder() {
        getAntProject().addDataTypeDefinition("gradleFileResource", AntFileResource.class);
        getAntProject().addDataTypeDefinition("gradleBaseDirSelector", BaseDirSelector.class);
    }

    @Override
    public Map<String, Object> getProperties() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Object> getReferences() {
        throw new UnsupportedOperationException();
    }

    public void importBuild(Object antBuildFile) {
        throw new UnsupportedOperationException();
    }

    public void importBuild(Object antBuildFile, Transformer<? extends String, ? super String> taskNamer) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void nodeCompleted(Object parent, Object node) {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(Project.class.getClassLoader());
        try {
            super.nodeCompleted(parent, node);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Override
    public void setLifecycleLogLevel(AntMessagePriority logLevel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AntMessagePriority getLifecycleLogLevel() {
        throw new UnsupportedOperationException();
    }

    protected Object postNodeCompletion(Object parent, Object node) {
        try {
            return NODE_FIELD.get(this);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    protected Object doInvokeMethod(String methodName, Object name, Object args) {
        Object value = super.doInvokeMethod(methodName, name, args);
        // Discard the node so it can be garbage collected. Some Ant tasks cache a potentially large amount of state
        // in fields.
        try {
            NODE_FIELD.set(this, null);
            getChildren().clear();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return value;
    }

    private List getChildren() {
        if (children == null) {
            try {
                Target target = (Target) COLLECTOR_FIELD.get(this);
                children = (List) CHILDREN_FIELD.get(target);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return children;
    }

    public void close() {
        Project project = getProject();
        project.fireBuildFinished(null);
        ComponentHelper helper = ComponentHelper.getComponentHelper(project);
        helper.getAntTypeTable().clear();
        helper.getDataTypeDefinitions().clear();
        project.getReferences().clear();
    }

}
