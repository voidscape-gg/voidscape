package com.openrsc.server.database;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class JDBCDatabaseConnectionLockTest {
	private static final long WORKER_WAIT_SECONDS = 10L;
	private static final long WORKER_JOIN_MILLIS = 10_000L;

	public static void main(String[] args) throws Exception {
		preparedStatementLifetimeExcludesTransactions();
		atomicBoundaryExcludesStandaloneStatementsBetweenBeginAndCommit();
		System.out.println("JDBC shared-connection transaction lock tests passed");
	}

	private static void preparedStatementLifetimeExcludesTransactions() throws Exception {
		AtomicBoolean executed = new AtomicBoolean(false);
		TestConnection connection = new TestConnection(fakeConnection(executed));
		CountDownLatch prepared = new CountDownLatch(1);
		CountDownLatch transactionAttempted = new CountDownLatch(1);
		CountDownLatch releaseStatement = new CountDownLatch(1);
		CountDownLatch transactionEntered = new CountDownLatch(1);

		Thread standalone = new Thread(() -> {
			try (PreparedStatement statement = connection.prepareStatement("standalone")) {
				prepared.countDown();
					if (!transactionAttempted.await(WORKER_WAIT_SECONDS, TimeUnit.SECONDS)) {
					throw new AssertionError("transaction thread did not attempt the lock");
				}
				statement.executeUpdate();
					if (!releaseStatement.await(WORKER_WAIT_SECONDS, TimeUnit.SECONDS)) {
					throw new AssertionError("test did not release the statement");
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}, "standalone-jdbc-test");

		Thread transaction = new Thread(() -> {
			try {
					if (!prepared.await(WORKER_WAIT_SECONDS, TimeUnit.SECONDS)) {
					throw new AssertionError("standalone statement was not prepared");
				}
				transactionAttempted.countDown();
				connection.getConnectionLock().lock();
				try {
					transactionEntered.countDown();
				} finally {
					connection.getConnectionLock().unlock();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			}
		}, "atomic-jdbc-test");

		standalone.start();
		transaction.start();
		assertTrue(prepared.await(WORKER_WAIT_SECONDS, TimeUnit.SECONDS), "statement preparation");
		assertTrue(transactionAttempted.await(WORKER_WAIT_SECONDS, TimeUnit.SECONDS), "transaction attempt");
		assertFalse(transactionEntered.await(200, TimeUnit.MILLISECONDS),
			"transaction entered while standalone statement remained open");
		releaseStatement.countDown();
		assertTrue(transactionEntered.await(WORKER_WAIT_SECONDS, TimeUnit.SECONDS),
			"transaction entered after statement close");
		standalone.join(WORKER_JOIN_MILLIS);
		transaction.join(WORKER_JOIN_MILLIS);
		assertFalse(standalone.isAlive(), "standalone test thread terminated");
		assertFalse(transaction.isAlive(), "transaction test thread terminated");
		assertTrue(executed.get(), "standalone statement executed");
	}

	private static void atomicBoundaryExcludesStandaloneStatementsBetweenBeginAndCommit()
		throws Exception {
		AtomicBoolean standaloneExecuted = new AtomicBoolean(false);
		AtomicBoolean began = new AtomicBoolean(false);
		AtomicBoolean committed = new AtomicBoolean(false);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		TestConnection connection = new TestConnection(fakeConnection(standaloneExecuted));
		CountDownLatch transactionBegan = new CountDownLatch(1);
		CountDownLatch standaloneAttempted = new CountDownLatch(1);
		CountDownLatch allowCommit = new CountDownLatch(1);
		CountDownLatch standaloneEntered = new CountDownLatch(1);

		Thread transaction = new Thread(() -> {
			try {
				boolean result = JDBCDatabase.withConnectionTransactionLock(connection, () -> {
					began.set(true);
					transactionBegan.countDown();
					try {
						if (!standaloneAttempted.await(WORKER_WAIT_SECONDS, TimeUnit.SECONDS)) {
							throw new AssertionError("standalone statement did not attempt during transaction");
						}
						if (!allowCommit.await(WORKER_WAIT_SECONDS, TimeUnit.SECONDS)) {
							throw new AssertionError("test did not allow transaction commit");
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new AssertionError("transaction test interrupted", e);
					}
					committed.set(true);
					return true;
				});
				if (!result) {
					throw new AssertionError("transaction wrapper returned false");
				}
			} catch (Throwable t) {
				failure.compareAndSet(null, t);
			}
		}, "real-atomic-boundary-test");

		Thread standalone = new Thread(() -> {
			try {
					if (!transactionBegan.await(WORKER_WAIT_SECONDS, TimeUnit.SECONDS)) {
					throw new AssertionError("transaction did not begin");
				}
				standaloneAttempted.countDown();
				try (PreparedStatement statement = connection.prepareStatement("standalone-during-tx")) {
					standaloneEntered.countDown();
					statement.executeUpdate();
				}
			} catch (Throwable t) {
				failure.compareAndSet(null, t);
			}
		}, "standalone-during-atomic-test");

		transaction.start();
		standalone.start();
		assertTrue(transactionBegan.await(WORKER_WAIT_SECONDS, TimeUnit.SECONDS), "transaction begin");
		assertTrue(standaloneAttempted.await(WORKER_WAIT_SECONDS, TimeUnit.SECONDS), "standalone attempt");
		assertFalse(standaloneEntered.await(200, TimeUnit.MILLISECONDS),
			"standalone statement entered between transaction begin and commit");
		assertTrue(began.get(), "transaction begin marker");
		assertFalse(committed.get(), "transaction not yet committed");
		allowCommit.countDown();
		assertTrue(standaloneEntered.await(WORKER_WAIT_SECONDS, TimeUnit.SECONDS),
			"standalone statement entered after transaction commit");
		transaction.join(WORKER_JOIN_MILLIS);
		standalone.join(WORKER_JOIN_MILLIS);
		assertFalse(transaction.isAlive(), "transaction boundary thread terminated");
		assertFalse(standalone.isAlive(), "standalone boundary thread terminated");
		if (failure.get() != null) {
			throw new AssertionError("transaction boundary worker failed", failure.get());
		}
		assertTrue(committed.get(), "transaction committed");
		assertTrue(standaloneExecuted.get(), "standalone statement executed after commit");
	}

	private static Connection fakeConnection(AtomicBoolean executed) {
		return (Connection) Proxy.newProxyInstance(JDBCDatabaseConnectionLockTest.class.getClassLoader(),
			new Class<?>[]{Connection.class}, (proxy, method, args) -> {
				if ("prepareStatement".equals(method.getName())) {
					return fakePreparedStatement(executed);
				}
				return defaultValue(method.getReturnType());
			});
	}

	private static PreparedStatement fakePreparedStatement(AtomicBoolean executed) {
		AtomicBoolean closed = new AtomicBoolean(false);
		return (PreparedStatement) Proxy.newProxyInstance(
			JDBCDatabaseConnectionLockTest.class.getClassLoader(),
			new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
				if ("executeUpdate".equals(method.getName())) {
					executed.set(true);
					return 1;
				}
				if ("close".equals(method.getName())) {
					closed.set(true);
					return null;
				}
				if ("isClosed".equals(method.getName())) {
					return closed.get();
				}
				return defaultValue(method.getReturnType());
			});
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) return null;
		if (type == boolean.class) return false;
		if (type == byte.class) return (byte) 0;
		if (type == short.class) return (short) 0;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == float.class) return 0.0F;
		if (type == double.class) return 0.0D;
		if (type == char.class) return '\0';
		return null;
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertFalse(boolean value, String message) {
		assertTrue(!value, message);
	}

	private static final class TestConnection extends JDBCDatabaseConnection {
		private final Connection connection;

		private TestConnection(Connection connection) {
			this.connection = connection;
		}

		@Override
		protected Statement getStatement() {
			return null;
		}

		@Override
		public Connection getConnection() {
			return connection;
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
			return null;
		}
	}
}
