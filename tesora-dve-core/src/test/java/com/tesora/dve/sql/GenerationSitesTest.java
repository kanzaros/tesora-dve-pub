package com.tesora.dve.sql;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.tesora.dve.common.catalog.CatalogDAO.CatalogDAOFactory;
import com.tesora.dve.common.catalog.MultitenantMode;
import com.tesora.dve.common.catalog.PersistentGroup;
import com.tesora.dve.common.catalog.PersistentSite;
import com.tesora.dve.common.catalog.StorageSite;
import com.tesora.dve.common.catalog.TestCatalogHelper;
import com.tesora.dve.common.catalog.UserDatabase;
import com.tesora.dve.resultset.ResultRow;
import com.tesora.dve.server.bootstrap.BootstrapHost;
import com.tesora.dve.server.connectionmanager.SSConnection;
import com.tesora.dve.server.connectionmanager.SSConnectionAccessor;
import com.tesora.dve.server.connectionmanager.SSConnectionProxy;
import com.tesora.dve.server.global.HostService;
import com.tesora.dve.singleton.Singletons;
import com.tesora.dve.sql.util.PEDDL;
import com.tesora.dve.sql.util.ProxyConnectionResource;
import com.tesora.dve.sql.util.ResourceResponse;
import com.tesora.dve.sql.util.StorageGroupDDL;
import com.tesora.dve.standalone.PETest;
import com.tesora.dve.worker.DBConnectionParameters;
import com.tesora.dve.worker.UserCredentials;

public class GenerationSitesTest extends SchemaTest {
	protected ProxyConnectionResource conn = null;

	private static final StorageGroupDDL sg = new StorageGroupDDL("gstsg",50,3,"pg");
	private static final StorageGroupDDL smallsg = new StorageGroupDDL("smallsg",5,"sg");
	
	private static final PEDDL testDDL =
			new PEDDL("gsdb",sg,"database");
	private static final PEDDL nonmtDDL =
			new PEDDL("nonmt",sg,"database");
	private static final PEDDL mtDDL =
			new PEDDL("mtdb",sg,"database").withMTMode(MultitenantMode.ADAPTIVE);
	private static final PEDDL smallDDL =
			new PEDDL("smalldb",smallsg,"database");
	
	@BeforeClass
	public static void setup() throws Exception {
		PETest.projectSetup(testDDL,nonmtDDL,mtDDL,smallDDL);
		PETest.bootHost = BootstrapHost.startServices(PETest.class);
	}

	@Before
	public void setupTest() throws Throwable {
		conn = new ProxyConnectionResource();
		testDDL.create(conn);
	}

	@After
	public void teardownTest() throws Throwable {
		if(testDDL != null)
			testDDL.destroy(conn);
		
		if(conn != null)
			conn.disconnect();
		conn = null;
	}

