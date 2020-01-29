package nablarch.common.web.session;

import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.SqlResultSet;
import nablarch.core.db.transaction.SimpleDbTransactionExecutor;
import nablarch.core.db.transaction.SimpleDbTransactionManager;
import nablarch.core.repository.initialization.Initializable;
import nablarch.fw.ExecutionContext;

import java.sql.Timestamp;

/**
 * DBを使用した{@link Expiration}実装クラス。
 *
 * @author Goro Kumano
 */
public class DbManagedExpiration implements Expiration, Initializable {
    /** SimpleDbTransactionManagerのインスタンス */
    private SimpleDbTransactionManager dbManager;

    /** 有効期限テーブルのスキーマ */
    private SessionExpirationSchema sessionExpirationSchema;

    /** 有効期限を取得するSQL */
    private String selectUserSessionSql;

    /** 有効期限を追加するSQL */
    private String insertUserSessionSql;

    /** 有効期限を更新するSQL */
    private String updateUserSessionSql;

    /**
     * DbManagerのインスタンスをセットする。
     *
     * @param dbManager SimpleDbTransactionManagerのインスタンス
     */
    public void setDbManager(SimpleDbTransactionManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * 有効期限テーブルのスキーマをセットする。
     *
     * @param sessionExpirationSchema 有効期限テーブルのスキーマ
     */
    public void setSessionExpirationSchema(SessionExpirationSchema sessionExpirationSchema) {
        this.sessionExpirationSchema = sessionExpirationSchema;
    }

    @Override
    public boolean isExpired(final String sessionID, long currentDateTime, ExecutionContext context) {
        SqlResultSet sessionRecords = new SimpleDbTransactionExecutor<SqlResultSet>(dbManager) {
            @Override
            public SqlResultSet execute(AppDbConnection connection) {
                // 有効期限テーブルをロードする
                SqlPStatement prepared = connection
                        .prepareStatement(selectUserSessionSql);
                prepared.setString(1, sessionID);
                return prepared.retrieve();
            }
        }.doTransaction();

        if (sessionRecords == null || sessionRecords.isEmpty()) {
            return true;
        }
        long expiration = sessionRecords.get(0)
                .getTimestamp(sessionExpirationSchema.getExpirationDatetimeName()).getTime();
        return expiration < currentDateTime;
    }

    @Override
    public void saveExpirationDateTime(final String sessionId, final long expirationDateTime, ExecutionContext context) {
        new SimpleDbTransactionExecutor<Void>(dbManager) {
            @Override
            public Void execute(AppDbConnection connection) {
                // 更新処理を行い更新対象がない場合は登録処理を行う
                int count = updateSessionExpiration(sessionId, expirationDateTime, connection);
                if (count == 0) {
                    insertSessionExpiration(sessionId, expirationDateTime, connection);
                }
                return null;
            }
        }.doTransaction();
    }

    /**
     * 有効期限を更新する。
     *
     * @param sessionId          セッションID
     * @param expirationDateTime 有効期限
     * @param connection         {@link AppDbConnection}
     * @return 更新件数
     */
    private int updateSessionExpiration(final String sessionId, final long expirationDateTime,
                                        final AppDbConnection connection) {
        final SqlPStatement update = connection.prepareStatement(updateUserSessionSql);
        update.setTimestamp(1, new Timestamp(expirationDateTime));
        update.setString(2, sessionId);
        return update.executeUpdate();
    }

    /**
     * 有効期限を挿入する。
     *
     * @param sessionId          セッションID
     * @param expirationDateTime 有効期限
     * @param connection         {@link AppDbConnection}
     */
    private void insertSessionExpiration(final String sessionId, final long expirationDateTime,
                                         final AppDbConnection connection) {
        final SqlPStatement insertStatement = connection.prepareStatement(insertUserSessionSql);
        insertStatement.setString(1, sessionId);
        insertStatement.setTimestamp(2, new Timestamp(expirationDateTime));
        insertStatement.executeUpdate();
    }

    @Override
    public void initialize() {
        if (sessionExpirationSchema == null) {
            // デフォルトの有効期限スキーマをセットする
            sessionExpirationSchema = new SessionExpirationSchema();
            sessionExpirationSchema.setTableName("SESSION_EXPIRATION");
            sessionExpirationSchema.setSessionIdName("SESSION_ID");
            sessionExpirationSchema.setExpirationDatetimeName("EXPIRATION_DATETIME");
        }

        // SQL文を初期化する。
        selectUserSessionSql = "SELECT " + sessionExpirationSchema.getExpirationDatetimeName()
                + " FROM " + sessionExpirationSchema.getTableName() + " " + " WHERE "
                + sessionExpirationSchema.getSessionIdName() + " = ? ";

        insertUserSessionSql = "INSERT INTO "
                + sessionExpirationSchema.getTableName() + " ( "
                + sessionExpirationSchema.getSessionIdName() + ", "
                + sessionExpirationSchema.getExpirationDatetimeName()
                + ") VALUES (?,?)";

        updateUserSessionSql = "UPDATE " + sessionExpirationSchema.getTableName()
                + " SET " + sessionExpirationSchema.getExpirationDatetimeName() + "=?"
                + " WHERE " + sessionExpirationSchema.getSessionIdName() + " = ?";
    }
}
