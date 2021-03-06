package com.tesora.dve.sql.schema.cache;

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

import java.util.List;

import com.tesora.dve.db.GenericSQLCommand;
import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.sql.expression.TableKey;
import com.tesora.dve.sql.parser.InvokeParser;
import com.tesora.dve.sql.schema.SchemaContext;
import com.tesora.dve.sql.transform.execution.ConnectionValuesMap;
import com.tesora.dve.sql.transform.execution.RootExecutionPlan;
import com.tesora.dve.sql.util.Pair;

public class CachedPreparedStatement implements CachedPlan {

	private final PlanCacheKey key;
	private final RootExecutionPlan thePlan;
	private final List<TableKey> tables;
	private final GenericSQLCommand logFormat;
	
	public CachedPreparedStatement(PlanCacheKey pck, RootExecutionPlan ep, List<TableKey> tabs, GenericSQLCommand logFormat) {
		this.key = pck;
		this.thePlan = ep;
		this.tables = tabs;
		this.logFormat = logFormat;
		thePlan.setOwningCache(this);
	}
	
	@Override
	public PlanCacheKey getKey() {
		return key;
	}

	@Override
	public boolean invalidate(SchemaCacheKey<?> unloaded) {
		for(TableKey tk : tables) {
			if (tk.getCacheKey().equals(unloaded))
				return true;
		}
		return false;
	}

	public Pair<RootExecutionPlan,ConnectionValuesMap> rebuildPlan(SchemaContext sc, List<Object> params) throws PEException {
		if (thePlan.getValueManager().getNumberOfParameters() != params.size()) {
			throw new PEException("Invalid prep. stmt. execute: require " + thePlan.getValueManager().getNumberOfParameters() + " parameters but have " + params.size());
		}
		ConnectionValuesMap cv = thePlan.resetForNewPStmtExec(sc, params);
		if (InvokeParser.isSqlLoggingEnabled()) {
			GenericSQLCommand resolved = logFormat.resolve(cv.getRootValues(), false, "  ");
			InvokeParser.logSql(sc, resolved.getDecoded());
		}
		return new Pair<RootExecutionPlan,ConnectionValuesMap>(thePlan,cv);
	}

	public int getNumberOfParameters() {
		return thePlan.getValueManager().getNumberOfParameters();
	}
	
}
