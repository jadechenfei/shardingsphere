/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
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
 * </p>
 */

package io.shardingsphere.core.executor;

import com.google.common.eventbus.EventBus;
import io.shardingsphere.core.constant.SQLType;
import io.shardingsphere.core.event.ShardingEventBusInstance;
import io.shardingsphere.core.executor.event.sql.SQLExecutionEvent;
import io.shardingsphere.core.executor.event.sql.SQLExecutionEventFactory;
import io.shardingsphere.core.executor.threadlocal.ExecutorDataMap;
import io.shardingsphere.core.executor.threadlocal.ExecutorExceptionHandler;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Statement execute callback interface.
 *
 * @author gaohongtao
 * @author zhangliang
 * 
 * @param <T> class type of return value
 */
@RequiredArgsConstructor
public final class ExecuteCallback<T> implements ShardingExecuteCallback<BaseStatementUnit, T> {
    
    @Getter
    private final SQLType sqlType;
    
    private final boolean isExceptionThrown;
    
    private final Map<String, Object> dataMap;
    
    private final JDBCExecuteCallback<T> jdbcCallback;
    
    private final EventBus shardingEventBus = ShardingEventBusInstance.getInstance();
    
    @Override
    public T execute(final BaseStatementUnit input) throws Exception {
        ExecutorExceptionHandler.setExceptionThrown(isExceptionThrown);
        ExecutorDataMap.setDataMap(dataMap);
        List<SQLExecutionEvent> events = new LinkedList<>();
        for (List<Object> each : input.getSqlExecutionUnit().getSqlUnit().getParameterSets()) {
            SQLExecutionEvent event = SQLExecutionEventFactory.createEvent(sqlType, input, each);
            events.add(event);
            shardingEventBus.post(event);
        }
        try {
            T result = jdbcCallback.execute(input);
            for (SQLExecutionEvent each : events) {
                each.setExecuteSuccess();
                shardingEventBus.post(each);
            }
            return result;
        } catch (final SQLException ex) {
            for (SQLExecutionEvent each : events) {
                each.setExecuteFailure(ex);
                shardingEventBus.post(each);
                ExecutorExceptionHandler.handleException(ex);
            }
            return null;
        }
    }
}
