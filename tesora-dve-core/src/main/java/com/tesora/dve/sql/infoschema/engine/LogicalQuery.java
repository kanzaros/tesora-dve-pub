package com.tesora.dve.sql.infoschema.engine;

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

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import com.tesora.dve.db.DBNative;
import com.tesora.dve.db.NativeType;
import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.resultset.ColumnSet;
import com.tesora.dve.server.global.HostService;
import com.tesora.dve.singleton.Singletons;
import com.tesora.dve.sql.statement.dml.SelectStatement;

public class LogicalQuery {

	protected ViewQuery orig;
	
	protected SelectStatement stmt;
	protected Map<String,Object> params;

	public LogicalQuery(ViewQuery orig, SelectStatement xlated, Map<String,Object> p) {
		this.orig = orig;
		this.stmt = xlated;
		params = (p == null ? new HashMap<String,Object>() : p);
		if (params.isEmpty())
			params = new HashMap<String,Object>();
	}
	
	public SelectStatement getQuery() { return stmt; }
	public Map<String,Object> getParams() { return params; }

	public ViewQuery getViewQuery() {
		return orig;
	}

	public boolean isDirect() {
		return true;
	}
	
	public static void buildNativeType(ColumnSet cs, String colName, String colAlias, Object in) throws PEException {
		if (in instanceof String) {
            NativeType nt = Singletons.require(DBNative.class).getTypeCatalog().findType(java.sql.Types.VARCHAR, true);
			cs.addColumn(colAlias, 255, nt.getTypeName(), java.sql.Types.VARCHAR);
		} else if (in instanceof BigInteger) {
            NativeType nt = Singletons.require(DBNative.class).getTypeCatalog().findType(java.sql.Types.BIGINT, true);
			cs.addColumn(colAlias, 32, nt.getTypeName(), java.sql.Types.BIGINT);
		} else {
			throw new PEException("Fill me in: type guess for result column type: " + in);
		}
	}

	
}
