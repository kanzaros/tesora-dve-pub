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

import com.tesora.dve.db.mysql.MysqlMessage;
import com.tesora.dve.db.mysql.libmy.*;
import com.tesora.dve.mysqlapi.repl.messages.MyComBinLogDumpRequest;
import com.tesora.dve.mysqlapi.repl.messages.MyComRegisterSlaveRequest;
import com.tesora.dve.mysqlapi.repl.messages.MyReplEvent;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;

import com.tesora.dve.clock.NoopTimingService;
import com.tesora.dve.clock.Timer;
import com.tesora.dve.clock.TimingService;
import com.tesora.dve.db.mysql.MyFieldType;
import com.tesora.dve.db.mysql.MysqlNativeConstants;
import com.tesora.dve.db.mysql.common.DBTypeBasedUtils;
import com.tesora.dve.db.mysql.common.DataTypeValueFunc;
import com.tesora.dve.exceptions.PECodingException;
import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.singleton.Singletons;

public class MyBackendDecoder extends ChannelDuplexHandler {
	protected static final Logger logger = Logger.getLogger(MyBackendDecoder.class);
    protected static final ParseStrategy UNSOLICITED = new UnsolicitedMessageParser();

    CachedAppendBuffer bufferCache = new CachedAppendBuffer();
    Packet mspPacket;

	String socketDesc;

    public interface CharsetDecodeHelper {
        long lookupMaxLength(byte mysqlCharsetID);
        boolean typeSupported(MyFieldType fieldType, short flags, int maxDataLen);
    }

    TimingService timingService = Singletons.require(TimingService.class, NoopTimingService.SERVICE);
    enum TimingDesc {BACKEND_DECODE, BACKEND_ENCODE, BACKEND_WAITING_FOR_MYSQL}

    CharsetDecodeHelper charsetHelper;
    ConcurrentLinkedDeque<ParseStrategy> parserStack = new ConcurrentLinkedDeque<>();

    public MyBackendDecoder(CharsetDecodeHelper charsetHelper) {
        this.charsetHelper = charsetHelper;
    }

    public MyBackendDecoder(String socketDesc, CharsetDecodeHelper charsetHelper) {
        this.socketDesc = socketDesc;
        this.charsetHelper = charsetHelper;
    }

    private final ByteToMessageDecoder decoder = new ByteToMessageDecoder() {
        @Override
        public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            MyBackendDecoder.this.decode(ctx, in, out);
        }

