package com.tesora.dve.tools;

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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.tesora.dve.common.PEXmlUtils;
import com.tesora.dve.exceptions.PEException;
import com.tesora.dve.sql.SchemaTest;
import com.tesora.dve.sql.statement.dml.InsertIntoValuesStatement;
import com.tesora.dve.sql.statement.dml.SelectStatement;
import com.tesora.dve.sql.statement.session.UseStatement;
import com.tesora.dve.sql.util.DBHelperConnectionResource;
import com.tesora.dve.sql.util.NativeDDL;
import com.tesora.dve.sql.util.TestResource;
import com.tesora.dve.standalone.PETest;
import com.tesora.dve.tools.analyzer.jaxb.DbAnalyzerCorpus;
import com.tesora.dve.tools.analyzer.jaxb.HasStatement;
import com.tesora.dve.tools.analyzer.jaxb.StatementNonDMLType;
import com.tesora.dve.tools.analyzer.jaxb.StatementPopulationType;

public class DVEAnalyzerCLITest extends SchemaTest {

	private static final String TEST_DATABASE_NAME = "analyzertestdb";
	private static final String TEST_TABLE_NAME = "A";
	private static final NativeDDL nativeDDL = new NativeDDL(TEST_DATABASE_NAME);
	private static DBHelperConnectionResource nativeConnection;
	private static TestResource nativeResource;

	@BeforeClass
	public static void setUp() throws Throwable {
		PETest.projectSetup(nativeDDL);
		nativeConnection = new DBHelperConnectionResource();
		nativeResource = new TestResource(nativeConnection, nativeDDL);
		nativeResource.create();

		nativeConnection.execute("USE " + TEST_DATABASE_NAME);
		nativeConnection.execute("CREATE TABLE IF NOT EXISTS " + TEST_TABLE_NAME + " ("
				+ "id INT NOT NULL AUTO_INCREMENT,"
				+ "name TEXT NOT NULL,"
				+ "PRIMARY KEY (id))");
		nativeConnection.execute("INSERT INTO " + TEST_TABLE_NAME + " VALUES (1, 'ParElastic')");
	}

	@AfterClass
	public static void tearDown() throws Throwable {
		nativeResource.destroy();
		nativeConnection.disconnect();
	}

	/**
	 * Generate templates while testing various template generator methods.
	 */
	private static void testTemplateGenerators(final DVEClientToolTestConsole console, final String cardinalityCutoff, final String frequencyCorpus,
			final String baseTemplate) {
		console.executeCommand("generate broadcast templates");
		console.executeCommand("generate random templates");

		console.executeCommand("generate basic templates " + cardinalityCutoff);
		console.executeCommand("generate basic templates " + cardinalityCutoff + " " + baseTemplate);

		console.executeCommand("generate guided templates " + cardinalityCutoff + " false false " + frequencyCorpus);
		console.executeCommand("generate guided templates " + cardinalityCutoff + " false false " + baseTemplate);
		console.executeCommand("generate guided templates " + cardinalityCutoff + " false false " + frequencyCorpus + " " + baseTemplate);

		console.executeCommand("generate templates false false " + frequencyCorpus);
		console.executeCommand("generate templates false false " + baseTemplate);
		console.executeCommand("generate templates false false " + frequencyCorpus + " " + baseTemplate);

	}

	private static void assertStatementCounts(final String frequencyCorpus, final Map<String, Integer> expectedStatementCounts) throws PEException {
		final DbAnalyzerCorpus frequencyAnalysis = PEXmlUtils.unmarshalJAXB(
				new File(frequencyCorpus),
				DbAnalyzerCorpus.class);

		final Set<String> expectedStmtTypes = expectedStatementCounts.keySet();
		final Map<String, Integer> actualStatementCounts = new HashMap<String, Integer>(expectedStatementCounts.size());
		for (final StatementNonDMLType statement : frequencyAnalysis.getNonDml()) {
			bumpStatementKindCount(statement, expectedStmtTypes, actualStatementCounts);
		}
		for (final StatementPopulationType statement : frequencyAnalysis.getPopulation()) {
			bumpStatementKindCount(statement, expectedStmtTypes, actualStatementCounts);
		}
		
		for (final String statementKind : expectedStatementCounts.keySet()) {
			final Integer expected = expectedStatementCounts.get(statementKind);
			final Integer actual = actualStatementCounts.get(statementKind);
			assertEquals(expected, actual);
		}
	}
	
	private static void bumpStatementKindCount(final StatementNonDMLType statement, final Set<String> expectedStmtTypes, final Map<String, Integer> counter) {
		bumpStatementKindCount(StatementNonDMLType.class.getSimpleName(), statement.getFreq(), expectedStmtTypes, counter);
	}
	
	private static void bumpStatementKindCount(final StatementPopulationType statement, final Set<String> expectedStmtTypes, final Map<String, Integer> counter) {
		final String statementKind = statement.getKind();
		bumpStatementKindCount(statementKind, statement.getFreq(), expectedStmtTypes, counter);
	}
	
	private static void bumpStatementKindCount(final String key, final int freq, final Set<String> expectedStmtTypes, final Map<String, Integer> counter) {
		if (expectedStmtTypes.contains(key)) {
			Integer count = counter.get(key);
			final Integer stmtFreq = Integer.valueOf(freq);
			if (count == null) {
				count = stmtFreq;
			} else {
				count += stmtFreq;
			}
			counter.put(key, count);
		}
	}

