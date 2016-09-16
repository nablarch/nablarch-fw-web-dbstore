package nablarch.common.web.session.store;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

/**
 * ユーザセッションテーブル
 *
 */
@Entity
@Table(name = "USER_SESSION")
public class UserSession {

    public UserSession() {
    };

    public UserSession(String sessionId, byte[] sessionObjec, Timestamp expirationDatetime) {
        this.sessionId = sessionId;
        this.sessionObjec = sessionObjec;
        this.expirationDatetime = expirationDatetime;
    }

    @Id
    @Column(name = "SESSION_ID", nullable = false)
    public String sessionId;

    @Lob
    @Column(name = "SESSION_OBJECT")
    public byte[] sessionObjec;

    @Column(name = "EXPIRATION_DATETIME")
    public Timestamp expirationDatetime;
}