	@SuppressWarnings("resource")
	@Test
	public void basicGenerationAddTest() throws Throwable {
		conn.execute("create range arange (int) persistent group " + testDDL.getPersistentGroup().getName());
		conn.execute("create range brange (char) persistent group " + testDDL.getPersistentGroup().getName());
		conn.execute("create range crange (varchar(10)) persistent group " + testDDL.getPersistentGroup().getName());
		conn.execute("create range drange (smallint) persistent group " + testDDL.getPersistentGroup().getName());
		conn.execute("create range erange (tinyint) persistent group " + testDDL.getPersistentGroup().getName());
		conn.execute("create range frange (datetime) persistent group " + testDDL.getPersistentGroup().getName());

		conn.execute("create table arange_table ( a int, b int, c int ) range distribute on (a) using arange");
		conn.execute("create table brange_table ( b char(10), a int, c int ) range distribute on (b) using brange");
		conn.execute("create table crange_table ( c varchar(10), a int ) range distribute on (c) using crange");
		conn.execute("create table drange_table ( d smallint, a int ) range distribute on (d) using drange");
		conn.execute("create table erange_table ( e tinyint, a int ) range distribute on (e) using erange");
		conn.execute("create table frange_table ( f datetime, a int, b datetime ) range distribute on (f) using frange");
		conn.execute("create table bcast_table ( col1 char(10), col2 varchar(255) ) broadcast distribute");
		conn.execute("create table random_table ( col1 int, col2 varchar(255) ) random distribute");

		conn.execute("insert into arange_table values (0,1,1),(22,2,2),(32,3,3),(2350,4,4)");
		conn.execute("insert into brange_table values ('12',1,1),('22',2,2),('32',3,3)");
		conn.execute("insert into crange_table values ('12',1),('22',2),('32',3)");
		conn.execute("insert into drange_table values (0,1),(22,2),(32,3)");
		conn.execute("insert into erange_table values (0,1),(22,2),(32,3)");
		conn.execute("insert into frange_table values ('2013-08-08 08:10:00',1,'2013-08-08 08:10:00'),"
				+ "('2013-08-09 08:10:00',2,'2013-08-08 08:10:00'),('2013-08-08 09:10:00',3,'2013-08-08 08:10:00')");
		conn.execute("insert into bcast_table values ('abcde', 'fghijk'), ('lmnop','qrstuv')");
		conn.execute("insert into random_table values (1,'abcde'),(2,'fghijk')");

		assertEquals(4, conn.fetch("select * from arange_table").getResults().size());
		assertEquals(3, conn.fetch("select * from brange_table").getResults().size());
		assertEquals(3, conn.fetch("select * from crange_table").getResults().size());
		assertEquals(3, conn.fetch("select * from drange_table").getResults().size());
		assertEquals(3, conn.fetch("select * from erange_table").getResults().size());
		assertEquals(3, conn.fetch("select * from frange_table").getResults().size());
		assertEquals(2, conn.fetch("select * from bcast_table").getResults().size());
		assertEquals(2, conn.fetch("select * from random_table").getResults().size());

		assertEquals(1, conn.fetch("select * from bcast_table where col1 = 'abcde'").getResults().size());
		assertEquals(1, conn.fetch("select * from bcast_table where col1 = 'lmnop'").getResults().size());

		assertEquals(1, conn.fetch("select * from random_table where col1 = 1").getResults().size());
		assertEquals(1, conn.fetch("select * from random_table where col1 = 2").getResults().size());

		assertEquals(1, conn.fetch("select * from arange_table where a = 0").getResults().size());
		assertEquals(1, conn.fetch("select * from arange_table where a = 22").getResults().size());
		assertEquals(1, conn.fetch("select * from arange_table where a = 32").getResults().size());
		assertEquals(1, conn.fetch("select * from arange_table where a = 2350").getResults().size());

		assertEquals(1, conn.fetch("select * from brange_table where b = '12'").getResults().size());
		assertEquals(1, conn.fetch("select * from brange_table where b = '22'").getResults().size());
		assertEquals(1, conn.fetch("select * from brange_table where b = '32'").getResults().size());

		assertEquals(1, conn.fetch("select * from crange_table where c = '12'").getResults().size());
		assertEquals(1, conn.fetch("select * from crange_table where c = '22'").getResults().size());
		assertEquals(1, conn.fetch("select * from crange_table where c = '32'").getResults().size());

		assertEquals(1, conn.fetch("select * from drange_table where d = 0").getResults().size());
		assertEquals(1, conn.fetch("select * from drange_table where d = 22").getResults().size());
		assertEquals(1, conn.fetch("select * from drange_table where d = 32").getResults().size());

		assertEquals(1, conn.fetch("select * from erange_table where e = 0").getResults().size());
		assertEquals(1, conn.fetch("select * from erange_table where e = 22").getResults().size());
		assertEquals(1, conn.fetch("select * from erange_table where e = 32").getResults().size());

		assertEquals(1, conn.fetch("select * from frange_table where f = '2013-08-08 08:10:00'").getResults().size());
		assertEquals(1, conn.fetch("select * from frange_table where f = '2013-08-09 08:10:00'").getResults().size());
		assertEquals(1, conn.fetch("select * from frange_table where f = '2013-08-08 09:10:00'").getResults().size());

		// get the stuff we need to poke at the persistent sites directly
		catalogDAO = CatalogDAOFactory.newInstance();
        DBConnectionParameters dbParams = new DBConnectionParameters(Singletons.require(HostService.class).getProperties());
		SSConnection ssConnection = SSConnectionAccessor.getSSConnection(new SSConnectionProxy());
		SSConnectionAccessor.setCatalogDAO(ssConnection, catalogDAO);
		ssConnection.startConnection(new UserCredentials(PETest.bootHost.getProperties()));
		Map<String, Connection> jdbcConnection = new HashMap<String, Connection>();
		for (PersistentSite site : catalogDAO.findAllPersistentSites()) {
			final Connection siteConn = DriverManager.getConnection(site.getMasterUrl(), dbParams.getUserid(), dbParams.getPassword());
			jdbcConnection.put(site.getName(), siteConn);
		}

		// make sure they didn't go all into the same site, and that the total count is correct
		UserDatabase db = catalogDAO.findDatabase(testDDL.getDatabaseName());
		PersistentGroup persistGroup = catalogDAO.findPersistentGroup(testDDL.getPersistentGroup().getName());
		String[] tableNames = { "arange_table", "brange_table", "crange_table", "drange_table", "erange_table" };
		Integer[] initialCount = { 4, 3, 3, 3, 3 };
		int index = 0;
		int[] initialNumSites = { 0, 0, 0, 0, 0 };
		for (String tableName : tableNames) {
			Integer tableCount = 0;
			for (StorageSite site : persistGroup.getStorageSites()) {
				Statement stmt = jdbcConnection.get(site.getName()).createStatement();
				boolean r = stmt.execute("select * from " + db.getNameOnSite(site) + "." + tableName);
				assertTrue(r);
				ResultSet rs = stmt.getResultSet();
				int siteCount = 0;
				while (rs.next()) {
					siteCount++;
					tableCount++;
				}
				if (siteCount > 0) {
					initialNumSites[index]++;
				}
				assertTrue("Should not have all records on the same site for table " + tableName,
						siteCount < initialCount[index]);
				stmt.close();
			}
			assertEquals("Should find all initial records for table " + tableName, initialCount[index], tableCount);
			index++;
		}
		for (Connection jdbcConn : jdbcConnection.values()) {
			jdbcConn.close();
		}
		ssConnection.close();

		// add a generation
		conn.execute(testDDL.getPersistentGroup().getAddGenerations());

		conn.execute("insert into arange_table values (1,5,5),(2,6,6),(3000,7,7),(50000,8,8)");
		conn.execute("insert into brange_table values ('100',1,1),('444',2,2),('555',3,3)");
		conn.execute("insert into crange_table values ('100',1),('444',2),('555',3)");
		conn.execute("insert into drange_table values (1,5),(2,6),(3000,7),(30000,8)");
		conn.execute("insert into erange_table values (1,5),(2,6),(112,7)");
		conn.execute("insert into frange_table values ('2013-08-08 10:10:00',5,'2013-08-08 08:10:00'),"
				+ "('2013-08-09 10:10:00',6,'2013-08-08 08:10:00'),('2014-08-08 09:10:00',7,'2013-08-08 08:10:00')");
		conn.execute("insert into bcast_table values ('abcde', 'fghijk'), ('lmnop','qrstuv')");
		conn.execute("insert into random_table values (1,'abcde'),(2,'fghijk')");

		assertEquals(8, conn.fetch("select * from arange_table").getResults().size());
		assertEquals(6, conn.fetch("select * from brange_table").getResults().size());
		assertEquals(6, conn.fetch("select * from crange_table").getResults().size());
		assertEquals(7, conn.fetch("select * from drange_table").getResults().size());
		assertEquals(6, conn.fetch("select * from erange_table").getResults().size());
		assertEquals(6, conn.fetch("select * from frange_table").getResults().size());
		assertEquals(4, conn.fetch("select * from bcast_table").getResults().size());
		assertEquals(4, conn.fetch("select * from random_table").getResults().size());

		assertEquals(2, conn.fetch("select * from bcast_table where col1 = 'abcde'").getResults().size());
		assertEquals(2, conn.fetch("select * from bcast_table where col1 = 'lmnop'").getResults().size());

		assertEquals(2, conn.fetch("select * from random_table where col1 = 1").getResults().size());
		assertEquals(2, conn.fetch("select * from random_table where col1 = 2").getResults().size());

		assertEquals(1, conn.fetch("select * from arange_table where a = 0").getResults().size());
		assertEquals(1, conn.fetch("select * from arange_table where a = 22").getResults().size());
		assertEquals(1, conn.fetch("select * from arange_table where a = 32").getResults().size());
		assertEquals(1, conn.fetch("select * from arange_table where a = 2350").getResults().size());
		assertEquals(1, conn.fetch("select * from arange_table where a = 1").getResults().size());
		assertEquals(1, conn.fetch("select * from arange_table where a = 2").getResults().size());
		assertEquals(1, conn.fetch("select * from arange_table where a = 3000").getResults().size());
		assertEquals(1, conn.fetch("select * from arange_table where a = 50000").getResults().size());

		assertEquals(1, conn.fetch("select * from brange_table where b = '12'").getResults().size());
		assertEquals(1, conn.fetch("select * from brange_table where b = '22'").getResults().size());
		assertEquals(1, conn.fetch("select * from brange_table where b = '32'").getResults().size());
		assertEquals(1, conn.fetch("select * from brange_table where b = '100'").getResults().size());
		assertEquals(1, conn.fetch("select * from brange_table where b = '444'").getResults().size());
		assertEquals(1, conn.fetch("select * from brange_table where b = '555'").getResults().size());

		assertEquals(1, conn.fetch("select * from crange_table where c = '12'").getResults().size());
		assertEquals(1, conn.fetch("select * from crange_table where c = '22'").getResults().size());
		assertEquals(1, conn.fetch("select * from crange_table where c = '32'").getResults().size());
		assertEquals(1, conn.fetch("select * from crange_table where c = '100'").getResults().size());
		assertEquals(1, conn.fetch("select * from crange_table where c = '444'").getResults().size());
		assertEquals(1, conn.fetch("select * from crange_table where c = '555'").getResults().size());

		assertEquals(1, conn.fetch("select * from drange_table where d = 0").getResults().size());
		assertEquals(1, conn.fetch("select * from drange_table where d = 22").getResults().size());
		assertEquals(1, conn.fetch("select * from drange_table where d = 32").getResults().size());
		assertEquals(1, conn.fetch("select * from drange_table where d = 1").getResults().size());
		assertEquals(1, conn.fetch("select * from drange_table where d = 2").getResults().size());
		assertEquals(1, conn.fetch("select * from drange_table where d = 3000").getResults().size());
		assertEquals(1, conn.fetch("select * from drange_table where d = 30000").getResults().size());

		assertEquals(1, conn.fetch("select * from erange_table where e = 0").getResults().size());
		assertEquals(1, conn.fetch("select * from erange_table where e = 22").getResults().size());
		assertEquals(1, conn.fetch("select * from erange_table where e = 32").getResults().size());
		assertEquals(1, conn.fetch("select * from erange_table where e = 1").getResults().size());
		assertEquals(1, conn.fetch("select * from erange_table where e = 2").getResults().size());
		assertEquals(1, conn.fetch("select * from erange_table where e = 112").getResults().size());

		assertEquals(1, conn.fetch("select * from frange_table where f = '2013-08-08 08:10:00'").getResults().size());
		assertEquals(1, conn.fetch("select * from frange_table where f = '2013-08-09 08:10:00'").getResults().size());
		assertEquals(1, conn.fetch("select * from frange_table where f = '2013-08-08 09:10:00'").getResults().size());
		assertEquals(1, conn.fetch("select * from frange_table where f = '2013-08-08 10:10:00'").getResults().size());
		assertEquals(1, conn.fetch("select * from frange_table where f = '2013-08-09 10:10:00'").getResults().size());
		assertEquals(1, conn.fetch("select * from frange_table where f = '2014-08-08 09:10:00'").getResults().size());

		// now check the sites again for at least some distribution
		catalogDAO = CatalogDAOFactory.newInstance();
		ssConnection = SSConnectionAccessor.getSSConnection(new SSConnectionProxy());
		SSConnectionAccessor.setCatalogDAO(ssConnection, catalogDAO);
		ssConnection.startConnection(new UserCredentials(PETest.bootHost.getProperties()));
		jdbcConnection = new HashMap<String, Connection>();
		for (PersistentSite site : catalogDAO.findAllPersistentSites()) {
			final Connection siteConn = DriverManager.getConnection(site.getMasterUrl(), dbParams.getUserid(), dbParams.getPassword());
			jdbcConnection.put(site.getName(), siteConn);
		}
		db = catalogDAO.findDatabase(testDDL.getDatabaseName());
		persistGroup = catalogDAO.findPersistentGroup(testDDL.getPersistentGroup().getName());
		Integer[] finalCount = { 8, 6, 6, 7, 6 };
		index = 0;
		for (String tableName : tableNames) {
			Integer tableCount = 0;
			int finalNumSites = 0;
			for (StorageSite site : persistGroup.getStorageSites()) {
				Statement stmt = jdbcConnection.get(site.getName()).createStatement();
				boolean r = stmt.execute("select * from " + db.getNameOnSite(site) + "." + tableName);
				assertTrue(r);
				ResultSet rs = stmt.getResultSet();
				int siteCount = 0;
				while (rs.next()) {
					siteCount++;
					tableCount++;
				}
				if (siteCount > 0) {
					finalNumSites++;
				}
				assertTrue("Should not have all records on the same site for table " + tableName,
						siteCount < finalCount[index]);
				stmt.close();
			}
			assertEquals("Should find all final records for table " + tableName, finalCount[index], tableCount );
			assertTrue("Should have records on more sites for table " + tableName, finalNumSites > initialNumSites[index]);
			index++;
		}
		for (Connection jdbcConn : jdbcConnection.values()) {
			jdbcConn.close();
		}
		ssConnection.close();
	}

