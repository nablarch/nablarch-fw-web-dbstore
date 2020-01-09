package nablarch.common.web.session.store;

import mockit.Expectations;
import mockit.Mocked;
import nablarch.common.web.session.SessionEntry;
import nablarch.common.web.session.SessionExpirationException;
import nablarch.common.web.session.encoder.JavaSerializeStateEncoder;
import nablarch.core.date.SystemTimeProvider;
import nablarch.core.date.SystemTimeUtil;
import nablarch.core.util.StringUtil;
import nablarch.fw.ExecutionContext;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link DbStore}のテスト。
 *
 * @author TIS
 */
@RunWith(DatabaseTestRunner.class)
public class DbStoreTest {

    @ClassRule
    public static SystemRepositoryResource repositoryResource = new SystemRepositoryResource(
            "nablarch/common/web/session/store/db-store-test.xml");

    @Before
    public void classSetup() throws Exception {
        VariousDbTestHelper.createTable(UserSession.class);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Mocked
    private SystemTimeProvider mockSystemTimeProvider;

    /**
     * 基本シナリオの確認。
     */
    @Test
    public void testBasicScenario() throws Exception {

        DbStore store = repositoryResource.getComponent("dbStore");
        store.setStateEncoder(new JavaSerializeStateEncoder());
        store.initialize();
        String unusedId = createSessionId();

        // 1順目 ユーザセッションテーブル delete,insertの確認とsaveしたデータがロードされることを確認
        // save処理
        List<SessionEntry> inEntries = Arrays.asList(
                new SessionEntry("key1", "val1", store),
                new SessionEntry("key2", "val2", store),
                new SessionEntry("key3", "val3", store),
                new SessionEntry("key4", "\uD83C\uDF63\uD83C\uDF63", store));

        ExecutionContext unusedCtx = null;
        store.save(unusedId, inEntries, unusedCtx);

        // load処理
        List<SessionEntry> outEntries1 = store.load(unusedId, unusedCtx);

        assertThat(outEntries1.size(), is(4));

        for (int i = 0; i < outEntries1.size(); i++) {
            final SessionEntry outEntry = outEntries1.get(i);
            final SessionEntry inEntry = inEntries.get(i);
            assertThat(outEntry.getKey(), is(inEntry.getKey()));
            assertThat(outEntry.getValue().toString(), is(inEntry.getValue()));
        }

        // エントリが空の時、ユーザセッションテーブル deleteだけされることの確認（ユーザセッションテーブルがロードされない）
        store.save(unusedId, inEntries, unusedCtx);
        // 処理前検索結果NotNull
        UserSession userSession = VariousDbTestHelper.findById(UserSession.class, unusedId.toString());
        assertNotNull(userSession);
        List<SessionEntry> inEntries2 = Arrays.asList();

        // save処理
        store.save(unusedId, inEntries2, unusedCtx);
        /* 処理後検索結果Null */
        userSession = VariousDbTestHelper.findById(UserSession.class, unusedId.toString());
        assertNull(userSession);

        // load処理
        List<SessionEntry> outEntries2 = store.load(unusedId, unusedCtx);

        assertTrue(outEntries2.isEmpty());

        // エントリがnullの時、ユーザセッションテーブル deleteだけされることの確認（ユーザセッションテーブルがロードされない）
        store.save(unusedId, inEntries, unusedCtx);
        /* 処理前検索結果NotNull */
        userSession = VariousDbTestHelper.findById(UserSession.class, unusedId.toString());
        assertNotNull(userSession);
        List<SessionEntry> inEntries3 = null;
        // save処理
        store.save(unusedId, inEntries3, unusedCtx);
        /* 処理後検索結果Null */
        userSession = VariousDbTestHelper.findById(UserSession.class, unusedId.toString());
        assertNull(userSession);

        // load処理
        List<SessionEntry> outEntries3 = store.load(unusedId, unusedCtx);

        assertTrue(outEntries3.isEmpty());
    }

    /**
     * デフォルトユーザセッションスキーマ適用の確認。
     */
    @Test
    public void testDefaultSchema() throws Exception {
        DbStore store = repositoryResource.getComponent("dbStore");
        store.setStateEncoder(new JavaSerializeStateEncoder());
        String unusedId = createSessionId();

        // セットされているユーザセッションスキーマをクリア
        store.setUserSessionSchema(null);
        store.initialize();
        // save処理
        List<SessionEntry> inEntries = Arrays.asList(
                new SessionEntry("key4", "val4", store),
                new SessionEntry("key5", "val5", store),
                new SessionEntry("key6", "val6", store),
                new SessionEntry("key7", "[\uD840\uDC0B\uD840\uDC0B]", store));

        ExecutionContext unusedCtx = new ExecutionContext();
        store.save(unusedId, inEntries, unusedCtx);

        // load処理
        List<SessionEntry> outEntries = store.load(unusedId, unusedCtx);

        assertThat(outEntries.size(), is(4));
        for (int i = 0; i < outEntries.size(); i++) {
            final SessionEntry outEntry = outEntries.get(i);
            final SessionEntry inEntry = inEntries.get(i);
            assertThat(outEntry.getKey(), is(inEntry.getKey()));
            assertThat(outEntry.getValue().toString(), is(inEntry.getValue()));
        }
    }

    /**
     * ユーザセッションスキーマ変更の確認。
     */
    @Test
    public void testChangeSchema() throws Exception {
        String unusedId = createSessionId();
        DbStore store = repositoryResource.getComponent("changeSchema");
        store.setStateEncoder(new JavaSerializeStateEncoder());
        store.initialize();

        VariousDbTestHelper.createTable(ChangeUserSession.class);
        // save処理
        List<SessionEntry> inEntries = Arrays.asList(
                new SessionEntry("key4", "val4", store),
                new SessionEntry("key5", "val5", store),
                new SessionEntry("key6", "val6", store),
                new SessionEntry("key7", "\uD844\uDE3D\uD844\uDE3D", store));

        ExecutionContext unusedCtx = new ExecutionContext();
        store.save(unusedId, inEntries, unusedCtx);

        /* changeしたテーブルにユーザセッションデータが保存されていることを確認 */
        ChangeUserSession changeUserSession = VariousDbTestHelper.findById(ChangeUserSession.class, unusedId);
        assertNotNull(changeUserSession);
        /* change前のテーブルにユーザセッションデータが保存されていないことを確認 */
        UserSession userSession = VariousDbTestHelper.findById(UserSession.class, unusedId);
        assertNull(userSession);

        // load処理
        List<SessionEntry> outEntries = store.load(unusedId, unusedCtx);

        assertThat(outEntries.size(), is(4));
        for (int i = 0; i < outEntries.size(); i++) {
            final SessionEntry outEntry = outEntries.get(i);
            final SessionEntry inEntry = inEntries.get(i);
            assertThat(outEntry.getKey(), is(inEntry.getKey()));
            assertThat(outEntry.getValue().toString(), is(inEntry.getValue()));
        }
    }

    /**
     * 期限切れでないユーザセッションテーブルがロードされ、
     * 期限切れのユーザセッションテーブルがロードされない確認。
     */
    @Test
    public void testTimeOverUserSession() throws Exception {
        new Expectations() {{
            final Timestamp timestamp = Timestamp.valueOf("2015-03-18 16:21:00");
            mockSystemTimeProvider.getDate();
            minTimes = 0;
            result = new Date(timestamp.getTime());
            
            mockSystemTimeProvider.getTimestamp();
            minTimes = 0;
            result = timestamp;
        }};
        DbStore store = repositoryResource.getComponent("dbStore");
        store.setStateEncoder(new JavaSerializeStateEncoder());
        store.initialize();
        String unusedId = createSessionId();

        repositoryResource.addComponent("systemTimeProvider", mockSystemTimeProvider);

        // save処理
        List<SessionEntry> inEntries = Arrays.asList(
                new SessionEntry("key1", "val1", store),
                new SessionEntry("key2", "val2", store),
                new SessionEntry("key3", "val3", store));

        ExecutionContext unusedCtx = new ExecutionContext();
        store.save(unusedId, inEntries, unusedCtx);

        // ユーザセッションテーブルを期限切れ直前(処理日時とイコール)にする。
        UserSession userSession = VariousDbTestHelper.findById(UserSession.class, unusedId);
        userSession.expirationDatetime = new Timestamp(SystemTimeUtil.getTimestamp()
                .getTime());
        VariousDbTestHelper.update(userSession);

        //  期限切れ直前なのでロードされる
        List<SessionEntry> outEntries1 = store.load(unusedId, unusedCtx);
        assertTrue(!outEntries1.isEmpty());

        // ユーザセッションテーブルを期限切れ(処理日時－１)にする。
        userSession = VariousDbTestHelper.findById(UserSession.class, unusedId);
        userSession.expirationDatetime = new Timestamp(SystemTimeUtil.getTimestamp()
                .getTime() - 10);
        VariousDbTestHelper.update(userSession);

        /* 期限切れの場合Exceptionがthrowされる */
        try {
            List<SessionEntry> outEntries2 = store.load(unusedId, unusedCtx);
            fail();
        } catch (SessionExpirationException expected) {
            // 期限切れでもエントリはExceptionに保持される
            List<SessionEntry> outEntries = expected.getSessionEntries();
            assertThat(outEntries.size(), is(3));
            for (int i = 0; i < outEntries.size(); i++) {
                final SessionEntry outEntry = outEntries.get(i);
                final SessionEntry inEntry = inEntries.get(i);
                assertThat(outEntry.getKey(), is(inEntry.getKey()));
                assertThat(outEntry.getValue().toString(), is(inEntry.getValue()));
            }
        }
    }

    /**
     * delete呼び出しで、セッションの内容が削除されること。
     */
    @Test
    public void testDelete() throws Exception {

        VariousDbTestHelper.insert(
                new UserSession("sid-1", null, null),
                new UserSession("sid-2", null, null),
                new UserSession("sid-3", null, null));

        assertThat(
                "3件だけセッションデータを格納しておく",
                VariousDbTestHelper.findAll(UserSession.class).size(), is(3));

        final DbStore sut = repositoryResource.getComponent("dbStore");
        sut.initialize();

        final ExecutionContext unusedCtx = new ExecutionContext();
        sut.delete("sid-2", unusedCtx);

        assertThat(
                "invalidateしたのでセッションデータが2件になっていること",
                VariousDbTestHelper.findAll(UserSession.class).size(), is(2));
        assertThat(
                "sid-2が存在しないこと",
                VariousDbTestHelper.findById(UserSession.class, "sid-2"), is(nullValue()));
    }

    /**
     * invalidate呼び出しで、セッションの内容が削除されること。
     */
    @Test
    public void testInvalidate() throws Exception {

        VariousDbTestHelper.insert(
                new UserSession("sid-1", null, null),
                new UserSession("sid-2", null, null),
                new UserSession("sid-3", null, null));

        assertThat(
                "3件だけセッションデータを格納しておく",
                VariousDbTestHelper.findAll(UserSession.class).size(), is(3));

        final DbStore sut = repositoryResource.getComponent("dbStore");
        sut.initialize();

        final ExecutionContext unusedCtx = new ExecutionContext();
        sut.invalidate("sid-2", unusedCtx);

        assertThat(
                "invalidateしたのでセッションデータが2件になっていること",
                VariousDbTestHelper.findAll(UserSession.class).size(), is(2));
        assertThat(
                "sid-2が存在しないこと",
                VariousDbTestHelper.findById(UserSession.class, "sid-2"), is(nullValue()));
    }

    /**
     * 複数スレッドで同時に保存処理を行った場合でも、例外などは発生しないこと。
     */
    @Test
    public void testMultiThreadAccess() throws Exception {
        final DbStore sut = repositoryResource.getComponent("dbStore");
        sut.setStateEncoder(new JavaSerializeStateEncoder());
        sut.initialize();

        final ExecutorService executorService = Executors.newFixedThreadPool(20);

        try {
            List<Future<Object>> futures = new ArrayList<Future<java.lang.Object>>(20);
            for (int i = 0; i < 20; i++) {
                futures.add(executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        final List<SessionEntry> entries = new ArrayList<SessionEntry>();
                        entries.add(new SessionEntry("hoge", "fuga", sut));
                        sut.save("12345", entries, new ExecutionContext());
                    }
                }, null));
            }

            for (Future<Object> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executorService.shutdownNow();
        }

        final List<SessionEntry> actual = sut.load("12345", new ExecutionContext());
        assertThat(actual, contains(
                allOf(
                        hasProperty("key", is("hoge")),
                        hasProperty("value", is("fuga"))
                )
        ));
    }

    private static Comparator<SessionEntry> keySort = new Comparator<SessionEntry>() {
        @Override
        public int compare(SessionEntry o1, SessionEntry o2) {
            return o1.getKey()
                    .compareTo(o2.getKey());
        }
    };

    private String createSessionId() throws Exception {
        String uuid = UUID.randomUUID()
                .toString();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.reset();
        md.update(StringUtil.getBytes(uuid, Charset.forName("UTF-8")));

        byte[] digest = md.digest();

        StringBuilder sb = new StringBuilder();
        int length = digest.length;
        for (int i = 0; i < length; i++) {
            int b = (0xFF & digest[i]);
            if (b < 16) {
                sb.append("0");
            }
            sb.append(Integer.toHexString(b));
        }
        return uuid + sb;
    }
}
