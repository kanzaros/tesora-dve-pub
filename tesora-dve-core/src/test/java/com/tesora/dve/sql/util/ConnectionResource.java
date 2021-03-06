package com.tesora.dve.sql.util;

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

import java.util.LinkedHashSet;
import java.util.List;

import com.tesora.dve.sql.parser.ParserInvoker.LineInfo;

public abstract class ConnectionResource implements AutoCloseable {
	
	private LinkedHashSet<String> postconnectCmds = new LinkedHashSet<String>();

	protected ConnectionResource(ConnectionResource other) {
		if (other != null)
			postconnectCmds = new LinkedHashSet<String>(other.postconnectCmds);
	}
	
	public ResourceResponse execute(String stmt) throws Throwable {
		return execute(stmt, true);
	}
	
	public ResourceResponse execute(String stmt, boolean normal) throws Throwable {
		return execute(null, stmt);
	}

	protected void executePostConnectCmds() throws Throwable {
		for ( String cmd : postconnectCmds ) {
			this.execute(cmd);
		}
	}

	public void addPostConnectCmd(String cmd) {
		postconnectCmds.add(cmd);
	}

	public abstract ResourceResponse execute(LineInfo info, String stmt) throws Throwable;
	
	public ResourceResponse fetch(String stmt) throws Throwable {
		return fetch(null, stmt);
	}
	
	public abstract Object prepare(LineInfo info, String stmt) throws Throwable;
	public abstract ResourceResponse executePrepared(Object id, List<Object> parameters) throws Throwable;
	public abstract void destroyPrepared(Object id) throws Throwable;
	
	public abstract ResourceResponse fetch(LineInfo info, String stmt) throws Throwable;
	
	public abstract void connect() throws Throwable;
	public abstract void disconnect() throws Throwable;
	public abstract boolean isConnected() throws Throwable;
	
	public abstract String describe();
	
	// make a new connection with the same credentials.
	public abstract ConnectionResource getNewConnection() throws Throwable;
	
	public enum ExceptionClassification {
		DNE, DUPLICATE, OUT_OF_RANGE, SYNTAX, UNKNOWN, UNSUPPORTED_OPERATION, FILE_NOT_FOUND
	}
	
	public abstract ExceptionClassification classifyException(Throwable t);

	// true if the last executed stmt had warnings
	public boolean hasWarnings() {
		return false;
	}
	
	public ResourceResponse assertResults(String stmt, Object[] results) throws Throwable {
		ResourceResponse out = fetch(stmt);
		out.assertResults(stmt, results);
		return out;
	}
	
	public String printResults(String stmt, String header, boolean showTypes) throws Throwable {
		ResourceResponse out = fetch(stmt);
		return out.displayRows(header, showTypes);
	}
	
	public String printResults(String stmt) throws Throwable {
		return printResults(stmt, null, false);
	}
	
	@Override
	public void close() throws Exception {
		try {
			disconnect();
		} catch (Error | Exception e) {
			throw e;
		} catch (Throwable e) {
			throw new Exception(e);
		}
	}
	
}
