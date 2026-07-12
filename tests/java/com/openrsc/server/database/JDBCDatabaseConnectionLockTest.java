package com.openrsc.server.database;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Verifies pre-created JDBC objects cannot interleave into an atomic transaction. */
public final class JDBCDatabaseConnectionLockTest {
	private JDBCDatabaseConnectionLockTest() {
	}

	public static void main(String[] args) throws Exception {
		testPreparedExecutionWaitsForTransactionOwner();
		testLazyResultSetCallWaitsForTransactionOwner();
		testTransactionOwnerCanReenterStatementProxy();
		testRealSqliteRoundTripAndRollback();
		System.out.println("JDBC transaction connection-lock tests passed.");
	}

	private static void testPreparedExecutionWaitsForTransactionOwner() throws Exception {
		FakeConnection connection = new FakeConnection();
		PreparedStatement prepared = connection.prepareStatement("UPDATE test SET value=1");
		AtomicReference<Throwable> failure = new AtomicReference<>();
		CountDownLatch started = new CountDownLatch(1);

		connection.getConnectionLock().lock();
		Thread worker = new Thread(() -> {
			started.countDown();
			try {
				prepared.executeUpdate();
			} catch (Throwable ex) {
				failure.set(ex);
			}
		}, "pre-created-statement-worker");
		try {
			worker.start();
			started.await();
			awaitQueued(connection, worker);
			check(!connection.executeCalled.get(),
				"statement prepared before BEGIN cannot execute while transaction lock is held");
		} finally {
			connection.getConnectionLock().unlock();
		}
		worker.join(2000L);
		check(!worker.isAlive(), "statement proceeds after transaction lock release");
		check(failure.get() == null, "statement worker completes without error");
		check(connection.executeCalled.get(), "delegate executes after transaction boundary");
		prepared.close();
	}

	private static void testLazyResultSetCallWaitsForTransactionOwner() throws Exception {
		FakeConnection connection = new FakeConnection();
		PreparedStatement prepared = connection.prepareStatement("SELECT value FROM test");
		ResultSet result = prepared.executeQuery();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		CountDownLatch started = new CountDownLatch(1);

		connection.getConnectionLock().lock();
		Thread worker = new Thread(() -> {
			started.countDown();
			try {
				result.next();
			} catch (Throwable ex) {
				failure.set(ex);
			}
		}, "lazy-result-set-worker");
		try {
			worker.start();
			started.await();
			awaitQueued(connection, worker);
			check(!connection.resultNextCalled.get(),
				"lazy ResultSet access cannot run inside another transaction");
		} finally {
			connection.getConnectionLock().unlock();
		}
		worker.join(2000L);
		check(!worker.isAlive(), "ResultSet proceeds after transaction lock release");
		check(failure.get() == null, "ResultSet worker completes without error");
		check(connection.resultNextCalled.get(), "ResultSet delegate runs after boundary");
		result.close();
		prepared.close();
	}

	private static void testTransactionOwnerCanReenterStatementProxy() throws Exception {
		FakeConnection connection = new FakeConnection();
		PreparedStatement prepared = connection.prepareStatement("UPDATE test SET value=2");
		connection.getConnectionLock().lock();
		try {
			prepared.setInt(1, 2);
			prepared.executeUpdate();
		} finally {
			connection.getConnectionLock().unlock();
		}
		check(connection.executeCalled.get(), "transaction owner reenters statement proxy without deadlock");
		prepared.close();
	}

	private static void testRealSqliteRoundTripAndRollback() throws Exception {
		Class.forName("org.sqlite.JDBC");
		RealSqliteConnection connection = new RealSqliteConnection();
		try {
			connection.execute("CREATE TABLE card_test (id INTEGER PRIMARY KEY, value TEXT)");
			connection.getConnectionLock().lock();
			try {
				connection.execute("BEGIN TRANSACTION");
				try (PreparedStatement insert = connection.prepareStatement(
					"INSERT INTO card_test (id, value) VALUES (?, ?)")) {
					insert.setInt(1, 1);
					insert.setString(2, "tentative");
					check(insert.executeUpdate() == 1, "real SQLite insert runs through locked proxy");
				}
				connection.execute("ROLLBACK");
			} finally {
				connection.getConnectionLock().unlock();
			}
			try (PreparedStatement select = connection.prepareStatement(
				"SELECT COUNT(*) FROM card_test"); ResultSet result = select.executeQuery()) {
				check(result.next() && result.getInt(1) == 0,
					"real SQLite rollback remains intact through statement/result proxies");
			}
		} finally {
			connection.close();
		}
	}

	private static void awaitQueued(FakeConnection connection, Thread worker) {
		long deadline = System.nanoTime() + 2_000_000_000L;
		while (!connection.getConnectionLock().hasQueuedThread(worker) && System.nanoTime() < deadline) {
			Thread.yield();
		}
		check(connection.getConnectionLock().hasQueuedThread(worker),
			"worker reached the shared connection-lock boundary");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class FakeConnection extends JDBCDatabaseConnection {
		private final AtomicBoolean executeCalled = new AtomicBoolean();
		private final AtomicBoolean resultNextCalled = new AtomicBoolean();
		private final ResultSet rawResultSet;
		private final PreparedStatement rawPreparedStatement;
		private final Statement rawStatement;
		private final Connection rawConnection;

		private FakeConnection() {
			rawResultSet = (ResultSet) Proxy.newProxyInstance(
				JDBCDatabaseConnectionLockTest.class.getClassLoader(),
				new Class<?>[] { ResultSet.class },
				(proxy, method, args) -> {
					if ("next".equals(method.getName())) {
						resultNextCalled.set(true);
						return false;
					}
					return defaultValue(method.getReturnType());
				});
			rawPreparedStatement = (PreparedStatement) Proxy.newProxyInstance(
				JDBCDatabaseConnectionLockTest.class.getClassLoader(),
				new Class<?>[] { PreparedStatement.class },
				(proxy, method, args) -> {
					if ("executeUpdate".equals(method.getName())) {
						executeCalled.set(true);
						return 1;
					}
					if ("executeQuery".equals(method.getName()) || "getResultSet".equals(method.getName())
						|| "getGeneratedKeys".equals(method.getName())) {
						return rawResultSet;
					}
					return defaultValue(method.getReturnType());
				});
			rawStatement = (Statement) Proxy.newProxyInstance(
				JDBCDatabaseConnectionLockTest.class.getClassLoader(),
				new Class<?>[] { Statement.class },
				(proxy, method, args) -> defaultValue(method.getReturnType()));
			rawConnection = (Connection) Proxy.newProxyInstance(
				JDBCDatabaseConnectionLockTest.class.getClassLoader(),
				new Class<?>[] { Connection.class },
				(proxy, method, args) -> {
					if ("prepareStatement".equals(method.getName())) {
						return rawPreparedStatement;
					}
					return defaultValue(method.getReturnType());
				});
		}

		@Override
		protected Statement getStatement() {
			return rawStatement;
		}

		@Override
		public Connection getConnection() {
			return rawConnection;
		}

		@Override
		protected boolean checkConnection() {
			return true;
		}

		@Override
		public boolean isConnected() {
			return true;
		}

		@Override
		public boolean open() {
			return true;
		}

		@Override
		public void close() {
		}

		@Override
		public DatabaseType getDatabaseType() {
			return DatabaseType.SQLITE;
		}
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

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) return null;
		if (type == boolean.class) return false;
		if (type == byte.class) return (byte) 0;
		if (type == short.class) return (short) 0;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == float.class) return 0F;
		if (type == double.class) return 0D;
		if (type == char.class) return (char) 0;
		return null;
	}
}
