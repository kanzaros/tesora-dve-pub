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

import java.sql.Types;

import com.tesora.dve.common.catalog.UserColumn;
import com.tesora.dve.db.mysql.MyFieldType;
import com.tesora.dve.db.mysql.MysqlNativeConstants;
import com.tesora.dve.db.mysql.MysqlNativeType;
import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.server.global.HostService;
import com.tesora.dve.singleton.Singletons;

public class TestColumnDef {

	private String database;
	private String origTblName;
	private String tableName;
	private String origColName;
	private String colName;
	private short charSet;
	private int length;
	private MyFieldType fieldType;
	private short flags;
	private byte scale;
	private String defaultValue;
	private String nativeType;
	private int sqlType;
	private long maxLen = 1; // character set character length

	public TestColumnDef(String database, String tableName, String origTblName, String colName,
			String origColName, int charSet, int length, MyFieldType fieldType, int flags, int scale, String defaultValue ) {
		this(database, tableName, origTblName, colName, origColName, charSet, length, fieldType, flags, scale, defaultValue, null, 0);
	}

	public TestColumnDef(String database, String tableName, String origTblName, String colName,
			String origColName, int charSet, int length, MyFieldType fieldType, int flags, int scale, String defaultValue, String nativeType, int sqlType) {
		this(database, tableName, origTblName, colName, origColName, charSet, length, fieldType, flags, scale, defaultValue, nativeType, sqlType, 1);
	}
	
	public TestColumnDef(String database, String tableName, String origTblName, String colName,
			String origColName, int charSet, int length, MyFieldType fieldType, int flags, int scale, String defaultValue, String nativeType, int sqlType, int maxLen) {
		this.database = database;
		this.tableName = tableName;
		this.origTblName = origTblName;
		this.colName = colName;
		this.origColName = origColName;
		this.charSet = (short) charSet;
		this.length = length;
		this.fieldType = fieldType;
		this.flags = (short) flags;
		this.scale = (byte) scale;
		this.defaultValue = defaultValue;
		this.nativeType = nativeType;
		this.sqlType = sqlType;
		this.setMaxLen(maxLen);
	}
	
	public TestColumnDef(MyFieldType fieldType, int flags, long maxLen) {
		this.fieldType = fieldType;
		this.flags = (short) flags;
		this.setMaxLen(maxLen);
	}
	
	public String getDatabase() {
		return database;
	}

	public String getOrigTblName() {
		return origTblName;
	}

	public String getTableName() {
		return tableName;
	}

	public String getOrigColName() {
		return origColName;
	}

	public String getColName() {
		return colName;
	}

	public short getCharSet() {
		return charSet;
	}

	public int getLength() {
		return length;
	}

	public MyFieldType getFieldType() {
		return fieldType;
	}

	public short getFlags() {
		return flags;
	}

	public byte getScale() {
		return scale;
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	public String getNativeType() {
		return nativeType;
	}

	public void setNativeType(String nativeType) {
		this.nativeType = nativeType;
	}

	public int getSqlType() {
		return sqlType;
	}

	public void setSqlType(int sqlType) {
		this.sqlType = sqlType;
	}
	
	public UserColumn getUserColumn() {
		String nativeTypeName = getNativeType();
		UserColumn uc = new UserColumn(getOrigColName(), getSqlType(), nativeTypeName);
		if (nativeTypeName.contains(" " + MysqlNativeType.MODIFIER_UNSIGNED)) {
			uc.setNativeTypeName(nativeTypeName.replace(" " + MysqlNativeType.MODIFIER_UNSIGNED, ""));
			uc.setNativeTypeModifiers(MysqlNativeType.MODIFIER_UNSIGNED);
		}
		uc.setDefaultValue(getDefaultValue());
		if ( sqlType == Types.DECIMAL || sqlType == Types.NUMERIC )
			uc.setPrecision(getLength()-2);		// the length returned from mysql is precision + 2;
		else
			uc.setPrecision(getLength());
		
		uc.setScale(getScale());
		uc.setSize(getLength());
		uc.setAutoGenerated((flags & MysqlNativeConstants.FLDPKT_FLAG_AUTO_INCREMENT) == MysqlNativeConstants.FLDPKT_FLAG_AUTO_INCREMENT);
		uc.setNullable(!((flags & MysqlNativeConstants.FLDPKT_FLAG_NOT_NULL) == MysqlNativeConstants.FLDPKT_FLAG_NOT_NULL));
			
		return uc;
	}

	public long getMaxLen(int charSetLen) {
		MysqlNativeType colNativeType;
		try {
            colNativeType = (MysqlNativeType) Singletons.require(HostService.class).getDBNative().findType(getNativeType());
			if (colNativeType.isStringType()) {
				return maxLen * charSetLen;
			}
			return maxLen;
		} catch (PEException e) {
			// can't figure out the native type so just return the maxLen
			return maxLen;
		}
	}

	public void setMaxLen(long maxLen) {
		this.maxLen = maxLen;
	}

}

