/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package grakn.client.rpc;

import grakn.client.GraknClient;
import grakn.client.concept.Concept;
import grakn.client.concept.ConceptId;
import grakn.client.concept.ValueType;
import grakn.client.concept.Label;
import grakn.client.exception.GraknClientException;
import grakn.protocol.keyspace.KeyspaceProto;
import grakn.protocol.session.ConceptProto;
import grakn.protocol.session.SessionProto;
import graql.lang.pattern.Pattern;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static grabl.tracing.client.GrablTracingThreadStatic.ThreadTrace;
import static grabl.tracing.client.GrablTracingThreadStatic.currentThreadTrace;
import static grabl.tracing.client.GrablTracingThreadStatic.isTracingEnabled;
import static java.util.stream.Collectors.toList;

/**
 * A utility class to build RPC Requests from a provided set of Grakn concepts.
 */
public class RequestBuilder {

    public static class Session {

        public static SessionProto.Session.Open.Req open(String keyspace) {
            return SessionProto.Session.Open.Req.newBuilder().putAllMetadata(getTracingData()).setKeyspace(keyspace).build();
        }

        public static SessionProto.Session.Close.Req close(String sessionId) {
            return SessionProto.Session.Close.Req.newBuilder().putAllMetadata(getTracingData()).setSessionId(sessionId).build();
        }
    }

    /**
     * An RPC Request Builder class for Transaction Service
     */
    public static class Transaction {

        public static SessionProto.Transaction.Req open(String sessionId, GraknClient.Transaction.Type txType) {
            SessionProto.Transaction.Open.Req openRequest = SessionProto.Transaction.Open.Req.newBuilder()
                    .setSessionId(sessionId)
                    .setType(SessionProto.Transaction.Type.valueOf(txType.id()))
                    .build();

            return SessionProto.Transaction.Req.newBuilder().putAllMetadata(getTracingData()).setOpenReq(openRequest).build();
        }

        public static SessionProto.Transaction.Req commit() {
            return SessionProto.Transaction.Req.newBuilder()
                    .putAllMetadata(getTracingData())
                    .setCommitReq(SessionProto.Transaction.Commit.Req.getDefaultInstance())
                    .build();
        }

        public static SessionProto.Transaction.Iter.Req query(String queryString, GraknClient.Transaction.QueryOptions options) {
            SessionProto.Transaction.Query.Options.Builder builder = SessionProto.Transaction.Query.Options.newBuilder();
            options
                    .whenSet(GraknClient.Transaction.BooleanOption.INFER, builder::setInferFlag)
                    .whenSet(GraknClient.Transaction.BooleanOption.EXPLAIN, builder::setExplainFlag);

            SessionProto.Transaction.Iter.Req.Builder req = SessionProto.Transaction.Iter.Req.newBuilder()
                    .setQueryIterReq(SessionProto.Transaction.Query.Iter.Req.newBuilder()
                            .setQuery(queryString)
                            .setOptions(builder));

            options.whenSet(GraknClient.Transaction.BatchOption.BATCH_SIZE, req::setOptions);

            return req.build();
        }

        public static SessionProto.Transaction.Req getSchemaConcept(Label label) {
            return SessionProto.Transaction.Req.newBuilder()
                    .putAllMetadata(getTracingData())
                    .setGetSchemaConceptReq(SessionProto.Transaction.GetSchemaConcept.Req.newBuilder().setLabel(label.getValue()))
                    .build();
        }

        public static SessionProto.Transaction.Req getConcept(ConceptId id) {
            return SessionProto.Transaction.Req.newBuilder()
                    .putAllMetadata(getTracingData())
                    .setGetConceptReq(SessionProto.Transaction.GetConcept.Req.newBuilder().setId(id.getValue()))
                    .build();
        }


        public static SessionProto.Transaction.Iter.Req getAttributes(Object value) {
            return SessionProto.Transaction.Iter.Req.newBuilder()
                            .setGetAttributesIterReq(SessionProto.Transaction.GetAttributes.Iter.Req.newBuilder()
                                                 .setValue(ConceptMessage.attributeValue(value))).build();
        }

