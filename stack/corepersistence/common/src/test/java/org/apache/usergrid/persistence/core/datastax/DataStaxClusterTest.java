/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.usergrid.persistence.core.datastax;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.google.inject.Inject;
import org.apache.usergrid.persistence.core.CassandraFig;
import org.apache.usergrid.persistence.core.guice.TestCommonModule;
import org.apache.usergrid.persistence.core.test.ITRunner;
import org.apache.usergrid.persistence.core.test.UseModules;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith( ITRunner.class )
@UseModules( TestCommonModule.class )
public class DataStaxClusterTest {


    @Inject
    DataStaxCluster dataStaxCluster;

    @Inject
    CassandraFig cassandraFig;


    @Test
    public void testConnectCloseCluster() {

        Cluster cluster = dataStaxCluster.getCluster();

        assertTrue(!cluster.isClosed());

        cluster.close();
        assertTrue(cluster.isClosed());

        // validate getCluster will re-init the cluster
        cluster = dataStaxCluster.getCluster();
        assertTrue(!cluster.isClosed());


    }

    @Test
    public void testGetClusterSession() {

        Session session = dataStaxCluster.getClusterSession();
        String clusterName = session.getCluster().getClusterName();
        String keyspaceName = session.getLoggedKeyspace();

        // cluster session is not logged to a keyspace
        assertNull(keyspaceName);
        assertNotNull(clusterName);
    }

    @Test
    public void testGetApplicationSession() {

        Session session = dataStaxCluster.getApplicationSession();
        String keyspaceName = session.getLoggedKeyspace();


        assertEquals(cassandraFig.getApplicationKeyspace(), keyspaceName);
    }

}