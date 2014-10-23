package com.tesora.dve.sql.transform.strategy.triggers;

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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.sql.ParserException.Pass;
import com.tesora.dve.sql.SchemaException;
import com.tesora.dve.sql.expression.ColumnKey;
import com.tesora.dve.sql.expression.ExpressionUtils;
import com.tesora.dve.sql.expression.TableKey;
import com.tesora.dve.sql.expression.TriggerTableKey;
import com.tesora.dve.sql.node.expression.ColumnInstance;
import com.tesora.dve.sql.node.expression.ExpressionNode;
import com.tesora.dve.sql.node.expression.FunctionCall;
import com.tesora.dve.sql.node.expression.LateBindingConstantExpression;
import com.tesora.dve.sql.node.expression.TableInstance;
import com.tesora.dve.sql.node.structural.FromTableReference;
import com.tesora.dve.sql.node.structural.LimitSpecification;
import com.tesora.dve.sql.node.structural.SortingSpecification;
import com.tesora.dve.sql.node.test.EngineConstant;
import com.tesora.dve.sql.parser.SourceLocation;
import com.tesora.dve.sql.schema.FunctionName;
import com.tesora.dve.sql.schema.PEColumn;
import com.tesora.dve.sql.schema.PEKey;
import com.tesora.dve.sql.schema.PETable;
import com.tesora.dve.sql.schema.PETableTriggerPlanningEventInfo;
import com.tesora.dve.sql.schema.TempTableCreateOptions;
import com.tesora.dve.sql.schema.TriggerEvent;
import com.tesora.dve.sql.schema.DistributionVector.Model;
import com.tesora.dve.sql.statement.dml.AliasInformation;
import com.tesora.dve.sql.statement.dml.DMLStatement;
import com.tesora.dve.sql.statement.dml.DMLStatementUtils;
import com.tesora.dve.sql.statement.dml.SelectStatement;
import com.tesora.dve.sql.statement.dml.UpdateStatement;
import com.tesora.dve.sql.transform.ColumnInstanceCollector;
import com.tesora.dve.sql.transform.CopyVisitor;
import com.tesora.dve.sql.transform.SchemaMapper;
import com.tesora.dve.sql.transform.behaviors.defaults.DefaultFeaturePlannerFilter;
import com.tesora.dve.sql.transform.execution.DMLExplainReason;
import com.tesora.dve.sql.transform.execution.ExecutionSequence;
import com.tesora.dve.sql.transform.strategy.FeaturePlannerIdentifier;
import com.tesora.dve.sql.transform.strategy.PlannerContext;
import com.tesora.dve.sql.transform.strategy.TransformFactory;
import com.tesora.dve.sql.transform.strategy.UpdateRewriteTransformFactory;
import com.tesora.dve.sql.transform.strategy.featureplan.FeatureStep;
import com.tesora.dve.sql.transform.strategy.featureplan.MultiFeatureStep;
import com.tesora.dve.sql.transform.strategy.featureplan.ProjectingFeatureStep;
import com.tesora.dve.sql.transform.strategy.featureplan.RedistFeatureStep;
import com.tesora.dve.sql.transform.strategy.featureplan.RedistributionFlags;
import com.tesora.dve.sql.util.ListOfPairs;
import com.tesora.dve.sql.util.ListSet;
import com.tesora.dve.sql.util.Pair;

public class UpdateTriggerPlanner extends TriggerPlanner {

