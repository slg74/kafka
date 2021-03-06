/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kafka.common.requests;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.protocol.Errors;
import org.apache.kafka.common.protocol.types.Struct;
import org.apache.kafka.common.utils.CollectionUtils;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeleteRecordsRequest extends AbstractRequest {

    public static final long HIGH_WATERMARK = -1L;

    // request level key names
    private static final String TOPICS_KEY_NAME = "topics";
    private static final String TIMEOUT_KEY_NAME = "timeout";

    // topic level key names
    private static final String TOPIC_KEY_NAME = "topic";
    private static final String PARTITIONS_KEY_NAME = "partitions";

    // partition level key names
    private static final String PARTITION_KEY_NAME = "partition";
    private static final String OFFSET_KEY_NAME = "offset";

    private final int timeout;
    private final Map<TopicPartition, Long> partitionOffsets;

    public static class Builder extends AbstractRequest.Builder<DeleteRecordsRequest> {
        private final int timeout;
        private final Map<TopicPartition, Long> partitionOffsets;

        public Builder(int timeout, Map<TopicPartition, Long> partitionOffsets) {
            super(ApiKeys.DELETE_RECORDS);
            this.timeout = timeout;
            this.partitionOffsets = partitionOffsets;
        }

        @Override
        public DeleteRecordsRequest build(short version) {
            return new DeleteRecordsRequest(timeout, partitionOffsets, version);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("(type=DeleteRecordsRequest")
                   .append(", timeout=").append(timeout)
                   .append(", partitionOffsets=(").append(partitionOffsets)
                   .append("))");
            return builder.toString();
        }
    }


    public DeleteRecordsRequest(Struct struct, short version) {
        super(version);
        partitionOffsets = new HashMap<>();
        for (Object topicStructObj : struct.getArray(TOPICS_KEY_NAME)) {
            Struct topicStruct = (Struct) topicStructObj;
            String topic = topicStruct.getString(TOPIC_KEY_NAME);
            for (Object partitionStructObj : topicStruct.getArray(PARTITIONS_KEY_NAME)) {
                Struct partitionStruct = (Struct) partitionStructObj;
                int partition = partitionStruct.getInt(PARTITION_KEY_NAME);
                long offset = partitionStruct.getLong(OFFSET_KEY_NAME);
                partitionOffsets.put(new TopicPartition(topic, partition), offset);
            }
        }
        timeout = struct.getInt(TIMEOUT_KEY_NAME);
    }

    public DeleteRecordsRequest(int timeout, Map<TopicPartition, Long> partitionOffsets, short version) {
        super(version);
        this.timeout = timeout;
        this.partitionOffsets = partitionOffsets;
    }
    @Override
    protected Struct toStruct() {
        Struct struct = new Struct(ApiKeys.DELETE_RECORDS.requestSchema(version()));
        Map<String, Map<Integer, Long>> offsetsByTopic = CollectionUtils.groupDataByTopic(partitionOffsets);
        struct.set(TIMEOUT_KEY_NAME, timeout);
        List<Struct> topicStructArray = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, Long>> offsetsByTopicEntry : offsetsByTopic.entrySet()) {
            Struct topicStruct = struct.instance(TOPICS_KEY_NAME);
            topicStruct.set(TOPIC_KEY_NAME, offsetsByTopicEntry.getKey());
            List<Struct> partitionStructArray = new ArrayList<>();
            for (Map.Entry<Integer, Long> offsetsByPartitionEntry : offsetsByTopicEntry.getValue().entrySet()) {
                Struct partitionStruct = topicStruct.instance(PARTITIONS_KEY_NAME);
                partitionStruct.set(PARTITION_KEY_NAME, offsetsByPartitionEntry.getKey());
                partitionStruct.set(OFFSET_KEY_NAME, offsetsByPartitionEntry.getValue());
                partitionStructArray.add(partitionStruct);
            }
            topicStruct.set(PARTITIONS_KEY_NAME, partitionStructArray.toArray());
            topicStructArray.add(topicStruct);
        }
        struct.set(TOPICS_KEY_NAME, topicStructArray.toArray());
        return struct;
    }

    @Override
    public AbstractResponse getErrorResponse(int throttleTimeMs, Throwable e) {
        Map<TopicPartition, DeleteRecordsResponse.PartitionResponse> responseMap = new HashMap<>();

        for (Map.Entry<TopicPartition, Long> entry : partitionOffsets.entrySet()) {
            responseMap.put(entry.getKey(), new DeleteRecordsResponse.PartitionResponse(DeleteRecordsResponse.INVALID_LOW_WATERMARK, Errors.forException(e)));
        }

        short versionId = version();
        switch (versionId) {
            case 0:
                return new DeleteRecordsResponse(throttleTimeMs, responseMap);
            default:
                throw new IllegalArgumentException(String.format("Version %d is not valid. Valid versions for %s are 0 to %d",
                    versionId, this.getClass().getSimpleName(), ApiKeys.DELETE_RECORDS.latestVersion()));
        }
    }

    public int timeout() {
        return timeout;
    }

    public Map<TopicPartition, Long> partitionOffsets() {
        return partitionOffsets;
    }

    public static DeleteRecordsRequest parse(ByteBuffer buffer, short version) {
        return new DeleteRecordsRequest(ApiKeys.DELETE_RECORDS.parseRequest(version, buffer), version);
    }
}
