package com.tesora.dve.variable.status;

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

import com.tesora.dve.sql.parser.SqlStatistics;
import com.tesora.dve.sql.statement.StatementType;

public class SqlStatusVariableHandler extends StatusVariableHandler {

	private final StatementType counter;
	
	public SqlStatusVariableHandler(String variableName, StatementType counter) {
		super(variableName);
		this.counter = counter;
	}
	
	@Override
	protected String getValueInternal() throws Throwable {
		return Long.toString(SqlStatistics.getCurrentValue(counter));		
	}
	
	@Override
	protected void resetValueInternal() throws Throwable {
		SqlStatistics.resetCurrentValue(counter);		
	}	
}