	@Ignore
	@Test
	public void test_pe481() throws Throwable {
		PEDDL test481DDL =
				new PEDDL("gsdb",
						new StorageGroupDDL("gstsg", 50, 47, "pg"),
						"database");
		testDDL.create(conn);
		conn.execute("create table bcast_table ( col1 char(10), col2 varchar(255) ) broadcast distribute");

		StringBuffer sb = new StringBuffer("insert into bcast_table values ('row0','random string')");
		for (int i = 1; i < 100000; i++) {
			sb.append(",('row").append(i).append("', 'random string')");
		}
		conn.execute(sb.toString());

		sb = new StringBuffer("insert into bcast_table values ('row0','random string')");
		for (int i = 100000; i < 300000; i++) {
			sb.append(",('row").append(i).append("', 'random string')");
		}
		conn.execute(sb.toString());

		conn.execute(test481DDL.getPersistentGroup().getAddGenerations());

		testDDL.destroy(conn);
	}

	@Test
	public void test_pe482() throws Throwable {
		conn.execute("create range arange (int) persistent group " + testDDL.getPersistentGroup().getName());

		conn.execute("create table foo ( a int, b int, c int ) range distribute on (a) using arange");

		conn.execute("insert into foo values ( 92, 1, 1 )");
		conn.assertResults("select * from foo", br(nr, 92, 1, 1));

		// add a generation
		conn.execute(testDDL.getPersistentGroup().getAddGenerations());

		conn.execute("insert into foo values ( 93, 1, 1 )");
		conn.assertResults("select * from foo order by a", br(nr, 92, 1, 1, nr, 93, 1, 1));
	}

