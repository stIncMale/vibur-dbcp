/**
 * Copyright 2013 Simeon Malchev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vibur.dbcp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.runners.MockitoJUnitRunner;
import org.vibur.dbcp.cache.MethodDef;
import org.vibur.dbcp.cache.ReturnVal;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;
import static org.vibur.dbcp.cache.ReturnVal.AVAILABLE;

/**
 * Simple JDBC integration test.
 *
 * @author Simeon Malchev
 */
@RunWith(MockitoJUnitRunner.class)
public class ViburDBCPDataSourceTest extends AbstractDataSourceTest {

    @Captor
    private ArgumentCaptor<MethodDef> key1, key2;

    @Test
    public void testSimpleSelectStatementNoStatementsCache() throws SQLException, IOException {
        DataSource ds = createDataSourceNoStatementsCache();
        Connection connection = null;
        try {
            connection = ds.getConnection();
            executeAndVerifySimpleSelectStatement(connection);
        } finally {
            if (connection != null) connection.close();
        }
        assertTrue(connection.isClosed());
    }

    @Test
    public void testSimpleSelectStatementFromExternalDataSource() throws SQLException, IOException {
        DataSource ds = createDataSourceFromExternalDataSource();
        Connection connection = null;
        try {
            connection = ds.getConnection();
            executeAndVerifySimpleSelectStatement(connection);
        } finally {
            if (connection != null) connection.close();
        }
        assertTrue(connection.isClosed());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleSelectStatementWithStatementsCache() throws SQLException, IOException {
        ViburDBCPDataSource ds = createDataSourceWithStatementsCache();
        Connection connection = null;
        try {
            ConcurrentMap<MethodDef<Connection>, ReturnVal<Statement>> mockedStatementCache =
                mock(ConcurrentMap.class, delegatesTo(ds.getStatementCache()));
            ds.setStatementCache(mockedStatementCache);

            connection = ds.getConnection();
            executeAndVerifySimpleSelectStatement(connection);
            executeAndVerifySimpleSelectStatement(connection);

            verifyZeroInteractions(mockedStatementCache);
        } finally {
            if (connection != null) connection.close();
        }
        assertTrue(connection.isClosed());
    }

    @Test
    public void testSimplePreparedSelectStatementNoStatementsCache() throws SQLException, IOException {
        DataSource ds = createDataSourceNoStatementsCache();
        Connection connection = null;
        try {
            connection = ds.getConnection();
            executeAndVerifySimplePreparedSelectStatement(connection);
        } finally {
            if (connection != null) connection.close();
        }
        assertTrue(connection.isClosed());
    }

    @Test
    public void testSimplePreparedSelectStatementFromExternalDataSource() throws SQLException, IOException {
        DataSource ds = createDataSourceFromExternalDataSource();
        Connection connection = null;
        try {
            connection = ds.getConnection();
            executeAndVerifySimplePreparedSelectStatement(connection);
        } finally {
            if (connection != null) connection.close();
        }
        assertTrue(connection.isClosed());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSimplePreparedSelectStatementWithStatementsCache() throws SQLException, IOException {
        ViburDBCPDataSource ds = createDataSourceWithStatementsCache();
        Connection connection = null;
        try {
            ConcurrentMap<MethodDef<Connection>, ReturnVal<Statement>> mockedStatementCache =
                mock(ConcurrentMap.class, delegatesTo(ds.getStatementCache()));
            ds.setStatementCache(mockedStatementCache);

            connection = ds.getConnection();
            executeAndVerifySimplePreparedSelectStatement(connection);
            executeAndVerifySimplePreparedSelectStatement(connection);

            InOrder inOrder = inOrder(mockedStatementCache);
            inOrder.verify(mockedStatementCache).get(key1.capture());
            inOrder.verify(mockedStatementCache).putIfAbsent(same(key1.getValue()), any(ReturnVal.class));
            inOrder.verify(mockedStatementCache).get(key2.capture());

            assertEquals(key1.getValue(), key2.getValue());
            assertEquals("prepareStatement", key1.getValue().getMethod().getName());
            ReturnVal<Statement> returnVal = mockedStatementCache.get(key1.getValue());
            assertTrue(returnVal.state().get() == AVAILABLE);
        } finally {
            if (connection != null) connection.close();
        }
        assertTrue(connection.isClosed());
    }

    private void executeAndVerifySimpleSelectStatement(Connection connection) throws SQLException {
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            statement = connection.createStatement();
            resultSet = statement.executeQuery("select * from actor where first_name = 'CHRISTIAN'");

            Set<String> expectedLastNames = new HashSet<String>(Arrays.asList("GABLE", "AKROYD", "NEESON"));
            int count = 0;
            while (resultSet.next()) {
                ++count;
                String lastName = resultSet.getString("last_name");
                assertTrue(expectedLastNames.remove(lastName));
            }
            assertEquals(3, count);
        } finally {
            if (resultSet != null) resultSet.close();
            if (statement != null) statement.close();
        }
        assertTrue(statement.isClosed());
    }

    private void executeAndVerifySimplePreparedSelectStatement(Connection connection) throws SQLException {
        PreparedStatement pStatement = null;
        ResultSet resultSet = null;
        try {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            pStatement = connection.prepareStatement("select * from actor where first_name = ?");
            pStatement.setString(1, "CHRISTIAN");
            resultSet = pStatement.executeQuery();

            Set<String> lastNames = new HashSet<String>(Arrays.asList("GABLE", "AKROYD", "NEESON"));
            int count = 0;
            while (resultSet.next()) {
                ++count;
                String lastName = resultSet.getString("last_name");
                assertTrue(lastNames.remove(lastName));
            }
            assertEquals(3, count);
        } finally {
            if (resultSet != null) resultSet.close();
            if (pStatement != null) pStatement.close();
        }
        assertTrue(pStatement.isClosed());
    }
}
