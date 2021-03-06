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
package org.gradle.cache.internal

import org.gradle.api.Action
import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheValidator
import org.gradle.cache.CleanupAction
import org.gradle.cache.FileLockManager
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.locklistener.NoOpFileLockContentionHandler
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GUtil

import java.util.concurrent.TimeUnit

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode

class DefaultPersistentDirectoryCacheTest extends AbstractProjectBuilderSpec {
    def metaDataProvider = Stub(ProcessMetaDataProvider) {
        getProcessDisplayName() >> "gradle"
        getProcessIdentifier() >> "id"
    }
    def lockManager = new DefaultFileLockManager(metaDataProvider, new NoOpFileLockContentionHandler())
    def validator = Stub(CacheValidator) {
        isValid() >> true
    }
    def initializationAction = Mock(Action)
    def cleanupAction = Mock(CleanupAction)

    def properties = ['prop': 'value', 'prop2': 'other-value']

    def initialisesCacheWhenCacheDirDoesNotExist() {
        given:
        def emptyDir = temporaryFolder.getTestDirectory().file("dir")

        expect:
        emptyDir.assertDoesNotExist()

        when:
        def cache = new DefaultPersistentDirectoryCache(emptyDir, "<display-name>", validator, properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), initializationAction, cleanupAction, lockManager, Mock(ExecutorFactory))
        try {
            cache.open()
        } finally {
            cache.close()
        }

