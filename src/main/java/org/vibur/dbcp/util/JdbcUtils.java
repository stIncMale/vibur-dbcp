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

package org.vibur.dbcp.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * @author Simeon Malchev
 */
public final class JdbcUtils {

    private static final Logger logger = LoggerFactory.getLogger(JdbcUtils.class);

    private JdbcUtils() {}

    public static void closeStatement(Statement statement) {
        try {
            statement.close();
        } catch (SQLException e) {
            logger.debug("Couldn't close " + statement, e);
        } catch (RuntimeException e) {
            logger.error("Unexpected exception thrown by the JDBC driver for " + statement, e);
        }
    }

    public static void clearWarnings(Statement statement) {
        try {
            statement.clearWarnings();
        } catch (SQLException e) {
            logger.debug("Couldn't clearWarnings on " + statement, e);
        } catch (RuntimeException e) {
            logger.error("Unexpected exception thrown by the JDBC driver for " + statement, e);
        }
    }

    public static void closeConnection(Connection connection) {
        try {
            connection.close();
        } catch (SQLException e) {
            logger.debug("Couldn't close " + connection, e);
        } catch (RuntimeException e) {
            logger.error("Unexpected exception thrown by the JDBC driver for " + connection, e);
        }
    }
}
