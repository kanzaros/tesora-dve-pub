package com.tesora.dve.mysqlapi.repl.messages;

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

import com.tesora.dve.exceptions.PEException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import org.apache.log4j.Logger;

public class MyNewLoadLogEvent extends MyLogEventPacket {
	private static final Logger logger = Logger
			.getLogger(MyNewLoadLogEvent.class);

	int threadId;
	int time;
	int ignoreLines;
	byte tableLen;
	byte dbLen;
	int columns;
	ByteBuf variableData; 
	
	public MyNewLoadLogEvent(MyReplEventCommonHeader ch) {
		super(ch);
	}

    @Override
    public void accept(ReplicationVisitorTarget visitorTarget) throws PEException {
        visitorTarget.visit((MyNewLoadLogEvent)this);
    }

	@Override
	public void unmarshallMessage(ByteBuf cb) {
		threadId = cb.readInt();
		time = cb.readInt();
		ignoreLines = cb.readInt();
		tableLen = cb.readByte();
		dbLen = cb.readByte();
		columns = cb.readInt();
		// TODO: need to parse out the variable part of the data
		variableData = Unpooled.buffer(cb.readableBytes());
		variableData.writeBytes(cb);
	}

	@Override
    public void marshallMessage(ByteBuf cb) {
		cb.writeInt(threadId);
		cb.writeInt(time);
		cb.writeInt(ignoreLines);
		cb.writeByte(tableLen);
		cb.writeByte(dbLen);
		cb.writeInt(columns);
		cb.writeBytes(variableData);
	}

}
