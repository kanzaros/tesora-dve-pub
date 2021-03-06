package com.tesora.dve.db.mysql.libmy;

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

import io.netty.buffer.ByteBuf;

import com.tesora.dve.exceptions.PECodingException;
import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.resultset.ColumnSet;
import com.tesora.dve.resultset.ResultRow;

public abstract class MyDataResponse extends MyResponseMessage {
    ColumnSet columnSet;
	ResultRow row;

	public MyDataResponse(ColumnSet columnSet, ResultRow row) {
		super();
		this.columnSet = columnSet;
		this.row = row;
	}

	@Override
	public void unmarshallMessage(ByteBuf cb) throws PEException {
		throw new PECodingException(getClass().getSimpleName());
	}

	@Override
	public MyMessageType getMessageType() {
		throw new PECodingException(getClass().getSimpleName());
	}

	public ResultRow getRow() {
		return row;
	}

}