	@SuppressWarnings("resource")
	@Test
	public void testDynamicAnalysisStack() throws Throwable {
		final DVEClientToolTestConsole console = new DVEClientToolTestConsole(new DVEAnalyzerCLI(null));
		final String staticReport = getTempFile("static", null);
		final String frequencyCorpus = getTempFile("corpus", null);
		final String template = getTempFile("template", null);
		final String baseTemplate = getTempFile("baseTemplate", Arrays.asList(
				"<?xml version=\"1.0\"?>"
				+ "<template name=\"allbroadcast\">"
				+ "<tabletemplate match=\".*\" model=\"Broadcast\" />"
				+ "</template>"
				));
		final String generalLog = getTempFile("general",
				Arrays.asList(
						"		1 Connect	" + nativeConnection.getUserid() + "@localhost on " + TEST_DATABASE_NAME,
						"		1 Query	SELECT * FROM " + TEST_TABLE_NAME,
						"		1 Quit	"
						)
				);
		final String dynamicLog = getTempFile("dynamic", null);

		console.executeCommand("connect " + getConnectionString());
		console.executeCommand("set database " + TEST_DATABASE_NAME);
		console.executeCommand("static true");
		console.executeCommand("save report " + staticReport);
		console.executeCommand("frequencies mysql " + frequencyCorpus + " "
				+ generalLog + " " + generalLog + " " + generalLog);

		testTemplateGenerators(console, "10", frequencyCorpus, baseTemplate);

		console.executeCommand("save template " + TEST_DATABASE_NAME + " " + template);
		console.executeCommand("dynamic mysql " + generalLog + " " + dynamicLog);

		console.assertValidConsoleOutput();

		final Map<String, Integer> expectedStatementCounts = new HashMap<String, Integer>();
		expectedStatementCounts.put(SelectStatement.class.getSimpleName(), 3);
		assertStatementCounts(frequencyCorpus, expectedStatementCounts);
	}
	
	@Test
	public void testCorpusMerging() throws Throwable {
		final DVEClientToolTestConsole console = new DVEClientToolTestConsole(new DVEAnalyzerCLI(null));
		final String outputCorpus = getTempFile("output_corpus", null);
		final String inputCorpus1 = getTempFile("input_corpus1",
				Arrays.asList(
						"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>",
						"<dbAnalyzerCorpus description=\"FileSource using Type:mysql File:corpus1\">",
						"    <population xsi:type=\"StatementNonInsertType\" literalCount=\"1\" db=\"magento_xl\" kind=\"SelectStatement\" freq=\"71060\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">",
						"        <stmt>SELECT `salesrule_customer_group`.`customer_group_id` FROM `salesrule_customer_group` WHERE (rule_id = '5')",
						"    </stmt>",
						"    </population>",
						"    <population xsi:type=\"StatementInsertIntoValuesType\" db=\"magento_xl\" kind=\"InsertIntoValuesStatement\" freq=\"1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">",
						"        <insertPrefix>INSERT INTO `eav_entity_store` (`eav_entity_store`.`entity_type_id`,`eav_entity_store`.`store_id`,`eav_entity_store`.`increment_prefix`)</insertPrefix>",
						"        <colWidth>3</colWidth>",
						"        <population tupleCount=\"1\" tuplePop=\"1\"/>",
						"    </population>",
						"</dbAnalyzerCorpus>"
						)
				);
		final String inputCorpus2 = getTempFile("input_corpus2",
				Arrays.asList(
						"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>",
						"<dbAnalyzerCorpus description=\"FileSource using Type:mysql File:corpus2\">",
						"    <population xsi:type=\"StatementNonInsertType\" literalCount=\"1\" db=\"magento_xl\" kind=\"SelectStatement\" freq=\"71060\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">",
						"        <stmt>SELECT `salesrule_customer_group`.`customer_group_id` FROM `salesrule_customer_group` WHERE (rule_id = '5')",
						"    </stmt>",
						"    </population>",
						"    <population xsi:type=\"StatementInsertIntoValuesType\" db=\"magento_xl\" kind=\"InsertIntoValuesStatement\" freq=\"874\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">",
						"        <insertPrefix>INSERT INTO `enterprise_customer_sales_flat_quote_address` (`enterprise_customer_sales_flat_quote_address`.`entity_id`)</insertPrefix>",
						"        <colWidth>1</colWidth>",
						"        <population tupleCount=\"1\" tuplePop=\"874\"/>",
						"    </population>",
						"    <nonDml freq=\"6752\">",
						"        <stmt>use magento_xl</stmt>",
						"    </nonDml>",
						"</dbAnalyzerCorpus>"
						)
				);
		
		console.executeCommand("merge corpus " + outputCorpus + " " + inputCorpus1 + " " + inputCorpus2, false);

		final Map<String, Integer> expectedStatementCounts = new HashMap<String, Integer>();
		expectedStatementCounts.put(SelectStatement.class.getSimpleName(), 142120);
		expectedStatementCounts.put(InsertIntoValuesStatement.class.getSimpleName(), 875);
		expectedStatementCounts.put(StatementNonDMLType.class.getSimpleName(), 6752);
		assertStatementCounts(outputCorpus, expectedStatementCounts);
	}

	private String getTempFile(final String name, final List<String> lines) throws IOException {
		final File tempFile = File.createTempFile("PEDBAnalyzerTest_" + name, ".tmp");

		if (lines != null) {
			FileUtils.writeLines(tempFile, lines);
		}

		return tempFile.getCanonicalPath();
	}

	private String getConnectionString() throws Throwable {
		return nativeConnection.getUrl() + " " + nativeConnection.getUserid() + " " + nativeConnection.getPassword();
	}

}
