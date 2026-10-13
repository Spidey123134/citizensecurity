"""Check actual account SQL on an isolated file, without executing Android repository code."""
import base64
import sqlite3
import sys
from pathlib import Path

scenario, create_sql, insert_sql, lookup_sql, verifier = [
    base64.b64decode(line, validate=True).decode("utf-8")
    for line in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
]
path = Path(sys.argv[1]).parent / "accounts.db"
first = ("a142cefe-f76b-44de-890e-d3a8b6630190", "ana_123", verifier)
second = ("b142cefe-f76b-44de-890e-d3a8b6630190", "otro_123", verifier)


def rows(database):
    return database.execute("SELECT id, username, password_verifier FROM local_accounts ORDER BY username").fetchall()


def state(database):
    return (
        rows(database),
        database.execute("SELECT type, name, sql FROM sqlite_master ORDER BY name").fetchall(),
        database.execute("PRAGMA user_version").fetchone()[0],
        database.total_changes,
    )


def rejected(database, values):
    before = rows(database)
    try:
        database.execute(insert_sql, values)
    except sqlite3.IntegrityError:
        pass
    else:
        raise AssertionError("An invalid/duplicate account was accepted")
    assert rows(database) == before


database = sqlite3.connect(path, isolation_level=None)
try:
    database.execute(create_sql)
    database.execute("PRAGMA user_version = 1")
    database.execute(insert_sql, first)
    if scenario == "reopen":
        before = rows(database)
        database.close()
        database = sqlite3.connect(path, isolation_level=None)
        assert rows(database) == before == [first]
        assert database.execute(lookup_sql, (first[1],)).fetchall() == [first]
        assert database.execute("PRAGMA user_version").fetchone()[0] == 1
        columns = [row[1] for row in database.execute("PRAGMA table_info(local_accounts)")]
        assert columns == ["id", "username", "password_verifier"]
        assert "password" not in columns
    elif scenario == "unique":
        rejected(database, (second[0], first[1], "a different verifier"))
        rejected(database, (first[0], second[1], "a different verifier"))
        assert database.execute(lookup_sql, (first[1],)).fetchall() == [first]
    elif scenario == "constraints":
        invalid_users = ["ab", "a" * 31, "ANA", "admin", "José", "a b", "a'b", "abc\0suffix", None]
        for username in invalid_users:
            rejected(database, (second[0], username, verifier))
        invalid_ids = ["short", "x" * 36, second[0].upper(), second[0].replace("-", "a", 1), second[0] + "\0", None]
        for identifier in invalid_ids:
            rejected(database, (identifier, second[1], verifier))
        for encoded in ["", "x" * 161, "prefix\0suffix", None]:
            rejected(database, (second[0], second[1], encoded))
        # Maxima are accepted without silently shortening persisted values.
        maximum = (second[0], "a" * 30, "x" * 160)
        database.execute(insert_sql, maximum)
        assert database.execute(lookup_sql, (maximum[1],)).fetchall() == [maximum]
    elif scenario == "rollback":
        database.execute("BEGIN IMMEDIATE")
        database.execute(insert_sql, second)
        assert database.execute(lookup_sql, (second[1],)).fetchall() == [second]
        database.execute("ROLLBACK")
        assert database.execute(lookup_sql, (second[1],)).fetchall() == []
        assert rows(database) == [first]
        database.close()
        database = sqlite3.connect(path, isolation_level=None)
        assert rows(database) == [first]
    elif scenario == "missing":
        before = state(database)
        database.execute("PRAGMA query_only = ON")
        assert database.execute(lookup_sql, ("missing_123",)).fetchall() == []
        assert state(database) == before
    elif scenario == "literal":
        database.execute(insert_sql, second)
        before = state(database)
        database.execute("PRAGMA query_only = ON")
        for literal in ["ana_123' OR 1=1 --", "' OR 1=1 --", "ana_123%", "ana_123\0suffix"]:
            assert database.execute(lookup_sql, (literal,)).fetchall() == []
        assert database.execute(lookup_sql, (first[1],)).fetchall() == [first]
        assert state(database) == before
    else:
        raise AssertionError("Unknown account SQL scenario")
finally:
    database.close()

print("ok:" + scenario)
