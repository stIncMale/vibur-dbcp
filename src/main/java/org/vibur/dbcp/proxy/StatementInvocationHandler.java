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
import org.vibur.dbcp.ViburConfig;
import org.vibur.dbcp.cache.StatementCache;
import org.vibur.dbcp.cache.StatementHolder;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

import static org.vibur.dbcp.proxy.Proxy.newProxyResultSet;
import static org.vibur.dbcp.util.QueryUtils.formatSql;
import static org.vibur.dbcp.util.QueryUtils.getSqlQuery;
import static org.vibur.dbcp.util.ViburUtils.getPoolName;

/**
 * @author Simeon Malchev
 */
class StatementInvocationHandler extends ChildObjectInvocationHandler<Connection, Statement> {

    private static final Logger logger = LoggerFactory.getLogger(StatementInvocationHandler.class);

    private final StatementHolder statement;
    private final StatementCache statementCache;
    private final ViburConfig config;

    private final boolean logSlowQuery;
    private final boolean logQueryParams;
    private final List<Object[]> queryParams;

    StatementInvocationHandler(StatementHolder statement, StatementCache statementCache, Connection connProxy,
                               ViburConfig config, ExceptionCollector exceptionCollector) {
        super(statement.value(), connProxy, "getConnection", config, exceptionCollector);
        this.config = config;
        this.statement = statement;
        this.statementCache = statementCache;
        this.logSlowQuery = config.getLogQueryExecutionLongerThanMs() >= 0;
        boolean logLargeResult = config.getLogLargeResultSet() >= 0;
        this.logQueryParams = config.isIncludeQueryParameters() && (logSlowQuery || logLargeResult);
        this.queryParams = logQueryParams ? new LinkedList<Object[]>() : null;
    }

    @Override
    Object unrestrictedInvocations(Statement proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        if (methodName == "close")
            return processClose(method, args);
        if (methodName == "isClosed")
            return isClosed();

        return super.unrestrictedInvocations(proxy, method, args);
    }

    @Override
    Object restrictedInvocations(Statement proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        if (methodName.startsWith("set")) // this intercepts all "set..." JDBC Prepared/Callable Statement methods
            return processSet(method, args);
        if (methodName.startsWith("execute")) // this intercepts all "execute..." JDBC Statement methods
            return processExecute(proxy, method, args);

        // Methods which results have to be proxied so that when getStatement() is called
        // on their results the return value to be the current JDBC Statement proxy.
        if (methodName == "getResultSet" || methodName == "getGeneratedKeys") // *2
            return newProxiedResultSet(proxy, method, args);

        if (methodName == "cancel")
            return processCancel(method, args);

        return super.restrictedInvocations(proxy, method, args);
    }

    @Override
    void logTargetInvokeFailure(Method method, Object[] args, Throwable t) {
        if (method.getName().startsWith("execute")) {
            if (logger.isDebugEnabled())
                logger.debug("SQL query execution from pool {}:\n{}\n-- threw:",
                        getPoolName(config), formatSql(getSqlQuery(getTarget(), args), queryParams), t);
        }
        else
            super.logTargetInvokeFailure(method, args, t);
    }

    private Object processClose(Method method, Object[] args) throws Throwable {
        if (!close())
            return null;
        if (statementCache == null)
            return targetInvoke(method, args);

        statementCache.restore(statement, config.isClearSQLWarnings());
        return null; // calls to close() are not passed when StatementCache is used
    }

    private Object processCancel(Method method, Object[] args) throws Throwable {
        if (statementCache != null)
            statementCache.remove(getTarget()); // because cancelled Statements are not longer valid
        return targetInvoke(method, args);
    }

    private Object processSet(Method method, Object[] args) throws Throwable {
        if (logQueryParams && args != null && args.length >= 2)
            addQueryParams(method, args);
        return targetInvoke(method, args); // the real "set..." call
    }

    /**
     * Mainly exists to provide Statement.execute... methods timing logging.
     */
    private Object processExecute(Statement proxy, Method method, Object[] args) throws Throwable {
        long startTime = logSlowQuery ? System.currentTimeMillis() : 0L;

        try {
            // executeQuery result has to be proxied so that when getStatement() is called
            // on its result the return value to be the current JDBC Statement proxy.
            if (method.getName() == "executeQuery") // *1
                return newProxiedResultSet(proxy, method, args);

            return targetInvoke(method, args); // the real "execute..." call
        } finally {
            if (logSlowQuery)
                logQuery(proxy, args, startTime);
        }
    }

    private ResultSet newProxiedResultSet(Statement proxy, Method method, Object[] args) throws Throwable {
        ResultSet rawResultSet = (ResultSet) targetInvoke(method, args);
        return newProxyResultSet(rawResultSet, proxy, args, queryParams, config, getExceptionCollector());
    }

    private void addQueryParams(Method method, Object[] args) {
        Object[] params = new Object[args.length + 1];
        params[0] = method.getName();
        System.arraycopy(args, 0, params, 1, args.length);
        queryParams.add(params);
    }

    private void logQuery(Statement proxy, Object[] args, long startTime) {
        long timeTaken = System.currentTimeMillis() - startTime;
        if (timeTaken >= config.getLogQueryExecutionLongerThanMs())
            config.getViburLogger().logQuery(
                    getPoolName(config), getSqlQuery(proxy, args), queryParams, timeTaken,
                    config.isLogStackTraceForLongQueryExecution() ? new Throwable().getStackTrace() : null);
    }
}
