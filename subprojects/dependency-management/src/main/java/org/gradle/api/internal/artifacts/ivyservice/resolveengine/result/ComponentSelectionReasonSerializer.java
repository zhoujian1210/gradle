/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.artifacts.result.ComponentSelectionDescription;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.util.List;

@NotThreadSafe
public class ComponentSelectionReasonSerializer implements Serializer<ComponentSelectionReason> {

    private final BiMap<String, Integer> descriptions = HashBiMap.create();

    private OperationType lastOperationType = OperationType.read;

    public ComponentSelectionReason read(Decoder decoder) throws IOException {
        prepareForOperation(OperationType.read);
        List<ComponentSelectionDescription> descriptions = readDescriptions(decoder);
        return VersionSelectionReasons.of(descriptions);
    }

    private List<ComponentSelectionDescription> readDescriptions(Decoder decoder) throws IOException {
        int size = decoder.readSmallInt();
        ImmutableList.Builder<ComponentSelectionDescription> builder = new ImmutableList.Builder<ComponentSelectionDescription>();
        for (int i = 0; i < size; i++) {
            ComponentSelectionCause cause = ComponentSelectionCause.values()[decoder.readByte()];
            String desc = readDescriptionText(decoder);
            builder.add(new DefaultComponentSelectionDescription(cause, desc));
        }
        return builder.build();
    }

    private String readDescriptionText(Decoder decoder) throws IOException {
        boolean alreadyKnown = decoder.readBoolean();
        if (alreadyKnown) {
            return descriptions.inverse().get(decoder.readSmallInt());
        } else {
            String description = decoder.readString();
            descriptions.put(description, descriptions.size());
            return description;
        }
    }

    public void write(Encoder encoder, ComponentSelectionReason value) throws IOException {
        prepareForOperation(OperationType.write);
        List<ComponentSelectionDescription> descriptions = value.getDescriptions();
        encoder.writeSmallInt(descriptions.size());
        for (ComponentSelectionDescription description : descriptions) {
            writeDescription(encoder, description);
        }
    }

    private void writeDescription(Encoder encoder, ComponentSelectionDescription description) throws IOException {
        encoder.writeByte((byte) description.getCause().ordinal());
        writeDescriptionText(encoder, description.getDescription());
    }

    private void writeDescriptionText(Encoder encoder, String description) throws IOException {
        Integer index = descriptions.get(description);
        encoder.writeBoolean(index != null); // already known custom reason
        if (index == null) {
            index = descriptions.size();
            descriptions.put(description, index);
            encoder.writeString(description);
        } else {
            encoder.writeSmallInt(index);
        }
    }

    /**
     * This serializer assumes that we are using it alternatively for writes, then reads, in cycles.
     * After each cycle completed, state has to be reset.
     *
     * @param operationType the current operation type
     */
    private void prepareForOperation(OperationType operationType) {
        if (operationType != lastOperationType) {
            descriptions.clear();
            lastOperationType = operationType;
        }
    }

    private enum OperationType {
        read,
        write
    }
}