	//	@Ignore
	@Test
	public void test_pe495() throws Throwable {
		conn.execute("create range arange (int) persistent group " + testDDL.getPersistentGroup().getName());

		conn.execute("create table t1 ( a int, b int, c int ) range distribute on (a) using arange");
		conn.execute("create table t2 ( a int, b int, c int ) range distribute on (b) using arange");

		conn.execute("insert into t1 values ( 3641, 1, 1 )");
		conn.assertResults("select * from t1", br(nr, 3641, 1, 1));

		conn.execute("insert into t2 values ( 1, 42899, 1 )");
		conn.assertResults("select * from t2", br(nr, 1, 42899, 1));

		// add a generation
		conn.execute(testDDL.getPersistentGroup().getAddGenerations());

		conn.execute("insert into t1 values ( 93, 1, 1 )");
		conn.assertResults("select * from t1 order by a", br(nr, 93, 1, 1, nr, 3641, 1, 1));
	}
	
	// mostly about examining the metadata associated with rebalancing
	@Test
	public void test_pe495Alt() throws Throwable {
		conn.execute("create range vrange (varchar) persistent group " + testDDL.getPersistentGroup().getName());
	
		conn.execute("create table vt1 (a varchar(32), b varchar(32), c int, primary key(a)) range distribute on (a) using vrange");
        conn.execute("create table vt2 (a varchar(32), b varchar(32), c int, primary key(a)) range distribute on (b) using vrange");

        StringBuilder buf1 = new StringBuilder();
        StringBuilder buf2 = new StringBuilder();
        buf1.append("insert into vt1 values ");
        buf2.append("insert into vt2 values ");
        for(int i = 0; i < 10; i++) {
        	if (i > 0) {
        		buf1.append(", ");
        		buf2.append(", ");
        	}
        	buf1.append("('key%d','one%d',%d)");
        	buf2.append("('two%d','key%d',%d)");
        }
        String vt1 = buf1.toString();
        String vt2 = buf2.toString();
        
        for (int i=0;i< 1000;i++){
        	Object[] args = new Object[30];
        	for(int j = 0; j < 10; j++) {
        		int val = i*10 + j;
        		args[3*j] = val;
        		args[3*j+1] = val;
        		args[3*j+2] = val;
        	}
        	conn.execute(String.format(vt1,args));
        	conn.execute(String.format(vt2,args));
        }

//        System.out.println(conn.printResults("select * from information_schema.storage_generation"));
        
		conn.execute(testDDL.getPersistentGroup().getAddGenerations() + " WITH REBALANCE");

//        System.out.println(conn.printResults("select * from information_schema.storage_generation"));

	}
	
