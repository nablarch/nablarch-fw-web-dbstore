package nablarch.common.web.session;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;

/**
 * 有効期限テーブル。
 */
@Entity
@Table(name = "DB_EXPIRATION")
public class DbExpiration {
    public DbExpiration() {
    }

    public DbExpiration(String sessionId, Timestamp expirationDatetime) {
        this.sessionId = sessionId;
        this.expirationDatetime = expirationDatetime;
    }

    @Id
    @Column(name = "SESSION_ID_COL", nullable = false)
    public String sessionId;

    @Column(name = "EXPIRATION_DATETIME_COL")
    public Timestamp expirationDatetime;
}
