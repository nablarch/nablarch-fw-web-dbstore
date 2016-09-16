package nablarch.common.web.session.store;

import nablarch.common.schema.TableSchema;

/**
 * ユーザセッションテーブルのスキーマ情報を保持するクラス。
 *
 * @author TIS
 */
public final class UserSessionSchema extends TableSchema {

    /** セッションIDカラムの名前 */
    private String sessionIdName;

    /** セッションオブジェクトカラムの名前 */
    private String sessionObjectName;

    /** 有効期限（DATETIME）カラムの名前 */
    private String expirationDatetimeName;

    /**
     * セッションIDカラムの名前を取得する。
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
     * セッションオブジェクトカラムの名前を取得する。
     *
     * @return セッションオブジェクトカラムの名前
     */
    public String getSessionObjectName() {
        return sessionObjectName;
    }

    /**
     * セッションオブジェクトカラムの名前を設定する。
     *
     * @param sessionObjectName セッションオブジェクトカラムの名前
     */
    public void setSessionObjectName(String sessionObjectName) {
        this.sessionObjectName = sessionObjectName;
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
