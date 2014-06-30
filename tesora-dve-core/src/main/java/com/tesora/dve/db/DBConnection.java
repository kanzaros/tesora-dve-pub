package com.tesora.dve.db;

/*
 * #%L
 * Tesora Inc.
 * Database Virtualization Engine
 * %%
 * Copyright (C) 2011 - 2014 Tesora Inc.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

import com.tesora.dve.common.catalog.StorageSite;
import com.tesora.dve.concurrent.CompletionHandle;
import com.tesora.dve.concurrent.CompletionTarget;
import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.exceptions.PESQLException;
import com.tesora.dve.server.messaging.SQLCommand;
import com.tesora.dve.worker.DevXid;
import io.netty.channel.EventLoopGroup;

public interface DBConnection extends CompletionTarget<Boolean> {
	
	interface Factory {
		DBConnection newInstance(EventLoopGroup eventLoop,StorageSite site);
	}
	
	interface Monitor {
		void onUpdate();
	}
	
	void connect(String url, String userid, String password, long clientCapabilities) throws PEException;
	void close();
	
	void execute(SQLCommand sql, DBResultConsumer consumer, CompletionHandle<Boolean> promise) throws PESQLException;
	void execute(SQLCommand sql, DBResultConsumer consumer) throws PESQLException;
	
	void start(DevXid xid) throws Exception;
	void end(DevXid xid) throws Exception;
	void prepare(DevXid xid) throws Exception;
	void commit(DevXid xid, boolean onePhase) throws Exception;
	void rollback(DevXid xid) throws Exception;
	void setCatalog(String databaseName) throws Exception;
	void cancel();

	boolean hasPendingUpdate();
	boolean hasActiveTransaction();
	int getConnectionId();
}
