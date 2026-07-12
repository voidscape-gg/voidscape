package com.openrsc.server.database;

import com.openrsc.server.database.struct.PortalCommerceEntitlement;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Exercises the production paid-card SQL against the checked-in SQLite schema. */
public final class PortalCommerceLedgerSqliteTest {
	private static final int SUBSCRIPTION_CARD_CATALOG_ID = 1602;
	private static final long CLAIMED_AT_MS = 1_700_000_123_456L;

	private PortalCommerceLedgerSqliteTest() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			throw new IllegalArgumentException("Expected SQLite commerce migration path");
		}
		Class.forName("org.sqlite.JDBC");
		RealSqliteConnection connection = new RealSqliteConnection();
		try {
			applyMigration(connection, Paths.get(args[0]));
			seedCommerceRows(connection);
			testFifoReadAndAccountIsolation(connection);
			testClaimRollbackAndConditionalUpdate(connection);
			testClaimCommitAndExactFields(connection);
		} finally {
			connection.close();
		}
		System.out.println("Portal commerce SQLite ledger tests passed.");
	}

	private static void testFifoReadAndAccountIsolation(RealSqliteConnection connection) {
		PortalCommerceEntitlement oldest = PortalCommerceLedger.loadOldestPending(connection, 42);
		check(oldest != null && oldest.id == 20L,
			"FIFO orders by created_at_ms then entitlement id");
		check(oldest.accountId == 42 && "pending".equals(oldest.state),
			"FIFO returns a pending row owned by the requested account");
		PortalCommerceEntitlement other = PortalCommerceLedger.loadOldestPending(connection, 43);
		check(other != null && other.id == 40L,
			"pending lookup cannot cross linked web accounts");
		check(PortalCommerceLedger.loadOldestPending(connection, 0) == null,
			"invalid account id cannot query a paid entitlement");
	}

	private static void testClaimRollbackAndConditionalUpdate(RealSqliteConnection connection)
		throws Exception {
		connection.getConnectionLock().lock();
		try {
			connection.execute("BEGIN TRANSACTION");
			check(PortalCommerceLedger.claimPending(connection, 20L, 99, 700,
				SUBSCRIPTION_CARD_CATALOG_ID, CLAIMED_AT_MS) == 0,
				"wrong account cannot claim an entitlement");
			check(PortalCommerceLedger.claimPending(connection, 20L, 42, 700,
				SUBSCRIPTION_CARD_CATALOG_ID, CLAIMED_AT_MS) == 1,
				"matching account conditionally claims one pending row");
			check("claimed".equals(PortalCommerceLedger.loadById(connection, 20L).state),
				"claim is visible inside its owning transaction");
			connection.execute("ROLLBACK");
		} finally {
			connection.getConnectionLock().unlock();
		}

		PortalCommerceEntitlement rolledBack = PortalCommerceLedger.loadById(connection, 20L);
		check("pending".equals(rolledBack.state)
			&& rolledBack.claimedPlayerId == 0
			&& rolledBack.claimedItemId == 0L
			&& rolledBack.claimedAtMs == 0L,
			"rollback restores the entitlement's fully unclaimed state");
	}

	private static void testClaimCommitAndExactFields(RealSqliteConnection connection)
		throws Exception {
		connection.getConnectionLock().lock();
		try {
			connection.execute("BEGIN TRANSACTION");
			check(PortalCommerceLedger.claimPending(connection, 20L, 42, 701,
				SUBSCRIPTION_CARD_CATALOG_ID, CLAIMED_AT_MS) == 1,
				"pending row is claimed exactly once");
			connection.execute("COMMIT");
		} finally {
			connection.getConnectionLock().unlock();
		}

		PortalCommerceEntitlement claimed = PortalCommerceLedger.loadById(connection, 20L);
		check("claimed".equals(claimed.state), "committed entitlement is claimed");
		check(claimed.claimedPlayerId == 701,
			"committed entitlement records the collecting character");
		check(claimed.claimedItemId == SUBSCRIPTION_CARD_CATALOG_ID,
			"claimed_item_id stores catalog item 1602, not the unique inventory instance");
		check(claimed.claimedAtMs == CLAIMED_AT_MS && claimed.updatedAtMs == CLAIMED_AT_MS,
			"claim and update timestamps are committed together");
		check(PortalCommerceLedger.claimPending(connection, 20L, 42, 702,
			SUBSCRIPTION_CARD_CATALOG_ID, CLAIMED_AT_MS + 1L) == 0,
			"already claimed row cannot be claimed again");
		check(PortalCommerceLedger.loadOldestPending(connection, 42).id == 30L,
			"FIFO advances to the next row after a committed claim");
	}

	private static void applyMigration(RealSqliteConnection connection, Path migration)
		throws Exception {
		String sql = new String(Files.readAllBytes(migration), StandardCharsets.UTF_8);
		for (String statement : sql.split(";")) {
			String trimmed = statement.trim();
			if (!trimmed.isEmpty()) connection.execute(trimmed);
		}
	}

	private static void seedCommerceRows(RealSqliteConnection connection) throws SQLException {
		connection.execute("INSERT INTO portal_commerce_checkout_intents "
			+ "(intent_id,account_id,provider,package_id,expected_currency,expected_amount_minor,"
			+ "basket_id,status,created_at_ms,expires_at_ms,updated_at_ms) VALUES "
			+ "('intent-42',42,'tebex','subscription-card','USD',499,'basket-42','completed',1,999999,1),"
			+ "('intent-43',43,'tebex','subscription-card','USD',499,'basket-43','completed',1,999999,1)");
		connection.execute("INSERT INTO portal_commerce_payments "
			+ "(id,provider,transaction_id,intent_id,account_id,basket_id,package_id,quantity,"
			+ "payment_sequence,currency,expected_amount_minor,product_paid_amount_minor,"
			+ "transaction_paid_amount_minor,tax_and_adjustments_minor,status,completed_at_ms,updated_at_ms) VALUES "
			+ "(100,'tebex','tx-42','intent-42',42,'basket-42','subscription-card',1,'oneoff',"
			+ "'USD',499,499,499,0,'complete',10,10),"
			+ "(101,'tebex','tx-43','intent-43',43,'basket-43','subscription-card',1,'oneoff',"
			+ "'USD',499,499,499,0,'complete',10,10)");
		connection.execute("INSERT INTO portal_commerce_entitlements "
			+ "(id,payment_id,account_id,provider,transaction_id,line_key,package_id,unit_index,"
			+ "state,created_at_ms,updated_at_ms) VALUES "
			+ "(30,100,42,'tebex','tx-42','line-30','subscription-card',1,'pending',100,100),"
			+ "(20,100,42,'tebex','tx-42','line-20','subscription-card',1,'pending',100,100),"
			+ "(10,100,42,'tebex','tx-42','line-10','subscription-card',1,'pending',200,200),"
			+ "(40,101,43,'tebex','tx-43','line-40','subscription-card',1,'pending',50,50)");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static final class RealSqliteConnection extends JDBCDatabaseConnection {
		private final Connection connection;

		private RealSqliteConnection() throws SQLException {
			connection = DriverManager.getConnection("jdbc:sqlite::memory:");
		}

		@Override
		protected Statement getStatement() {
			try {
				return connection.createStatement();
			} catch (SQLException ex) {
				throw new IllegalStateException(ex);
			}
		}

		@Override
		public Connection getConnection() {
			return connection;
		}

		@Override
		protected boolean checkConnection() {
			return isConnected();
		}

		@Override
		public boolean isConnected() {
			try {
				return !connection.isClosed();
			} catch (SQLException ex) {
				return false;
			}
		}

		@Override
		public boolean open() {
			return isConnected();
		}

		@Override
		public void close() {
			try {
				connection.close();
			} catch (SQLException ex) {
				throw new IllegalStateException(ex);
			}
		}

		@Override
		public DatabaseType getDatabaseType() {
			return DatabaseType.SQLITE;
		}
	}
}
