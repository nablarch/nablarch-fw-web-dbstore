package nablarch.common.web.session;

import nablarch.common.web.session.store.UserSession;
import nablarch.fw.ExecutionContext;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.sql.Timestamp;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * {@link DbManagedExpiration}のテスト。
 *
 * @author Goro Kumano
 */
@RunWith(DatabaseTestRunner.class)
public class DbManagedExpirationTest {
    @ClassRule
    public static SystemRepositoryResource repositoryResource = new SystemRepositoryResource(
            "nablarch/common/web/session/db-managed-expiration-test.xml");
    ExecutionContext unused;
    private static final String SESSION_ID = "sessionId";
    private static final String DEFAULT_SCHEMA_COMPONENT = "expiration";
    private static final String ANOTHER_SCHEMA_COMPONENT = "anotherSchema";
    private static final Timestamp BASE_TIMESTAMP = new Timestamp(0);

    @Before

    public void classSetup() {
        VariousDbTestHelper.createTable(UserSession.class);
    }

    /**
     * 現在日時が有効期限より大きい場合、有効期限切れと判定されること。
     */
    @Test
    public void testIsExpiredCurrentTimeLaterThanExpiration() {
        VariousDbTestHelper.createTable(SessionExpiration.class);
        VariousDbTestHelper.setUpTable(new SessionExpiration(SESSION_ID, BASE_TIMESTAMP));

        DbManagedExpiration expiration = repositoryResource.getComponent(DEFAULT_SCHEMA_COMPONENT);
        assertTrue(expiration.isExpired(SESSION_ID, 1, unused));
    }

    /**
     * 現在日時が有効期限より小さい場合、有効期限内と判定されること。
     */
    @Test
    public void testIsExpiredCurrentTimeEarlierThanExpiration() {
        VariousDbTestHelper.createTable(SessionExpiration.class);
        VariousDbTestHelper.setUpTable(new SessionExpiration(SESSION_ID, BASE_TIMESTAMP));

        DbManagedExpiration expiration = repositoryResource.getComponent(DEFAULT_SCHEMA_COMPONENT);
        assertFalse(expiration.isExpired(SESSION_ID, -1, unused));
    }

    /**
     * 現在日時が有効期限と同じ場合、有効期限内と判定されること。
     * （設定でデフォルトと異なるスキーマを指定）
     */
    @Test
    public void testIsExpiredCurrentTimeEqualsExpirationByAnotherSchema() {
        VariousDbTestHelper.createTable(DbExpiration.class);
        VariousDbTestHelper.setUpTable(new DbExpiration(SESSION_ID, BASE_TIMESTAMP));

        DbManagedExpiration expiration = repositoryResource.getComponent(ANOTHER_SCHEMA_COMPONENT);
        assertFalse(expiration.isExpired(SESSION_ID, 0, unused));
    }

    /**
     * トークンテーブルが空の場合、有効期限切れと判定されること。
     */
    @Test
    public void testIsExpiredForEmptyTable() {
        VariousDbTestHelper.createTable(SessionExpiration.class);
        DbManagedExpiration expiration = repositoryResource.getComponent(DEFAULT_SCHEMA_COMPONENT);
        assertTrue(expiration.isExpired(SESSION_ID, 0, unused));
    }

    /**
     * トークンテーブルにレコードが見つからない場合、有効期限切れと判定されること。
     */
    @Test
    public void testIsExpiredForSessionIdNotFound() {
        VariousDbTestHelper.createTable(SessionExpiration.class);
        VariousDbTestHelper.setUpTable(new SessionExpiration("sessionId1", BASE_TIMESTAMP));
        DbManagedExpiration expiration = repositoryResource.getComponent(DEFAULT_SCHEMA_COMPONENT);
        assertTrue(expiration.isExpired("sessionId2", 0, unused));
    }

    /**
     * saveExpirationDateTimeでテーブルに登録されること。
     */
    @Test
    public void testSaveExpirationDateTimeInsert() {
        VariousDbTestHelper.createTable(SessionExpiration.class);
        DbManagedExpiration expiration = repositoryResource.getComponent(DEFAULT_SCHEMA_COMPONENT);
        expiration.saveExpirationDateTime(SESSION_ID, 0, unused);
        SessionExpiration saved = VariousDbTestHelper.findById(SessionExpiration.class, SESSION_ID);
        assertNotNull(saved);
        assertThat(saved.sessionId, is(SESSION_ID));
        assertThat(saved.expirationDatetime, is(BASE_TIMESTAMP));
    }

    /**
     * saveExpirationDateTimeでテーブルに登録されること。
     * （設定でデフォルトと異なるスキーマを指定）
     */
    @Test
    public void testSaveExpirationDateTimeInsertByAnotherSchema() {
        VariousDbTestHelper.createTable(DbExpiration.class);
        VariousDbTestHelper.setUpTable(
                new DbExpiration("sessionId1", new Timestamp(1)),
                new DbExpiration("sessionId2", new Timestamp(2))
        );
        DbManagedExpiration expiration = repositoryResource.getComponent(ANOTHER_SCHEMA_COMPONENT);
        expiration.saveExpirationDateTime(SESSION_ID, 0, unused);
        DbExpiration saved = VariousDbTestHelper.findById(DbExpiration.class, SESSION_ID);
        assertNotNull(saved);
        assertThat(saved.sessionId, is(SESSION_ID));
        assertThat(saved.expirationDatetime, is(BASE_TIMESTAMP));
    }

    /**
     * saveExpirationDateTimeでセッションIDの同じものがある場合は更新されること。
     */
    @Test
    public void testSaveExpirationDateTimeUpdate() {
        VariousDbTestHelper.createTable(SessionExpiration.class);
        VariousDbTestHelper.setUpTable(new SessionExpiration(SESSION_ID, BASE_TIMESTAMP));
        DbManagedExpiration expiration = repositoryResource.getComponent(DEFAULT_SCHEMA_COMPONENT);
        expiration.saveExpirationDateTime(SESSION_ID, 1, unused);
        SessionExpiration saved = VariousDbTestHelper.findById(SessionExpiration.class, SESSION_ID);
        assertNotNull(saved);
        assertThat(saved.sessionId, is(SESSION_ID));
        assertThat(saved.expirationDatetime, is(new Timestamp(1)));
    }
}
