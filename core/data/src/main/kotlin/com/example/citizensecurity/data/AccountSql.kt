package com.example.citizensecurity.data

/** SQL productivo compartido con la comprobación SQLite del host, sin dependencias Android. */
internal object AccountSql {
    val CREATE_TABLE = """
        CREATE TABLE local_accounts (
            id TEXT NOT NULL PRIMARY KEY CHECK (
                length(id) = 36 AND id = lower(id) AND instr(id, char(0)) = 0
                AND substr(id, 9, 1) = '-' AND substr(id, 14, 1) = '-'
                AND substr(id, 19, 1) = '-' AND substr(id, 24, 1) = '-'
                AND length(replace(id, '-', '')) = 32 AND id NOT GLOB '*[^0-9a-f-]*'
            ),
            username TEXT NOT NULL UNIQUE CHECK (
                length(username) BETWEEN 3 AND 30 AND username = lower(username)
                AND username NOT GLOB '*[^a-z0-9_]*' AND username <> 'admin'
                AND instr(username, char(0)) = 0
            ),
            password_verifier TEXT NOT NULL CHECK (
                length(password_verifier) BETWEEN 1 AND 160 AND instr(password_verifier, char(0)) = 0
            )
        )
    """.trimIndent()

    const val LOOKUP = "SELECT id, username, password_verifier FROM local_accounts WHERE username = ? LIMIT 1"
    const val INSERT = "INSERT INTO local_accounts (id, username, password_verifier) VALUES (?, ?, ?)"
}