	@Override
	public FeatureStep plan(DMLStatement stmt, PlannerContext context)
			throws PEException {
		UpdateStatement us = (UpdateStatement) stmt;
		ListOfPairs<ColumnKey,ExpressionNode> updateExprs = UpdateRewriteTransformFactory.getUpdateExpressions(us);
		TableKey updateTable = UpdateRewriteTransformFactory.getUpdateTables(updateExprs);
		PETableTriggerPlanningEventInfo triggerInfo = getTriggerInfo(context,updateTable, TriggerEvent.UPDATE);
		if (triggerInfo == null)
			return null;

		LinkedHashMap<ColumnKey,Integer> updateExprOffsets = new LinkedHashMap<ColumnKey,Integer>();
		LinkedHashMap<PEColumn,Integer> uniqueKeyOffsets = new LinkedHashMap<PEColumn,Integer>();
		
		SelectStatement srcSelect = buildTempTableSelect(context,us,updateTable,triggerInfo,updateExprOffsets, uniqueKeyOffsets);
		
		ProjectingFeatureStep srcStep = 
				(ProjectingFeatureStep) buildPlan(srcSelect, context.withTransform(getFeaturePlannerID()), DefaultFeaturePlannerFilter.INSTANCE);

		final RedistFeatureStep rowsTable = 
				srcStep.redist(context, this,
						new TempTableCreateOptions(Model.BROADCAST,
								context.getTempGroupManager().getGroup(true)),
						new RedistributionFlags(), 
						DMLExplainReason.TRIGGER_SRC_TABLE.makeRecord());

		// we need a feature step to get the rows results
		SelectStatement rows = rowsTable.buildNewSelect(context);
		
		UpdateStatement uniqueKeyUpdate = buildUniqueKeyUpdate(context, updateTable, updateExprOffsets, uniqueKeyOffsets);
		
		final FeatureStep rowUpdate = 
				buildPlan(uniqueKeyUpdate, context.withTransform(getFeaturePlannerID()), DefaultFeaturePlannerFilter.INSTANCE);
		
		final FeatureStep beforeStep = triggerInfo.getBeforeStep(context.getContext());
		final FeatureStep afterStep = triggerInfo.getAfterStep(context.getContext());
		
		MultiFeatureStep out = new MultiFeatureStep(this)  {
			
			@Override
			public void schedule(PlannerContext sc, ExecutionSequence es, Set<FeatureStep> scheduled) throws PEException {
				
				throw new PEException("help");
			}
			
		};
		out.addChild(rowsTable);
		out.addChild(rowUpdate);
		if (beforeStep != null)
			out.addChild(beforeStep);
		if (afterStep != null)
			out.addChild(afterStep);
		
		return out;
	}

	@Override
	public FeaturePlannerIdentifier getFeaturePlannerID() {
		return FeaturePlannerIdentifier.UPDATE_TRIGGER;
	}

