/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.initialization;


import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * Configuring a build.
 *
 * @since 4.5
 */
public interface ConfigureBuildBuildOperationType extends BuildOperationType<ConfigureBuildBuildOperationType.Details, ConfigureBuildBuildOperationType.Result> {
    @UsedByScanPlugin
    interface Details {
    }

    @UsedByScanPlugin
    interface Result {
        /**
         * The build path configured.
         * Never null.
         *
         * @since 4.5
         */
        String getBuildPath();
    }
}
