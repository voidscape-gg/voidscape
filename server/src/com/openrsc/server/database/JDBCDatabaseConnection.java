package com.openrsc.server.database;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.locks.ReentrantLock;

public abstract class JDBCDatabaseConnection {
    /**
     * Guards the single shared JDBC connection. GameDatabase.atomically() holds this for
     * the whole BEGIN..COMMIT/ROLLBACK span so concurrent threads (login/save, auction,
     * game logger) can no longer interleave statements into an open transaction — the old
     * per-statement synchronized allowed that, and a colliding ROLLBACK could destroy an
     * in-flight player save.
     */
    private final ReentrantLock connectionLock = new ReentrantLock();

    public ReentrantLock getConnectionLock() {
        return connectionLock;
    }

    public int executeUpdate(final String string) throws SQLException {
        connectionLock.lock();
        try {
            return getStatement().executeUpdate(string);
        } finally {
            connectionLock.unlock();
        }
    }

    public ResultSet executeQuery(final String string) throws SQLException {
        connectionLock.lock();
        try {
            return lockResultSet(getStatement().executeQuery(string));
        } finally {
            connectionLock.unlock();
        }
    }

    public void execute(final String string) throws SQLException {
        connectionLock.lock();
        try {
            getStatement().execute(string);
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Create a Prepared Statement
     *
     * @param statement The MySQL query to run represented as a java.lang.String
     * @return The MySQL query to run represented as a java.sql.PreparedStatement
     * @throws SQLException if there was an error when preparing the statement
     */
    public PreparedStatement prepareStatement(final String statement) throws SQLException {
        connectionLock.lock();
        try {
            return lockPreparedStatement(getConnection().prepareStatement(statement));
        } finally {
            connectionLock.unlock();
        }
    }

    public PreparedStatement prepareStatement(final String statement, final String[] generatedColumns) throws SQLException {
        connectionLock.lock();
        try {
            return lockPreparedStatement(getConnection().prepareStatement(statement, generatedColumns));
        } finally {
            connectionLock.unlock();
        }
    }

    public PreparedStatement prepareStatement(final String statement, final int returnKeys) throws SQLException {
        connectionLock.lock();
        try {
            return lockPreparedStatement(getConnection().prepareStatement(statement, returnKeys));
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * A statement may be prepared immediately before another thread begins an atomic
     * transaction. Locking only prepareStatement() would still let that old statement
     * execute on the shared JDBC connection between BEGIN and COMMIT. Proxy every JDBC
     * call so pre-created statements and lazy ResultSets must acquire the same reentrant
     * connection lock. Calls made by the transaction owner remain reentrant.
     */
    private PreparedStatement lockPreparedStatement(final PreparedStatement statement) {
        return lockedJdbcProxy(statement, PreparedStatement.class);
    }

    private ResultSet lockResultSet(final ResultSet resultSet) {
        return lockedJdbcProxy(resultSet, ResultSet.class);
    }

    private <T> T lockedJdbcProxy(final T delegate, final Class<T> contract) {
        if (delegate == null) {
            return null;
        }
        if (Proxy.isProxyClass(delegate.getClass())) {
            final InvocationHandler existing = Proxy.getInvocationHandler(delegate);
            if (existing instanceof LockedJdbcInvocationHandler) {
                return contract.cast(delegate);
            }
        }
        return contract.cast(Proxy.newProxyInstance(
            JDBCDatabaseConnection.class.getClassLoader(),
            new Class<?>[] { contract },
            new LockedJdbcInvocationHandler(delegate)));
    }

    private final class LockedJdbcInvocationHandler implements InvocationHandler {
        private final Object delegate;

        private LockedJdbcInvocationHandler(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            connectionLock.lock();
            try {
                final Object result;
                try {
                    result = method.invoke(delegate, args);
                } catch (InvocationTargetException ex) {
                    throw ex.getCause();
                }
                if (result instanceof ResultSet) {
                    return lockResultSet((ResultSet) result);
                }
                return result;
            } finally {
                connectionLock.unlock();
            }
        }
    }

    protected abstract Statement getStatement();

    public abstract Connection getConnection();

    protected abstract boolean checkConnection();

    public abstract boolean isConnected();

    public abstract boolean open();

    public abstract void close();

    public abstract DatabaseType getDatabaseType();
}
