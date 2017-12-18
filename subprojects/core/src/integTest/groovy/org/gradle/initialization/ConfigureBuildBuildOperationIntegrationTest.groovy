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

package org.gradle.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationNotificationsFixture
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.operations.trace.BuildOperationRecord


class ConfigureBuildBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    final buildOperations = new BuildOperationsFixture(executer, temporaryFolder)
    final buildOperationNotifications = new BuildOperationNotificationsFixture(executer, temporaryFolder)

    def "multiproject settings with customizations are are exposed correctly"() {
        settingsFile << """
        include "b"
        include "a"
        """

        when:
        succeeds('help')

        then:
        operation().result.buildPath == ":"
    }

    def "composite participants expose their project structure"() {
        settingsFile << """
        include "a"
        includeBuild "nested"

        rootProject.name = "root"
        rootProject.buildFileName = 'root.gradle'

        """

        file("nested/settings.gradle") << """
        rootProject.name = "nested"
        include "b"
        """

        file("nested/build.gradle") << """
        group = "org.acme"
        version = "1.0"
        """

        when:
        succeeds('help')


        then:
        operations()[1].result.buildPath == ":"
        operations()[0].result.buildPath == ":nested"
    }

    private BuildOperationRecord operation(){
        def operationRecords = operations()
        assert operationRecords.size() == 1
        operationRecords.iterator().next()
    }

    private List<BuildOperationRecord> operations() {
        buildOperations.all(ConfigureBuildBuildOperationType)
    }
}
