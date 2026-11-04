# PostgreSQL

`schema.sql` is a DDL-only snapshot of the current `okoshko_db` structure. It contains no user or sample records. To prepare a fresh database:

```bash
psql -d okoshko_db -f db/schema.sql
psql -d okoshko_db -f db/seed-reference-data.sql
```

The reference seed inserts only the application roles. The SQL files in `patches/` record incremental schema changes and can be applied in numeric order to an earlier schema. Since there is no migration framework, keep the snapshot and the applied patches in sync when the schema changes. The snapshot already includes all five current patches.
