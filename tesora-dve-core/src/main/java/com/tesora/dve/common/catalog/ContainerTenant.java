package com.tesora.dve.common.catalog;

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


import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.ForeignKey;

import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.resultset.ColumnSet;
import com.tesora.dve.resultset.ResultRow;

@Entity
@Table(name = "container_tenant") 
//@org.hibernate.annotations.Table(appliesTo="container_tenant",
//		indexes={@org.hibernate.annotations.Index(name="cont_ten_idx", columnNames = { "container_id", "discriminant" })})
public class ContainerTenant implements ITenant {

	private static final long serialVersionUID = 1L;

	// the global tenant is what the container tenant is set to when the user does a use container global
	public static final ContainerTenant GLOBAL_CONTAINER_TENANT = new ContainerTenant(null,null) {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		@Override
		public boolean isGlobalTenant() {
			return true;
		}
	};
	
	@Id
	@GeneratedValue
	@Column(name = "ctid")
	int id;

	@ForeignKey(name="fk_cont_tenant_cont")
	@ManyToOne
	@JoinColumn(name = "container_id", nullable=false)
	Container container;

	@Column(name = "discriminant", nullable=false)
	@Lob
	String discriminant;

	// should we have a separate container tenant id?  or would we just update the ctid?
	
	public ContainerTenant(Container ofContainer, String valueRep) {
		container = ofContainer;
		discriminant = valueRep;
	}
	
	ContainerTenant() {
		
	}
	
	@Override
	public int getId() {
		return id;
	}

	public Container getContainer() {
		return container;
	}
	
	public String getDiscriminant() {
		return discriminant;
	}
	
	
	@Override
	public ColumnSet getShowColumnSet(CatalogQueryOptions cqo)
			throws PEException {
		return null;
	}

	@Override
	public ResultRow getShowResultRow(CatalogQueryOptions cqo)
			throws PEException {
		return null;
	}

	@Override
	public void removeFromParent() throws Throwable {
	}

	@Override
	public List<? extends CatalogEntity> getDependentEntities(CatalogDAO c)
			throws Throwable {
		return null;
	}

	@Override
	public boolean isGlobalTenant() {
		return false;
	}

	@Override
	public String getUniqueIdentifier() {
		return discriminant;
	}

	@Override
	public boolean isPersistent() {
		// not always true - the global tenant is not an actual tenant
		return !isGlobalTenant();
	}

	@Override
	public void onUpdate() {
	}

	@Override
	public void onDrop() {
	}
	
}