        then:
        1 * initializationAction.execute(_ as PersistentCache)
        0 * _
        loadProperties(emptyDir.file("cache.properties")) == properties
    }

    def initializesCacheWhenPropertiesFileDoesNotExist() {
        given:
        def dir = temporaryFolder.getTestDirectory().file("dir").createDir()
        def cache = new DefaultPersistentDirectoryCache(dir, "<display-name>", validator, properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), initializationAction, cleanupAction, lockManager, Mock(ExecutorFactory))

        when:
        try {
            cache.open()
        } finally {
            cache.close()
        }

        then:
        1 * initializationAction.execute(_ as PersistentCache)
        0 * _
        loadProperties(dir.file("cache.properties")) == properties
    }

    def rebuildsCacheWhenPropertiesHaveChanged() {
        given:
        def dir = createCacheDir("prop", "other-value")
        def cache = new DefaultPersistentDirectoryCache(dir, "<display-name>", validator, properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), initializationAction, cleanupAction, lockManager, Mock(ExecutorFactory))

        when:
        try {
            cache.open()
        } finally {
            cache.close()
        }

        then:
        1 * initializationAction.execute(_ as PersistentCache)
        0 * _
        loadProperties(dir.file("cache.properties")) == properties
    }

    def rebuildsCacheWhenCacheValidatorReturnsFalse() {
        given:
        def dir = createCacheDir()
        def invalidator = Mock(CacheValidator)
        def cache = new DefaultPersistentDirectoryCache(dir, "<display-name>", invalidator, properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), initializationAction, cleanupAction, lockManager, Mock(ExecutorFactory))

        when:
        try {
            cache.open()
        } finally {
            cache.close()
        }

        then:
        1 * initializationAction.execute(_ as PersistentCache)
        2 * invalidator.isValid() >> false
        _ * invalidator.isValid() >> true
        0 * _
        loadProperties(dir.file("cache.properties")) == properties
    }

    def rebuildsCacheWhenInitializerFailedOnPreviousOpen() {
        given:
        def dir = temporaryFolder.getTestDirectory().file("dir").createDir()
        final RuntimeException failure = new RuntimeException()
        Action<PersistentCache> failingAction = Stub(Action) {
            execute(_ as PersistentCache) >> { throw failure }
        }
        def cache = new DefaultPersistentDirectoryCache(dir, "<display-name>", validator, properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), failingAction, cleanupAction, lockManager, Mock(ExecutorFactory))

        when:
        try {
            cache.open()
        } finally {
            cache.close()
        }

        then:
        RuntimeException e = thrown()
        e.cause.is(failure)

        when:
        cache = new DefaultPersistentDirectoryCache(dir, "<display-name>", validator, properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), initializationAction, cleanupAction, lockManager, Mock(ExecutorFactory))
        try {
            cache.open()
        } finally {
            cache.close()
        }

        then:
        1 * initializationAction.execute(_ as PersistentCache)
        0 * _
        loadProperties(dir.file("cache.properties")) == properties
    }

    def doesNotInitializeCacheWhenCacheDirExistsAndIsNotInvalid() {
        given:
        def dir = createCacheDir()
        def cache = new DefaultPersistentDirectoryCache(dir, "<display-name>", validator, properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), initializationAction, cleanupAction, lockManager, Mock(ExecutorFactory))

        when:
        try {
            cache.open()
        } finally {
            cache.close()
        }

        then:
        0 * _  // Does not call initialization action.
        dir.file("cache.properties").isFile()
        dir.file("some-file").isFile()
    }

    def "runs cleanup action when it is due"() {
        given:
        def dir = createCacheDir()
        def gcFile = dir.file("gc.properties")
        def cache = new DefaultPersistentDirectoryCache(dir, "<display-name>", validator, properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), initializationAction, cleanupAction, lockManager, Mock(ExecutorFactory))

        when:
        try {
            cache.open()
        } finally {
            cache.close()
        }

        then:
        0 * _  // Does not call initialization or cleanup action.
        gcFile.assertIsFile()

        when:
        gcFile.setLastModified(gcFile.lastModified() - TimeUnit.DAYS.toMillis(7))
        try {
            cache.open()
        } finally {
            cache.close()
        }
        then:
        1 * cleanupAction.clean(cache)
        0 * _
    }

    def "fails gracefully if cleanup action fails"() {
        given:
        def dir = createCacheDir()
        def gcFile = dir.file("gc.properties")
        def failingCleanupAction = new CleanupAction() {
            @Override
            void clean(PersistentCache persistentCache) {
                throw new Exception("Boom")
            }
        }
        def cache = new DefaultPersistentDirectoryCache(dir, "<display-name>", validator, properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), initializationAction, failingCleanupAction, lockManager, Mock(ExecutorFactory))

        when:
        try {
            cache.open()
        } finally {
            cache.close()
        }

        then:
        0 * _  // Does not call initialization or cleanup action.
        gcFile.assertIsFile()

        when:
        markCacheForCleanup(gcFile)
        try {
            cache.open()
        } finally {
            cache.close()
        }
        then:
        noExceptionThrown()
        0 * _
    }

    private void markCacheForCleanup(TestFile gcFile) {
        gcFile.setLastModified(gcFile.lastModified() - TimeUnit.DAYS.toMillis(7))
    }

    def "does not use gc.properties when no cleanup action is defined"() {
        given:
        def dir = createCacheDir()
        def gcFile = dir.file("gc.properties")
        def cache = new DefaultPersistentDirectoryCache(dir, "<display-name>", validator, properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), initializationAction, null, lockManager, Mock(ExecutorFactory))

        when:
        try {
            cache.open()
        } finally {
            cache.close()
        }

        then:
        0 * _
        gcFile.assertDoesNotExist()
    }

    private static Map<String, String> loadProperties(TestFile file) {
        Properties properties = GUtil.loadProperties(file)
        Map<String, String> result = new HashMap<String, String>()
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue().toString())
        }
        return result
    }

    private TestFile createCacheDir(String... extraProps) {
        def dir = temporaryFolder.getTestDirectory()

        Map<String, Object> properties = new HashMap<String, Object>()
        properties.putAll(this.properties)
        properties.putAll(GUtil.map((Object[]) extraProps))

        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, "<display-name>", validator, properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), null, null, lockManager, Mock(ExecutorFactory))

        try {
            cache.open()
            dir.file("some-file").touch()
        } finally {
            cache.close()
        }

        return dir
    }
}
