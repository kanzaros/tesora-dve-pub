package com.tesora.dve.db.mysql.portal.protocol;

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

public class MSPComPingRequestMessage extends BaseMSPMessage {
    public static final MSPComPingRequestMessage PROTOTYPE = new MSPComPingRequestMessage();

    protected MSPComPingRequestMessage() {
        super();
    }

    protected MSPComPingRequestMessage(ByteBuf backing) {
        super(backing);
    }

    @Override
    public byte getMysqlMessageType() {
        return (byte) 0x0e;
    }

    @Override
    public MSPComPingRequestMessage newPrototype(ByteBuf source) {
        source = source.slice();
        return new MSPComPingRequestMessage(source);
    }
}
