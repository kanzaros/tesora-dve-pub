package com.tesora.dve.sql.infoschema.direct;

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


import java.util.ArrayList;
import java.util.List;

import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.persist.PersistedEntity;
import com.tesora.dve.sql.infoschema.InfoView;
import com.tesora.dve.sql.infoschema.InformationSchemaColumn;
import com.tesora.dve.sql.infoschema.InformationSchemaException;
import com.tesora.dve.sql.infoschema.InformationSchemaTable;
import com.tesora.dve.sql.infoschema.persist.CatalogColumnEntity;
import com.tesora.dve.sql.infoschema.persist.CatalogDatabaseEntity;
import com.tesora.dve.sql.infoschema.persist.CatalogSchema;
import com.tesora.dve.sql.infoschema.persist.CatalogTableEntity;
import com.tesora.dve.sql.schema.ConnectionValues;
import com.tesora.dve.sql.schema.Database;
import com.tesora.dve.sql.schema.Lookup;
import com.tesora.dve.sql.schema.Name;
import com.tesora.dve.sql.schema.PEColumn;
import com.tesora.dve.sql.schema.SchemaContext;
import com.tesora.dve.sql.schema.UnqualifiedName;
import com.tesora.dve.sql.util.Cast;
import com.tesora.dve.sql.util.Functional;
import com.tesora.dve.sql.util.ResizableArray;
import com.tesora.dve.sql.util.UnaryFunction;
import com.tesora.dve.variables.KnownVariables;

public class DirectInformationSchemaTable implements InformationSchemaTable {

	private final boolean privileged;
	private final boolean extension;
	private final InfoView view;

	private final Name name; // might be different than what is declared (i.e. case)
	private final Name pluralName;
	
	protected List<DirectInformationSchemaColumn> columns;
	protected Lookup<DirectInformationSchemaColumn> lookup;
	
	private final DirectInformationSchemaColumn identColumn;
	private final ResizableArray<DirectInformationSchemaColumn> orderByColumns;

	public static final String tenantVariable = "tenant";
	public static final String metadataExtensions = "mdex";
	public static final String sessid = "sessid";

	public DirectInformationSchemaTable(SchemaContext sc, InfoView view, List<PEColumn> cols,
			UnqualifiedName tableName,
			UnqualifiedName pluralTableName,
			boolean privileged, boolean extension,
			List<DirectColumnGenerator> columnGenerators) {
		this.privileged = privileged;
		this.extension = extension;
		this.view = view;
		// load up our columns
		columns = new ArrayList<DirectInformationSchemaColumn>();
		this.lookup = new Lookup<DirectInformationSchemaColumn>(columns, 
				new UnaryFunction<Name[], DirectInformationSchemaColumn>() {

					@Override
					public Name[] evaluate(DirectInformationSchemaColumn object) {
						return new Name[] { object.getName() };
					}
			
				},				
				false, view.isLookupCaseSensitive());
		for(int i = 0; i < cols.size(); i++) {
			DirectColumnGenerator dcg = columnGenerators.get(i);
			PEColumn pec = cols.get(i);
			addColumn(sc,new DirectInformationSchemaColumn(view,pec.getName().getUnqualified(),pec,
					dcg.isIdent(),dcg.getOrderByOffset() > -1,dcg.isExtension(),dcg.isPrivilege(),dcg.isFull()));
		}
		this.name = (view.isCapitalizeNames() ? tableName.getCapitalized().getUnqualified() : tableName);
		if (pluralTableName == null) this.pluralName = pluralTableName;
		else this.pluralName = (view.isCapitalizeNames() ? pluralTableName.getCapitalized().getUnqualified() : pluralTableName);
		orderByColumns = new ResizableArray<DirectInformationSchemaColumn>();
		DirectInformationSchemaColumn ic = null;
		for(DirectColumnGenerator dcg : columnGenerators) {
			if (dcg.getOrderByOffset() > -1) {
				orderByColumns.set(dcg.getOrderByOffset(),lookup.lookup(dcg.getName()));
			}
			if (ic == null && dcg.isIdent())
				ic = lookup.lookup(dcg.getName());
		}
		this.identColumn = ic;
	}