        public static SessionProto.Transaction.Req putEntityType(Label label) {
            return SessionProto.Transaction.Req.newBuilder()
                    .putAllMetadata(getTracingData())
                    .setPutEntityTypeReq(SessionProto.Transaction.PutEntityType.Req.newBuilder().setLabel(label.getValue()))
                    .build();
        }

        public static SessionProto.Transaction.Req putAttributeType(Label label, ValueType<?> valueType) {
            SessionProto.Transaction.PutAttributeType.Req request = SessionProto.Transaction.PutAttributeType.Req.newBuilder()
                    .setLabel(label.getValue())
                    .setValueType(ConceptMessage.setValueType(valueType))
                    .build();

            return SessionProto.Transaction.Req.newBuilder().putAllMetadata(getTracingData()).setPutAttributeTypeReq(request).build();
        }

        public static SessionProto.Transaction.Req putRelationType(Label label) {
            SessionProto.Transaction.PutRelationType.Req request = SessionProto.Transaction.PutRelationType.Req.newBuilder()
                    .setLabel(label.getValue())
                    .build();
            return SessionProto.Transaction.Req.newBuilder().putAllMetadata(getTracingData()).setPutRelationTypeReq(request).build();
        }

        public static SessionProto.Transaction.Req putRole(Label label) {
            SessionProto.Transaction.PutRole.Req request = SessionProto.Transaction.PutRole.Req.newBuilder()
                    .setLabel(label.getValue())
                    .build();
            return SessionProto.Transaction.Req.newBuilder().putAllMetadata(getTracingData()).setPutRoleReq(request).build();
        }

        public static SessionProto.Transaction.Req putRule(Label label, Pattern when, Pattern then) {
            SessionProto.Transaction.PutRule.Req request = SessionProto.Transaction.PutRule.Req.newBuilder()
                    .setLabel(label.getValue())
                    .setWhen(when.toString())
                    .setThen(then.toString())
                    .build();
            return SessionProto.Transaction.Req.newBuilder().putAllMetadata(getTracingData()).setPutRuleReq(request).build();
        }
    }

    /**
     * An RPC Request Builder class for Concept messages
     */
    public static class ConceptMessage {

        public static ConceptProto.Concept from(Concept<?> concept) {
            return ConceptProto.Concept.newBuilder()
                    .setId(concept.id().getValue())
                    .setBaseType(getBaseType(concept))
                    .build();
        }

        private static ConceptProto.Concept.BASE_TYPE getBaseType(Concept<?> concept) {
            if (concept.isEntityType()) {
                return ConceptProto.Concept.BASE_TYPE.ENTITY_TYPE;
            } else if (concept.isRelationType()) {
                return ConceptProto.Concept.BASE_TYPE.RELATION_TYPE;
            } else if (concept.isAttributeType()) {
                return ConceptProto.Concept.BASE_TYPE.ATTRIBUTE_TYPE;
            } else if (concept.isEntity()) {
                return ConceptProto.Concept.BASE_TYPE.ENTITY;
            } else if (concept.isRelation()) {
                return ConceptProto.Concept.BASE_TYPE.RELATION;
            } else if (concept.isAttribute()) {
                return ConceptProto.Concept.BASE_TYPE.ATTRIBUTE;
            } else if (concept.isRole()) {
                return ConceptProto.Concept.BASE_TYPE.ROLE;
            } else if (concept.isRule()) {
                return ConceptProto.Concept.BASE_TYPE.RULE;
            } else if (concept.isType()) {
                return ConceptProto.Concept.BASE_TYPE.META_TYPE;
            } else {
                throw GraknClientException.unreachableStatement("Unrecognised concept " + concept);
            }
        }

        public static Collection<ConceptProto.Concept> concepts(Collection<Concept<?>> concepts) {
            return concepts.stream().map(ConceptMessage::from).collect(toList());
        }

