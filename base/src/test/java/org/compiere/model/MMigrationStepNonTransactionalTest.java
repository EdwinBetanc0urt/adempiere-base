package org.compiere.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Which SQL migration steps must run outside the migration transaction (PostgreSQL cannot run them in a
 * transaction block); every other step, DDL included, runs in the migration transaction.
 */
class MMigrationStepNonTransactionalTest {

	@ParameterizedTest(name = "{0} => {1}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', value = {
		//	Must run outside a transaction block
		"CREATE INDEX CONCURRENTLY IF NOT EXISTS m_costdetail_idx ON m_costdetail (m_product_id)|true",
		"create unique index concurrently x_uk on x (a)|true",
		"DROP INDEX CONCURRENTLY IF EXISTS x_idx|true",
		"REINDEX INDEX CONCURRENTLY x_idx|true",
		"VACUUM ANALYZE m_costdetail|true",
		"vacuum|true",
		"CREATE DATABASE test|true",
		"DROP TABLESPACE ts|true",
		"ALTER SYSTEM SET work_mem = '64MB'|true",
		"-- comment first\\nCREATE INDEX\\n  CONCURRENTLY x_idx ON x (a)|true",
		//	Run in the migration transaction (DDL is transactional in PostgreSQL)
		"CREATE UNIQUE INDEX IF NOT EXISTS ad_assignedwidget_instance_report_uk ON AD_AssignedWidget (AD_DashboardInstance_ID, Report_ID)|false",
		"ALTER TABLE c_order ADD COLUMN IF NOT EXISTS x NUMERIC(10)|false",
		"DROP VIEW IF EXISTS SP027_rv_invoicecreatefrom|false",
		"CREATE OR REPLACE VIEW v AS SELECT 1|false",
		"UPDATE AD_Column SET IsUpdateable = 'Y' WHERE AD_Column_ID = 1|false",
		"INSERT INTO t (a) VALUES (1)|false",
		"DO $$ BEGIN RAISE NOTICE 'x'; END $$|false",
		"ANALYZE m_costdetail|false",
		"ALTER DATABASE adempiere SET search_path TO adempiere|false",
		"-- VACUUM in a comment\\nUPDATE t SET a = 1|false",
	})
	void classifiesSingleStatement(String sql, boolean expected) {
		assertEquals(expected, MMigrationStep.isNonTransactional(List.of(sql.replace("\\n", "\n"))));
	}

	@ParameterizedTest(name = "{0} => {1}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', value = {
		//	One statement that cannot run in a transaction moves the whole step out of it
		"UPDATE t SET a = 1;CREATE INDEX CONCURRENTLY x_idx ON t (a)|true",
		"DROP VIEW IF EXISTS v;CREATE OR REPLACE VIEW v AS SELECT 1|false",
	})
	void classifiesStepWithSeveralStatements(String sql, boolean expected) {
		assertEquals(expected, MMigrationStep.isNonTransactional(List.of(sql.split(";"))));
	}
}