	private SelectStatement buildTempTableSelect(PlannerContext pc, UpdateStatement us,  
			TableKey updatedTable, PETableTriggerPlanningEventInfo triggerInfo,
			Map<ColumnKey,Integer> updateExprOffsets, Map<PEColumn,Integer> ukOffsets) throws PEException {

		// figure out the unique key we're going to use
		PEKey uk = updatedTable.getAbstractTable().asTable().getUniqueKey(pc.getContext());
		if (uk == null) 
			throw new PEException("No support for updating a table with update triggers but no unique key");
		ListSet<PEColumn> ukColumns = new ListSet<PEColumn>(uk.getColumns(pc.getContext()));
		
		UpdateStatement copy = CopyVisitor.copy(us);
		ListOfPairs<ColumnKey,ExpressionNode> updateExprs = UpdateRewriteTransformFactory.getUpdateExpressions(copy);

		SelectStatement out = new SelectStatement(new AliasInformation())
		.setTables(copy.getTables())
		.setWhereClause(copy.getWhereClause());
		out.setOrderBy(copy.getOrderBys());
		out.setLimit(copy.getLimit());
		out.getDerivedInfo().take(copy.getDerivedInfo());
		SchemaMapper mapper = new SchemaMapper(copy.getMapper().getOriginals(), out, copy.getMapper().getCopyContext());
		out.setMapper(mapper);
		
		// key is the trigger column key, value is the original column key
		HashMap<ColumnKey,ColumnKey> updateKeyForwarding = new HashMap<ColumnKey,ColumnKey>();
		// key is orginal column key, value is update expression
		LinkedHashMap<ColumnKey,ExpressionNode> updateExprMap = new LinkedHashMap<ColumnKey,ExpressionNode>();
		for(Pair<ColumnKey,ExpressionNode> p : updateExprs) {
			ColumnKey triggerColumnKey = new ColumnKey(new TriggerTableKey(updatedTable.getTable(),-1,false),p.getFirst().getPEColumn());
			updateKeyForwarding.put(triggerColumnKey,p.getFirst());
			updateExprMap.put(p.getFirst(),p.getSecond());
		}
		List<ExpressionNode> proj = new ArrayList<ExpressionNode>();
		
		Collection<ColumnKey> triggerColumns = triggerInfo.getTriggerBodyColumns(pc.getContext());
		for(ColumnKey ck : triggerColumns) {
			int position = proj.size();
			boolean isKeyPart = false;
			TriggerTableKey ttk = (TriggerTableKey) ck.getTableKey();
			if (ttk.isBefore()) {
				isKeyPart = ukColumns.contains(ck.getPEColumn());
				proj.add(new ColumnInstance(ck.getPEColumn(),updatedTable.toInstance()));
			} else {
				ColumnKey origCK = updateKeyForwarding.get(ck);
				if (origCK != null) {
					ExpressionNode en = updateExprMap.remove(origCK);
					updateExprOffsets.put(origCK,position);
					proj.add(en);
				} else {
					// not updated, so just take the before image
					isKeyPart = ukColumns.contains(ck.getPEColumn());
					proj.add(new ColumnInstance(ck.getPEColumn(),updatedTable.toInstance()));
				}
			}
			if (isKeyPart) {
				Integer any = ukOffsets.get(ck.getPEColumn());
				if (any == null) 
					ukOffsets.put(ck.getPEColumn(), position);
			}
		}
		
		// proj now has the trigger columns, left to right
		// add any unreferenced update exprs
		for(Map.Entry<ColumnKey, ExpressionNode> me : updateExprMap.entrySet()) {
			updateExprOffsets.put(me.getKey(), proj.size());
			proj.add(me.getValue());
		}
		
		// finally, if there are any parts of the unique key which are missing, add those too
		for(PEColumn pec : ukColumns) {
			Integer any = ukOffsets.get(pec);
			if (any == null) {
				ukOffsets.put(pec, proj.size());
				proj.add(new ColumnInstance(pec,updatedTable.toInstance()));
			}
		}

		out.setProjection(proj);
		
		return out;
	}
	
	private UpdateStatement buildUniqueKeyUpdate(PlannerContext context, TableKey updatedTable, LinkedHashMap<ColumnKey,Integer> updateExprOffsets,
			LinkedHashMap<PEColumn,Integer> uniqueKeyOffsets) {
		// build a new table key for this thing
		List<ExpressionNode> updateExprs = new ArrayList<ExpressionNode>();
		for(Map.Entry<ColumnKey,Integer> me : updateExprOffsets.entrySet()) {
			FunctionCall eq = new FunctionCall(FunctionName.makeEquals(),me.getKey().toInstance(),new LateBindingConstantExpression(me.getValue()));
			updateExprs.add(eq);
		}

		List<ExpressionNode> eqs = new ArrayList<ExpressionNode>();
		for(Map.Entry<PEColumn,Integer> me : uniqueKeyOffsets.entrySet()) {
			FunctionCall eq = new FunctionCall(FunctionName.makeEquals(),
					new ColumnInstance(me.getKey(),updatedTable.toInstance()),
					new LateBindingConstantExpression(me.getValue()));
			eqs.add(eq);
		}
		FromTableReference ftr = new FromTableReference(updatedTable.toInstance());
		UpdateStatement out = new UpdateStatement(Collections.singletonList(ftr),
				updateExprs,
				ExpressionUtils.safeBuildAnd(eqs),
				Collections.EMPTY_LIST,
				null,
				new AliasInformation(),
				null);
		return out;
	}	
}