        public static ConceptProto.ValueObject attributeValue(Object value) {
            // TODO: this conversion method should use Serialiser class, once it's moved to grakn.common

            ConceptProto.ValueObject.Builder builder = ConceptProto.ValueObject.newBuilder();
            if (value instanceof String) {
                builder.setString((String) value);
            } else if (value instanceof Boolean) {
                builder.setBoolean((boolean) value);
            } else if (value instanceof Integer) {
                builder.setInteger((int) value);
            } else if (value instanceof Long) {
                builder.setLong((long) value);
            } else if (value instanceof Float) {
                builder.setFloat((float) value);
            } else if (value instanceof Double) {
                builder.setDouble((double) value);
            } else if (value instanceof LocalDateTime) {
                builder.setDatetime(((LocalDateTime) value).atZone(ZoneId.of("Z")).toInstant().toEpochMilli());
            } else {
                throw GraknClientException.unreachableStatement("Unrecognised " + value);
            }

            return builder.build();
        }

        @SuppressWarnings("unchecked")
        public static <D> ValueType<D> valueType(ConceptProto.AttributeType.VALUE_TYPE valueType) {
            switch (valueType) {
                case STRING:
                    return (ValueType<D>) ValueType.STRING;
                case BOOLEAN:
                    return (ValueType<D>) ValueType.BOOLEAN;
                case INTEGER:
                    return (ValueType<D>) ValueType.INTEGER;
                case LONG:
                    return (ValueType<D>) ValueType.LONG;
                case FLOAT:
                    return (ValueType<D>) ValueType.FLOAT;
                case DOUBLE:
                    return (ValueType<D>) ValueType.DOUBLE;
                case DATETIME:
                    return (ValueType<D>) ValueType.DATETIME;
                default:
                case UNRECOGNIZED:
                    throw new IllegalArgumentException("Unrecognised " + valueType);
            }
        }

        static ConceptProto.AttributeType.VALUE_TYPE setValueType(ValueType<?> valueType) {
            if (valueType.equals(ValueType.STRING)) {
                return ConceptProto.AttributeType.VALUE_TYPE.STRING;
            } else if (valueType.equals(ValueType.BOOLEAN)) {
                return ConceptProto.AttributeType.VALUE_TYPE.BOOLEAN;
            } else if (valueType.equals(ValueType.INTEGER)) {
                return ConceptProto.AttributeType.VALUE_TYPE.INTEGER;
            } else if (valueType.equals(ValueType.LONG)) {
                return ConceptProto.AttributeType.VALUE_TYPE.LONG;
            } else if (valueType.equals(ValueType.FLOAT)) {
                return ConceptProto.AttributeType.VALUE_TYPE.FLOAT;
            } else if (valueType.equals(ValueType.DOUBLE)) {
                return ConceptProto.AttributeType.VALUE_TYPE.DOUBLE;
            } else if (valueType.equals(ValueType.DATETIME)) {
                return ConceptProto.AttributeType.VALUE_TYPE.DATETIME;
            } else {
                throw GraknClientException.unreachableStatement("Unrecognised " + valueType);
            }
        }
    }

    /**
     * An RPC Request Builder class for Keyspace Service
     */
    public static class KeyspaceMessage {

        public static KeyspaceProto.Keyspace.Delete.Req delete(String name, String username, String password) {
            KeyspaceProto.Keyspace.Delete.Req.Builder builder = KeyspaceProto.Keyspace.Delete.Req.newBuilder();
            if (username != null) {
                builder.setUsername(username);
            }
            if (password != null) {
                builder.setPassword(password);
            }
            return builder.setName(name).build();
        }

        public static KeyspaceProto.Keyspace.Retrieve.Req retrieve(String username, String password) {
            KeyspaceProto.Keyspace.Retrieve.Req.Builder builder = KeyspaceProto.Keyspace.Retrieve.Req.newBuilder();
            if (username != null) {
                builder.setUsername(username);
            }
            if (password != null) {
                builder.setPassword(password);
            }
            return builder.build();
        }
    }

    public static Map<String, String> getTracingData() {
        if (isTracingEnabled()) {
            ThreadTrace threadTrace = currentThreadTrace();
            if (threadTrace == null) {
                return Collections.emptyMap();
            }

            if (threadTrace.getId() == null || threadTrace.getRootId() == null) {
                return Collections.emptyMap();
            }

            Map<String, String> metadata = new HashMap<>(2);
            metadata.put("traceParentId", threadTrace.getId().toString());
            metadata.put("traceRootId", threadTrace.getRootId().toString());
            return metadata;
        } else {
            return Collections.emptyMap();
        }
    }
}
