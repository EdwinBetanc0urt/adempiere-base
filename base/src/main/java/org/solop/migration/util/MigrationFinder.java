/**
 * 
 */
package org.solop.migration.util;


import org.adempiere.exceptions.AdempiereException;
import org.compiere.util.DB;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *    @author Yamel Senih, yamel.senih@solopsoftware.com, Solop <a href="http://www.solopsoftware.com">solopsoftware.com</a>
 */
public class MigrationFinder {
    //	Read from the importer threads (files are loaded in parallel): it must be thread safe
    private final Set<String> currentMigrations = ConcurrentHashMap.newKeySet();

    public MigrationFinder() {
        loadCurrentMigrations();
    }

    public static MigrationFinder newInstance() {
        return new MigrationFinder();
    }

    private String getMigrationKey(String entityType, String releaseNo, String name, int sequence) {
        return entityType + "|" + Optional.ofNullable(releaseNo).orElse("") + "|" + name + "|" + sequence;
    }

    public boolean existsMigration(String entityType, String releaseNo, String name, int sequence) {
        return currentMigrations.contains(getMigrationKey(entityType, releaseNo, name, sequence));
    }

    /**
     * Load the keys of the registered migrations with one query: loading each migration in a parallel stream into a
     * not synchronized map could lose keys, and a lost key makes the importer register the migration again (duplicate)
     */
    private void loadCurrentMigrations() {
        String sql = "SELECT EntityType, ReleaseNo, Name, SeqNo FROM AD_Migration WHERE IsActive = 'Y'";
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            statement = DB.prepareStatement(sql, null);
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                currentMigrations.add(getMigrationKey(resultSet.getString(1), resultSet.getString(2),
                        Optional.ofNullable(resultSet.getString(3)).orElse("").trim(), resultSet.getInt(4)));
            }
        } catch (Exception e) {
            throw new AdempiereException(e);
        } finally {
            DB.close(resultSet, statement);
        }
    }
}