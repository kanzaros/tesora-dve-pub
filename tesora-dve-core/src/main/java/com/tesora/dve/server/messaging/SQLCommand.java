package com.tesora.dve.server.messaging;

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

import io.netty.util.CharsetUtil;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import com.tesora.dve.db.GenericSQLCommand;
import com.tesora.dve.db.GenericSQLCommand.CommandFragment;
import com.tesora.dve.queryplan.ExecutionState;
import com.tesora.dve.resultset.ProjectionInfo;
import com.tesora.dve.resultset.ResultColumn;
import com.tesora.dve.resultset.ResultRow;
import com.tesora.dve.sql.schema.SchemaContext;
import com.tesora.dve.sql.statement.StatementType;
import com.tesora.dve.variables.VariableStoreSource;

// we need to support both regular statements and parameterized statements;
// this class just wraps that all up
public class SQLCommand implements Serializable {

	static AtomicLong nextId = new AtomicLong();
	long thisId = nextId.incrementAndGet();

	private static final long serialVersionUID = 1L;

	// charset of an empty string should not matter
	public static final SQLCommand EMPTY = new SQLCommand(CharsetUtil.UTF_8, "");

	private final GenericSQLCommand sql;
	private List<Object> parameters;
	private ProjectionInfo projection;
	private long referenceTime = 0;

	public SQLCommand(GenericSQLCommand command) {
		this(command, null);
	}

	public SQLCommand(GenericSQLCommand command, List<Object> params) {
		sql = command;
		parameters = params;
	}

	public SQLCommand(final SchemaContext sc, String regularStatement) {
		this(new GenericSQLCommand(sc, regularStatement));
	}

	public SQLCommand(final VariableStoreSource vs, String regularStatement) {
		this(new GenericSQLCommand(vs, regularStatement));
	}

	public SQLCommand(final Charset connectionCharset, String regularStatement) {
		this(new GenericSQLCommand(connectionCharset, regularStatement));
	}

    public Charset getEncoding(){
        return sql.getEncoding();
    }

	public SQLCommand withProjection(ProjectionInfo projection) {
		this.projection = projection;
		return this;
	}

	public SQLCommand withReferenceTime(long refTime) {
		this.referenceTime = refTime;
		return this;
	}

	public String getRawSQL() {
		return sql.getDecoded();
	}

	public String getSQL() {
		return sql.getDecoded();
	}

    public ByteBuffer getBytes() {
		return sql.getEncoded();
	}

	public List<CommandFragment> viewCommandFragments() {
		return sql.viewCommandFragments();
	}

	public int getDecodedLength() {
		return sql.getDecodedLength();
	}

	public int getEncodedLength() {
		return sql.getEncodedLength();
	}

	public boolean isEmpty() {
		return (sql == null) || sql.viewCommandFragments().isEmpty();
	}

	public boolean isPreparedStatement() {
		return parameters != null;
	}

	public List<Object> getParameters() {
		return parameters;
	}

	public void setParameters(ResultRow row) {
		setParameters(Collections.singletonList(row));
	}

	public void setParameters(List<ResultRow> rows) {
		parameters = new ArrayList<Object>();
		for (final ResultRow row : rows) {
			final List<ResultColumn> columns = row.getRow();
			if (columns.size() == 0) {
				throw new IllegalStateException("ResultRow has no columns");
			}
			for (int i = 0; i < columns.size(); ++i) {
				parameters.add(columns.get(i).getColumnValue());
			}
		}
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "(" + thisId + "): " + " \"" + sql.getDecoded() + "\": " + parameters;
	}

	public ProjectionInfo getProjection() {
		return projection;
	}

	public long getReferenceTime() {
		return referenceTime;
	}

	public boolean hasReferenceTime() {
		return referenceTime != 0;
	}

	public boolean isForUpdateStatement() {
		return (sql.isForUpdate() == null ? false : sql.isForUpdate().booleanValue());
	}

	public SQLCommand getResolvedCommand(final GenericSQLCommand.DBNameResolver worker) {
		final SQLCommand newCommand = new SQLCommand(sql.resolveLateEntries(worker));
		return copyFields(newCommand);
	}

	public SQLCommand getLateResolvedCommand(ExecutionState estate) {
		final SQLCommand newCommand = new SQLCommand(sql.resolveLateConstants(estate.getValues()));
		return copyFields(newCommand);
	}
	
	private SQLCommand copyFields(SQLCommand newCommand) {
		newCommand.parameters = parameters;
		newCommand.projection = projection;
		newCommand.referenceTime = referenceTime;
		return newCommand;
	}
	
	public StatementType getStatementType() {
		return sql.getStatementType();
	}

	public String getDisplayForLog() {
		String stringToDisplay = getRawSQL();
		final int lenToDisplay = Math.min(stringToDisplay.length(), 1024);
		if (lenToDisplay < stringToDisplay.length()) {
			stringToDisplay = stringToDisplay.substring(0, lenToDisplay) + "...";
		}
		return stringToDisplay;
	}
}
