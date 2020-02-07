package nablarch.common.web.session;

import nablarch.common.web.session.store.UserSessionSchema;
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

    /** ユーザセッションテーブルのスキーマ */
    private UserSessionSchema userSessionSchema;

    /** 有効期限を取得するSQL */
    private String selectUserSessionSql;

    /** 有効期限を追加するSQL */
    private String insertUserSessionSql;

    /** 有効期限を更新するSQL */
    private String updateUserSessionSql;

    /** 有効期限の件数を取得するSQL */
    private String countUserSessionSql;

    /** 有効期限の件数エイリアス **/
    private static final String COUNT = "COUNT_";

    /**
     * DbManagerのインスタンスをセットする。
     *
     * @param dbManager SimpleDbTransactionManagerのインスタンス
     */
    public void setDbManager(SimpleDbTransactionManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * ユーザセッションテーブルのスキーマをセットする。
     *
     * @param userSessionSchema ユーザセッションテーブルのスキーマ
     */
    public void setUserSessionSchema(UserSessionSchema userSessionSchema) {
        this.userSessionSchema = userSessionSchema;
    }

    @Override
    public boolean isExpired(final String sessionId, long currentDateTime, ExecutionContext context) {
        SqlResultSet sessionRecords = new SimpleDbTransactionExecutor<SqlResultSet>(dbManager) {
            @Override
            public SqlResultSet execute(AppDbConnection connection) {
                // 有効期限を取得する
                SqlPStatement prepared = connection
                        .prepareStatement(selectUserSessionSql);
                prepared.setString(1, sessionId);
                return prepared.retrieve();
            }
        }.doTransaction();

        if (sessionRecords == null || sessionRecords.isEmpty()) {
            return true;
        }
        long expiration = sessionRecords.get(0)
                .getTimestamp(userSessionSchema.getExpirationDatetimeName()).getTime();
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
                    // 主キーとなるセッションIDはUUIDV4で払い出すため一意制約違反となることは考慮不要
                    insertSessionExpiration(sessionId, expirationDateTime, connection);
                }
                return null;
            }
        }.doTransaction();
    }

    @Override
    public boolean isDeterminable(final String sessionId, ExecutionContext context) {
        return new SimpleDbTransactionExecutor<Boolean>(dbManager) {
            @Override
            public Boolean execute(AppDbConnection connection) {
                // 有効期限を取得する
                SqlPStatement prepared = connection
                        .prepareStatement(countUserSessionSql);
                prepared.setString(1, sessionId);
                return prepared.retrieve().get(0).getInteger(COUNT) > 0;
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
        if (userSessionSchema == null) {
            // デフォルトのユーザセッションスキーマをセットする
            userSessionSchema = new UserSessionSchema();
            userSessionSchema.setTableName("USER_SESSION");
            userSessionSchema.setSessionIdName("SESSION_ID");
            userSessionSchema.setExpirationDatetimeName("EXPIRATION_DATETIME");
        }

        // SQL文を初期化する。
        selectUserSessionSql = "SELECT " + userSessionSchema.getExpirationDatetimeName()
                + " FROM " + userSessionSchema.getTableName() + " WHERE "
                + userSessionSchema.getSessionIdName() + " = ? ";

        countUserSessionSql = "SELECT COUNT(" + userSessionSchema.getExpirationDatetimeName() + ") " + COUNT
                + " FROM (" + selectUserSessionSql + ") SUB_";

        insertUserSessionSql = "INSERT INTO "
                + userSessionSchema.getTableName() + " ( "
                + userSessionSchema.getSessionIdName() + ", "
                + userSessionSchema.getExpirationDatetimeName()
                + ") VALUES (?,?)";

        updateUserSessionSql = "UPDATE " + userSessionSchema.getTableName()
                + " SET " + userSessionSchema.getExpirationDatetimeName() + "=?"
                + " WHERE " + userSessionSchema.getSessionIdName() + " = ?";
    }
}
