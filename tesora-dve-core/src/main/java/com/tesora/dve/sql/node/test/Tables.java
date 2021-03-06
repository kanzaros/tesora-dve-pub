package com.tesora.dve.sql.node.test;

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


import com.tesora.dve.sql.expression.TableKey;
import com.tesora.dve.sql.node.DerivedAttribute;
import com.tesora.dve.sql.node.LanguageNode;
import com.tesora.dve.sql.schema.SchemaContext;
import com.tesora.dve.sql.statement.dml.DMLStatement;
import com.tesora.dve.sql.util.ListSet;

class Tables extends DerivedAttribute<ListSet<TableKey>> {

	private final boolean crossNested;
	
	public Tables(boolean cn) {
		super();
		crossNested = cn;
	}
	
	@Override
	public boolean isApplicableSubject(LanguageNode ln) {
		return (ln instanceof DMLStatement);
	}

	@Override
	public ListSet<TableKey> computeValue(SchemaContext sc, LanguageNode ln) {
		DMLStatement dmls = (DMLStatement) ln;
		if (crossNested)
			return dmls.getDerivedInfo().getAllTableKeys();
		return dmls.getDerivedInfo().getLocalTableKeys();
	}

}
