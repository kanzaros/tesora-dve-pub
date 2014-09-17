package com.tesora.dve.mysqlapi;

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

import org.apache.log4j.Logger;

import com.tesora.dve.db.mysql.libmy.MyMessage;
import com.tesora.dve.db.mysql.libmy.MyMessageType;

public class MyClientSideSyncDecoder extends MyDecoder {
	private static final Logger logger = Logger.getLogger(MyClientSideSyncDecoder.class);
	static final byte RESP_TYPE_OK = (byte) 0x00;
	static final byte RESP_TYPE_ERR = (byte) 0xff;
	static final byte RESP_TYPE_EOF = (byte) 0xfe;

	@Override
	MyMessage instantiateMessage(ByteBuf frame) {
		MyMessage nativeMsg;
		MyMessageType mt = MyMessageType.UNKNOWN;
		if (!isHandshakeDone()) {
			// if the handshake isn't done, then the message coming in
			// must be a SERVER_GREETING_RESPONSE
			setHandshakeDone(true);
			mt = MyMessageType.SERVER_GREETING_RESPONSE;
		} else {
			// if the handshake is done, the message must be one of the
			// response packets
			switch (frame.readByte()) {
			case RESP_TYPE_OK:
				mt = MyMessageType.OK_RESPONSE;
				break;
			case RESP_TYPE_ERR:
				mt = MyMessageType.ERROR_RESPONSE;
				break;
			case RESP_TYPE_EOF:
				mt = MyMessageType.EOFPKT_RESPONSE;
				break;
			default:
				 mt = MyMessageType.UNKNOWN;
				break;
			}
			
			if (logger.isDebugEnabled())
				logger.debug("Decoding message of type " + mt.toString());
		}
		// create a new instance of the appropriate message
		nativeMsg = super.newResponseInstance(mt);
		
		return nativeMsg;
	}

}