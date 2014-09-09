package com.tesora.dve.db.mysql;

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

import com.tesora.dve.concurrent.*;
import com.tesora.dve.db.DBConnection;
import com.tesora.dve.db.mysql.libmy.*;
import com.tesora.dve.db.mysql.portal.protocol.MysqlGroupedPreparedStatementId;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import com.tesora.dve.exceptions.PESQLStateException;
import com.tesora.dve.common.catalog.StorageSite;
import com.tesora.dve.server.messaging.SQLCommand;

public class MysqlPrepareStatementForwarder extends MysqlPrepareParallelConsumer {

	private ChannelHandlerContext outboundCtx;
	MyPreparedStatement<String> outboundPStmt;
	
	public MysqlPrepareStatementForwarder(ChannelHandlerContext outboundCtx, MyPreparedStatement<String> pstmt) {
		this.outboundPStmt = pstmt;
		this.outboundCtx = outboundCtx;
	}

	@Override
	public void consumeHeader(MyPrepareOKResponse preparedOK) {
        MyPrepareOKResponse copyPrepareOK = new MyPrepareOKResponse(preparedOK);//copy since we are mutating the id.
        int outboundID = Long.valueOf(outboundPStmt.getStmtId()).intValue();
        copyPrepareOK.setStmtId( outboundID );
		outboundCtx.writeAndFlush(copyPrepareOK);
		outboundPStmt.setNumParams(getNumParams());
	}

    @Override
    public void writeCommandExecutor(final Channel channel, StorageSite site, DBConnection.Monitor connectionMonitor, SQLCommand sql, final CompletionHandle<Boolean> promise) {
		final MysqlPrepareStatementForwarder resultForwarder = this;
		final PEDefaultPromise<Boolean> preparePromise = new PEDefaultPromise<Boolean>();
		preparePromise.addListener(new CompletionTarget<Boolean>() {
			@Override
			public void success(Boolean returnValue) {
				MyPreparedStatement<MysqlGroupedPreparedStatementId> pstmt = resultForwarder.getPreparedStatement();
				channel.writeAndFlush(new MysqlStmtCloseCommand(pstmt));
				promise.success(false);
			}
			@Override
			public void failure(Exception e) {
				promise.failure(e);
			}
		});
		super.writeCommandExecutor(channel, site, connectionMonitor, sql, preparePromise);
	}

	@Override
	public void consumeParamDef(MyFieldPktResponse paramDef) {
		outboundCtx.write(paramDef);
	}

	@Override
	public void consumeParamDefEOF(MyEOFPktResponse myEof) {
		outboundCtx.writeAndFlush(myEof);
	}

	@Override
	public void consumeColDef(MyFieldPktResponse columnDef) {
		outboundCtx.write(columnDef);
	}

	@Override
	public void consumeColDefEOF(MyEOFPktResponse colEof) {
		outboundCtx.writeAndFlush(colEof);
	}

	@Override
	public void consumeError(MyErrorResponse error) {
		outboundCtx.writeAndFlush(error);
	}


	public void sendError(Exception e) {
        Throwable rootCause = e;

        while (rootCause != null){
            rootCause = rootCause.getCause();
            if (rootCause instanceof PESQLStateException)
                return;//we've already forwarded sql error responses directly back to the client in consumeError().
        }

		MyMessage respMsg = new MyErrorResponse(e);
		outboundCtx.writeAndFlush(respMsg);
	}

}
