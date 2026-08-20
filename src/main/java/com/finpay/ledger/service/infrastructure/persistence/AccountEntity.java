package com.finpay.ledger.service.infrastructure.persistence;

import com.finpay.ledger.service.domain.Account;
import com.finpay.ledger.service.domain.Account.AccountStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Currency;

@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    private String accountId;
    private String ownerId;
    private String currency;
    private String status;

    public AccountEntity() {}

    public static AccountEntity from(Account a) {
        AccountEntity e = new AccountEntity();
        e.accountId = a.accountId();
        e.ownerId = a.ownerId();
        e.currency = a.currency().getCurrencyCode();
        e.status = a.status().name();
        return e;
    }

    public Account toDomain() {
        return new Account(accountId, ownerId,
                Currency.getInstance(currency), AccountStatus.valueOf(status));
    }

    public String getAccountId() { return accountId; }
    public void setAccountId(String v) { this.accountId = v; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String v) { this.ownerId = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
}
