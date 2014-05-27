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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vibur.dbcp.ViburDBCPConfig;
import org.vibur.dbcp.cache.MethodDef;
import org.vibur.dbcp.cache.ReturnVal;
import org.vibur.dbcp.pool.ConnHolder;
import org.vibur.dbcp.pool.PoolOperations;
import org.vibur.dbcp.proxy.listener.ExceptionListenerImpl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.Arrays;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.vibur.dbcp.cache.ReturnVal.AVAILABLE;
import static org.vibur.dbcp.cache.ReturnVal.IN_USE;

/**
 * @author Simeon Malchev
 */
public class ConnectionInvocationHandler extends AbstractInvocationHandler<Connection>
    implements InvocationHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionInvocationHandler.class);

    private final PoolOperations poolOperations;
    private final ConnHolder conn;

    private final ViburDBCPConfig config;
    private final ConcurrentMap<MethodDef<Connection>, ReturnVal<Statement>> statementCache;

    public ConnectionInvocationHandler(ConnHolder conn, ViburDBCPConfig config) {
        super(conn.value(), new ExceptionListenerImpl());
        this.poolOperations = config.getPoolOperations();
        this.conn = conn;
        this.config = config;
        this.statementCache = config.getStatementCache();
    }

    @SuppressWarnings("unchecked")
    protected Object doInvoke(Connection proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        boolean aborted = methodName == "abort";
        if (aborted || methodName == "close")
            return processCloseOrAbort(aborted, method, args);
        if (methodName == "isClosed")
            return isClosed();

        if (methodName == "isValid")
            return isClosed() ? false : targetInvoke(method, args);

        ensureNotClosed(); // all other Connection interface methods cannot work if the JDBC Connection is closed

        // Methods which results have to be proxied so that when getConnection() is called
        // on their results the return value to be the current JDBC Connection proxy.
        if (methodName == "createStatement") { // *3
            ReturnVal<Statement> statement = (ReturnVal<Statement>) uncachedGetStatement(method, args);
            return Proxy.newStatement(statement, null, proxy, config, getExceptionListener());
        }
        if (methodName == "prepareStatement") { // *6
            ReturnVal<PreparedStatement> statement =
                (ReturnVal<PreparedStatement>) cachedGetStatement(method, args);
            return Proxy.newPreparedStatement(statement, statementCache, proxy, config, getExceptionListener());
        }
        if (methodName == "prepareCall") { // *3
            ReturnVal<CallableStatement> statement =
                (ReturnVal<CallableStatement>) cachedGetStatement(method, args);
            return Proxy.newCallableStatement(statement, statementCache, proxy, config, getExceptionListener());
        }
        if (methodName == "getMetaData") { // *1
            DatabaseMetaData rawDatabaseMetaData = (DatabaseMetaData) targetInvoke(method, args);
            return Proxy.newDatabaseMetaData(rawDatabaseMetaData, proxy, getExceptionListener());
        }

        return super.doInvoke(proxy, method, args);
    }

    private ReturnVal<? extends Statement> cachedGetStatement(Method method, Object[] args) throws Throwable {
        if (statementCache != null) {
            Connection target = getTarget();
            MethodDef<Connection> key = new MethodDef<Connection>(target, method, args);
            ReturnVal<Statement> statement = statementCache.get(key);
            if (statement == null || statement.state().getAndSet(IN_USE) != AVAILABLE) {
                Statement rawStatement = (Statement) targetInvoke(method, args);
                if (statement == null) { // there was no entry for the key, so we'll try to put a new one
                    statement = new ReturnVal<Statement>(rawStatement, new AtomicInteger(IN_USE));
                    if (statementCache.putIfAbsent(key, statement) != null)
                        // because another thread succeeded to put the entry before us
                        statement = new ReturnVal<Statement>(rawStatement, null);
                }
                return statement;
            } else { // the statement is valid and was available
                if (logger.isTraceEnabled())
                    logger.trace("Using cached statement for connection {}, method {}, args {}",
                        target, method, Arrays.toString(args));
                return statement;
            }
        } else {
            return uncachedGetStatement(method, args);
        }
    }

    private ReturnVal<? extends Statement> uncachedGetStatement(Method method, Object[] args) throws Throwable {
        Statement rawStatement = (Statement) targetInvoke(method, args);
        return new ReturnVal<Statement>(rawStatement, null);
    }

    private Object processCloseOrAbort(boolean aborted, Method method, Object[] args) throws Throwable {
        if (aborted)
            targetInvoke(method, args); // executes the abort() call, which in turn may throw an exception
        if (!getAndSetClosed())
            poolOperations.restore(conn, aborted, getExceptionListener().getExceptions());
        return null;
    }
}
