package nablarch.common.web.session.store;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * ユーザセッションテーブル
 *
 */
@Entity
@Table(name = "USER_SESSION_DB")
public class ChangeUserSession {

    public ChangeUserSession() {
    };

    public ChangeUserSession(String sessionId, byte[] sessionObjec, Timestamp expirationDatetime) {
        this.sessionId = sessionId;
        this.sessionObjec = sessionObjec;
        this.expirationDatetime = expirationDatetime;
    }

    @Id
    @Column(name = "SESSION_ID_COL", nullable = false)
    public String sessionId;

    @Lob
    @Column(name = "SESSION_OBJECT_COL")
    public byte[] sessionObjec;

    @Column(name = "EXPIRATION_DATETIME_COL")
    public Timestamp expirationDatetime;
}