	@Override
	public InformationSchemaColumn addColumn(SchemaContext sc, InformationSchemaColumn c) {
		DirectInformationSchemaColumn cv = (DirectInformationSchemaColumn) c;
		cv.setPosition(columns.size());
		columns.add(cv);
		lookup.refreshBacking(columns);
		cv.setTable(this);
		return cv;
	}

	@Override
	public List<InformationSchemaColumn> getColumns(SchemaContext sc) {
		return Functional.apply(columns, new Cast<InformationSchemaColumn,DirectInformationSchemaColumn>());
	}

	@Override
	public DirectInformationSchemaColumn lookup(SchemaContext sc, Name n) {
		return lookup.lookup(n);
	}

	public DirectInformationSchemaColumn lookup(String n) {
		return lookup.lookup(new UnqualifiedName(n));
	}
	
	@Override
	public Name getName(SchemaContext sc,ConnectionValues cv) {
		return name;
	}

	@Override
	public Name getPluralName() {
		return pluralName;
	}
	
	@Override
	public boolean isInfoSchema() {
		return true;
	}

	@Override
	public boolean isTempTable() {
		return false;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Database<?> getDatabase(SchemaContext sc) {
		return ((Database<?>)sc.getSource().find(sc, view.getCacheKey()));
	}

	
	@Override
	public Name getName() {
		return name;
	}

	@Override
	public InfoView getView() {
		return view;
	}

	@Override
	public DirectInformationSchemaColumn getOrderByColumn() {
		if (orderByColumns.size() > 0)
			return orderByColumns.get(0);
		return null;
	}

	public ResizableArray<DirectInformationSchemaColumn> getOrderByColumns() {
		return orderByColumns;
	}
	
	@Override
	public DirectInformationSchemaColumn getIdentColumn() {
		return identColumn;
	}

	@Override
	public boolean requiresPriviledge() {
		return privileged;
	}

	@Override
	public boolean isExtension() {
		return extension;
	}

	@Override
	public boolean isVariablesTable() {
		// TODO Auto-generated method stub
		return false;
	}
	
	public void assertPermissions(SchemaContext sc) {
		if (!privileged) return;
		if (!sc.getPolicyContext().isRoot())
			throw new InformationSchemaException("You do not have permissions to query " + getName().get());	
	}

	@Override
	public boolean isView() {
		return true;
	}

	
	public List<DirectInformationSchemaColumn> getProjectionColumns(boolean includeExtensions, boolean includePriviledged, boolean includeFull) {
		ArrayList<DirectInformationSchemaColumn> out = new ArrayList<DirectInformationSchemaColumn>();
		for(DirectInformationSchemaColumn isc : columns) {
			if (!isc.isVisible())
				continue;
			if (isc.isExtension() && !includeExtensions)
				continue;
			if (isc.requiresPrivilege() && !includePriviledged)
				continue;
			if (isc.isFull() && !includeFull)
				continue;
			out.add(isc);
		}
		return out;
	}

	protected boolean useExtensions(SchemaContext sc) {
		return 	KnownVariables.SHOW_METADATA_EXTENSIONS.getValue(sc.getConnection().getVariableSource()).booleanValue();
	}

	@Override
	public void buildTableEntity(CatalogSchema cs, CatalogDatabaseEntity db,
			int dmid, int storageid, List<PersistedEntity> acc)
			throws PEException {
		buildTableEntity(cs,db,dmid,storageid,acc,
				Functional.apply(columns,new UnaryFunction<PEColumn,DirectInformationSchemaColumn>() {

					@Override
					public PEColumn evaluate(
							DirectInformationSchemaColumn object) {
						return object.getColumn();
					}
					
				}),
				getName().getUnqualified());
	}


	public static void buildTableEntity(CatalogSchema cs, CatalogDatabaseEntity db, int dmid, int storageid, 
			List<PersistedEntity> acc, List<PEColumn> columns, UnqualifiedName tableName) throws PEException {
		CatalogTableEntity cte = new CatalogTableEntity(cs,db,tableName.get(),dmid,storageid,"MEMORY");
		acc.add(cte);
		int counter = 0;
		for(PEColumn pec : columns) {
			CatalogColumnEntity cce = new CatalogColumnEntity(cs,cte);
			cce.setName(pec.getName().get());
			cce.setNullable(pec.isNullable());
			cce.setType(pec.getType());
			cce.setPosition(counter);
			acc.add(cce);
		}
	}

}
