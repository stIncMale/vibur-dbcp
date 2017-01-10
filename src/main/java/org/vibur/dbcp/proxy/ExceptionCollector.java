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

package org.vibur.dbcp.proxy;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransactionRollbackException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This collector will receive notifications for all SQL exceptions thrown by the operations invoked on a JDBC
 * Connection object or any of its direct or indirect derivative objects (such as Statement, ResultSet,
 * or database Metadata objects).
 *
 * @author Simeon Malchev
 */
class ExceptionCollector {

    private volatile Queue<SQLException> exceptions = null;

    /**
     * This method will be called when an operation invoked on a JDBC object throws an SQLException.
     * It will accumulate a list of all non-transient SQL exceptions.
     *
     * @param exception the exception thrown
     */
    void addException(SQLException exception) {
        if (!(exception instanceof SQLTimeoutException) && !(exception instanceof SQLTransactionRollbackException))
            getOrInit().offer(exception); // SQLExceptions from the above two sub-types are not stored
    }

    private Queue<SQLException> getOrInit() {
        Queue<SQLException> ex = exceptions;
        if (ex == null) {
            synchronized (this) {
                ex = exceptions;
                if (ex == null)
                    exceptions = ex = new ConcurrentLinkedQueue<>();
            }
        }
        return ex;
    }

    /**
     * Returns a list of all SQL exceptions collected by {@link #addException}. This method will be
     * called when a pooled Connection is closed, in order to determine whether the underlying
     * (raw) JDBC Connection also needs to be closed.
     */
    List<SQLException> getExceptions() {
        Queue<SQLException> ex = exceptions;
        return ex == null ? Collections.<SQLException>emptyList() : new ArrayList<>(ex);
    }
}
