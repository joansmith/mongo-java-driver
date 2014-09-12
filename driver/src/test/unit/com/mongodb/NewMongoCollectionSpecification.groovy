/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb

import com.mongodb.client.MongoCollectionOptions
import com.mongodb.client.model.FindModel
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.codecs.DocumentCodecProvider
import com.mongodb.operation.InsertOperation
import com.mongodb.operation.QueryOperation
import com.mongodb.operation.ReplaceOperation
import com.mongodb.protocol.AcknowledgedWriteResult
import org.bson.BsonBoolean
import org.bson.BsonDocument
import org.bson.codecs.configuration.RootCodecRegistry
import org.mongodb.Document
import spock.lang.Specification

import static com.mongodb.ReadPreference.secondary

class NewMongoCollectionSpecification extends Specification {

    def namespace = new MongoNamespace('db', 'coll')
    def collection;
    def options = MongoCollectionOptions.builder().writeConcern(WriteConcern.JOURNALED)
                                        .readPreference(secondary())
                                        .codecRegistry(new RootCodecRegistry([new DocumentCodecProvider()]))
                                        .build()

    def 'should insert a document'() {
        given:
        def executor = new TestOperationExecutor(new AcknowledgedWriteResult(1, false, null))
        collection = new NewMongoCollectionImpl<Document>(namespace, Document, options, executor)

        when:
        def result = collection.insertOne(new Document('_id', 1))

        then:
        executor.getWriteOperation() as InsertOperation
        !result.insertedId
        result.insertedCount == 1
    }

    def 'should replace'() {
        given:
        def executor = new TestOperationExecutor(new AcknowledgedWriteResult(1, false, null))
        collection = new NewMongoCollectionImpl<Document>(namespace, Document, options, executor)

        when:
        def result = collection.replaceOne(new ReplaceOneModel<>(new Document('_id', 1),
                                                                 new Document('_id', 1).append('color', 'blue')))

        then:
        executor.getWriteOperation() as ReplaceOperation
        result.modifiedCount == 0
        result.matchedCount == 1
        !result.upsertedId
    }

    def 'should find'() {
        given:
        def document = new Document('_id', 1)
        def cursor = Stub(MongoCursor)
        cursor.hasNext() >>> [true, false]
        cursor.next() >> document
        def executor = new TestOperationExecutor(cursor)
        collection = new NewMongoCollectionImpl<Document>(namespace, Document, options, executor)

        def model = new FindModel<>().criteria(new Document('cold', true))

        when:
        def result = collection.find(model).into([])

        then:
        def operation = executor.getReadOperation() as QueryOperation
        operation.criteria == new BsonDocument('cold', BsonBoolean.TRUE)
        executor.readPreference == secondary()
        result == [document]
    }
}