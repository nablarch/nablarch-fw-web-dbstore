package nablarch.common.web.session;

import nablarch.common.schema.TableSchema;

/**
 * セッション有効期限テーブルのスキーマ情報を保持するクラス。
 *
 * @author Goro Kumano
 */
public class SessionExpirationSchema extends TableSchema {

    /** セッションIDカラムの名前 */
    private String sessionIdName;

    /** 有効期限（DATETIME）カラムの名前 */
    private String expirationDatetimeName;

    /**
     * セッションIDカラムの名前を取得する。
     *
     * @return セッションIDカラムの名前
     */
    public String getSessionIdName() {
        return sessionIdName;
    }

    /**
     * セッションIDカラムの名前を設定する。
     *
     * @param sessionIdName セッションIDカラムの名前
     */
    public void setSessionIdName(String sessionIdName) {
        this.sessionIdName = sessionIdName;
    }

    /**
     * 有効期限（DATETIME）カラムの名前を取得する。
     *
     * @return 有効期限（DATETIME）カラムの名前
     */
    public String getExpirationDatetimeName() {
        return expirationDatetimeName;
    }

    /**
     * 有効期限（DATETIME）カラムの名前を設定する。
     *
     * @param expirationDatetimeName 有効期限（DATETIME）カラムの名前
     */
    public void setExpirationDatetimeName(String expirationDatetimeName) {
        this.expirationDatetimeName = expirationDatetimeName;
    }
}
