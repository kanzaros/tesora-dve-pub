package com.tesora.dve.sql.raw;

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

import com.tesora.dve.common.PEXmlUtils;
import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.sql.raw.jaxb.DMLType;
import com.tesora.dve.sql.raw.jaxb.DistVectColumn;
import com.tesora.dve.sql.raw.jaxb.DistributionType;
import com.tesora.dve.sql.raw.jaxb.DynamicGroupType;
import com.tesora.dve.sql.raw.jaxb.GroupScaleType;
import com.tesora.dve.sql.raw.jaxb.LiteralType;
import com.tesora.dve.sql.raw.jaxb.ModelType;
import com.tesora.dve.sql.raw.jaxb.ParameterType;
import com.tesora.dve.sql.raw.jaxb.ProjectingStepType;
import com.tesora.dve.sql.raw.jaxb.Rawplan;
import com.tesora.dve.sql.raw.jaxb.TargetTableType;

public class RawPlanBuilder {

	private final Rawplan rp;
	
	public RawPlanBuilder() {
		rp = new Rawplan();
	}
	
	public RawPlanBuilder withInSQL(String sql) {
		rp.setInsql(sql);
		return this;
	}
	
	public RawPlanBuilder withParameter(String name, LiteralType lt) {
		ParameterType pt = new ParameterType();
		pt.setName(name);
		pt.setType(lt);
		rp.getParameter().add(pt);
		return this;
	}
	
	public RawPlanBuilder withDynamicGroup(String name, GroupScaleType scale) {
		DynamicGroupType dgt = new DynamicGroupType();
		dgt.setName(name);
		dgt.setSize(scale);
		rp.getDyngroup().add(dgt);
		return this;
	}
	
	public RawPlanBuilder withFinalProjectingStep(String sql, String srcGroup, ModelType srcMod) {
		ProjectingStepType pst = new ProjectingStepType();
		pst.setSrcsql(sql);
		pst.setSrcgrp(srcGroup);
		pst.setSrcmod(srcMod);
		pst.setAction(DMLType.PROJECTING);
		rp.getStep().add(pst);
		return this;
	}
	
	public RawPlanBuilder withRedistStep(String sql, String srcGroup, ModelType srcmod,
			String targetTable, boolean tempTable, String targetGroup, ModelType targetModel, String...targetCols) {
		ProjectingStepType pst = new ProjectingStepType();
		pst.setAction(DMLType.PROJECTING);
		pst.setSrcsql(sql);
		pst.setSrcgrp(srcGroup);
		pst.setSrcmod(srcmod);
		TargetTableType ttt = new TargetTableType();
		ttt.setName(targetTable);
		ttt.setTemp(tempTable);
		ttt.setGroup(targetGroup);
		DistributionType dt = new DistributionType();
		dt.setModel(targetModel);
		ttt.setDistvect(dt);
		for(int i = 0; i < targetCols.length; i++) {
			DistVectColumn dvc = new DistVectColumn();
			dvc.setPosition(i);
			dvc.setName(targetCols[i]);
			dt.getColumn().add(dvc);
		}
		pst.setTarget(ttt);
		rp.getStep().add(pst);
		return this;
	}
	
	public Rawplan toPlan() {
		return rp;
	}
	
	public String toXML() throws PEException {
		return PEXmlUtils.marshalJAXB(rp);
	}
}