        @Override
        protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            MyBackendDecoder.this.decodeLast(ctx, in, out);
        }
    };

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try{
            super.channelInactive(ctx);
        } finally {
            bufferCache.releaseSlab();
            if (mspPacket != null){
                mspPacket.release();
                mspPacket = null;
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        decoder.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {                
        if (logger.isDebugEnabled())
            logger.debug("writing message "+msg.getClass().getName());
        //TODO: need to convert this to something more efficient, maybe an enum map.

        //create two timers.  One covers the entire request/response, the other covers just encoding the request.
        Timer parentTimer = timingService.getTimerOnThread();
        Timer backendEncoding = parentTimer.newSubTimer(TimingDesc.BACKEND_ENCODE);

        ParseStrategy responseParseStrategy = null;
        if (msg instanceof MSPComQueryRequestMessage){
            responseParseStrategy = new ExecuteResponseParser(charsetHelper,ExecuteResponseParser.ExecMode.PROTOCOL_TEXT);
        } else if (msg instanceof BufferedExecute) {
            responseParseStrategy = new ExecuteResponseParser(charsetHelper,ExecuteResponseParser.ExecMode.PROTOCOL_BINARY);
        } else if (msg instanceof MSPComStmtExecuteRequestMessage) {
            responseParseStrategy = new ExecuteResponseParser(charsetHelper,ExecuteResponseParser.ExecMode.PROTOCOL_BINARY);
        } else if (msg instanceof MSPComStmtCloseRequestMessage){
            responseParseStrategy = new NoResponseParser();
        } else if (msg instanceof MSPComQuitRequestMessage){
            responseParseStrategy = new SimpleOKParser();
        } else if (msg instanceof MSPComPrepareStmtRequestMessage){
            responseParseStrategy = new PrepareResponseParser();
        } else if (msg instanceof MyComRegisterSlaveRequest){
            responseParseStrategy = new SimpleOKParser();
        } else if (msg instanceof MyComBinLogDumpRequest) {
            responseParseStrategy = new ReplDumpLogParser();
        } else {
            logger.warn(String.format("Unexpected message transmitted, %s",msg.getClass().getName()));
        }
        if (responseParseStrategy != null){
            responseParseStrategy.setSocketDesc(socketDesc);
            responseParseStrategy.setParentTimer(parentTimer);
            if (responseParseStrategy.isDone()){
                //looks like this parser doesn't expect a response, don't bother adding it.
                responseParseStrategy.endAtomicWaitTimer();//ok to end before we start, will noop.
            } else {
                parserStack.add(responseParseStrategy);
            }
        }

        try {
        if (msg instanceof MysqlMessage){
            MysqlMessage mysql = (MysqlMessage)msg;
            int sequenceStart = 0; //right now all outbound messages on the backend are full requests, and start a new sequence.
            ByteBuf append = bufferCache.startAppend(ctx);

            int nextSequence = Packet.encodeFullMessage(sequenceStart, mysql, append);

            ByteBuf fullyEncodedSlice = bufferCache.sliceWritableData();

            if (responseParseStrategy != null) //if a response is expected, save the sequence number the response should start with.
                responseParseStrategy.setNextSequenceNumber(nextSequence);

//            ByteBuf heap = Unpooled.buffer();
//            ((MysqlMessage) msg).marshallPayload(heap);
//            String asString = heap.toString(CharsetUtil.UTF_8);
//            System.out.println("*** writing message, "+msg + " ==> "+asString);

            super.write(ctx, fullyEncodedSlice,promise);
        } else
            super.write(ctx, msg, promise);
        } catch (Exception e){
            logger.warn("Problem during encoding of message." , e);
        }
        backendEncoding.end(
            socketDesc,
            (msg == null ? "null" : msg.getClass().getName())
        );

        if (responseParseStrategy != null)
            responseParseStrategy.startAtomicWaitTimer();
    }

    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        this.decode0(ctx,in,out,false);
    }

    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        this.decode0(ctx,in,out,true);
    }

	protected void decode0(ChannelHandlerContext ctx, ByteBuf in, List<Object> out, boolean lastPacket) throws Exception {
        if (logger.isDebugEnabled())
            logger.debug("processing packet, "+in);

        try {
            //get a contextual parser that decodes data based on what was previously transmitted, never null.
            ParseStrategy responseParser = lookupParser();
            int expectedSequence = responseParser.nextSequenceNumber();

            if (mspPacket == null)
                mspPacket = new Packet(ctx.alloc(), expectedSequence, Packet.Modifier.HEAPCOPY_ON_READ, "backend");

            if (!mspPacket.decodeMore(in)) //deals with framing and extended packets.
                return;

            //we got a packet, maybe extended.  update the next expected sequence (might be > +1, if extended)
            responseParser.setNextSequenceNumber( mspPacket.getNextSequenceNumber() );

            ByteBuf leHeader = mspPacket.unwrapHeader().order(ByteOrder.LITTLE_ENDIAN).retain();
            ByteBuf lePayload = mspPacket.unwrapPayload().order(ByteOrder.LITTLE_ENDIAN).retain();//retain a separate reference to the payload.
            mspPacket.release();
            mspPacket = null;




            //ok, we aren't waiting for a packet anymore, end the wait timer, start the decode timer.
            responseParser.endAtomicWaitTimer();
			Timer decodeTimer = responseParser.getNewSubTimer(TimingDesc.BACKEND_DECODE);

            //use the active response parser to decode the buffer into a protocol message.
            MyMessage message = responseParser.parsePacket(ctx,leHeader,lePayload);
            decodeTimer.end(
                socketDesc,
                (message == null ? "null" : message.getClass().getName())
            );

            lookupParser();//check if we are done and pop the parser now, to reduce memory usage and get tighter timer measurements.

			if (message != null) {
				out.add(message);
			}

		} catch (Exception e) {
            logger.warn(String.format("Unexpected problem parsing frame, closing %s :", socketDesc), e);
            ctx.close();
		}
	}


    /**
     * Finds the response parser that should process the next buffer received on the socket.
     * Note: this method is written in a way that assumes it will only be called by one thread at a time, presumably the
     * response processing thread.  Queuing another parser on the tail as part of a pipelined write is safe, but
     * this method should only be called by a single thread doing packet decoding.
     *
     * @return the active parser expecting packets from mysql, or null if no packets are expected.
     */
    protected ParseStrategy lookupParser(){
        ParseStrategy parser;
        for (;;){
             parser = parserStack.peekFirst();
            if (parser == null){
                return UNSOLICITED;
            } else if (parser.isDone()){
                parser.endAtomicWaitTimer();//this parser certainly isn't waiting for mysql anymore.
                parserStack.removeFirst();
                continue;
            } else
                break;
        }
        return parser;
    }

    public static interface ParseStrategy {
        void setSocketDesc(String desc);
        MyMessage parsePacket(ChannelHandlerContext ctx, ByteBuf leHeader, ByteBuf lePayload) throws PEException;
        boolean isDone();
        void setParentTimer(Timer parent);
        Timer getParentTimer();
		Timer getNewSubTimer(Enum location);

        void startAtomicWaitTimer();
        void endAtomicWaitTimer();
        void setNextSequenceNumber(int seq);
        int nextSequenceNumber();
    }

    static abstract class BaseParseStrategy implements ParseStrategy {
        String socketDesc;
        Timer parent;
        AtomicReference<Timer> waitTimer = new AtomicReference<>(null);
        int nextSeq;

        public void setSocketDesc(String desc){
            this.socketDesc = desc;
        }

        public Timer getParentTimer() {
            return parent;
        }

        public void setParentTimer(Timer parent) {
            this.parent = parent;
        }

		public Timer getNewSubTimer(Enum location) {
			return (this.parent != null) ? this.parent.newSubTimer(TimingDesc.BACKEND_DECODE) : null;
		}

        @Override
        public void startAtomicWaitTimer() {
            if (parent != null){
                Timer startedWaiting = parent.newSubTimer(TimingDesc.BACKEND_WAITING_FOR_MYSQL);
                waitTimer.compareAndSet(null,startedWaiting);//install if no timer already exists.
            }
        }

        @Override
        public void endAtomicWaitTimer() {
            Timer existingWait = waitTimer.getAndSet( NoopTimingService.NOOP_TIMER );//install a noop timer to disable future start/stops.
            if (existingWait != null)
                existingWait.end(

                );
        }

        @Override
        public void setNextSequenceNumber(int seq) {
            this.nextSeq = seq;
        }

        public int nextSequenceNumber(){
            return this.nextSeq;
        }
    }

    static class UnsolicitedMessageParser extends BaseParseStrategy {

        @Override
        public MyMessage parsePacket(ChannelHandlerContext ctx, ByteBuf leHeader, ByteBuf lePayload) throws PEException {
            String message = String.format("Unexpected data received on %s, header=%s, payload=%s", socketDesc, leHeader, lePayload);
            throw new PEException(message);
        }

        @Override
        public boolean isDone() {
            return true;
        }
    }


    static class SimpleOKParser extends BaseParseStrategy {
        boolean parsedOne = false;

        @Override
        public MyMessage parsePacket(ChannelHandlerContext ctx, ByteBuf leHeader, ByteBuf lePayload) throws PEException {
            parsedOne = true;
            ByteBuf payload = lePayload;
            byte statusByte = payload.getByte(0); //5th byte in the full packet
            MyMessage message;
            if (statusByte == 0){
                message = new MyOKResponse();
                message.unmarshallMessage(payload);
            } else {
                message = new MyErrorResponse();
                message.unmarshallMessage(payload);
            }
            message.setSequenceEnd(true);
            return message;
        }

        @Override
        public boolean isDone() {
            return parsedOne;
        }
    }

    static class ReplDumpLogParser extends BaseParseStrategy {
        boolean errorOrEof = false;
        @Override
        public MyMessage parsePacket(ChannelHandlerContext ctx, ByteBuf leHeader, ByteBuf lePayload) throws PEException {

            ByteBuf payload = lePayload;
            byte statusByte = payload.getByte(0);//5th byte in the full packet
            MyMessage message;
            switch (statusByte){
                case MyOKResponse.OKPKT_INDICATOR: //replication events use 0 to indicate a replication event, same as an OK packet.
                    MyReplEvent repl = new MyReplEvent();
                    lePayload.skipBytes(1);//TODO: MyReplEvent expects first byte of payload to already be consumed. -sgossard
                    repl.unmarshallMessage(lePayload);
                    message = repl;
                    break;
                case MyErrorResponse.ERRORPKT_FIELD_COUNT:
                    errorOrEof = true;
                    MyErrorResponse errorResponse = new MyErrorResponse();
                    errorResponse.unmarshallMessage(lePayload);
                    errorResponse.setSequenceEnd(true);
                    message = errorResponse;
                    break;
                case MyEOFPktResponse.EOFPKK_FIELD_COUNT:
                    errorOrEof = true;
                    MyEOFPktResponse eofResponse = new MyEOFPktResponse();
                    eofResponse.unmarshallMessage(lePayload);
                    eofResponse.setSequenceEnd(true);
                    message = eofResponse;
                    break;
                default:
                    throw new PEException("Unexpected response while parsing replication dump log response, type ID was " + statusByte);
            }
            return message;
        }

        @Override
        public boolean isDone() {
            return errorOrEof;
        }
    }

    static class NoResponseParser extends BaseParseStrategy {

        @Override
        public MyMessage parsePacket(ChannelHandlerContext ctx, ByteBuf leHeader, ByteBuf lePayload) throws PEException {
            throw new PECodingException("No response expected, shouldn't be parsing a packet here");
        }

        @Override
        public boolean isDone() {
            return true;
        }

    }

    /**
     * Looks up the appropriate data type function for the given column / parameter.  This call is equivilent to
     * DBTypeBasedUtils.getMysqlTypeFunc(FieldMetadataAdapter.buildMetadata(fieldPacket)), but avoids touching the named
     * info fields in the field packet that would result in an expensive full unpack of all the variable length strings.
     * @param columnDefPacket
     * @return
     */
    public static DecodedMeta buildTypeCodec(CharsetDecodeHelper helper, MyFieldPktResponse columnDefPacket, boolean binary) throws PEException {
        int maxDataLen = columnDefPacket.getColumn_length();
        if (maxDataLen < 0)  //TODO: mysql length is 32 bits, unsigned, but we use 32 bits signed.  This is a workaround until we deal with lengths greater than Integer.MAX_VALUE.
            maxDataLen = Integer.MAX_VALUE;
        byte charSet = columnDefPacket.getCharset();
        short flags = columnDefPacket.getFlags();
        if (charSet != MysqlNativeConstants.MYSQL_CHARSET_BINARY && (flags & MysqlNativeConstants.FLDPKT_FLAG_BINARY) == 0) {
            // charSet 63 is Binary - ugly, but I'm not sure what else to do here
            long maxCharLength = helper.lookupMaxLength(charSet);
            maxDataLen /= maxCharLength;
        }

        MyFieldType fieldType = columnDefPacket.getColumn_type();
        boolean supported;
        supported = helper.typeSupported(fieldType, flags, maxDataLen);
        if (!supported)
            throw new PECodingException("Unsupported native type " + fieldType);
        DataTypeValueFunc mysqlTypeFunc = DBTypeBasedUtils.getMysqlTypeFunc(fieldType, maxDataLen, flags);
        return new DecodedMeta(fieldType,mysqlTypeFunc,flags);
    }


    static class ExecuteResponseParser extends BaseParseStrategy {

		private CharsetDecodeHelper helper;

        enum ExecMode {PROTOCOL_BINARY, PROTOCOL_TEXT}
        enum ResponseState { AWAIT_FIELD_COUNT, AWAIT_FIELD, AWAIT_FIELD_EOF, AWAIT_ROW, DONE }

        ExecMode mode;
        ResponseState bufferState = ResponseState.AWAIT_FIELD_COUNT;
        private int bufferFieldCount;
        private List<DecodedMeta> typeDecoders = new ArrayList<>();

        ExecuteResponseParser(CharsetDecodeHelper help,ExecMode mode) {
            this.helper = help;
            this.mode = mode;
        }

        @Override
        public MyMessage parsePacket(ChannelHandlerContext ctx, ByteBuf leHeader, ByteBuf lePayload) throws PEException {
            MyMessage message;
            try{
                switch (bufferState) {
                    case AWAIT_ROW:
                    	message = parseAwaitRow(leHeader,lePayload);
                        break;
                    case AWAIT_FIELD_COUNT:
                        message = parseFieldCount(leHeader,lePayload);
                        break;
                    case AWAIT_FIELD:
                        message = parseAwaitField(leHeader,lePayload);
                        break;
                    case AWAIT_FIELD_EOF:
                        message = parseAwaitFieldEOF(leHeader,lePayload);
                        break;
                    default:
                        throw new PECodingException("Unrecognized buffer state " + bufferState + " occurred while processing packets in " + this.getClass().getName());
                }
                return message;
            } catch (Exception e){
                logger.warn("encountered problem processing packet, ",e);
                throw e;
            }
        }

        @Override
        public boolean isDone() {
            return bufferState == ResponseState.DONE;
        }

        public MyMessage parseAwaitFieldEOF(ByteBuf leHeader, ByteBuf lePayload) throws PEException {
            bufferState = ResponseState.AWAIT_ROW;

            //TODO: there is zero type inspection/verification on this packet, it just gets blindly discarded or forwarded.
            MyMessage message = new MyRawMessage();
            message.unmarshallMessage(lePayload);
            return message;
        }

        public MyMessage parseAwaitField(ByteBuf leHeader, ByteBuf lePayload) throws PEException {
            if (--bufferFieldCount == 0)
                bufferState = ResponseState.AWAIT_FIELD_EOF;

            MyFieldPktResponse columnDef = new MyFieldPktResponse();
            columnDef.unmarshallMessage(lePayload);

            try {
                //peek at the column define so we know how to decode,encode the value.
                DecodedMeta codec = buildTypeCodec(helper,columnDef, mode == ExecMode.PROTOCOL_BINARY);
                typeDecoders.add(codec);
            } catch (Exception e) {
                String errorMessage = "Ignoring problem finding type codec for " + columnDef.getColumn_type() + ", will cause problems for binary rowsets.";
                logger.debug(errorMessage);
            }

            return columnDef;
        }





        public MyMessage parseFieldCount(ByteBuf leHeader, ByteBuf lePayload) {
            MyMessage message;
            byte pktId = lePayload.getByte(0);
            switch (pktId){
                case MyOKResponse.OKPKT_INDICATOR:
                    bufferState = ResponseState.DONE;
                    MyOKResponse ok = new MyOKResponse();
                    ok.unmarshallMessage(lePayload);
                    ok.setSequenceEnd(true);
                    message = ok;
                    break;
                case MyErrorResponse.ERRORPKT_FIELD_COUNT:
                    bufferState = ResponseState.DONE;
                    MyErrorResponse errorResponse = new MyErrorResponse();
                    errorResponse.unmarshallMessage(lePayload);
                    errorResponse.setSequenceEnd(true);
                    message = errorResponse;
                    break;
                case MyEOFPktResponse.EOFPKK_FIELD_COUNT:
                    throw new PECodingException("Cannot handle packet id EOFPKK_FIELD_COUNT in " + this.getClass().getName());
                default:
                    bufferState = ResponseState.AWAIT_FIELD;
                    MyColumnCount count = new MyColumnCount();
                    count.unmarshallMessage(lePayload);
                    bufferFieldCount = count.getColumnCount();
                    message = count;
            }
            return message;
        }

		public MyMessage parseAwaitRow(ByteBuf leHeader, ByteBuf lePayload) throws PEException {
            final byte messageType = lePayload.getByte(0);

			MyMessage message = null;
			if (messageType == MyEOFPktResponse.EOFPKK_FIELD_COUNT && lePayload.readableBytes() == 5) { //EOF payload is exactly 5 bytes and first byte is 0xfe
				bufferState = ResponseState.DONE;
				MyEOFPktResponse eofPkt = new MyEOFPktResponse();
				eofPkt.unmarshallMessage(lePayload);
                eofPkt.setSequenceEnd(true);
				message = eofPkt;
			} else if (mode == ExecMode.PROTOCOL_BINARY) {
				MyBinaryResultRow binRow = new MyBinaryResultRow(typeDecoders);
				binRow.unmarshallMessage(lePayload);
				message = binRow;
			} else if (mode == ExecMode.PROTOCOL_TEXT) {
				MyTextResultRow textRow = new MyTextResultRow();
				textRow.unmarshallMessage(lePayload);
				message = textRow;
			} else {
				throw new PECodingException("Unexpected reponse parsing mode, " + mode);
			}

			return message;
		}
    }

    static class PrepareResponseParser extends BaseParseStrategy {
        enum ResponseState { AWAIT_HEADER, AWAIT_COL_DEF, AWAIT_COL_DEF_EOF, AWAIT_PARAM_DEF, AWAIT_PARAM_DEF_EOF, DONE };
        ResponseState bufferState = ResponseState.AWAIT_HEADER;
        private int bufferNumColumns;
        private int bufferNumParams;

        @Override
        public MyMessage parsePacket(ChannelHandlerContext ctx, ByteBuf leHeader, ByteBuf lePayload) throws PEException {
            byte pktId = lePayload.getByte(0);

            MyMessage message;
            try {
                switch (bufferState) {
                    case AWAIT_HEADER:
                        if (pktId == MyOKResponse.OKPKT_INDICATOR) {
                            message = parseAwaitHeaderOK(lePayload);
                        } else if (pktId == MyErrorResponse.ERRORPKT_FIELD_COUNT) {
                            message = parseAwaitHeaderErr(lePayload);
                        } else {
                            throw new PEException("Invalid packet from mysql (expected PrepareResponse header, got " + pktId +")");
                        }
                        break;
                    case AWAIT_PARAM_DEF:
                        message = parseAwaitParam(lePayload);
                        break;
                    case AWAIT_PARAM_DEF_EOF:
                        message = parseAwaitParamEOF(lePayload);
                        break;
                    case AWAIT_COL_DEF:
                        message = parseAwaitCol(lePayload);
                        break;
                    case AWAIT_COL_DEF_EOF:
                        message = parseAwaitColEOF(lePayload);
                        break;

                    case DONE:
                    default:
                        logger.debug("Received a packet after we believe we are DONE, packet had fieldCount/ID type " + pktId);
                        throw new PECodingException("received packet, but already in DONE state.");
                }

                return message;
            } catch (PEException e) {
                logger.warn(e);
                throw e;
            } catch (Exception e) {
                logger.warn(e);
                throw new PEException(e);
            }
        }

        @Override
        public boolean isDone() {
            return bufferState == ResponseState.DONE;
        }

        public MyMessage parseAwaitColEOF(ByteBuf wholePacket) throws PEException {
            MyMessage message;
            message = new MyEOFPktResponse();
            message.unmarshallMessage(wholePacket);
            message.setSequenceEnd(true);
            bufferState = ResponseState.DONE;
            return message;
        }

        public MyMessage parseAwaitCol(ByteBuf wholePacket) throws PEException {
            MyMessage message;//a column definition
            if (--bufferNumColumns == 0)
                bufferState = ResponseState.AWAIT_COL_DEF_EOF;

            message = new MyFieldPktResponse();
            message.unmarshallMessage(wholePacket);
            return message;
        }

        public MyMessage parseAwaitParamEOF(ByteBuf wholePacket) throws PEException {
            MyMessage message;
            message = new MyEOFPktResponse();
            message.unmarshallMessage(wholePacket);
            bufferState = (bufferNumColumns == 0) ? ResponseState.DONE : ResponseState.AWAIT_COL_DEF;
            if (bufferState == ResponseState.DONE)
                message.setSequenceEnd(true);
            return message;
        }

        public MyMessage parseAwaitParam(ByteBuf wholePacket) throws PEException {
            MyMessage message;
            if (--bufferNumParams == 0)
                bufferState = ResponseState.AWAIT_PARAM_DEF_EOF;

            message = new MyFieldPktResponse();
            message.unmarshallMessage(wholePacket);
            return message;
        }

        public MyMessage parseAwaitHeaderErr(ByteBuf wholePacket) throws PEException {
            MyMessage message;//an error packet
            message = new MyErrorResponse();
            message.unmarshallMessage(wholePacket);
            message.setSequenceEnd(true);
            bufferState = ResponseState.DONE;
            return message;
        }

        public MyMessage parseAwaitHeaderOK(ByteBuf wholePacket) throws PEException {
            MyMessage message;//this is a prepare ok result header.
            MyPrepareOKResponse newPrepareOK = new MyPrepareOKResponse();
            message = newPrepareOK;
            message.unmarshallMessage(wholePacket);
            bufferNumColumns = newPrepareOK.getNumColumns();
            bufferNumParams = newPrepareOK.getNumParams();
            if (bufferNumParams > 0)
                bufferState = ResponseState.AWAIT_PARAM_DEF;
            else if (bufferNumColumns > 0)
                bufferState = ResponseState.AWAIT_COL_DEF;
            else {
                message.setSequenceEnd(true);
                bufferState = ResponseState.DONE;
            }
            return message;
        }


    }
}