	@Test
	public void test1390() throws Throwable {
		try {
			SchemaTest.removeUser(conn, "auser", "localhost");
			SchemaTest.removeUser(conn, "buser", "localhost");
			SchemaTest.removeUser(conn, "cuser", "localhost");
			conn.execute("set foreign_key_checks=0");
			conn.execute("create table fref (id int, fid int, primary key (id), foreign key (fid) references targ (id)) broadcast distribute");
			conn.execute("create table targ (id int, fid int, primary key (id)) broadcast distribute");
			conn.execute("create table ref (id int, fid int, primary key (id)) broadcast distribute");
			conn.execute("create table ctab (id int, fid int, primary key (id), foreign key (fid) references " + nonmtDDL.getDatabaseName() + ".targ (id)) broadcast distribute");
			conn.execute("insert into fref (id, fid) values (1,1),(2,2),(3,3),(4,4),(5,5),(6,6)");
			conn.execute("insert into ref (id, fid) values (1,1),(2,2),(3,3),(4,4),(5,5),(6,6)");
			conn.execute("insert into targ (id, fid) values (1,1),(2,2),(3,3),(4,4),(5,5),(6,6)");
			conn.execute("insert into ctab (id, fid) values (1,1),(2,2),(3,3),(4,4),(5,5),(6,6)");
			conn.execute("create table fview (id int, fid int, primary key (id)) broadcast distribute");
			conn.execute("create view rview as select max(fid) as mfid from fview group by id");
			conn.execute("drop table fview");
			conn.execute("create view fview as select f.id, t.fid from fref f inner join targ t on f.fid = t.id");
			conn.execute("create user 'auser'@'localhost' identified by 'auser'");
			conn.execute("grant all on " + testDDL.getDatabaseName() + ".* to 'auser'@'localhost'");
			conn.execute(nonmtDDL.getCreateDatabaseStatement());
			conn.execute("use " + nonmtDDL.getDatabaseName());
			conn.execute("create table targ (id int, fid int, primary key (id)) broadcast distribute");
			conn.execute("insert into targ (id, fid) values (1,1),(2,2),(3,3),(4,4),(5,5),(6,6)");
			conn.execute("create user 'buser'@'localhost' identified by 'buser'");
			conn.execute("grant all on " + nonmtDDL.getDatabaseName() + ".* to 'buser'@'localhost'");
			conn.execute("create user 'cuser'@'localhost' identified by 'cuser'");
			conn.execute("grant all on " + testDDL.getDatabaseName() + ".* to 'cuser'@'localhost'");
			conn.execute("grant all on " + nonmtDDL.getDatabaseName() + ".* to 'cuser'@'localhost'");
			conn.execute("set foreign_key_checks=1");
			conn.execute(testDDL.getPersistentGroup().getAddGenerations());
		} finally {
			conn.execute("drop database " + nonmtDDL.getDatabaseName());
		}
	}
	
