/******************************************************************************
 * Product: ADempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 or later of the GNU General Public License as    *
 * published by the Free Software Foundation. This program is distributed in  *
 * the hope that it will be useful, but WITHOUT ANY WARRANTY; without even    *
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR        *
 * PURPOSE. See the GNU General Public License for more details.              *
 *****************************************************************************/
package org.compiere.dbPort;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * LIMIT clause vs "limit" column (AD_Workflow, AD_WF_Node, I_Workflow,
 * PP_Order_Workflow, PP_Order_Node) on PostgreSQL conversion.
 */
class ConvertPostgreSQLLimitTest {

	private Convert_PostgreSQL converter;

	@BeforeEach
	void setUp() {
		converter = new Convert_PostgreSQL();
	}

	private String convert(String sql) {
		return converter.convert(sql)[0];
	}

	@ParameterizedTest(name = "pagination: {0}")
	@CsvSource(delimiter = '|', value = {
		"SELECT a FROM t ORDER BY x LIMIT 20 OFFSET 0 | SELECT a FROM t ORDER BY x LIMIT 20 OFFSET 0",
		"SELECT a FROM t ORDER BY x LIMIT ? OFFSET ? | SELECT a FROM t ORDER BY x LIMIT ? OFFSET ?",
		"SELECT a FROM t ORDER BY x LIMIT ? | SELECT a FROM t ORDER BY x LIMIT ?",
		"SELECT a FROM t ORDER BY x OFFSET ? LIMIT ? | SELECT a FROM t ORDER BY x OFFSET ? LIMIT ?",
		"SELECT a FROM t WHERE a LIKE ? LIMIT ? OFFSET ? | SELECT a FROM t WHERE a LIKE ? LIMIT ? OFFSET ?",
		"SELECT a, COUNT(*) FROM t GROUP BY a HAVING COUNT(*) > ? LIMIT ? | SELECT a, COUNT(*) FROM t GROUP BY a HAVING COUNT(*) > ? LIMIT ?",
		"SELECT a FROM t WHERE b IN (SELECT b FROM u ORDER BY c LIMIT ?) | SELECT a FROM t WHERE b IN (SELECT b FROM u ORDER BY c LIMIT ?)",
		"SELECT COUNT(*) FROM (SELECT a FROM t ORDER BY a LIMIT ? OFFSET ?) q | SELECT COUNT(*) FROM (SELECT a FROM t ORDER BY a LIMIT ? OFFSET ?) q",
		"SELECT a FROM t ORDER BY x limit ? offset ? | SELECT a FROM t ORDER BY x LIMIT ? offset ?",
		"SELECT a FROM t ORDER BY x LIMIT ALL | SELECT a FROM t ORDER BY x LIMIT ALL",
		"SELECT a FROM t ORDER BY x limit all | SELECT a FROM t ORDER BY x LIMIT all",
		"DELETE FROM t WHERE a IN (SELECT a FROM u LIMIT ?) | DELETE FROM t WHERE a IN (SELECT a FROM u LIMIT ?)",
	})
	void restoresPaginationClause(String sql, String expected) {
		assertEquals(expected, convert(sql));
	}

	@Test
	void restoresMultilinePaginationClause() {
		assertEquals(
			"SELECT a FROM t ORDER BY x LIMIT ? OFFSET ?",
			convert("SELECT a FROM t\nORDER BY x\nLIMIT\n?\nOFFSET ?")
		);
	}

	@ParameterizedTest(name = "column: {0}")
	@CsvSource(delimiter = '|', value = {
		"SELECT Limit FROM AD_WF_Node WHERE AD_WF_Node_ID=? | SELECT \"limit\" FROM AD_WF_Node WHERE AD_WF_Node_ID=?",
		"SELECT AD_WF_Node_ID FROM AD_WF_Node WHERE Limit > ? | SELECT AD_WF_Node_ID FROM AD_WF_Node WHERE \"limit\" > ?",
		"UPDATE AD_WF_Node SET Limit = ? WHERE AD_WF_Node_ID=? | UPDATE AD_WF_Node SET \"limit\" = ? WHERE AD_WF_Node_ID=?",
		"UPDATE AD_WF_Node SET Limit=?, Name=? WHERE AD_WF_Node_ID=? | UPDATE AD_WF_Node SET \"limit\"=?, Name=? WHERE AD_WF_Node_ID=?",
		"INSERT INTO AD_WF_Node (AD_WF_Node_ID, Limit) VALUES (?, ?) | INSERT INTO AD_WF_Node (AD_WF_Node_ID, \"limit\") VALUES (?, ?)",
		"SELECT AD_WF_Node_ID FROM AD_WF_Node ORDER BY Limit | SELECT AD_WF_Node_ID FROM AD_WF_Node ORDER BY \"limit\"",
	})
	void keepsLimitColumnQuoted(String sql, String expected) {
		assertEquals(expected, convert(sql));
	}

	@ParameterizedTest(name = "column and clause: {0}")
	@CsvSource(delimiter = '|', value = {
		"SELECT AD_WF_Node_ID FROM AD_WF_Node WHERE Limit = ? LIMIT ? | SELECT AD_WF_Node_ID FROM AD_WF_Node WHERE \"limit\" = ? LIMIT ?",
		"SELECT AD_WF_Node_ID FROM AD_WF_Node ORDER BY Limit LIMIT ? | SELECT AD_WF_Node_ID FROM AD_WF_Node ORDER BY \"limit\" LIMIT ?",
		"SELECT AD_WF_Node_ID FROM AD_WF_Node n WHERE n.Limit > ? LIMIT 5 | SELECT AD_WF_Node_ID FROM AD_WF_Node n WHERE n.\"limit\" > ? LIMIT 5",
	})
	void distinguishesColumnFromClause(String sql, String expected) {
		assertEquals(expected, convert(sql));
	}

	@ParameterizedTest(name = "string literal: {0}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', value = {
		"SELECT 'credit limit' AS d FROM t LIMIT ? | SELECT 'credit limit' AS d FROM t LIMIT ?",
		"SELECT a FROM t WHERE d = 'limit 5' LIMIT ? | SELECT a FROM t WHERE d = 'limit 5' LIMIT ?",
	})
	void keepsStringLiterals(String sql, String expected) {
		assertEquals(expected, convert(sql));
	}
}
