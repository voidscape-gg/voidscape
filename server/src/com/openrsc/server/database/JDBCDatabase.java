package com.openrsc.server.database;

import com.openrsc.server.Server;
import com.openrsc.server.util.checked.CheckedConsumer;
import com.openrsc.server.util.checked.CheckedFunction;
import com.openrsc.server.util.checked.CheckedRunnable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class JDBCDatabase extends GameDatabase {

    public JDBCDatabase(Server server) {
        super(server);
    }

    public abstract JDBCDatabaseConnection getConnection();

    /**
     * Holds the connection lock for the whole BEGIN..COMMIT/ROLLBACK span so concurrent
     * threads (login/save, auction, game logger) cannot interleave statements — or a
     * colliding rollback — into an open transaction on the single shared connection.
     * The lock is reentrant, so statements inside the runnable re-acquire it freely.
     */
    @Override
    public boolean atomically(CheckedRunnable<Exception> runnable) {
        getConnection().getConnectionLock().lock();
        try {
            return super.atomically(runnable);
        } finally {
            getConnection().getConnectionLock().unlock();
        }
    }

    public void withPreparedStatement(
            String query,
            CheckedConsumer<Exception, PreparedStatement> statementConsumer
    ) {
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(query)) {
            statementConsumer.accept(preparedStatement);
        } catch (Exception ex) {
            throw new GameDatabaseException(
                    getClass(),
                    ex.getMessage()
            );
        }
    }

    public <T> T withPreparedStatement(
            String query,
            CheckedFunction<Exception, PreparedStatement, T> statementFunction
    ) {
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(query)) {
            return statementFunction.apply(preparedStatement);
        } catch (Exception ex) {
            throw new GameDatabaseException(
                    getClass(),
                    ex.getMessage()
            );
        }
    }

    public void executeQuery(
            String query,
            CheckedConsumer<Exception, ResultSet> resultSetConsumer
    ) {
        // The ResultSet must be consumed inside the statement's scope: withPreparedStatement
        // closes the PreparedStatement on return, which closes its ResultSet, and a closed
        // sqlite-jdbc ResultSet reads as empty instead of throwing.
        withPreparedStatement(
                query,
                (CheckedConsumer<Exception, PreparedStatement>) statement -> {
                    try (ResultSet resultSet = statement.executeQuery()) {
                        resultSetConsumer.accept(resultSet);
                    }
                }
        );
    }

    public PreparedStatement preparedStatement(String query) throws SQLException {
        return getConnection().prepareStatement(query);
    }
}
