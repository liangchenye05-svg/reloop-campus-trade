package com.campustrade.identity;

public record Account(long id, String username, String displayName, String passwordHash, Role role) {
    public enum Role { USER, ADMIN }

    public record View(long id, String username, String displayName, Role role) {
        public View(Account account) {
            this(account.id, account.username, account.displayName, account.role);
        }
    }

    public record Session(String token, View user) {}
}
