# Migration conventions

New database instances execute `../schema/001_init.sql`. Append subsequent ordered SQL migrations in this directory and apply each once before deploying the corresponding application version. Keep initial credentials in deployment environment variables. Public registration creates USER accounts, and role promotion is an explicit database administration operation.
