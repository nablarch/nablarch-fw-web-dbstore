package nablarch.common.web.session.store;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

import nablarch.common.web.session.SessionEntry;
import nablarch.common.web.session.SessionStore;
import nablarch.core.date.SystemTimeUtil;
import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.statement.ResultSetIterator;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.exception.DuplicateStatementException;
import nablarch.core.db.transaction.SimpleDbTransactionExecutor;
import nablarch.core.db.transaction.SimpleDbTransactionManager;
import nablarch.core.repository.initialization.Initializable;
import nablarch.fw.ExecutionContext;

/**
 * セッションの内容をDBに格納/読み込みする{@link DbStore}。
 * <p/>
 * デフォルトのストア名は"db"。
 *
 * @author TIS
 */
public class DbStore extends SessionStore implements Initializable {

    /**
     * SimpleDbTransactionManagerのインスタンス。
     */
    private SimpleDbTransactionManager dbManager;

    /**
     * ユーザセッションテーブルのスキーマ。
     */
    private UserSessionSchema userSessionSchema;

    /** ユーザセッションテーブルを取得するSQL */
    private String selectUserSessionSql;

    /** ユーザセッションテーブルを追加するSQL */
    private String insertUserSessionSql;

    /** ユーザセッションテーブルを削除するSQL */
    private String deleteUserSessionSql;

    /**
     * コンストラクタ。
     */
    public DbStore() {
        super("db");
    }

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
    public List<SessionEntry> load(final String sessionId,
            ExecutionContext executionContext) {
        return new SimpleDbTransactionExecutor<List<SessionEntry>>(dbManager) {
            @Override
            public List<SessionEntry> execute(AppDbConnection connection) {
                // ユーザセッションテーブルをロードする
                SqlPStatement prepared = connection
                        .prepareStatement(selectUserSessionSql);
                prepared.setString(1, sessionId);
                prepared.setTimestamp(2, new Timestamp(SystemTimeUtil
                        .getTimestamp().getTime()));
                
                ResultSetIterator iterator = prepared.executeQuery();
                if (iterator.next()) {
                    return decode(iterator.getBytes(1));
                } else {
                    return Collections.emptyList();
                }
            }
        }.doTransaction();

    }

    /**
     * ユーザセッションテーブルにセッション情報を保存する。
     * <p>
     * 既にユーザセッションテーブルにセッションIDのレコードが保存されていた場合は、
     * そのレコードを削除後に保存処理を行う。
     * <p>
     * 新規でセッション情報を保存する場合で複数スレッドから同時に本処理が呼び出された場合、
     * 登録処理(insert)が同時実行され片方の処理が一意制約違反となる。
     * このため、一意制約違反が発生した場合には、1回だけリトライを実施する。
     * リトライ時には、既にレコードが存在しているため、削除処理(delete処理)で排他ロックがかかるため一意制約違反が発生することはない。
     */
    @Override
    public void save(final String sessionId, final List<SessionEntry> entries,
                       ExecutionContext executionContext) {
        try {
            deleteAndInsert(sessionId, entries);
        } catch (DuplicateStatementException e) {
            // 一意制約違反発生時には、一度だけリトライを行う。
            deleteAndInsert(sessionId, entries);
        }
    }

    @Override
    public void delete(final String sessionId, final ExecutionContext executionContext) {
        new SimpleDbTransactionExecutor<Void>(dbManager) {
            @Override
            public Void execute(AppDbConnection connection) {
                deleteUserSession(sessionId, connection);
                return null;
            }
        }.doTransaction();
    }

    @Override
    public void invalidate(final String sessionId, final ExecutionContext executionContext) {
        delete(sessionId, executionContext);
    }

    /**
     * ユーザセッションテーブルにセッション情報を登録する。
     * <p>
     * 既にセッションIDに紐づくセッション情報が登録されている場合には、削除後に登録処理を行う。
     *
     * @param sessionId セッションID
     * @param entries セッションに保存する情報
     */
    private void deleteAndInsert(final String sessionId, final List<SessionEntry> entries) {
        new SimpleDbTransactionExecutor<Void>(dbManager) {
            @Override
            public Void execute(AppDbConnection connection) {
                deleteUserSession(sessionId, connection);
                if (entries != null && !entries.isEmpty()) {
                    insertUserSession(sessionId, entries, connection);
                }
                return null;
            }
        }.doTransaction();
    }

    /**
     * ユーザセッションテーブルにセッションの内容を挿入する。
     *
     * @param sessionId セッションID
     * @param entries セッションエントリ
     * @param connection {@link AppDbConnection}
     */
    private void insertUserSession(
            final String sessionId, final List<SessionEntry> entries, final AppDbConnection connection) {
        final SqlPStatement insertStatement = connection.prepareStatement(insertUserSessionSql);
        insertStatement.setString(1, sessionId);
        insertStatement.setBytes(2, encode(entries));
        insertStatement.setTimestamp(3, new Timestamp(SystemTimeUtil.getTimestamp().getTime()
                + getExpiresMilliSeconds()));
        insertStatement.executeUpdate();
    }

    /**
     * ユーザセッションテーブルからセッションの内容を削除する。
     *
     * @param sessionId セッションID
     * @param connection {@link AppDbConnection}
     */
    private void deleteUserSession(final String sessionId, final AppDbConnection connection) {
        final SqlPStatement deleteStatement = connection
                .prepareStatement(deleteUserSessionSql);
        deleteStatement.setString(1, sessionId);
        deleteStatement.executeUpdate();
    }

    /**
     * 初期化処理。
     */
    public void initialize() {
        if (userSessionSchema == null) {
            // デフォルトのユーザセッションスキーマをセットする
            userSessionSchema = new UserSessionSchema();
            userSessionSchema.setTableName("USER_SESSION");
            userSessionSchema.setSessionIdName("SESSION_ID");
            userSessionSchema.setSessionObjectName("SESSION_OBJECT");
            userSessionSchema.setExpirationDatetimeName("EXPIRATION_DATETIME");
        }

        // SQL文を初期化する。
        selectUserSessionSql = "SELECT " + userSessionSchema.getSessionObjectName()
                + " FROM " + userSessionSchema.getTableName() + " " + " WHERE "
                + userSessionSchema.getSessionIdName() + " = ? " + " AND "
                + userSessionSchema.getExpirationDatetimeName() + " >= ?";

        insertUserSessionSql = "INSERT INTO "
                + userSessionSchema.getTableName() + " ( "
                + userSessionSchema.getSessionIdName() + ", "
                + userSessionSchema.getSessionObjectName() + ", "
                + userSessionSchema.getExpirationDatetimeName()
                + ") VALUES (?,?,?)";

        deleteUserSessionSql = "DELETE FROM "
                + userSessionSchema.getTableName() + " WHERE "
                + userSessionSchema.getSessionIdName() + " = ?";

    }
}