	@Test
	public void test1390MT() throws Throwable {
		try {
			SchemaTest.removeUser(conn, "duser", "localhost");
			conn.execute("set foreign_key_checks=0");
			conn.execute(mtDDL.getCreateDatabaseStatement());
			conn.execute("create tenant ftenant 'ftenant'");
			conn.execute("use ftenant");
			conn.execute("create table ref (id int, fid int, primary key (id), foreign key (fid) references targ (id)) broadcast distribute");
			conn.execute("create table targ (id int, fid int, primary key (id)) broadcast distribute");
			conn.execute("insert into ref (id, fid) values (1,1),(2,2),(3,3),(4,4),(5,5),(6,6)");
			conn.execute("insert into targ (id, fid) values (1,1),(2,2),(3,3),(4,4),(5,5),(6,6)");
			conn.execute("create user 'duser'@'localhost' identified by 'duser'");
			conn.execute("grant all on ftenant.* to 'duser'@'localhost'");
			conn.execute("set foreign_key_checks=1");
			conn.execute(testDDL.getPersistentGroup().getAddGenerations());
		} finally {
			conn.execute("drop multitenant database " + mtDDL.getDatabaseName());
		}		
	}
	
	@Test
	public void testScaling() throws Throwable {
		ProxyConnectionResource uc = null;
		try {
			setTemplateModeOptional();
			SchemaTest.removeUser(conn, "buser", "localhost");
			for(int i = 1; i <= 5; i++) {
				conn.execute(String.format("create persistent site smallsg%d url='%s' user='%s' password='%s'",
						i,
						TestCatalogHelper.getInstance().getCatalogBaseUrl(),
						TestCatalogHelper.getInstance().getCatalogUser(),
						TestCatalogHelper.getInstance().getCatalogPassword()));
			}
			// create the first generation - just site1
			conn.execute("create persistent group sg add smallsg1");
			conn.execute("create database if not exists smalldb default persistent group sg");
			// give our user access
			conn.execute("create user 'buser'@'localhost' identified by 'buser'");
			conn.execute("grant all on smalldb.* to 'buser'@'localhost'");
			uc = new ProxyConnectionResource("buser","buser");
			uc.execute("use smalldb");
			// create our tiny schema.  first we need a range
			conn.execute("create range scaler (int) persistent group sg");
			uc.execute("create table gendata (id int, vers int, primary key (id)) range distribute on (id) using scaler");
			uc.execute("create table genname (drow int, contents varchar(64), foreign key (drow) references gendata (id)) range distribute on (drow) using scaler");
			uc.execute("create view genview as select d.id as `id`, d.vers as `version`, n.contents as `contents` from gendata d inner join genname n on d.id=n.drow");
			// first row
			int counter = -1;
			scalingInsert(uc,++counter,0,"smallsg1");
			// all our generations, in order
			String[] generations = new String[] {
					"smallsg2", "smallsg3", "smallsg1,smallsg2", "smallsg2,smallsg3", "smallsg1,smallsg3", "smallsg1,smallsg2,smallsg3", 
					"smallsg1,smallsg3","smallsg2,smallsg3","smallsg1,smallsg2","smallsg3","smallsg2","smallsg1"
			};
			int[] rows = new int[] {
					1,1,2,2,2,3,2,2,2,1,1,1
			};
			for(int i = 0; i < generations.length; i++) {
				conn.execute(String.format("alter persistent group sg add generation %s",generations[i]));
				for(int j = 0; j < rows[i]; j++) 
					scalingInsert(uc,++counter,i+1,generations[i]);
			}
			String sql = "select g.id, g.version, g.contents, cast(@dve_sitename as char(64)) from genview g order by g.id";
//			System.out.println(uc.printResults(sql));
			ResourceResponse resp = uc.execute(sql);
			for(ResultRow rr : resp.getResults()) {
				// verify that the last parameter is part of the second to last
				String contents = (String) rr.getResultColumn(3).getColumnValue();
				String site = (String) rr.getResultColumn(4).getColumnValue();
				if (contents.indexOf(site) == -1) {
					fail("Site '" + site + "' is not among allowed sites '" + contents + "'");
				}
			}
		} finally {
			smallDDL.destroy(conn);
			if (uc != null)
				uc.close();
		}
	}
	
	private static void scalingInsert(ProxyConnectionResource uc, int id, int version, String sites) throws Throwable {
		uc.execute(String.format("insert into gendata (id,vers) values (%d,%d)",id,version));
		uc.execute(String.format("insert into genname (drow, contents) values (%d,'%s')",id,sites));
	}
}
