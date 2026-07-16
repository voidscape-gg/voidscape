package com.openrsc.server.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
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
            return getStatement().executeQuery(string);
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
            return lockUntilClosed(getConnection().prepareStatement(statement));
        } catch (SQLException | RuntimeException ex) {
            connectionLock.unlock();
            throw ex;
        }
    }

    public PreparedStatement prepareStatement(final String statement, final String[] generatedColumns) throws SQLException {
        connectionLock.lock();
        try {
            return lockUntilClosed(getConnection().prepareStatement(statement, generatedColumns));
        } catch (SQLException | RuntimeException ex) {
            connectionLock.unlock();
            throw ex;
        }
    }

    public PreparedStatement prepareStatement(final String statement, final int returnKeys) throws SQLException {
        connectionLock.lock();
        try {
            return lockUntilClosed(getConnection().prepareStatement(statement, returnKeys));
        } catch (SQLException | RuntimeException ex) {
            connectionLock.unlock();
            throw ex;
        }
    }

    /**
     * The server uses one JDBC connection. Holding its reentrant lock until statement close
     * covers parameter binding, execution, generated keys, and ResultSet consumption; locking
     * only prepareStatement() allowed standalone queries to interleave into a transaction.
     */
    private PreparedStatement lockUntilClosed(final PreparedStatement delegate) {
        final AtomicBoolean released = new AtomicBoolean(false);
        return (PreparedStatement) Proxy.newProxyInstance(
            PreparedStatement.class.getClassLoader(),
            new Class<?>[]{PreparedStatement.class},
            (proxy, method, args) -> {
                if ("close".equals(method.getName())) {
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    } finally {
                        if (released.compareAndSet(false, true)) {
                            connectionLock.unlock();
                        }
                    }
                }
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException ex) {
                    throw ex.getCause();
                }
            });
    }

    protected abstract Statement getStatement();

    public abstract Connection getConnection();

    protected abstract boolean checkConnection();

    public abstract boolean isConnected();

    public abstract boolean open();

    public abstract void close();

    public abstract DatabaseType getDatabaseType();
}
