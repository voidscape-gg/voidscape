package com.openrsc.server.database.patches;

import com.openrsc.server.database.DatabaseType;
import com.openrsc.server.database.JDBCDatabaseConnection;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JDBCPatchApplierStatementCloseTest {
    public static void main(String[] args) throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        AtomicBoolean closed = new AtomicBoolean(false);
        TestConnection connection = new TestConnection(fakeConnection(executed, closed));

        JDBCPatchApplier.executePatchMarker(connection, "INSERT INTO patches VALUES ('test')");

        assertTrue(executed.get(), "patch marker statement executed");
        assertTrue(closed.get(), "patch marker statement closed");
        assertTrue(!connection.getConnectionLock().isLocked(),
            "shared JDBC lock released after patch marker write");
        assertTrue(connection.getConnectionLock().getHoldCount() == 0,
            "current thread retains no JDBC lock holds");
        System.out.println("JDBC patch marker statement lifecycle test passed");
    }

    private static Connection fakeConnection(AtomicBoolean executed, AtomicBoolean closed) {
        return (Connection) Proxy.newProxyInstance(
            JDBCPatchApplierStatementCloseTest.class.getClassLoader(),
            new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                if ("prepareStatement".equals(method.getName())) {
                    return fakePreparedStatement(executed, closed);
                }
                return defaultValue(method.getReturnType());
            });
    }

    private static PreparedStatement fakePreparedStatement(AtomicBoolean executed,
                                                              AtomicBoolean closed) {
        return (PreparedStatement) Proxy.newProxyInstance(
            JDBCPatchApplierStatementCloseTest.class.getClassLoader(),
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
            return DatabaseType.SQLITE;
        }
    }
}
