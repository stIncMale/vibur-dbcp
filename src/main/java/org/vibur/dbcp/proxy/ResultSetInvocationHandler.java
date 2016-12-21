/**
 * Copyright 2015 Simeon Malchev
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

import org.vibur.dbcp.ViburConfig;
import org.vibur.dbcp.pool.Hook;

import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.vibur.dbcp.util.QueryUtils.getSqlQuery;

/**
 * @author Simeon Malchev
 */
class ResultSetInvocationHandler extends ChildObjectInvocationHandler<Statement, ResultSet> {

    private final Object[] executeMethodArgs;
    private final List<Object[]> queryParams;
    private final InvocationHooksHolder invocationHooks;

    private final AtomicLong resultSetSize = new AtomicLong(0);

    ResultSetInvocationHandler(ResultSet rawResultSet, Statement statementProxy,
                               Object[] executeMethodArgs, List<Object[]> queryParams,
                               ViburConfig config, ExceptionCollector exceptionCollector) {
        super(rawResultSet, statementProxy, "getStatement", config, exceptionCollector);
        this.executeMethodArgs = executeMethodArgs;
        this.queryParams = queryParams;
        this.invocationHooks = config.getInvocationHooks();
    }

    @Override
    Object unrestrictedInvoke(ResultSet proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        if (methodName == "close")
            return processClose(method, args);
        if (methodName == "isClosed")
            return isClosed();

        return super.unrestrictedInvoke(proxy, method, args);
    }

    @Override
    Object restrictedInvoke(ResultSet proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        if (methodName == "next")
            return processNext(method, args);

        return super.restrictedInvoke(proxy, method, args);
    }

    private Object processNext(Method method, Object[] args) throws Throwable {
        resultSetSize.incrementAndGet();
        return targetInvoke(method, args);
    }

    private Object processClose(Method method, Object[] args) throws Throwable {
        if (!close())
            return null;

        long size = resultSetSize.get() - 1;
        for (Hook.ResultSetRetrieval hook : invocationHooks.onResultSetRetrieval())
            hook.on(getSqlQuery(getParentProxy(), executeMethodArgs), queryParams, size);

        return targetInvoke(method, args);
    }
}
