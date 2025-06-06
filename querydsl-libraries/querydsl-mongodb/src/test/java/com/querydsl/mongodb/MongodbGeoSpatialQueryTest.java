/*
 * Copyright 2015, The Querydsl Team (http://www.querydsl.com/team)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.querydsl.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.querydsl.core.testutil.MongoDB;
import com.querydsl.mongodb.domain.GeoEntity;
import com.querydsl.mongodb.domain.QGeoEntity;
import com.querydsl.mongodb.morphia.MorphiaQuery;
import dev.morphia.Datastore;
import dev.morphia.Morphia;
import java.net.UnknownHostException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(MongoDB.class)
public class MongodbGeoSpatialQueryTest {

  private final String dbname = "geodb";
  private final MongoClient mongo;
  private final Datastore ds;
  private final QGeoEntity geoEntity = new QGeoEntity("geoEntity");

  public MongodbGeoSpatialQueryTest() throws UnknownHostException, MongoException {
    mongo = MongoClients.create();
    ds = Morphia.createDatastore(mongo, dbname);
    ds.getMapper().map(GeoEntity.class);
  }

  @Before
  public void before() {
    ds.getCollection(GeoEntity.class).deleteMany(new org.bson.Document());
    ds.ensureIndexes();
  }

  @Test
  public void near() {
    ds.save(new GeoEntity(10.0, 50.0));
    ds.save(new GeoEntity(20.0, 50.0));
    ds.save(new GeoEntity(30.0, 50.0));

    var entities = query().where(geoEntity.location.near(50.0, 50.0)).fetch();
    assertThat(entities.getFirst().getLocation()[0]).isCloseTo(30.0, within(0.1));
    assertThat(entities.get(1).getLocation()[0]).isCloseTo(20.0, within(0.1));
    assertThat(entities.get(2).getLocation()[0]).isCloseTo(10.0, within(0.1));
  }

  @Test
  public void near_sphere() {
    ds.save(new GeoEntity(10.0, 50.0));
    ds.save(new GeoEntity(20.0, 50.0));
    ds.save(new GeoEntity(30.0, 50.0));

    var entities =
        query().where(MongodbExpressions.nearSphere(geoEntity.location, 50.0, 50.0)).fetch();
    assertThat(entities.getFirst().getLocation()[0]).isCloseTo(30.0, within(0.1));
    assertThat(entities.get(1).getLocation()[0]).isCloseTo(20.0, within(0.1));
    assertThat(entities.get(2).getLocation()[0]).isCloseTo(10.0, within(0.1));
  }

  @Test
  public void geo_within_box() {
    ds.save(new GeoEntity(10.0, 50.0));
    ds.save(new GeoEntity(20.0, 50.0));
    ds.save(new GeoEntity(30.0, 50.0));

    var entities =
        query().where(MongodbExpressions.withinBox(geoEntity.location, 0, 0, 20, 50)).fetch();
    assertThat(entities).hasSize(2);
    assertThat(entities.getFirst().getLocation()[0]).isCloseTo(10.0, within(0.1));
    assertThat(entities.get(1).getLocation()[0]).isCloseTo(20.0, within(0.1));
  }

  @Test
  public void geo_intersects() {
    ds.save(new GeoEntity(10.0, 50.0));
    ds.save(new GeoEntity(20.0, 50.0));
    ds.save(new GeoEntity(30.0, 50.0));

    var entities =
        query().where(MongodbExpressions.geoIntersects(geoEntity.location, 20.0, 50.0)).fetch();
    assertThat(entities).hasSize(1);
    assertThat(entities.getFirst().getLocation()[0]).isCloseTo(20.0, within(0.1));
  }

  private MorphiaQuery<GeoEntity> query() {
    return new MorphiaQuery<>(ds, geoEntity);
  }
}
