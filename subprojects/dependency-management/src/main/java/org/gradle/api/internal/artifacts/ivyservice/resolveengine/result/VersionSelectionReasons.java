/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.artifacts.result.ComponentSelectionDescription;
import org.gradle.api.artifacts.result.ComponentSelectionReason;

import java.util.List;
import java.util.Set;

public class VersionSelectionReasons {
    public static final ComponentSelectionDescriptionInternal REQUESTED = new DefaultComponentSelectionDescription(ComponentSelectionCause.REQUESTED);
    public static final ComponentSelectionDescriptionInternal ROOT = new DefaultComponentSelectionDescription(ComponentSelectionCause.ROOT);
    public static final ComponentSelectionDescriptionInternal FORCED = new DefaultComponentSelectionDescription(ComponentSelectionCause.FORCED);
    public static final ComponentSelectionDescriptionInternal CONFLICT_RESOLUTION = new DefaultComponentSelectionDescription(ComponentSelectionCause.CONFLICT_RESOLUTION);
    public static final ComponentSelectionDescriptionInternal SELECTED_BY_RULE = new DefaultComponentSelectionDescription(ComponentSelectionCause.SELECTED_BY_RULE);
    public static final ComponentSelectionDescriptionInternal COMPOSITE_BUILD = new DefaultComponentSelectionDescription(ComponentSelectionCause.COMPOSITE_BUILD);

    public static ComponentSelectionReasonInternal requested() {
        return new DefaultComponentSelectionReason(REQUESTED);
    }


    public static ComponentSelectionReason root() {
        return new DefaultComponentSelectionReason(ROOT);
    }

    public static ComponentSelectionReasonInternal of(List<ComponentSelectionDescription> descriptions) {
        return new DefaultComponentSelectionReason(descriptions);
    }

    private static class DefaultComponentSelectionReason implements ComponentSelectionReasonInternal {

        // Use a set to de-duplicate same descriptions
        private final Set<ComponentSelectionDescription> descriptions;

        private DefaultComponentSelectionReason(ComponentSelectionDescription description) {
            descriptions = Sets.newLinkedHashSet();
            descriptions.add(description);
        }

        public DefaultComponentSelectionReason(List<ComponentSelectionDescription> descriptions) {
            this.descriptions = Sets.newLinkedHashSet(descriptions);
        }


        public boolean isForced() {
            return hasCause(ComponentSelectionCause.FORCED);
        }

        private boolean hasCause(ComponentSelectionCause cause) {
            for (ComponentSelectionDescription description : descriptions) {
                if (description.getCause() == cause) {
                    return true;
                }
            }
            return false;
        }

        public boolean isConflictResolution() {
            return hasCause(ComponentSelectionCause.CONFLICT_RESOLUTION);
        }

        public boolean isSelectedByRule() {
            return hasCause(ComponentSelectionCause.SELECTED_BY_RULE);
        }

        public boolean isExpected() {
            ComponentSelectionCause cause = Iterables.getLast(descriptions).getCause();
            return cause == ComponentSelectionCause.ROOT || cause == ComponentSelectionCause.REQUESTED;
        }

        public boolean isCompositeSubstitution() {
            return hasCause(ComponentSelectionCause.COMPOSITE_BUILD);
        }

        public String getDescription() {
            // for backwards compatibility, we use the last added description
            return Iterables.getLast(descriptions).toString();
        }

        public String toString() {
            return getDescription();
        }

        @Override
        public ComponentSelectionReasonInternal addCause(ComponentSelectionCause cause, String description) {
            descriptions.add(new DefaultComponentSelectionDescription(cause, description));
            return this;
        }


        @Override
        public ComponentSelectionReasonInternal setCause(ComponentSelectionDescription description) {
            descriptions.clear();
            descriptions.add(description);
            return this;
        }

        @Override
        public ComponentSelectionReasonInternal addCause(ComponentSelectionDescription description) {
            descriptions.add(description);
            return this;
        }

        @Override
        public List<ComponentSelectionDescription> getDescriptions() {
            return ImmutableList.copyOf(descriptions);
        }

        @Override
        public boolean hasCustomDescriptions() {
            for (ComponentSelectionDescription description : descriptions) {
                if (description.hasCustomDescription()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DefaultComponentSelectionReason that = (DefaultComponentSelectionReason) o;
            return Objects.equal(descriptions, that.descriptions);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(descriptions);
        }
    }
}
